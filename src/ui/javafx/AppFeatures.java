package ui.javafx;

public final class AppFeatures {

    public static final boolean DEMO_MODE = feature("DEMO_MODE", true);
    public static final boolean FEATURE_MESSAGES = feature("APP_FEATURE_MESSAGES", true);
    public static final boolean FEATURE_NOTIFICATIONS = feature("APP_FEATURE_NOTIFICATIONS", true);
    public static final boolean FEATURE_MEDICAL_FILES = !DEMO_MODE && feature("APP_FEATURE_MEDICAL_FILES", false);

    private AppFeatures() {
    }

    public static boolean messagesEnabled() {
        return FEATURE_MESSAGES;
    }

    public static boolean notificationsEnabled() {
        return FEATURE_NOTIFICATIONS;
    }

    public static boolean medicalFilesEnabled() {
        return FEATURE_MEDICAL_FILES;
    }

    private static boolean feature(String key, boolean defaultValue) {
        String property = System.getProperty(key);
        String environment = System.getenv(key);
        String value = property == null || property.isBlank() ? environment : property;
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(value)
                || "1".equals(value)
                || "yes".equalsIgnoreCase(value)
                || "on".equalsIgnoreCase(value);
    }
}
