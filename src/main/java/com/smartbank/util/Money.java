package com.smartbank.util;

import com.smartbank.exception.InvalidAmountException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/** Helpers for monetary values. All money in the system is a BigDecimal with scale 2. */
public final class Money {
    public static final int SCALE = 2;
    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE);
    public static final BigDecimal MAX_AMOUNT = new BigDecimal("1000000000.00");

    private Money() {
    }

    /** Parses user text into a valid, strictly positive amount. */
    public static BigDecimal parse(String text) {
        if (text == null || text.isBlank()) {
            throw new InvalidAmountException("Amount is required");
        }
        try {
            return requirePositive(new BigDecimal(text.strip()));
        } catch (NumberFormatException e) {
            throw new InvalidAmountException("'" + text.strip() + "' is not a valid amount");
        }
    }

    /** Validates an amount (positive, bounded, max 2 decimals) and normalises it to scale 2. */
    public static BigDecimal requirePositive(BigDecimal amount) {
        if (amount == null) {
            throw new InvalidAmountException("Amount is required");
        }
        if (amount.signum() <= 0) {
            throw new InvalidAmountException("Amount must be greater than zero");
        }
        if (amount.compareTo(MAX_AMOUNT) > 0) {
            throw new InvalidAmountException("Amount exceeds the maximum allowed (" + format(MAX_AMOUNT) + ")");
        }
        if (amount.stripTrailingZeros().scale() > SCALE) {
            throw new InvalidAmountException("Amount cannot have more than " + SCALE + " decimal places");
        }
        return amount.setScale(SCALE, RoundingMode.UNNECESSARY);
    }

    public static BigDecimal round(BigDecimal value) {
        return value.setScale(SCALE, RoundingMode.HALF_EVEN);
    }

    public static String format(BigDecimal value) {
        return String.format(Locale.US, "%,.2f", value);
    }
}
