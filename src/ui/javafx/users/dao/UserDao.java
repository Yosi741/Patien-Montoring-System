package ui.javafx.users.dao;

import Data_Access_Object.Dao;
import users.User;

import java.sql.SQLException;
import java.util.Optional;

public interface UserDao extends Dao<User, String> {
    Optional<User> findByUsername(String username) throws SQLException;

    boolean usernameExists(String username) throws SQLException;
}
