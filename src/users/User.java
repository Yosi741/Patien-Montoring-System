package users;

public class User {

    private String username;
    private String password;
    private String role;
    private String section;

    public User(String username, String password, String role) {
        this(username, password, role, defaultSection(role));
    }

    public User(String username, String password, String role, String section) {

        this.username = username;
        this.password = password;
        this.role = role;
        this.section = section;

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
