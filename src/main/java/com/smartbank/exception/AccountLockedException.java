package com.smartbank.exception;

public class AccountLockedException extends AuthenticationException {
    private static final long serialVersionUID = 1L;

    public AccountLockedException(String accountNumber) {
        super("Account " + accountNumber + " is locked after too many failed PIN attempts. Contact the bank.");
    }
}
