package ui.javafx.services;

import Data_Access_Object.SqlitePasswordResetDao;
import Data_Access_Object.SqliteUserDao;
import security.PasswordHasher;
import ui.javafx.helpers.AuditWriteHelper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

public class PasswordResetService {

    private static final DateTimeFormatter SQLITE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int TOKEN_BYTES = 24;

    private final SqliteUserDao userDao;
    private final SqlitePasswordResetDao resetDao;
    private final SecureRandom secureRandom = new SecureRandom();

    public PasswordResetService() {
        this(new SqliteUserDao(), new SqlitePasswordResetDao());
    }

    public PasswordResetService(SqliteUserDao userDao, SqlitePasswordResetDao resetDao) {
        this.userDao = userDao;
        this.resetDao = resetDao;
    }

    public ResetTokenResult createResetToken(String username) throws SQLException {
        String cleanUsername = cleanUsername(username);
        if (!userDao.usernameExists(cleanUsername)) {
            throw new IllegalArgumentException("No SQLite user exists with that username.");
        }
        String token = generateToken();
        String now = now();
        String expiresAt = LocalDateTime.now().plusMinutes(20).format(SQLITE_TIME);
        resetDao.expireOpenTokens(cleanUsername, now);
        resetDao.insertToken(cleanUsername, hashToken(token), expiresAt);
        AuditWriteHelper.write(cleanUsername, "CREATE_PASSWORD_RESET_TOKEN", "Local demo password reset token created for " + cleanUsername);
        return new ResetTokenResult(cleanUsername, token, expiresAt);
    }

    public void resetPassword(String username, String token, char[] newPassword) throws SQLException {
        String cleanUsername = cleanUsername(username);
        validatePassword(newPassword);
        SqlitePasswordResetDao.TokenRow row = resetDao.findValidToken(cleanUsername, hashToken(token), now())
                .orElseThrow(() -> new IllegalArgumentException("Reset token is invalid, expired, or already used."));
        userDao.resetPasswordHash(cleanUsername, PasswordHasher.hash(newPassword));
        resetDao.markUsed(row.getId(), now());
        AuditWriteHelper.write(cleanUsername, "RESET_PASSWORD_WITH_TOKEN", "Local demo password reset completed for " + cleanUsername);
    }

    private String cleanUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username is required.");
        }
        return username.trim();
    }

    private void validatePassword(char[] password) {
        if (password == null || password.length < 8) {
            throw new IllegalArgumentException("New password must be at least 8 characters.");
        }
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            throw new IllegalArgumentException("Reset token is required.");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.trim().getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Reset token hashing is unavailable.", e);
        }
    }

    private String now() {
        return LocalDateTime.now().format(SQLITE_TIME);
    }

    public static class ResetTokenResult {
        private final String username;
        private final String token;
        private final String expiresAt;

        public ResetTokenResult(String username, String token, String expiresAt) {
            this.username = username;
            this.token = token;
            this.expiresAt = expiresAt;
        }

        public String getUsername() { return username; }
        public String getToken() { return token; }
        public String getExpiresAt() { return expiresAt; }
    }
}
