package com.smartbank.persistence;

import com.smartbank.exception.StorageException;
import com.smartbank.model.Transaction;
import com.smartbank.model.TransactionType;
import java.math.BigDecimal;
import java.time.Instant;

/** One transaction per line: id|epochMillis|TYPE|account|counterparty or '-'|amount|fee|balanceAfter. */
final class TransactionCodec {
    private static final int FIELDS = 8;

    private TransactionCodec() {
    }

    static String encode(Transaction t) {
        return String.join("|",
                Long.toString(t.id()),
                Long.toString(t.timestamp().toEpochMilli()),
                t.type().name(),
                t.accountNumber(),
                t.counterparty() == null ? "-" : t.counterparty(),
                t.amount().toPlainString(),
                t.fee().toPlainString(),
                t.balanceAfter().toPlainString());
    }

    static Transaction decode(String line) {
        String[] p = line.split("\\|", -1);
        if (p.length != FIELDS) {
            throw new StorageException("Corrupt transaction line: " + line);
        }
        try {
            return Transaction.builder()
                    .id(Long.parseLong(p[0]))
                    .timestamp(Instant.ofEpochMilli(Long.parseLong(p[1])))
                    .type(TransactionType.valueOf(p[2]))
                    .accountNumber(p[3])
                    .counterparty("-".equals(p[4]) ? null : p[4])
                    .amount(new BigDecimal(p[5]))
                    .fee(new BigDecimal(p[6]))
                    .balanceAfter(new BigDecimal(p[7]))
                    .build();
        } catch (IllegalArgumentException e) { // NumberFormatException is a subclass
            throw new StorageException("Corrupt transaction line: " + line, e);
        }
    }
}
