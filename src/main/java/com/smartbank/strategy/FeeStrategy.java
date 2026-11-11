package com.smartbank.strategy;

import com.smartbank.model.TransactionType;
import java.math.BigDecimal;

/** Strategy pattern: each account type plugs in its own fee policy. */
public interface FeeStrategy {
    BigDecimal calculateFee(TransactionType type, BigDecimal amount);
}
