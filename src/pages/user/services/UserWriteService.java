package pages.user.services;

import pages.room_section.SqliteSectionDao;
import pages.user.dao.SqliteUserDao;
import app.PasswordHasher;
import pages.audit_log.AuditAction;
import pages.audit_log.AuditWriteHelper;
import app.helpers.FormValidationHelper;
import app.helpers.PermissionHelper;
import pages.user.User;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.Locale;

public class UserWriteService {

    private static final int MIN_PASSWORD_LENGTH = 8;
    private final SqliteUserDao userDao;
    private final SqliteSectionDao sectionDao = new SqliteSectionDao();

    public UserWriteService() {
        this(new SqliteUserDao());
    }

    public UserWriteService(SqliteUserDao userDao) {
        this.userDao = userDao;
    }

    public String generateNextStaffId() throws SQLException {
        return userDao.generateNextStaffId();
    }

    public void createUser(User admin, SqliteUserDao.UserWriteRecord record, char[] password) throws SQLException {
        require(PermissionHelper.canCreateUser(admin), "Only ADMIN users can create staff accounts.");
        SqliteUserDao.UserWriteRecord preparedRecord = withGeneratedStaffId(record);
        FormValidationHelper.ValidationResult validation = FormValidationHelper.combine(
                validateRecord(preparedRecord, true),
                validatePassword(password)
        );
        requireValid(validation);
        require(!userDao.usernameExists(preparedRecord.getUsername()), "Username already exists.");
        require(!userDao.staffIdExists(preparedRecord.getStaffId()), "Staff ID already exists.");

        try {
            String hash = PasswordHasher.hash(password);
            userDao.insertUser(preparedRecord, hash);
            audit(admin, AuditAction.CREATE_USER, "Admin " + username(admin) + " created user "
                    + preparedRecord.getUsername() + " with Staff ID " + preparedRecord.getStaffId());
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
        require(!userDao.staffIdExistsExcept(record.getStaffId(), original), "Staff ID already exists.");

        userDao.updateUser(original, record);
        audit(admin, AuditAction.UPDATE_USER,
                "Admin " + username(admin) + " updated user " + original + " (" + record.getStaffId() + ")");
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
                FormValidationHelper.validateRequired("Staff ID", record.getStaffId()),
                validateStaffId(record.getStaffId()),
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
        if ("ADMIN".equals(role)) {
            return FormValidationHelper.ValidationResult.ok();
        }
        if (!hasText(record.getSection()) || "All".equalsIgnoreCase(record.getSection())) {
            return FormValidationHelper.ValidationResult.error("A real active section is required for Doctor, Nurse, and Staff users.");
        }
        try {
            SqliteSectionDao.SectionRecord section = sectionDao.findByName(record.getSection()).orElse(null);
            if (section == null || !"ACTIVE".equalsIgnoreCase(section.getStatus())) {
                return FormValidationHelper.ValidationResult.error("Select an active section for this role.");
            }
        } catch (SQLException e) {
            return FormValidationHelper.ValidationResult.error("Could not validate section: " + e.getMessage());
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

    private FormValidationHelper.ValidationResult validateStaffId(String staffId) {
        if (!hasText(staffId)) {
            return FormValidationHelper.ValidationResult.error("Staff ID is required.");
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

    private SqliteUserDao.UserWriteRecord withGeneratedStaffId(SqliteUserDao.UserWriteRecord record) throws SQLException {
        if (record == null) {
            return null;
        }
        if (hasText(record.getStaffId())) {
            return record;
        }
        return new SqliteUserDao.UserWriteRecord(
                userDao.generateNextStaffId(),
                record.getUsername(),
                record.getRole(),
                record.getSection(),
                record.isActive()
        );
    }

    private void clear(char[] password) {
        if (password != null) {
            Arrays.fill(password, '\0');
        }
    }
}
