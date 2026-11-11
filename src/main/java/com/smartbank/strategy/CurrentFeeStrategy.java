package com.smartbank.strategy;

import com.smartbank.model.TransactionType;
import com.smartbank.util.Money;
import java.math.BigDecimal;

/** Current: free withdrawals, outgoing transfers cost 0.1% of the amount with a minimum of 1.00. */
public final class CurrentFeeStrategy implements FeeStrategy {
    private static final BigDecimal RATE = new BigDecimal("0.001");
    private static final BigDecimal MINIMUM = new BigDecimal("1.00");

    @Override
    public BigDecimal calculateFee(TransactionType type, BigDecimal amount) {
        if (type != TransactionType.TRANSFER_OUT) {
            return Money.ZERO;
        }
        return Money.round(amount.multiply(RATE)).max(MINIMUM);
    }
}
