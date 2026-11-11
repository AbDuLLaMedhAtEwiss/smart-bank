package com.smartbank.strategy;

import com.smartbank.util.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** Annual rate / 12 applied to the current balance each month (compounds because it is credited). */
public final class CompoundInterestStrategy implements InterestStrategy {
    private static final BigDecimal MONTHS = BigDecimal.valueOf(12);
    private final BigDecimal annualRate;

    public CompoundInterestStrategy(BigDecimal annualRate) {
        this.annualRate = annualRate;
    }

    @Override
    public BigDecimal monthlyInterest(BigDecimal balance) {
        if (balance.signum() <= 0) {
            return Money.ZERO;
        }
        return balance.multiply(annualRate).divide(MONTHS, Money.SCALE, RoundingMode.HALF_EVEN);
    }
}
