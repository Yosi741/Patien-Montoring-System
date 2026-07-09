package app.helpers;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;

public final class FormValidationHelper {

    private static final DateTimeFormatter DISPLAY_DATE_TIME = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
    private static final DateTimeFormatter SQLITE_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private FormValidationHelper() {
    }

    public static ValidationResult validateRequired(String label, String value) {
        return hasText(value)
                ? ValidationResult.ok()
                : ValidationResult.error(label + " is required.");
    }

    public static ValidationResult validateNumeric(String label, String value, double min, double max) {
        if (!hasText(value)) {
            return ValidationResult.error(label + " is required.");
        }
        try {
            double number = Double.parseDouble(value.trim());
            if (number < min || number > max) {
                return ValidationResult.error(label + " must be between " + min + " and " + max + ".");
            }
            return ValidationResult.ok();
        } catch (NumberFormatException e) {
            return ValidationResult.error(label + " must be a numeric value.");
        }
    }

    public static ValidationResult validateDateTime(String label, String value) {
        if (!hasText(value)) {
            return ValidationResult.error(label + " is required.");
        }
        String trimmed = value.trim();
        try {
            LocalDateTime.parse(trimmed, SQLITE_DATE_TIME);
            return ValidationResult.ok();
        } catch (DateTimeParseException ignored) {
            try {
                LocalDateTime.parse(trimmed, DISPLAY_DATE_TIME);
                return ValidationResult.ok();
            } catch (DateTimeParseException ignoredAgain) {
                try {
                    LocalDateTime.parse(trimmed.replace(" ", "T"));
                    return ValidationResult.ok();
                } catch (DateTimeParseException e) {
                    return ValidationResult.error(label + " must be a valid date/time.");
                }
            }
        }
    }

    public static ValidationResult validatePatientId(String value) {
        return validateNineDigitId("Patient ID", value);
    }

    public static ValidationResult validateNineDigitId(String label, String value) {
        if (!hasText(value)) {
            return ValidationResult.error(label + " is required.");
        }
        return value.trim().matches("\\d{9}")
                ? ValidationResult.ok()
                : ValidationResult.error(label + " must contain only digits and exactly 9 digits.");
    }

    public static ValidationResult validatePersonName(String label, String value) {
        if (!hasText(value)) {
            return ValidationResult.ok();
        }
        String trimmed = value.trim();
        return trimmed.matches("[\\p{L}][\\p{L} '\\-’]*")
                ? ValidationResult.ok()
                : ValidationResult.error("Name must contain letters only.");
    }

    public static ValidationResult validateMaxLength(String label, String value, int maxLength) {
        if (value != null && value.length() > maxLength) {
            return ValidationResult.error(label + " must be " + maxLength + " characters or fewer.");
        }
        return ValidationResult.ok();
    }



    public static ValidationResult combine(ValidationResult... results) {
        ArrayList<String> errors = new ArrayList<>();
        for (ValidationResult result : results) {
            if (result != null && !result.isValid()) {
                errors.addAll(result.getErrors());
            }
        }
        return errors.isEmpty() ? ValidationResult.ok() : ValidationResult.errors(errors);
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    public static class ValidationResult {
        private final List<String> errors;

        private ValidationResult(List<String> errors) {
            this.errors = errors;
        }

        public static ValidationResult ok() {
            return new ValidationResult(List.of());
        }

        public static ValidationResult error(String error) {
            return new ValidationResult(List.of(error));
        }

        public static ValidationResult errors(List<String> errors) {
            return new ValidationResult(List.copyOf(errors));
        }

        public boolean isValid() {
            return errors.isEmpty();
        }

        public List<String> getErrors() {
            return errors;
        }

        public String getMessage() {
            return String.join("\n", errors);
        }
    }
}
