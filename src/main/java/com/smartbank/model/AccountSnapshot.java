package com.smartbank.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Plain immutable copy of an account's persistent state.
 * {@code parameter} is the annual rate for savings accounts and the overdraft limit for current accounts.
 */
public record AccountSnapshot(
        AccountType type,
        long id,
        Customer owner,
        String pinHash,
        BigDecimal balance,
        BigDecimal parameter,
        boolean locked,
        Instant createdAt) {
}
