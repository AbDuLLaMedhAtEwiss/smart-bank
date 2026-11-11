package com.smartbank.model;

import com.smartbank.exception.InsufficientFundsException;
import com.smartbank.strategy.FeeStrategy;
import com.smartbank.strategy.InterestStrategy;
import com.smartbank.util.Money;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Base class of every account (abstraction + encapsulation).
 *
 * <p><b>Concurrency model:</b> each account owns a {@link ReentrantLock} that guards all mutable state.
 * The mutators ({@link #credit}, {@link #debit}) acquire it themselves, and because the lock is reentrant a
 * caller such as a transfer can also hold it across several calls to make a compound operation atomic.
 *
 * <p>The class is sealed: the set of account kinds is closed, which lets callers use exhaustive
 * pattern-matching {@code switch} expressions (Java 21).
 */
public abstract sealed class Account permits SavingsAccount, CurrentAccount {

    public static final int MAX_FAILED_ATTEMPTS = 3;

    private final long id;
    private final String number;
    private final Customer owner;
    private final String pinHash;
    private final Instant createdAt;
    private final FeeStrategy feeStrategy;
    private final InterestStrategy interestStrategy;
    private final ReentrantLock lock = new ReentrantLock();

    // --- mutable state, guarded by 'lock' ---
    private BigDecimal balance;
    private boolean locked;
    private int failedAttempts;

    protected Account(long id, Customer owner, String pinHash, BigDecimal balance, boolean locked,
                      Instant createdAt, FeeStrategy feeStrategy, InterestStrategy interestStrategy) {
        this.id = id;
        this.number = "SB%08d".formatted(id);
        this.owner = owner;
        this.pinHash = pinHash;
        this.balance = Money.round(balance);
        this.locked = locked;
        this.createdAt = createdAt;
        this.feeStrategy = feeStrategy;
        this.interestStrategy = interestStrategy;
    }

    // ---------- polymorphic parts ----------

    public abstract AccountType getType();

    /** Lowest balance this account may reach (0 for savings, negative overdraft limit for current). */
    protected abstract BigDecimal minimumAllowedBalance();

    /** Annual rate (savings) or overdraft limit (current); persisted as-is. */
    public abstract BigDecimal getParameter();

    // ---------- immutable getters ----------

    public long getId() {
        return id;
    }

    public String getNumber() {
        return number;
    }

    public Customer getOwner() {
        return owner;
    }

    public String getPinHash() {
        return pinHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** Exposed so a service can hold several accounts' locks at once (always in ascending id order!). */
    public ReentrantLock getLock() {
        return lock;
    }

    // ---------- guarded state ----------

    public BigDecimal getBalance() {
        lock.lock();
        try {
            return balance;
        } finally {
            lock.unlock();
        }
    }

    public void credit(BigDecimal amount) {
        lock.lock();
        try {
            balance = balance.add(amount);
        } finally {
            lock.unlock();
        }
    }

    /** Removes {@code total} (amount + fees) or throws, leaving the balance untouched. */
    public void debit(BigDecimal total) {
        lock.lock();
        try {
            BigDecimal newBalance = balance.subtract(total);
            if (newBalance.compareTo(minimumAllowedBalance()) < 0) {
                throw new InsufficientFundsException(number, total, balance.subtract(minimumAllowedBalance()));
            }
            balance = newBalance;
        } finally {
            lock.unlock();
        }
    }

    public BigDecimal calculateFee(TransactionType type, BigDecimal amount) {
        return feeStrategy.calculateFee(type, amount);
    }

    public BigDecimal calculateMonthlyInterest() {
        lock.lock();
        try {
            return interestStrategy.monthlyInterest(balance);
        } finally {
            lock.unlock();
        }
    }

    // ---------- PIN lockout state ----------

    public boolean isLocked() {
        lock.lock();
        try {
            return locked;
        } finally {
            lock.unlock();
        }
    }

    /** Records a wrong PIN; returns true if this attempt locked the account. */
    public boolean registerFailedAttempt() {
        lock.lock();
        try {
            failedAttempts++;
            if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
                locked = true;
            }
            return locked;
        } finally {
            lock.unlock();
        }
    }

    public int remainingAttempts() {
        lock.lock();
        try {
            return Math.max(0, MAX_FAILED_ATTEMPTS - failedAttempts);
        } finally {
            lock.unlock();
        }
    }

    public void resetFailedAttempts() {
        lock.lock();
        try {
            failedAttempts = 0;
        } finally {
            lock.unlock();
        }
    }

    public void unlock() {
        lock.lock();
        try {
            locked = false;
            failedAttempts = 0;
        } finally {
            lock.unlock();
        }
    }

    /** Consistent copy of the persistent state. */
    public AccountSnapshot snapshot() {
        lock.lock();
        try {
            return new AccountSnapshot(getType(), id, owner, pinHash, balance, getParameter(), locked, createdAt);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public String toString() {
        return getType() + "[" + number + ", " + owner.name() + "]";
    }
}
