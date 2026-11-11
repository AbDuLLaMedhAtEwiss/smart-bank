package com.smartbank.model;

import com.smartbank.strategy.CurrentFeeStrategy;
import com.smartbank.strategy.NoInterestStrategy;
import java.math.BigDecimal;
import java.time.Instant;

/** No interest, but may go negative up to the overdraft limit. */
public final class CurrentAccount extends Account {
    private final BigDecimal overdraftLimit;

    public CurrentAccount(long id, Customer owner, String pinHash, BigDecimal balance, boolean locked,
                          Instant createdAt, BigDecimal overdraftLimit) {
        super(id, owner, pinHash, balance, locked, createdAt,
                new CurrentFeeStrategy(), new NoInterestStrategy());
        this.overdraftLimit = overdraftLimit;
    }

    public BigDecimal getOverdraftLimit() {
        return overdraftLimit;
    }

    @Override
    public AccountType getType() {
        return AccountType.CURRENT;
    }

    @Override
    protected BigDecimal minimumAllowedBalance() {
        return overdraftLimit.negate();
    }

    @Override
    public BigDecimal getParameter() {
        return overdraftLimit;
    }
}
