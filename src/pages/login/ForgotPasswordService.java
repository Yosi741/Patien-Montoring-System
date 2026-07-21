package pages.login;

import pages.user.User;
import pages.user.profile_settings.SqliteUserDao;

import java.sql.SQLException;

/**
 * Validates username and staff ID recovery requests and updates local account passwords.
 */
public class ForgotPasswordService {

    private static final int MIN_PASSWORD_LENGTH = 8;

    private final SqliteUserDao userDao;

    /**
     * Creates the service with the dependencies used by the login workflow.
     */
    public ForgotPasswordService() {
        this(new SqliteUserDao());
    }

    /**
     * Creates the service with the dependencies used by the login workflow.
     */
    public ForgotPasswordService(SqliteUserDao userDao) {
        this.userDao = userDao;
    }

    /**
     * Validates and submits reset.
     */
    public ForgotPasswordResult requestReset(String username, String staffId) throws SQLException {
        String cleanUsername = clean(username);
        String cleanStaffId = clean(staffId);
        if (cleanUsername.isEmpty() || cleanStaffId.isEmpty()) {
            return ForgotPasswordResult.emptyCredentials();
        }

        User matchedUser = userDao.findActiveUserByUsernameAndStaffId(cleanUsername, cleanStaffId).orElse(null);
        if (matchedUser == null) {
            return ForgotPasswordResult.credentialsMismatch();
        }
        return ForgotPasswordResult.readyForReset(matchedUser.getUsername(), matchedUser.getStaffId());
    }

    /**
     * Updates password.
     */
    public void updatePassword(String username, String staffId, String newPassword, String confirmPassword) throws SQLException {
        ForgotPasswordResult identityResult = requestReset(username, staffId);
        if (identityResult.status() == Status.EMPTY_CREDENTIALS) {
            throw new IllegalArgumentException("Username and Staff ID are required.");
        }
        if (identityResult.status() != Status.READY_FOR_RESET) {
            throw new IllegalArgumentException("The username and staff ID do not match our records.");
        }

        String cleanPassword = newPassword == null ? "" : newPassword;
        String cleanConfirm = confirmPassword == null ? "" : confirmPassword;
        if (cleanPassword.isBlank() || cleanConfirm.isBlank()) {
            throw new IllegalArgumentException("New password and confirmation are required.");
        }
        if (!cleanPassword.equals(cleanConfirm)) {
            throw new IllegalArgumentException("New password and confirmation do not match.");
        }
        if (cleanPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("New password must be at least " + MIN_PASSWORD_LENGTH + " characters.");
        }

        userDao.updatePassword(identityResult.username(), cleanPassword);
    }

    /**
     * Trims and normalizes clean before storage or comparison.
     */
    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    public enum Status {
        EMPTY_CREDENTIALS,
        CREDENTIALS_MISMATCH,
        READY_FOR_RESET
    }

    public record ForgotPasswordResult(Status status, String username, String staffId) {
        /**
         * Creates the forgot-password result used when required credentials are blank.
         */
        public static ForgotPasswordResult emptyCredentials() {
            return new ForgotPasswordResult(Status.EMPTY_CREDENTIALS, "", "");
        }

        /**
         * Creates the forgot-password result used when username and email do not match.
         */
        public static ForgotPasswordResult credentialsMismatch() {
            return new ForgotPasswordResult(Status.CREDENTIALS_MISMATCH, "", "");
        }

        /**
         * Creates the successful forgot-password result that permits a password reset.
         */
        public static ForgotPasswordResult readyForReset(String username, String staffId) {
            return new ForgotPasswordResult(Status.READY_FOR_RESET, username, staffId);
        }
    }
}
