package services;

import dao.SqliteUserDao;
import security.PasswordHasher;
import ui.javafx.helpers.AuditAction;
import ui.javafx.helpers.AuditWriteHelper;
import ui.javafx.helpers.FormValidationHelper;
import ui.javafx.helpers.PermissionHelper;
import users.User;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.Locale;

public class UserWriteService {

    private static final int MIN_PASSWORD_LENGTH = 8;
    private final SqliteUserDao userDao;

    public UserWriteService() {
        this(new SqliteUserDao());
    }

    public UserWriteService(SqliteUserDao userDao) {
        this.userDao = userDao;
    }

    public void createUser(User admin, SqliteUserDao.UserWriteRecord record, char[] password) throws SQLException {
        require(PermissionHelper.canCreateUser(admin), "Only ADMIN users can create staff accounts.");
        FormValidationHelper.ValidationResult validation = FormValidationHelper.combine(
                validateRecord(record, true),
                validatePassword(password)
        );
        requireValid(validation);
        require(!userDao.usernameExists(record.getUsername()), "Username already exists.");

        try {
            String hash = PasswordHasher.hash(password);
            userDao.insertUser(record, hash);
            audit(admin, AuditAction.CREATE_USER, "Admin " + username(admin) + " created user " + record.getUsername());
        } finally {
            clear(password);
        }
    }

    public void updateUser(User admin, SqliteUserDao.UserWriteRecord record) throws SQLException {
        require(PermissionHelper.canUpdateUser(admin), "Only ADMIN users can update staff accounts.");
        FormValidationHelper.ValidationResult validation = validateRecord(record, false);
        requireValid(validation);
        require(userDao.usernameExists(record.getUsername()), "User does not exist: " + record.getUsername());

        userDao.updateUser(record);
        audit(admin, AuditAction.UPDATE_USER, "Admin " + username(admin) + " updated user " + record.getUsername());
    }

    public void deactivateUser(User admin, String affectedUsername, boolean confirmedSelfDeactivation) throws SQLException {
        require(PermissionHelper.canDeactivateUser(admin), "Only ADMIN users can deactivate staff accounts.");
        FormValidationHelper.ValidationResult validation = FormValidationHelper.combine(
                FormValidationHelper.validateRequired("Username", affectedUsername),
                FormValidationHelper.validateMaxLength("Username", affectedUsername, 64)
        );
        requireValid(validation);
        require(userDao.usernameExists(affectedUsername.trim()), "User does not exist: " + affectedUsername);
        if (isSameUser(admin, affectedUsername) && !confirmedSelfDeactivation) {
            throw new IllegalStateException("Self-deactivation requires explicit confirmation.");
        }

        userDao.deactivateUser(affectedUsername.trim());
        audit(admin, AuditAction.DEACTIVATE_USER, "Admin " + username(admin) + " deactivated user " + affectedUsername.trim());
    }

    public void resetPassword(User admin, String affectedUsername, char[] newPassword) throws SQLException {
        require(PermissionHelper.canResetUserPassword(admin), "Only ADMIN users can reset staff passwords.");
        FormValidationHelper.ValidationResult validation = FormValidationHelper.combine(
                FormValidationHelper.validateRequired("Username", affectedUsername),
                FormValidationHelper.validateMaxLength("Username", affectedUsername, 64),
                validatePassword(newPassword)
        );
        requireValid(validation);
        require(userDao.usernameExists(affectedUsername.trim()), "User does not exist: " + affectedUsername);

        try {
            String hash = PasswordHasher.hash(newPassword);
            userDao.resetPasswordHash(affectedUsername.trim(), hash);
            audit(admin, AuditAction.RESET_USER_PASSWORD, "Admin " + username(admin) + " reset password for " + affectedUsername.trim());
        } finally {
            clear(newPassword);
        }
    }

    private FormValidationHelper.ValidationResult validateRecord(SqliteUserDao.UserWriteRecord record, boolean create) {
        if (record == null) {
            return FormValidationHelper.ValidationResult.error("User record is required.");
        }
        FormValidationHelper.ValidationResult base = FormValidationHelper.combine(
                FormValidationHelper.validateRequired("Username", record.getUsername()),
                validateUsername(record.getUsername()),
                FormValidationHelper.validateRequired("Role", record.getRole()),
                validateRole(record.getRole()),
                FormValidationHelper.validateMaxLength("Section", record.getSection(), 80)
        );
        if (!base.isValid()) {
            return base;
        }
        String role = normalizeRole(record.getRole());
        if (("DOCTOR".equals(role) || "NURSE".equals(role)) && !hasText(record.getSection())) {
            return FormValidationHelper.ValidationResult.error("Section/department is required for Doctor and Nurse users.");
        }
        return FormValidationHelper.ValidationResult.ok();
    }

    private FormValidationHelper.ValidationResult validateUsername(String username) {
        if (!hasText(username)) {
            return FormValidationHelper.ValidationResult.error("Username is required.");
        }
        String trimmed = username.trim();
        if (!trimmed.matches("[A-Za-z0-9._-]{3,64}")) {
            return FormValidationHelper.ValidationResult.error("Username must be 3-64 letters, numbers, dots, dashes, or underscores.");
        }
        return FormValidationHelper.ValidationResult.ok();
    }

    private FormValidationHelper.ValidationResult validateRole(String role) {
        String normalized = normalizeRole(role);
        if ("ADMIN".equals(normalized) || "DOCTOR".equals(normalized) || "NURSE".equals(normalized) || "STAFF".equals(normalized)) {
            return FormValidationHelper.ValidationResult.ok();
        }
        return FormValidationHelper.ValidationResult.error("Role must be ADMIN, DOCTOR, NURSE, or STAFF.");
    }

    private FormValidationHelper.ValidationResult validatePassword(char[] password) {
        if (password == null || password.length == 0) {
            return FormValidationHelper.ValidationResult.error("Password is required.");
        }
        if (password.length < MIN_PASSWORD_LENGTH) {
            return FormValidationHelper.ValidationResult.error("Password must be at least " + MIN_PASSWORD_LENGTH + " characters.");
        }
        return FormValidationHelper.ValidationResult.ok();
    }

    private void require(boolean allowed, String message) {
        if (!allowed) {
            throw new SecurityException(message);
        }
    }

    private void requireValid(FormValidationHelper.ValidationResult validation) {
        if (!validation.isValid()) {
            throw new IllegalArgumentException(validation.getMessage());
        }
    }

    private void audit(User admin, String action, String detail) {
        try {
            AuditWriteHelper.write(username(admin), action, detail);
        } catch (Exception e) {
            System.out.println("SQLite user write audit skipped: " + e.getMessage());
        }
    }

    private boolean isSameUser(User admin, String username) {
        return admin != null
                && admin.getUsername() != null
                && username != null
                && admin.getUsername().equalsIgnoreCase(username.trim());
    }

    private String username(User user) {
        return user == null || user.getUsername() == null || user.getUsername().isBlank()
                ? "Unknown"
                : user.getUsername();
    }

    private String normalizeRole(String role) {
        return role == null ? "" : role.trim().toUpperCase(Locale.ROOT);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private void clear(char[] password) {
        if (password != null) {
            Arrays.fill(password, '\0');
        }
    }
}
