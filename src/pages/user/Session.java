package pages.user;

public class Session {

    private static User currentUser;

    public static void setCurrentUser(User user) {
        currentUser = user;
    }

    public static User getCurrentUser() {
        return currentUser;
    }

    public static String getUsername() {
        if (currentUser == null) {
            return "Unknown";
        }

        return currentUser.getUsername();
    }

    public static String getRole() {
        if (currentUser == null) {
            return "Unknown";
        }

        return currentUser.getRole();
    }
}
