package com.smartbank.strategy;

import java.math.BigDecimal;

public interface InterestStrategy {
    /** Interest to credit for one month on the given balance (never negative). */
    BigDecimal monthlyInterest(BigDecimal balance);
}
