package com.smartbank.observer;

import com.smartbank.model.Transaction;
import com.smartbank.persistence.TransactionLog;

/** Writes every transaction to the append-only ledger. */
public final class AuditLogger implements TransactionListener {
    private final TransactionLog log;

    public AuditLogger(TransactionLog log) {
        this.log = log;
    }

    @Override
    public void onTransaction(Transaction transaction) {
        log.append(transaction);
    }
}
