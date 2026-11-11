package com.smartbank.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smartbank.exception.AccountLockedException;
import com.smartbank.exception.AccountNotFoundException;
import com.smartbank.exception.AuthenticationException;
import com.smartbank.exception.InsufficientFundsException;
import com.smartbank.exception.InvalidAmountException;
import com.smartbank.exception.ValidationException;
import com.smartbank.model.AccountType;
import com.smartbank.model.Transaction;
import com.smartbank.model.TransactionType;
import com.smartbank.persistence.InMemoryTransactionLog;
import com.smartbank.persistence.SnapshotStore;
import com.smartbank.repository.InMemoryAccountRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BankServiceTest {
    private BankService bank;

    @BeforeEach
    void setUp() {
        bank = new BankService(new InMemoryAccountRepository(), new InMemoryTransactionLog(), SnapshotStore.noop());
    }

    private String open(AccountType type, String name, String initial) {
        String email = name.toLowerCase().replace(" ", "") + "@test.com";
        return bank.openAccount(type, name, email, "1234".toCharArray(), new BigDecimal(initial)).getNumber();
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "expected " + expected + " but was " + actual);
    }

    // ------------------------------------------------------------ basic operations

    @Test
    void depositAndWithdrawUpdateTheBalance() {
        String acc = open(AccountType.SAVINGS, "Ann", "100.00");
        bank.deposit(acc, new BigDecimal("50.25"));
        bank.withdraw(acc, new BigDecimal("30"));
        assertMoney("120.25", bank.getBalance(acc));
    }

    @Test
    void savingsAccountCannotGoBelowZero() {
        String acc = open(AccountType.SAVINGS, "Ann", "100.00");
        assertThrows(InsufficientFundsException.class, () -> bank.withdraw(acc, new BigDecimal("100.01")));
        assertMoney("100.00", bank.getBalance(acc)); // untouched after the failure
        bank.withdraw(acc, new BigDecimal("100.00")); // exactly the balance is allowed
        assertMoney("0.00", bank.getBalance(acc));
    }

    @Test
    void currentAccountMayOverdrawUpToItsLimit() {
        String acc = open(AccountType.CURRENT, "Bob", "100.00"); // overdraft limit 500
        bank.withdraw(acc, new BigDecimal("550.00"));
        assertMoney("-450.00", bank.getBalance(acc));
        assertThrows(InsufficientFundsException.class, () -> bank.withdraw(acc, new BigDecimal("50.01")));
        bank.withdraw(acc, new BigDecimal("50.00")); // lands exactly on -500
        assertMoney("-500.00", bank.getBalance(acc));
    }

    @Test
    void invalidAmountsAreRejected() {
        String acc = open(AccountType.SAVINGS, "Ann", "100.00");
        assertThrows(InvalidAmountException.class, () -> bank.deposit(acc, BigDecimal.ZERO));
        assertThrows(InvalidAmountException.class, () -> bank.deposit(acc, new BigDecimal("-1")));
        assertThrows(InvalidAmountException.class, () -> bank.deposit(acc, new BigDecimal("1.005")));
        assertThrows(InvalidAmountException.class, () -> bank.withdraw(acc, null));
    }

    @Test
    void unknownAccountIsReported() {
        assertThrows(AccountNotFoundException.class, () -> bank.getBalance("SB99999999"));
        assertThrows(AccountNotFoundException.class, () -> bank.deposit("nope", BigDecimal.ONE));
    }

    // ------------------------------------------------------------ transfers & fees

    @Test
    void transferMovesMoneyAndChargesTheSenderOnly() {
        String from = open(AccountType.SAVINGS, "Ann", "1000.00"); // flat 0.50 transfer fee
        String to = open(AccountType.CURRENT, "Bob", "0.00");

        Transaction out = bank.transfer(from, to, new BigDecimal("100.00"));

        assertMoney("899.50", bank.getBalance(from));
        assertMoney("100.00", bank.getBalance(to));
        assertMoney("0.50", out.fee());
        assertMoney("0.50", bank.totalFees());
        assertMoney("999.50", bank.totalBalance());
    }

    @Test
    void currentAccountTransferFeeIsPercentageWithMinimum() {
        String cur = open(AccountType.CURRENT, "Bob", "100000.00");
        String sav = open(AccountType.SAVINGS, "Ann", "0.00");

        assertMoney("1.00", bank.transfer(cur, sav, new BigDecimal("100.00")).fee());   // 0.1% = 0.10 -> min 1.00
        assertMoney("50.00", bank.transfer(cur, sav, new BigDecimal("50000.00")).fee()); // 0.1% = 50.00
    }

    @Test
    void transferFailsAtomicallyWhenFundsAreInsufficient() {
        String from = open(AccountType.SAVINGS, "Ann", "100.00");
        String to = open(AccountType.SAVINGS, "Bob", "10.00");

        // amount alone would fit (100.00) but amount + 0.50 fee would not
        assertThrows(InsufficientFundsException.class, () -> bank.transfer(from, to, new BigDecimal("100.00")));

        assertMoney("100.00", bank.getBalance(from));
        assertMoney("10.00", bank.getBalance(to));
        assertMoney("0.00", bank.totalFees());
    }

    @Test
    void transferToSelfOrUnknownAccountIsRejected() {
        String acc = open(AccountType.SAVINGS, "Ann", "100.00");
        assertThrows(ValidationException.class, () -> bank.transfer(acc, acc, BigDecimal.TEN));
        assertThrows(AccountNotFoundException.class, () -> bank.transfer(acc, "SB99999999", BigDecimal.TEN));
        assertMoney("100.00", bank.getBalance(acc));
    }

    // ------------------------------------------------------------ interest

    @Test
    void savingsEarnInterestCurrentDoNot() {
        String sav = open(AccountType.SAVINGS, "Ann", "1200.00"); // 4% / 12 of 1200 = 4.00
        String cur = open(AccountType.CURRENT, "Bob", "1200.00");

        BigDecimal paid = bank.applyMonthlyInterest();

        assertMoney("4.00", paid);
        assertMoney("1204.00", bank.getBalance(sav));
        assertMoney("1200.00", bank.getBalance(cur));
    }

    // ------------------------------------------------------------ statement & listeners

    @Test
    void statementListsNewestFirstAndHonoursTheLimit() {
        String acc = open(AccountType.SAVINGS, "Ann", "100.00");
        bank.deposit(acc, new BigDecimal("1.00"));
        bank.deposit(acc, new BigDecimal("2.00"));
        bank.withdraw(acc, new BigDecimal("3.00"));

        List<Transaction> last2 = bank.getStatement(acc, 2);

        assertEquals(2, last2.size());
        assertEquals(TransactionType.WITHDRAW, last2.get(0).type());
        assertEquals(TransactionType.DEPOSIT, last2.get(1).type());
        assertMoney("100.00", last2.get(0).balanceAfter());
        assertEquals(4, bank.getStatement(acc, 50).size()); // opening deposit + 3
        assertThrows(ValidationException.class, () -> bank.getStatement(acc, 0));
    }

    @Test
    void listenersAreNotifiedAndABrokenListenerDoesNotBreakTheOperation() {
        List<Transaction> seen = new ArrayList<>();
        bank.addListener(t -> {
            throw new IllegalStateException("boom");
        });
        bank.addListener(seen::add);

        String acc = open(AccountType.SAVINGS, "Ann", "0.00");
        bank.deposit(acc, new BigDecimal("10.00"));

        assertMoney("10.00", bank.getBalance(acc));
        assertEquals(1, seen.size());
        assertEquals(TransactionType.DEPOSIT, seen.get(0).type());
    }

    // ------------------------------------------------------------ authentication

    @Test
    void correctPinAuthenticates() {
        String acc = open(AccountType.SAVINGS, "Ann", "0.00");
        assertEquals(acc, bank.authenticate(acc, "1234".toCharArray()).getNumber());
    }

    @Test
    void accountLocksAfterThreeWrongPinsAndAdminCanUnlock() {
        String acc = open(AccountType.SAVINGS, "Ann", "0.00");
        char[] wrong = "0000".toCharArray();

        assertThrows(AuthenticationException.class, () -> bank.authenticate(acc, wrong));
        assertThrows(AuthenticationException.class, () -> bank.authenticate(acc, wrong));
        assertThrows(AccountLockedException.class, () -> bank.authenticate(acc, wrong));
        // even the right PIN is refused now
        assertThrows(AccountLockedException.class, () -> bank.authenticate(acc, "1234".toCharArray()));

        bank.unlockAccount(acc);
        assertTrue(bank.authenticate(acc, "1234".toCharArray()).getNumber().equals(acc));
    }

    @Test
    void successfulLoginResetsTheFailureCounter() {
        String acc = open(AccountType.SAVINGS, "Ann", "0.00");
        char[] wrong = "0000".toCharArray();
        assertThrows(AuthenticationException.class, () -> bank.authenticate(acc, wrong));
        assertThrows(AuthenticationException.class, () -> bank.authenticate(acc, wrong));
        bank.authenticate(acc, "1234".toCharArray());           // resets
        assertThrows(AuthenticationException.class, () -> bank.authenticate(acc, wrong));
        assertThrows(AuthenticationException.class, () -> bank.authenticate(acc, wrong)); // still not locked
    }

    @Test
    void openingAnAccountValidatesItsInput() {
        char[] pin = "1234".toCharArray();
        assertThrows(ValidationException.class,
                () -> bank.openAccount(AccountType.SAVINGS, "", "a@b.com", pin, BigDecimal.ZERO));
        assertThrows(ValidationException.class,
                () -> bank.openAccount(AccountType.SAVINGS, "Ann", "not-an-email", pin, BigDecimal.ZERO));
        assertThrows(ValidationException.class,
                () -> bank.openAccount(AccountType.SAVINGS, "A|B", "a@b.com", pin, BigDecimal.ZERO));
        assertThrows(ValidationException.class,
                () -> bank.openAccount(AccountType.SAVINGS, "Ann", "a@b.com", "12".toCharArray(), BigDecimal.ZERO));
        assertThrows(ValidationException.class,
                () -> bank.openAccount(AccountType.SAVINGS, "Ann", "a@b.com", "12ab".toCharArray(), BigDecimal.ZERO));
        assertThrows(InvalidAmountException.class,
                () -> bank.openAccount(AccountType.SAVINGS, "Ann", "a@b.com", pin, new BigDecimal("-5")));
        assertEquals(0, bank.listAccounts().size());
    }
}
