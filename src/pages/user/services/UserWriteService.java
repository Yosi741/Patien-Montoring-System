package pages.user.services;

import pages.user.dao.SqliteUserDao;
import app.helpers.FormValidationHelper;
import app.helpers.PermissionHelper;
import pages.user.User;

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

    public String generateNextStaffId() throws SQLException {
        return userDao.generateNextStaffId();
    }

    public boolean staffIdExists(String staffId) throws SQLException {
        return userDao.staffIdExists(staffId);
    }

    public void createUser(User admin, SqliteUserDao.UserWriteRecord record, char[] password) throws SQLException {
        require(PermissionHelper.canCreateUser(admin), "Only ADMIN users can create staff accounts.");
        FormValidationHelper.ValidationResult validation = FormValidationHelper.combine(
                validateRecord(record, true),
                validatePassword(password)
        );
        requireValid(validation);
        require(!userDao.usernameExists(record.getUsername()), "Username already exists.");
        require(!userDao.staffIdExists(record.getStaffId()), "A staff member with this ID already exists.");

        try {
            userDao.insertUser(record, password == null ? "" : new String(password));
        } finally {
            clear(password);
        }
    }

    public void updateUser(User admin, SqliteUserDao.UserWriteRecord record) throws SQLException {
        updateUser(admin, record == null ? "" : record.getUsername(), record);
    }

    public void updateUser(User admin, String originalUsername, SqliteUserDao.UserWriteRecord record) throws SQLException {
        require(PermissionHelper.canUpdateUser(admin), "Only ADMIN users can update staff accounts.");
        FormValidationHelper.ValidationResult validation = validateRecord(record, false);
        requireValid(validation);
        String original = originalUsername == null ? "" : originalUsername.trim();
        require(userDao.usernameExists(original), "User does not exist: " + original);
        if (!record.getUsername().equalsIgnoreCase(original)) {
            throw new IllegalArgumentException("Username cannot be changed after creation. Create a new user if a different username is needed.");
        }
        require(!userDao.staffIdExistsExcept(record.getStaffId(), original), "A staff member with this ID already exists.");

        userDao.updateUser(original, record);
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
    }

    public void deleteUser(User admin, String affectedUsername) throws SQLException {
        require(PermissionHelper.canDeactivateUser(admin), "Only ADMIN users can delete staff accounts.");
        FormValidationHelper.ValidationResult validation = FormValidationHelper.combine(
                FormValidationHelper.validateRequired("Username", affectedUsername),
                FormValidationHelper.validateMaxLength("Username", affectedUsername, 64)
        );
        requireValid(validation);
        String normalized = affectedUsername.trim();
        SqliteUserDao.UserDirectoryRow target = userDao.findDirectoryRowByUsername(normalized)
                .orElseThrow(() -> new IllegalArgumentException("User does not exist: " + normalized));
        if (isSameUser(admin, normalized)) {
            throw new IllegalStateException("You cannot delete the currently logged-in staff account.");
        }
        if (target.isActive() && "Admin".equalsIgnoreCase(visibleRole(target.getRole()))
                && userDao.countActiveAdmins() <= 1) {
            throw new IllegalStateException("You cannot delete the last active ADMIN account.");
        }
        userDao.deleteUserAccount(normalized);
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
            userDao.updatePassword(affectedUsername.trim(), newPassword == null ? "" : new String(newPassword));
        } finally {
            clear(newPassword);
        }
    }

    private FormValidationHelper.ValidationResult validateRecord(SqliteUserDao.UserWriteRecord record, boolean create) {
        if (record == null) {
            return FormValidationHelper.ValidationResult.error("User record is required.");
        }
        FormValidationHelper.ValidationResult base = FormValidationHelper.combine(
                FormValidationHelper.validateRequired("Staff ID", record.getStaffId()),
                validateStaffId(record.getStaffId()),
                FormValidationHelper.validateRequired("Username", record.getUsername()),
                validateUsername(record.getUsername()),
                FormValidationHelper.validateRequired("Role", record.getRole()),
                validateRole(record.getRole())
                );
        if (!base.isValid()) {
            return base;
        }
        return base;
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

    private FormValidationHelper.ValidationResult validateStaffId(String staffId) {
        if (!hasText(staffId)) {
            return FormValidationHelper.ValidationResult.ok();
        }
        String trimmed = staffId.trim().toUpperCase(Locale.ROOT);
        if (!trimmed.matches("U\\d{4,}")) {
            return FormValidationHelper.ValidationResult.error("Staff ID must use the format U0001.");
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

    private String visibleRole(String internalRole) {
        if (internalRole == null || internalRole.isBlank()) {
            return "Secretary";
        }
        String upper = internalRole.toUpperCase(Locale.ROOT);
        if (upper.contains("ADMIN")) {
            return "Admin";
        }
        if (upper.contains("DOCTOR") || upper.contains("MEDICAL") || upper.contains("DEPARTMENT HEAD")) {
            return "Doctor";
        }
        if (upper.contains("NURSE") || upper.contains("NURSING")) {
            return "Nurse";
        }
        return "Secretary";
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
