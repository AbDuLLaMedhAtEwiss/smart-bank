package com.smartbank.model;

import com.smartbank.exception.ValidationException;

/** Immutable customer data. The compact constructor validates and normalises the input. */
public record Customer(String name, String email) {

    public Customer {
        name = clean(name, "Name");
        email = clean(email, "Email");
        if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new ValidationException("Invalid email address: " + email);
        }
    }

    private static String clean(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(label + " is required");
        }
        String v = value.strip();
        if (v.length() > 100) {
            throw new ValidationException(label + " is too long (max 100 characters)");
        }
        // '|' is the field separator of the storage format; control chars would break the line format.
        if (v.chars().anyMatch(c -> c == '|' || Character.isISOControl(c))) {
            throw new ValidationException(label + " contains illegal characters");
        }
        return v;
    }
}
