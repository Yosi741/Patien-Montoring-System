package app.core;

import pages.user.User;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Exposes read-only details about the currently authenticated ClinicPulse user.
 * Shared controllers and services use it for role-aware display and queries.
 */
public class SessionContext {

    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
    private static SessionContext current;

    private final String username;
    private final String role;
    private final String authSource;
    private final LocalDateTime loginTime;

    /**
     * Creates a session context from the supplied record values.
     */
    private SessionContext(String username, String role, String authSource, LocalDateTime loginTime) {
        this.username = username;
        this.role = role;
        this.authSource = authSource;
        this.loginTime = loginTime;
    }

    /**
     * Starts start.
     */
    public static void start(User user, String authSource) {
        if (user == null) {
            current = null;
            return;
        }
        current = new SessionContext(
                user.getUsername(),
                user.getRole(),
                authSource == null || authSource.isBlank() ? "Unknown" : authSource,
                LocalDateTime.now()
        );
    }

    /**
     * Clears clear and restores its default state.
     */
    public static void clear() {
        current = null;
    }

    public static SessionContext getCurrent() {
        return current;
    }

    /**
     * Returns the username associated with the current session or workflow record.
     */
    public static String username() {
        return current == null ? "Unknown" : current.username;
    }

    /**
     * Returns the role associated with the current session.
     */
    public static String role() {
        return current == null ? "Unknown" : current.role;
    }


    /**
     * Returns the authentication source recorded for the current session.
     */
    public static String authSource() {
        return current == null ? "Unknown" : current.authSource;
    }

    /**
     * Logs login time text for resource diagnostics.
     */
    public static String loginTimeText() {
        return current == null ? "-" : current.loginTime.format(DISPLAY_TIME);
    }

    public String getUsername() { return username; }
    public String getRole() { return role; }
}
