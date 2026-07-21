package pages.user;

/**
 * Represents a ClinicPulse staff account, role, department, contact details, and active state.
 */
public class User {

    private String username;
    private String password;
    private String role;
    private String section;
    private String staffId;

    /**
     * Creates a user from the supplied record values.
     */
    public User(String username, String password, String role) {
        this(username, password, role, defaultSection(role), "");
    }

    /**
     * Creates a user from the supplied record values.
     */
    public User(String username, String password, String role, String section) {
        this(username, password, role, section, "");
    }

    /**
     * Creates a user from the supplied record values.
     */
    public User(String username, String password, String role, String section, String staffId) {
        this.username = username;
        this.password = password;
        this.role = UserRole.fromValue(role).databaseValue();
        this.section = section;
        this.staffId = staffId == null ? "" : staffId;
    }

    public String getUsername() {
        return username;
    }

    public String getRole() {
        return role;
    }

    public String getSection() {
        return section;
    }

    public String getStaffId() {
        return staffId;
    }

    public void setStaffId(String staffId) {
        this.staffId = staffId == null ? "" : staffId;
    }

    /**
     * Logs login for resource diagnostics.
     */
    public boolean login(String enteredPassword) {

        return password.equals(enteredPassword);

    }
    public String getPassword() {
        return password;
    }

    /**
     * Returns the default section used by this workflow.
     */
    private static String defaultSection(String role) {
        if (role.equals("Admin") || role.equals("System Admin") || role.equals("Hospital Director")) {
            return "All";
        }
        return "All";
    }

}
