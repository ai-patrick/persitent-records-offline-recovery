package com.example.persistencerecordsapisandofflinerecovery.repository;

import java.util.Map;

/**
 * Validation exception with field-level errors.
 */
public class ValidationException extends Exception {
    public final Map<String, String> errors;

    public ValidationException(Map<String, String> errors) {
        super("Validation failed: " + formatErrors(errors));
        this.errors = errors;
    }

    private static String formatErrors(Map<String, String> errors) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : errors.entrySet()) {
            if (!first) sb.append(", ");
            sb.append(entry.getKey()).append("=").append(entry.getValue());
            first = false;
        }
        return sb.toString();
    }
}