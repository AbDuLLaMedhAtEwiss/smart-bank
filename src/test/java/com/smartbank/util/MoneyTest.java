package com.smartbank.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.smartbank.exception.InvalidAmountException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void parsesValidAmountsToScaleTwo() {
        assertEquals("100.00", Money.parse("100").toPlainString());
        assertEquals("12.30", Money.parse(" 12.3 ").toPlainString());
        assertEquals("0.01", Money.parse("0.010").toPlainString());
    }

    @Test
    void rejectsInvalidAmounts() {
        assertThrows(InvalidAmountException.class, () -> Money.parse("0"));
        assertThrows(InvalidAmountException.class, () -> Money.parse("-5"));
        assertThrows(InvalidAmountException.class, () -> Money.parse("1.234"));
        assertThrows(InvalidAmountException.class, () -> Money.parse("abc"));
        assertThrows(InvalidAmountException.class, () -> Money.parse(""));
        assertThrows(InvalidAmountException.class, () -> Money.parse("1e30"));
        assertThrows(InvalidAmountException.class, () -> Money.requirePositive(null));
    }

    @Test
    void roundsHalfEven() {
        assertEquals(new BigDecimal("0.12"), Money.round(new BigDecimal("0.125")));
        assertEquals(new BigDecimal("0.14"), Money.round(new BigDecimal("0.135")));
    }
}
