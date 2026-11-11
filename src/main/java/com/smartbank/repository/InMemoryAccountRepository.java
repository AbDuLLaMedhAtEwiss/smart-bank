package com.smartbank.repository;

import com.smartbank.model.Account;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryAccountRepository implements AccountRepository {
    private final Map<String, Account> accounts = new ConcurrentHashMap<>();

    @Override
    public void add(Account account) {
        if (accounts.putIfAbsent(account.getNumber(), account) != null) {
            throw new IllegalStateException("Duplicate account number " + account.getNumber());
        }
    }

    @Override
    public Optional<Account> findByNumber(String number) {
        return number == null ? Optional.empty() : Optional.ofNullable(accounts.get(number));
    }

    @Override
    public Collection<Account> findAll() {
        return List.copyOf(accounts.values());
    }
}
