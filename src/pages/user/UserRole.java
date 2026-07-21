package pages.user;

import java.util.Locale;

/**
 * Defines the supported ClinicPulse roles and converts stored role values to presentation labels.
 */
public enum UserRole {
    ADMIN,
    DOCTOR,
    NURSE,
    SECRETARY;

    /**
     * Parses the stored value into the corresponding enum constant.
     */
    public static UserRole fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Role is required");
        }

        String normalized = value.trim().toUpperCase(Locale.ROOT);

        if ("STAFF".equals(normalized)
                || "RECEPTION".equals(normalized)
                || "RECEPTIONIST".equals(normalized)) {
            return SECRETARY;
        }

        return UserRole.valueOf(normalized);
    }

    /**
     * Formats name for display in the JavaFX UI.
     */
    public String displayName() {
        return switch (this) {
            case ADMIN -> "Admin";
            case DOCTOR -> "Doctor";
            case NURSE -> "Nurse";
            case SECRETARY -> "Secretary";
        };
    }

    /**
     * Returns the stable value stored in SQLite.
     */
    public String databaseValue() {
        return name();
    }
}
