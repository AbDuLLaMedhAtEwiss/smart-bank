package com.smartbank.exception;

public class InvalidAmountException extends ValidationException {
    private static final long serialVersionUID = 1L;

    public InvalidAmountException(String message) {
        super(message);
    }
}
