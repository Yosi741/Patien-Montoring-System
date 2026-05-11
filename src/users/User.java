package users;

public class User {

    private String username;
    private String password;
    private String role;

    public User(
            String username,
            String password,
            String role
    ) {

        this.username = username;
        this.password = password;
        this.role = role;

    }

    public String getUsername() {
        return username;
    }

    public String getRole() {
        return role;
    }

    public boolean login(String enteredPassword) {

        return password.equals(enteredPassword);

    }
    public String getPassword() {
        return password;
    }

}