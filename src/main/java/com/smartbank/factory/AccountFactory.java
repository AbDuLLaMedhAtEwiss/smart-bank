package com.smartbank.factory;

import com.smartbank.model.Account;
import com.smartbank.model.AccountSnapshot;
import com.smartbank.model.AccountType;
import com.smartbank.model.CurrentAccount;
import com.smartbank.model.Customer;
import com.smartbank.model.SavingsAccount;
import java.math.BigDecimal;
import java.time.Instant;

/** Factory pattern: the only place that knows which concrete class belongs to which {@link AccountType}. */
public final class AccountFactory {
    public static final BigDecimal DEFAULT_SAVINGS_RATE = new BigDecimal("0.04");
    public static final BigDecimal DEFAULT_OVERDRAFT_LIMIT = new BigDecimal("500.00");

    private AccountFactory() {
    }

    /** Creates a brand-new account with the default parameters for its type. */
    public static Account create(AccountType type, long id, Customer owner, String pinHash, BigDecimal initialBalance) {
        BigDecimal parameter = switch (type) {
            case SAVINGS -> DEFAULT_SAVINGS_RATE;
            case CURRENT -> DEFAULT_OVERDRAFT_LIMIT;
        };
        return build(new AccountSnapshot(type, id, owner, pinHash, initialBalance, parameter, false, Instant.now()));
    }

    /** Rebuilds an account from persisted state. */
    public static Account restore(AccountSnapshot s) {
        return build(s);
    }

    private static Account build(AccountSnapshot s) {
        return switch (s.type()) {
            case SAVINGS -> new SavingsAccount(s.id(), s.owner(), s.pinHash(), s.balance(),
                    s.locked(), s.createdAt(), s.parameter());
            case CURRENT -> new CurrentAccount(s.id(), s.owner(), s.pinHash(), s.balance(),
                    s.locked(), s.createdAt(), s.parameter());
        };
    }
}
