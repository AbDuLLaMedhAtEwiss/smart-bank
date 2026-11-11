package com.smartbank.exception;

/** Root of all domain errors. Unchecked so business code stays uncluttered. */
public class BankException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public BankException(String message) {
        super(message);
    }

    public BankException(String message, Throwable cause) {
        super(message, cause);
    }
}
