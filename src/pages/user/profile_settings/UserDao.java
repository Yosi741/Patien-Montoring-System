package pages.user.profile_settings;

import app.database.CrudDao;
import pages.user.User;

import java.sql.SQLException;
import java.util.Optional;

/**
 * Defines persistence operations for ClinicPulse user accounts.
 */
public interface UserDao extends CrudDao<User, String> {
    /**
     * Finds by username in SQLite.
     */
    Optional<User> findByUsername(String username) throws SQLException;

    /**
     * Checks SQLite for username exists.
     */
    boolean usernameExists(String username) throws SQLException;
}
