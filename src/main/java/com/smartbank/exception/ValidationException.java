package com.smartbank.exception;

public class ValidationException extends BankException {
    private static final long serialVersionUID = 1L;

    public ValidationException(String message) {
        super(message);
    }
}
