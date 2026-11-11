package com.smartbank.exception;

import com.smartbank.util.Money;
import java.math.BigDecimal;

public class InsufficientFundsException extends BankException {
    private static final long serialVersionUID = 1L;

    public InsufficientFundsException(String accountNumber, BigDecimal required, BigDecimal available) {
        super("Insufficient funds in %s: required %s, available %s"
                .formatted(accountNumber, Money.format(required), Money.format(available)));
    }
}
