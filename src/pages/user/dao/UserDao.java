package pages.user.dao;

import app.database.CrudDao;
import pages.user.User;

import java.sql.SQLException;
import java.util.Optional;

public interface UserDao extends CrudDao<User, String> {
    Optional<User> findByUsername(String username) throws SQLException;

    boolean usernameExists(String username) throws SQLException;
}
