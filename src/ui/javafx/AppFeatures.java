package ui.javafx;

public final class AppFeatures {

    public static final boolean DEMO_MODE = feature("DEMO_MODE", true);
    public static final boolean APP_FEATURE_AI = !DEMO_MODE && feature("APP_FEATURE_AI", false);
    public static final boolean APP_FEATURE_DEVICES = !DEMO_MODE && feature("APP_FEATURE_DEVICES", false);

    private AppFeatures() {
    }

    public static boolean aiEnabled() {
        return APP_FEATURE_AI;
    }

    public static boolean devicesEnabled() {
        return APP_FEATURE_DEVICES;
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
