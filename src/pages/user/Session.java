package pages.user;

/**
 * Stores the currently authenticated user for the active ClinicPulse desktop session.
 */
public class Session {

    private static User currentUser;

    public static void setCurrentUser(User user) {
        currentUser = user;
    }

    public static User getCurrentUser() {
        return currentUser;
    }

    /**
     * Returns username used by the staff workflow.
     */
    public static String getUsername() {
        if (currentUser == null) {
            return "Unknown";
        }

        return currentUser.getUsername();
    }

    /**
     * Returns role used by the staff workflow.
     */
    public static String getRole() {
        if (currentUser == null) {
            return "Unknown";
        }

        return currentUser.getRole();
    }
}
