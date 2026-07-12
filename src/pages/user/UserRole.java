package pages.user;

import java.util.Locale;

public enum UserRole {
    ADMIN,
    DOCTOR,
    NURSE,
    SECRETARY;

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

    public String displayName() {
        return switch (this) {
            case ADMIN -> "Admin";
            case DOCTOR -> "Doctor";
            case NURSE -> "Nurse";
            case SECRETARY -> "Secretary";
        };
    }

    public String databaseValue() {
        return name();
    }
}
