package com.smartbank.exception;

public class AuthenticationException extends BankException {
    private static final long serialVersionUID = 1L;

    public AuthenticationException(String message) {
        super(message);
    }
}
