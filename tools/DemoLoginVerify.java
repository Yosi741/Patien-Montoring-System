import app.database.SchemaInitializer;
import pages.user.profile_settings.SqliteUserDao;

import java.util.Map;

/**
 * Manual verification utility that tests the configured demo usernames and passwords against SQLite authentication.
 */
public class DemoLoginVerify {

    /**
     * Runs this manual ClinicPulse verification or demo utility.
     */
    public static void main(String[] args) throws Exception {
        SchemaInitializer.initialize();
        SqliteUserDao userDao = new SqliteUserDao();
        Map<String, String> accounts = Map.of(
                "admin", "admin123",
                "doctor", "doctor123",
                "nurse", "nurse123",
                "secretary", "staff123"
        );
        boolean ok = true;
        for (Map.Entry<String, String> account : accounts.entrySet()) {
            boolean verified = userDao.verifyPassword(account.getKey(), account.getValue());
            System.out.printf("%-10s %s%n", account.getKey(), verified ? "OK" : "FAIL");
            ok &= verified;
        }
        if (!ok) {
            throw new IllegalStateException("One or more demo logins failed.");
        }
        System.out.println("Demo login verification passed.");
    }
}
