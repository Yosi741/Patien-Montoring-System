package Data_Access_Object;

import database.DatabaseManager;
import database.SchemaInitializer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class SqlitePasswordResetDao {

    public SqlitePasswordResetDao() {
        ensureSchema();
    }

    public long insertToken(String username, String tokenHash, String expiresAt) throws SQLException {
        String sql = "INSERT INTO password_reset_tokens(username, token_hash, expires_at) VALUES(?, ?, ?)";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, username);
            statement.setString(2, tokenHash);
            statement.setString(3, expiresAt);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : 0;
            }
        }
    }

    public Optional<TokenRow> findValidToken(String username, String tokenHash, String now) throws SQLException {
        String sql = "SELECT id, username, expires_at FROM password_reset_tokens "
                + "WHERE username = ? AND token_hash = ? AND used_at IS NULL AND expires_at > ? "
                + "ORDER BY id DESC LIMIT 1";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            statement.setString(2, tokenHash);
            statement.setString(3, now);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(new TokenRow(
                            resultSet.getLong("id"),
                            resultSet.getString("username"),
                            resultSet.getString("expires_at")
                    ));
                }
            }
        }
        return Optional.empty();
    }

    public void markUsed(long id, String usedAt) throws SQLException {
        String sql = "UPDATE password_reset_tokens SET used_at = ? WHERE id = ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, usedAt);
            statement.setLong(2, id);
            statement.executeUpdate();
        }
    }

    public void expireOpenTokens(String username, String usedAt) throws SQLException {
        String sql = "UPDATE password_reset_tokens SET used_at = ? WHERE username = ? AND used_at IS NULL";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, usedAt);
            statement.setString(2, username);
            statement.executeUpdate();
        }
    }

    private void ensureSchema() {
        try {
            SchemaInitializer.initialize();
        } catch (Exception e) {
            System.out.println("SQLite password reset schema check failed: " + e.getMessage());
        }
    }

    public static class TokenRow {
        private final long id;
        private final String username;
        private final String expiresAt;

        public TokenRow(long id, String username, String expiresAt) {
            this.id = id;
            this.username = username;
            this.expiresAt = expiresAt;
        }

        public long getId() { return id; }
        public String getUsername() { return username; }
        public String getExpiresAt() { return expiresAt; }
    }
}
