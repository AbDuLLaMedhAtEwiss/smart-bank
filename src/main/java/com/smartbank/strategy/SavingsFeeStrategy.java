package com.smartbank.strategy;

import com.smartbank.model.TransactionType;
import com.smartbank.util.Money;
import java.math.BigDecimal;

/** Savings: free withdrawals, flat 0.50 fee on outgoing transfers. */
public final class SavingsFeeStrategy implements FeeStrategy {
    private static final BigDecimal TRANSFER_FEE = new BigDecimal("0.50");

    @Override
    public BigDecimal calculateFee(TransactionType type, BigDecimal amount) {
        return type == TransactionType.TRANSFER_OUT ? TRANSFER_FEE : Money.ZERO;
    }
}
