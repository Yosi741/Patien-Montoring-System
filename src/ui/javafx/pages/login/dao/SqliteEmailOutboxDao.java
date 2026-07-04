package ui.javafx.pages.login.dao;

import app.DatabaseManager;
import app.SchemaInitializer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class SqliteEmailOutboxDao {

    public SqliteEmailOutboxDao() {
        ensureSchema();
    }

    public long queueEmail(String recipientEmail, String subject, String body) throws SQLException {
        String sql = "INSERT INTO email_outbox(recipient_email, subject, body, status, created_at) "
                + "VALUES(?, ?, ?, 'QUEUED', CURRENT_TIMESTAMP)";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, recipientEmail);
            statement.setString(2, subject);
            statement.setString(3, body);
            statement.executeUpdate();
            try (var keys = statement.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : 0L;
            }
        }
    }

    private void ensureSchema() {
        try {
            SchemaInitializer.initialize();
        } catch (Exception e) {
            System.out.println("SQLite email outbox schema check failed: " + e.getMessage());
        }
    }
}
