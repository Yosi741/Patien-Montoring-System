package pages.login;

import pages.login.dao.SqliteEmailOutboxDao;
import pages.user.dao.SqliteUserDao;
import pages.audit_log.AuditWriteHelper;

import java.sql.SQLException;

public class ForgotPasswordService {

    private final SqliteUserDao userDao;
    private final SqliteEmailOutboxDao emailOutboxDao;

    public ForgotPasswordService() {
        this(new SqliteUserDao(), new SqliteEmailOutboxDao());
    }

    public ForgotPasswordService(SqliteUserDao userDao, SqliteEmailOutboxDao emailOutboxDao) {
        this.userDao = userDao;
        this.emailOutboxDao = emailOutboxDao;
    }

    public ForgotPasswordResult requestReset(String username) throws SQLException {
        String cleanUsername = username == null ? "" : username.trim();
        if (cleanUsername.isEmpty()) {
            return ForgotPasswordResult.emptyUsername();
        }

        SqliteUserDao.PasswordResetContact contact = userDao.findPasswordResetContact(cleanUsername).orElse(null);
        if (contact == null) {
            return ForgotPasswordResult.userNotFound(cleanUsername);
        }
        if (contact.email().isBlank()) {
            return ForgotPasswordResult.noEmailConfigured(contact.username());
        }

        emailOutboxDao.queueEmail(
                contact.email(),
                "SPMS password reset request",
                buildResetMessage(contact.username())
        );
        AuditWriteHelper.write(contact.username(), "QUEUE_PASSWORD_RESET_EMAIL",
                "Password reset email queued for " + maskEmail(contact.email()));
        return ForgotPasswordResult.emailQueued(contact.username(), maskEmail(contact.email()));
    }

    private String buildResetMessage(String username) {
        return "A password reset request was received for SPMS account '" + username + "'.\n\n"
                + "For this local academic demo, password reset requests are queued for administrator review. "
                + "Please contact an administrator to complete the reset.";
    }

    private String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "";
        }
        String trimmed = email.trim();
        int atIndex = trimmed.indexOf('@');
        if (atIndex <= 0) {
            return "***";
        }
        String local = trimmed.substring(0, atIndex);
        String domain = trimmed.substring(atIndex);
        String visible = local.length() <= 2 ? local.substring(0, 1) : local.substring(0, 2);
        return visible + "***" + domain;
    }

    public enum Status {
        EMPTY_USERNAME,
        USER_NOT_FOUND,
        NO_EMAIL_CONFIGURED,
        EMAIL_QUEUED
    }

    public record ForgotPasswordResult(Status status, String username, String maskedEmail) {
        public static ForgotPasswordResult emptyUsername() {
            return new ForgotPasswordResult(Status.EMPTY_USERNAME, "", "");
        }

        public static ForgotPasswordResult userNotFound(String username) {
            return new ForgotPasswordResult(Status.USER_NOT_FOUND, username, "");
        }

        public static ForgotPasswordResult noEmailConfigured(String username) {
            return new ForgotPasswordResult(Status.NO_EMAIL_CONFIGURED, username, "");
        }

        public static ForgotPasswordResult emailQueued(String username, String maskedEmail) {
            return new ForgotPasswordResult(Status.EMAIL_QUEUED, username, maskedEmail);
        }
    }
}
