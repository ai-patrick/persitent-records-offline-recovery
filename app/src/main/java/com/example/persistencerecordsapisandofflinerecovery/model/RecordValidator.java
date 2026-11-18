package com.example.persistencerecordsapisandofflinerecovery.model;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Record validator with business rules
 */
public class RecordValidator {
    private static final int MAX_TITLE_LENGTH = 100;
    private static final int MAX_DESCRIPTION_LENGTH = 500;
    private static final double MIN_QUANTITY = 0.0;
    private static final double MAX_QUANTITY = 1_000_000.0;
    private static final Set<String> VALID_UNITS = Set.of("kg", "g", "m", "cm", "l", "ml", "pcs", "units");

    public static ValidationResult validate(String title, String description, double quantity, String unit) {
        Map<String, String> errors = new HashMap<>();

        if (title == null || title.trim().isEmpty()) {
            errors.put("title", "Title is required");
        } else if (title.length() > MAX_TITLE_LENGTH) {
            errors.put("title", "Title must be " + MAX_TITLE_LENGTH + " characters or less");
        }

        if (description != null && description.length() > MAX_DESCRIPTION_LENGTH) {
            errors.put("description", "Description must be " + MAX_DESCRIPTION_LENGTH + " characters or less");
        }

        if (quantity < MIN_QUANTITY || quantity > MAX_QUANTITY) {
            errors.put("quantity", "Quantity must be between " + MIN_QUANTITY + " and " + MAX_QUANTITY);
        }

        if (unit == null || !VALID_UNITS.contains(unit.toLowerCase())) {
            errors.put("unit", "Invalid unit. Valid units: " + String.join(", ", VALID_UNITS));
        }

        if (errors.isEmpty()) {
            return new ValidationResult.Valid(Record.create(
                title != null ? title.trim() : "",
                description != null ? description.trim() : "",
                quantity,
                unit != null ? unit.toLowerCase() : "pcs"
            ));
        } else {
            return new ValidationResult.Invalid(errors);
        }
    }
}