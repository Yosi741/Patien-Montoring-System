package pages.user.dao;

import app.DatabaseManager;
import app.SchemaInitializer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class SqliteUserProfileDao {

    public SqliteUserProfileDao() {
        ensureSchema();
    }

    public Optional<UserProfileRow> findByUsername(String username) throws SQLException {
        String sql = "SELECT username, email, phone, updated_at FROM user_profiles WHERE username = ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(new UserProfileRow(
                            resultSet.getString("username"),
                            value(resultSet.getString("email")),
                            value(resultSet.getString("phone")),
                            value(resultSet.getString("updated_at"))
                    ));
                }
            }
        }
        return Optional.empty();
    }

    public void upsert(String username, String email, String phone) throws SQLException {
        String sql = "INSERT INTO user_profiles(username, email, phone, updated_at) VALUES(?, ?, ?, CURRENT_TIMESTAMP) "
                + "ON CONFLICT(username) DO UPDATE SET "
                + "email = excluded.email, phone = excluded.phone, updated_at = CURRENT_TIMESTAMP";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            statement.setString(2, value(email));
            statement.setString(3, value(phone));
            statement.executeUpdate();
        }
    }

    private void ensureSchema() {
        try {
            SchemaInitializer.initialize();
        } catch (Exception e) {
            System.out.println("SQLite user profile schema check failed: " + e.getMessage());
        }
    }

    private String value(String value) {
        return value == null ? "" : value.trim();
    }

    public static class UserProfileRow {
        private final String username;
        private final String email;
        private final String phone;
        private final String updatedAt;

        public UserProfileRow(String username, String email, String phone, String updatedAt) {
            this.username = username;
            this.email = email;
            this.phone = phone;
            this.updatedAt = updatedAt;
        }

        public String getUsername() { return username; }
        public String getEmail() { return email; }
        public String getPhone() { return phone; }
        public String getUpdatedAt() { return updatedAt; }
    }
}
