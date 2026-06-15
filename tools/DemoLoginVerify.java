import Data_Access_Object.SqliteUserDao;
import users.User;

import java.util.Optional;

public class DemoLoginVerify {

    private static final String[][] ACCOUNTS = {
            {"admin", "admin123"},
            {"doctor", "doctor123"},
            {"nurse", "nurse123"},
            {"staff", "staff123"}
    };

    public static void main(String[] args) throws Exception {
        SqliteUserDao userDao = new SqliteUserDao();
        for (String[] account : ACCOUNTS) {
            String username = account[0];
            char[] password = account[1].toCharArray();
            boolean valid = userDao.verifyPassword(username, password);
            Optional<User> user = userDao.findByUsername(username.toUpperCase());
            if (user.isEmpty()) {
                throw new IllegalStateException("Missing active demo user: " + username);
            }
            if (!valid) {
                throw new IllegalStateException("Password verification failed for demo user: " + username);
            }
            System.out.println(username + " | " + user.get().getRole() + " | " + user.get().getSection() + " | active | login-ok");
        }
    }
}
