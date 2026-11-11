package com.smartbank.repository;

import com.smartbank.model.Account;
import java.util.Collection;
import java.util.Optional;

/** Where live {@link Account} objects are held. Swappable (in-memory now, database later). */
public interface AccountRepository {
    /** @throws IllegalStateException if an account with the same number already exists */
    void add(Account account);

    Optional<Account> findByNumber(String number);

    Collection<Account> findAll();
}
