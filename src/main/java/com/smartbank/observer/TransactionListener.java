package com.smartbank.observer;

import com.smartbank.model.Transaction;

/** Observer pattern: anything interested in completed transactions implements this. */
@FunctionalInterface
public interface TransactionListener {
    void onTransaction(Transaction transaction);
}
