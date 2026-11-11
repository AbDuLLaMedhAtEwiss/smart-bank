package com.smartbank.factory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.smartbank.model.Account;
import com.smartbank.model.AccountSnapshot;
import com.smartbank.model.AccountType;
import com.smartbank.model.CurrentAccount;
import com.smartbank.model.Customer;
import com.smartbank.model.SavingsAccount;
import com.smartbank.util.Money;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class AccountFactoryTest {
    private final Customer owner = new Customer("Test User", "test@example.com");

    @Test
    void createsTheRightSubclassForEachType() {
        Account savings = AccountFactory.create(AccountType.SAVINGS, 7, owner, "hash", Money.ZERO);
        Account current = AccountFactory.create(AccountType.CURRENT, 8, owner, "hash", Money.ZERO);

        SavingsAccount s = assertInstanceOf(SavingsAccount.class, savings);
        CurrentAccount c = assertInstanceOf(CurrentAccount.class, current);
        assertEquals(AccountFactory.DEFAULT_SAVINGS_RATE, s.getAnnualRate());
        assertEquals(AccountFactory.DEFAULT_OVERDRAFT_LIMIT, c.getOverdraftLimit());
    }

    @Test
    void generatesPaddedAccountNumbersFromTheId() {
        Account a = AccountFactory.create(AccountType.SAVINGS, 7, owner, "hash", Money.ZERO);
        assertEquals("SB00000007", a.getNumber());
    }

    @Test
    void restoreRebuildsExactlyWhatSnapshotCaptured() {
        Account original = AccountFactory.create(AccountType.CURRENT, 3, owner, "hash", new BigDecimal("42.00"));
        AccountSnapshot snap = original.snapshot();

        Account restored = AccountFactory.restore(snap);

        assertInstanceOf(CurrentAccount.class, restored);
        assertEquals(snap, restored.snapshot());
        assertFalse(restored.isLocked());
    }
}
