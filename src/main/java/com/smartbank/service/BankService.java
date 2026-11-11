package com.smartbank.service;

import com.smartbank.config.AppConfig;
import com.smartbank.exception.AccountLockedException;
import com.smartbank.exception.AccountNotFoundException;
import com.smartbank.exception.AuthenticationException;
import com.smartbank.exception.ValidationException;
import com.smartbank.factory.AccountFactory;
import com.smartbank.model.Account;
import com.smartbank.model.AccountSnapshot;
import com.smartbank.model.AccountType;
import com.smartbank.model.Customer;
import com.smartbank.model.Transaction;
import com.smartbank.model.TransactionType;
import com.smartbank.observer.AuditLogger;
import com.smartbank.observer.NotificationService;
import com.smartbank.observer.TransactionListener;
import com.smartbank.persistence.FileSnapshotStore;
import com.smartbank.persistence.FileTransactionLog;
import com.smartbank.persistence.SnapshotStore;
import com.smartbank.persistence.TransactionLog;
import com.smartbank.repository.AccountRepository;
import com.smartbank.repository.InMemoryAccountRepository;
import com.smartbank.security.PinHasher;
import com.smartbank.util.Money;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The banking engine.
 *
 * <h2>Concurrency rules (read before changing anything)</h2>
 * <ol>
 *   <li>Every account has its own {@code ReentrantLock}; there is no global lock on the hot path.</li>
 *   <li>Single-account operations lock only that account.</li>
 *   <li><b>Whenever more than one account lock is needed, locks are taken in ascending account id.</b>
 *       Transfers and {@link #snapshot()} both follow this order, so a lock cycle (deadlock) is impossible.</li>
 *   <li>Ledger entries are published while the account lock is held, so per-account order in the log is the
 *       real order of the operations. Listeners must therefore be fast and must never lock accounts.</li>
 * </ol>
 *
 * <p>Singleton: {@link #getInstance()} gives the application-wide instance (initialization-on-demand holder idiom,
 * thread-safe and lazy without any explicit synchronization). The public constructor exists for dependency
 * injection, which is what the tests use.
 */
public final class BankService implements AutoCloseable {
    private static final Logger LOG = Logger.getLogger(BankService.class.getName());
    private static final int MAX_STATEMENT_ENTRIES = 1000;

    private final AccountRepository accounts;
    private final TransactionLog transactionLog;
    private final SnapshotStore snapshotStore;
    private final List<TransactionListener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicLong accountIds = new AtomicLong();
    private final AtomicLong transactionIds;
    private final AtomicReference<BigDecimal> feesCollected = new AtomicReference<>(Money.ZERO);
    private final Object saveLock = new Object();
    private final AtomicBoolean closed = new AtomicBoolean();
    private ScheduledExecutorService scheduler; // guarded by 'this'

    // ------------------------------------------------------------------ singleton

    private static final class Holder {
        static final BankService INSTANCE = createDefault(AppConfig.load());
    }

    public static BankService getInstance() {
        return Holder.INSTANCE;
    }

    /** Production wiring: file-backed storage, notifications and background tasks. */
    public static BankService createDefault(AppConfig config) {
        BankService bank = new BankService(
                new InMemoryAccountRepository(),
                new FileTransactionLog(config.transactionsFile()),
                new FileSnapshotStore(config.accountsFile()));
        bank.addListener(new NotificationService());
        bank.startScheduler(config.autosaveInterval(), config.interestInterval());
        return bank;
    }

    public BankService(AccountRepository accounts, TransactionLog transactionLog, SnapshotStore snapshotStore) {
        this.accounts = accounts;
        this.transactionLog = transactionLog;
        this.snapshotStore = snapshotStore;
        this.transactionIds = new AtomicLong(transactionLog.maxTransactionId());
        for (AccountSnapshot snapshot : snapshotStore.load()) {
            Account account = AccountFactory.restore(snapshot);
            accounts.add(account);
            accountIds.accumulateAndGet(account.getId(), Math::max);
        }
        listeners.add(new AuditLogger(transactionLog));
        LOG.info("Bank started with " + accounts.findAll().size() + " account(s)");
    }

    public void addListener(TransactionListener listener) {
        listeners.add(listener);
    }

    // ------------------------------------------------------------------ accounts & security

    public Account openAccount(AccountType type, String name, String email, char[] pin, BigDecimal initialDeposit) {
        Customer customer = new Customer(name, email);
        PinHasher.validateFormat(pin);
        BigDecimal deposit = (initialDeposit == null || initialDeposit.signum() == 0)
                ? Money.ZERO : Money.requirePositive(initialDeposit);

        Account account = AccountFactory.create(type, accountIds.incrementAndGet(), customer,
                PinHasher.hash(pin), Money.ZERO);
        accounts.add(account);
        LOG.info("Opened " + account);
        if (deposit.signum() > 0) {
            deposit(account.getNumber(), deposit);
        }
        saveQuietly();
        return account;
    }

    /** Verifies the PIN; after {@value Account#MAX_FAILED_ATTEMPTS} failures the account is locked. */
    public Account authenticate(String accountNumber, char[] pin) {
        Account account = require(accountNumber);
        if (account.isLocked()) {
            throw new AccountLockedException(accountNumber);
        }
        if (!PinHasher.matches(pin, account.getPinHash())) {
            if (account.registerFailedAttempt()) {
                LOG.warning("Account " + accountNumber + " locked after repeated failed PIN attempts");
                saveQuietly();
                throw new AccountLockedException(accountNumber);
            }
            throw new AuthenticationException(
                    "Wrong account number or PIN (" + account.remainingAttempts() + " attempt(s) left)");
        }
        account.resetFailedAttempts();
        return account;
    }

    public void unlockAccount(String accountNumber) {
        require(accountNumber).unlock();
        LOG.info("Account " + accountNumber + " unlocked by administrator");
        saveQuietly();
    }

    // ------------------------------------------------------------------ money operations

    public Transaction deposit(String accountNumber, BigDecimal amount) {
        BigDecimal value = Money.requirePositive(amount);
        Account account = require(accountNumber);
        account.getLock().lock();
        try {
            account.credit(value);
            return record(account, TransactionType.DEPOSIT, null, value, Money.ZERO);
        } finally {
            account.getLock().unlock();
        }
    }

    public Transaction withdraw(String accountNumber, BigDecimal amount) {
        BigDecimal value = Money.requirePositive(amount);
        Account account = require(accountNumber);
        account.getLock().lock();
        try {
            BigDecimal fee = account.calculateFee(TransactionType.WITHDRAW, value);
            account.debit(value.add(fee)); // throws InsufficientFundsException, balance untouched
            addFees(fee);
            return record(account, TransactionType.WITHDRAW, null, value, fee);
        } finally {
            account.getLock().unlock();
        }
    }

    /**
     * Moves money atomically between two accounts. Both locks are taken in ascending id order (see class docs),
     * the debit is validated first, and the credit cannot fail, so no rollback logic is ever needed.
     *
     * @return the TRANSFER_OUT entry of the source account
     */
    public Transaction transfer(String fromNumber, String toNumber, BigDecimal amount) {
        BigDecimal value = Money.requirePositive(amount);
        if (fromNumber != null && fromNumber.equals(toNumber)) {
            throw new ValidationException("Cannot transfer to the same account");
        }
        Account from = require(fromNumber);
        Account to = require(toNumber);

        Account first = from.getId() < to.getId() ? from : to;
        Account second = first == from ? to : from;

        first.getLock().lock();
        try {
            second.getLock().lock();
            try {
                BigDecimal fee = from.calculateFee(TransactionType.TRANSFER_OUT, value);
                from.debit(value.add(fee));
                to.credit(value);
                addFees(fee);
                Transaction out = record(from, TransactionType.TRANSFER_OUT, to.getNumber(), value, fee);
                record(to, TransactionType.TRANSFER_IN, from.getNumber(), value, Money.ZERO);
                return out;
            } finally {
                second.getLock().unlock();
            }
        } finally {
            first.getLock().unlock();
        }
    }

    /** Credits one month of interest to every account that earns it. Returns the total paid out. */
    public BigDecimal applyMonthlyInterest() {
        BigDecimal total = Money.ZERO;
        for (Account account : accounts.findAll()) {
            account.getLock().lock();
            try {
                BigDecimal interest = account.calculateMonthlyInterest();
                if (interest.signum() > 0) {
                    account.credit(interest);
                    record(account, TransactionType.INTEREST, null, interest, Money.ZERO);
                    total = total.add(interest);
                }
            } finally {
                account.getLock().unlock();
            }
        }
        LOG.info("Monthly interest applied, total " + Money.format(total));
        return total;
    }

    // ------------------------------------------------------------------ queries

    public BigDecimal getBalance(String accountNumber) {
        return require(accountNumber).getBalance();
    }

    public List<Transaction> getStatement(String accountNumber, int limit) {
        if (limit < 1 || limit > MAX_STATEMENT_ENTRIES) {
            throw new ValidationException("Statement size must be between 1 and " + MAX_STATEMENT_ENTRIES);
        }
        return transactionLog.findByAccount(require(accountNumber).getNumber(), limit);
    }

    public List<Account> listAccounts() {
        return accounts.findAll().stream().sorted(Comparator.comparingLong(Account::getId)).toList();
    }

    /** Sum of all balances, measured at one consistent instant. */
    public BigDecimal totalBalance() {
        return snapshot().stream().map(AccountSnapshot::balance).reduce(Money.ZERO, BigDecimal::add);
    }

    /** Fees collected by the bank since this instance started. */
    public BigDecimal totalFees() {
        return feesCollected.get();
    }

    /**
     * Consistent picture of all accounts: every account lock is held (in ascending id order) while copying, so a
     * transfer can never be captured half-done. Blocks writers only for the few microseconds the copy takes.
     */
    public List<AccountSnapshot> snapshot() {
        List<Account> sorted = listAccounts();
        for (Account a : sorted) {
            a.getLock().lock();
        }
        try {
            return sorted.stream().map(Account::snapshot).toList();
        } finally {
            for (int i = sorted.size() - 1; i >= 0; i--) {
                sorted.get(i).getLock().unlock();
            }
        }
    }

    // ------------------------------------------------------------------ persistence & lifecycle

    public void save() {
        synchronized (saveLock) { // one writer at a time: they share a temp file
            snapshotStore.save(snapshot());
        }
    }

    private void saveQuietly() {
        try {
            save();
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "Could not save accounts", e);
        }
    }

    /** Starts autosave and periodic interest. A non-positive interval disables that task. */
    public synchronized void startScheduler(java.time.Duration autosave, java.time.Duration interest) {
        if (scheduler != null) {
            throw new IllegalStateException("Scheduler already started");
        }
        ScheduledExecutorService s = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "bank-scheduler");
            t.setDaemon(true);
            return t;
        });
        if (autosave.toSeconds() > 0) {
            s.scheduleWithFixedDelay(this::saveQuietly,
                    autosave.toSeconds(), autosave.toSeconds(), TimeUnit.SECONDS);
        }
        if (interest.toSeconds() > 0) {
            s.scheduleAtFixedRate(() -> {
                try {
                    applyMonthlyInterest(); // an exception would silently cancel all future runs
                } catch (RuntimeException e) {
                    LOG.log(Level.SEVERE, "Scheduled interest run failed", e);
                }
            }, interest.toSeconds(), interest.toSeconds(), TimeUnit.SECONDS);
        }
        scheduler = s;
    }

    /** Stops background tasks, writes a final snapshot and closes the ledger. Safe to call twice. */
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        synchronized (this) {
            if (scheduler != null) {
                scheduler.shutdown();
                try {
                    if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                        scheduler.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    scheduler.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
        }
        saveQuietly();
        transactionLog.close();
        LOG.info("Bank closed");
    }

    // ------------------------------------------------------------------ internals

    private Account require(String accountNumber) {
        return accounts.findByNumber(accountNumber).orElseThrow(() -> new AccountNotFoundException(accountNumber));
    }

    private void addFees(BigDecimal fee) {
        if (fee.signum() > 0) {
            feesCollected.accumulateAndGet(fee, BigDecimal::add);
        }
    }

    /** Builds the ledger entry and notifies observers. The caller must hold the account's lock. */
    private Transaction record(Account account, TransactionType type, String counterparty,
                               BigDecimal amount, BigDecimal fee) {
        Transaction t = Transaction.builder()
                .id(transactionIds.incrementAndGet())
                .timestamp(Instant.now())
                .type(type)
                .accountNumber(account.getNumber())
                .counterparty(counterparty)
                .amount(amount)
                .fee(fee)
                .balanceAfter(account.getBalance())
                .build();
        for (TransactionListener listener : listeners) {
            try {
                listener.onTransaction(t);
            } catch (RuntimeException e) {
                // The money has already moved; a broken observer must not turn that into a failed operation.
                LOG.log(Level.SEVERE, "Transaction listener failed for transaction " + t.id(), e);
            }
        }
        return t;
    }
}
