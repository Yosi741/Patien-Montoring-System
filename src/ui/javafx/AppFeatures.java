package ui.javafx;

public final class AppFeatures {

    public static final boolean DEMO_MODE = feature("DEMO_MODE", true);
    public static final boolean FEATURE_AI = !DEMO_MODE && feature("APP_FEATURE_AI", false);
    public static final boolean FEATURE_DEVICES = !DEMO_MODE && feature("APP_FEATURE_DEVICES", false);
    public static final boolean FEATURE_BACKUP_EXPORT = !DEMO_MODE && feature("APP_FEATURE_BACKUP_EXPORT", false);
    public static final boolean FEATURE_STAFF_ACTIVITY = !DEMO_MODE && feature("APP_FEATURE_STAFF_ACTIVITY", false);
    public static final boolean FEATURE_MESSAGES = feature("APP_FEATURE_MESSAGES", true);
    public static final boolean FEATURE_ALERT_CENTER_PAGE = !DEMO_MODE && feature("APP_FEATURE_ALERT_CENTER_PAGE", false);
    public static final boolean FEATURE_NOTIFICATIONS = feature("APP_FEATURE_NOTIFICATIONS", true);
    public static final boolean FEATURE_MEDICAL_FILES = !DEMO_MODE && feature("APP_FEATURE_MEDICAL_FILES", false);

    public static final boolean APP_FEATURE_AI = FEATURE_AI;
    public static final boolean APP_FEATURE_DEVICES = FEATURE_DEVICES;

    private AppFeatures() {
    }

    public static boolean aiEnabled() {
        return FEATURE_AI;
    }

    public static boolean devicesEnabled() {
        return FEATURE_DEVICES;
    }

    public static boolean backupExportEnabled() {
        return FEATURE_BACKUP_EXPORT;
    }

    public static boolean staffActivityEnabled() {
        return FEATURE_STAFF_ACTIVITY;
    }

    public static boolean messagesEnabled() {
        return FEATURE_MESSAGES;
    }

    public static boolean alertCenterPageEnabled() {
        return FEATURE_ALERT_CENTER_PAGE;
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
