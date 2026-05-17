package dao;

import database.DatabaseManager;
import database.SchemaInitializer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class SqliteAiNoteDao implements AiNoteDao {

    public SqliteAiNoteDao() {
        ensureSchema();
    }

    @Override
    public boolean saveLegacyNote(String patientId, String sourceTitle, String note, String createdAt, int riskScore) throws SQLException {
        String sql = "INSERT INTO ai_notes(patient_id, risk_score, note, created_at, source_title) "
                + "SELECT ?, ?, ?, ?, ? "
                + "WHERE NOT EXISTS ("
                + "SELECT 1 FROM ai_notes WHERE patient_id = ? AND created_at = ? AND note = ? AND COALESCE(source_title, '') = ?"
                + ")";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, patientId);
            statement.setInt(2, riskScore);
            statement.setString(3, note);
            statement.setString(4, createdAt);
            statement.setString(5, value(sourceTitle));
            statement.setString(6, patientId);
            statement.setString(7, createdAt);
            statement.setString(8, note);
            statement.setString(9, value(sourceTitle));
            return statement.executeUpdate() > 0;
        }
    }

    @Override
    public int count() throws SQLException {
        String sql = "SELECT COUNT(*) FROM ai_notes";
        try (Connection connection = DatabaseManager.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            return resultSet.next() ? resultSet.getInt(1) : 0;
        }
    }

    private void ensureSchema() {
        try {
            SchemaInitializer.initialize();
        } catch (Exception e) {
            System.out.println("SQLite AI note schema check failed: " + e.getMessage());
        }
    }

    private String value(String value) {
        return value == null ? "" : value;
    }
}
