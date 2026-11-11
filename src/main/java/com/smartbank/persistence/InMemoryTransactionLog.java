package com.smartbank.persistence;

import com.smartbank.model.Transaction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Non-persistent log for tests and the concurrency demo. */
public final class InMemoryTransactionLog implements TransactionLog {
    private final Queue<Transaction> entries = new ConcurrentLinkedQueue<>();

    @Override
    public void append(Transaction transaction) {
        entries.add(transaction);
    }

    @Override
    public List<Transaction> findByAccount(String accountNumber, int limit) {
        List<Transaction> mine = new ArrayList<>();
        for (Transaction t : entries) {
            if (t.accountNumber().equals(accountNumber)) {
                mine.add(t);
            }
        }
        Collections.reverse(mine);
        return mine.size() > limit ? new ArrayList<>(mine.subList(0, limit)) : mine;
    }

    @Override
    public long maxTransactionId() {
        return entries.stream().mapToLong(Transaction::id).max().orElse(0);
    }

    public int size() {
        return entries.size();
    }
}
