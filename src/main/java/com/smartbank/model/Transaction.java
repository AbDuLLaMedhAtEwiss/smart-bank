package com.smartbank.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Immutable ledger entry, always written from the point of view of ONE account.
 * A transfer therefore produces two entries: TRANSFER_OUT and TRANSFER_IN.
 */
public record Transaction(
        long id,
        Instant timestamp,
        TransactionType type,
        String accountNumber,
        String counterparty,
        BigDecimal amount,
        BigDecimal fee,
        BigDecimal balanceAfter) {

    public Transaction {
        Objects.requireNonNull(timestamp, "timestamp");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(accountNumber, "accountNumber");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(fee, "fee");
        Objects.requireNonNull(balanceAfter, "balanceAfter");
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Builder pattern: a Transaction has too many fields for a readable constructor call. */
    public static final class Builder {
        private long id;
        private Instant timestamp = Instant.now();
        private TransactionType type;
        private String accountNumber;
        private String counterparty;
        private BigDecimal amount;
        private BigDecimal fee = BigDecimal.ZERO.setScale(2);
        private BigDecimal balanceAfter;

        public Builder id(long id) {
            this.id = id;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder type(TransactionType type) {
            this.type = type;
            return this;
        }

        public Builder accountNumber(String accountNumber) {
            this.accountNumber = accountNumber;
            return this;
        }

        public Builder counterparty(String counterparty) {
            this.counterparty = counterparty;
            return this;
        }

        public Builder amount(BigDecimal amount) {
            this.amount = amount;
            return this;
        }

        public Builder fee(BigDecimal fee) {
            this.fee = fee;
            return this;
        }

        public Builder balanceAfter(BigDecimal balanceAfter) {
            this.balanceAfter = balanceAfter;
            return this;
        }

        public Transaction build() {
            return new Transaction(id, timestamp, type, accountNumber, counterparty, amount, fee, balanceAfter);
        }
    }
}
