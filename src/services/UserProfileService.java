package services;

import dao.SqliteUserDao;
import dao.SqliteUserProfileDao;
import security.PasswordHasher;
import ui.javafx.helpers.AuditWriteHelper;
import ui.javafx.helpers.FormValidationHelper;
import users.User;

import java.sql.SQLException;
import java.util.Optional;

public class UserProfileService {

    private final SqliteUserDao userDao;
    private final SqliteUserProfileDao profileDao;

    public UserProfileService() {
        this(new SqliteUserDao(), new SqliteUserProfileDao());
    }

    public UserProfileService(SqliteUserDao userDao, SqliteUserProfileDao profileDao) {
        this.userDao = userDao;
        this.profileDao = profileDao;
    }

    public Optional<SqliteUserProfileDao.UserProfileRow> findProfile(String username) throws SQLException {
        return profileDao.findByUsername(username);
    }

    public void updateProfile(User user, String email, String phone) throws SQLException {
        String username = username(user);
        validateProfile(email, phone);
        profileDao.upsert(username, email, phone);
        userDao.updateEmail(username, email);
        AuditWriteHelper.write(username, "UPDATE_PROFILE", "Updated safe profile fields for " + username);
    }

    public void changeOwnPassword(User user, char[] currentPassword, char[] newPassword) throws SQLException {
        String username = username(user);
        if (!userDao.verifyPassword(username, currentPassword)) {
            throw new IllegalArgumentException("Current password is incorrect.");
        }
        validatePassword(newPassword);
        userDao.resetPasswordHash(username, PasswordHasher.hash(newPassword));
        AuditWriteHelper.write(username, "CHANGE_PASSWORD", "Changed own JavaFX SQLite password");
    }

    private void validateProfile(String email, String phone) {
        FormValidationHelper.ValidationResult validation = FormValidationHelper.combine(
                FormValidationHelper.validateMaxLength("Email", email, 120),
                FormValidationHelper.validateMaxLength("Phone", phone, 40)
        );
        if (!validation.isValid()) {
            throw new IllegalArgumentException(validation.getMessage());
        }
        if (email != null && !email.isBlank() && !email.trim().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new IllegalArgumentException("Email must be a valid email address.");
        }
    }

    private void validatePassword(char[] newPassword) {
        if (newPassword == null || newPassword.length < 8) {
            throw new IllegalArgumentException("New password must be at least 8 characters.");
        }
    }

    private String username(User user) {
        if (user == null || user.getUsername() == null || user.getUsername().isBlank()) {
            throw new SecurityException("Login is required.");
        }
        return user.getUsername();
    }
}
