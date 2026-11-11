package com.smartbank.strategy;

import com.smartbank.util.Money;
import java.math.BigDecimal;

public final class NoInterestStrategy implements InterestStrategy {
    @Override
    public BigDecimal monthlyInterest(BigDecimal balance) {
        return Money.ZERO;
    }
}
