package com.smartbank.model;

import com.smartbank.strategy.CompoundInterestStrategy;
import com.smartbank.strategy.SavingsFeeStrategy;
import com.smartbank.util.Money;
import java.math.BigDecimal;
import java.time.Instant;

/** Earns monthly interest, can never go below zero. */
public final class SavingsAccount extends Account {
    private final BigDecimal annualRate;

    public SavingsAccount(long id, Customer owner, String pinHash, BigDecimal balance, boolean locked,
                          Instant createdAt, BigDecimal annualRate) {
        super(id, owner, pinHash, balance, locked, createdAt,
                new SavingsFeeStrategy(), new CompoundInterestStrategy(annualRate));
        this.annualRate = annualRate;
    }

    public BigDecimal getAnnualRate() {
        return annualRate;
    }

    @Override
    public AccountType getType() {
        return AccountType.SAVINGS;
    }

    @Override
    protected BigDecimal minimumAllowedBalance() {
        return Money.ZERO;
    }

    @Override
    public BigDecimal getParameter() {
        return annualRate;
    }
}
