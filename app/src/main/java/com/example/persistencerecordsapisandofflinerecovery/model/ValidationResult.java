package com.example.persistencerecordsapisandofflinerecovery.model;

import java.util.Map;

/**
 * Validation result for record input
 */
public sealed class ValidationResult permits ValidationResult.Valid, ValidationResult.Invalid {
    public static final class Valid extends ValidationResult {
        public final Record record;
        public Valid(Record record) { this.record = record; }
    }

    public static final class Invalid extends ValidationResult {
        public final Map<String, String> errors;
        public Invalid(Map<String, String> errors) { this.errors = errors; }
    }
}