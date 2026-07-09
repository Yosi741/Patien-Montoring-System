package app.core;

import pages.user.User;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class SessionContext {

    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
    private static SessionContext current;

    private final String username;
    private final String role;
    private final String section;
    private final String authSource;
    private final LocalDateTime loginTime;

    private SessionContext(String username, String role, String section, String authSource, LocalDateTime loginTime) {
        this.username = username;
        this.role = role;
        this.section = section;
        this.authSource = authSource;
        this.loginTime = loginTime;
    }

    public static void start(User user, String authSource) {
        if (user == null) {
            current = null;
            return;
        }
        current = new SessionContext(
                user.getUsername(),
                user.getRole(),
                user.getSection(),
                authSource == null || authSource.isBlank() ? "Unknown" : authSource,
                LocalDateTime.now()
        );
    }

    public static void clear() {
        current = null;
    }

    public static SessionContext getCurrent() {
        return current;
    }

    public static String username() {
        return current == null ? "Unknown" : current.username;
    }

    public static String role() {
        return current == null ? "Unknown" : current.role;
    }

    public static String section() {
        return current == null ? "Unknown" : current.section;
    }

    public static String authSource() {
        return current == null ? "Unknown" : current.authSource;
    }

    public static String loginTimeText() {
        return current == null ? "-" : current.loginTime.format(DISPLAY_TIME);
    }

    public String getUsername() { return username; }
    public String getRole() { return role; }
    public String getSection() { return section; }
    public String getAuthSource() { return authSource; }
    public String getLoginTimeText() { return loginTime.format(DISPLAY_TIME); }
}
