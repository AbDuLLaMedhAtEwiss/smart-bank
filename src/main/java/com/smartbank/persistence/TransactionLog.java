package com.smartbank.persistence;

import com.smartbank.model.Transaction;
import java.util.List;

/** Append-only ledger of every successful transaction. */
public interface TransactionLog extends AutoCloseable {

    void append(Transaction transaction);

    /** The most recent {@code limit} entries of one account, newest first. */
    List<Transaction> findByAccount(String accountNumber, int limit);

    /** Highest transaction id ever written (0 if empty); used to resume id generation after a restart. */
    long maxTransactionId();

    @Override
    default void close() {
    }
}
