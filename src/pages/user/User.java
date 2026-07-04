package pages.user;

public class User {

    private String username;
    private String password;
    private String role;
    private String section;
    private String staffId;

    public User(String username, String password, String role) {
        this(username, password, role, defaultSection(role), "");
    }

    public User(String username, String password, String role, String section) {
        this(username, password, role, section, "");
    }

    public User(String username, String password, String role, String section, String staffId) {
        this.username = username;
        this.password = password;
        this.role = role;
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

    public boolean login(String enteredPassword) {

        return password.equals(enteredPassword);

    }
    public String getPassword() {
        return password;
    }

    private static String defaultSection(String role) {
        if (role.equals("Admin") || role.equals("System Admin") || role.equals("Hospital Director")) {
            return "All";
        }
        return "All";
    }

}
