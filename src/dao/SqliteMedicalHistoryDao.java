package dao;

import database.DatabaseManager;
import database.SchemaInitializer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class SqliteMedicalHistoryDao implements MedicalHistoryDao {

    public SqliteMedicalHistoryDao() {
        ensureSchema();
    }

    @Override
    public boolean saveEntry(String patientId, String category, String details, String createdBy, String createdAt) throws SQLException {
        String sql = "INSERT INTO medical_history(patient_id, category, details, created_by, created_at) "
                + "SELECT ?, ?, ?, ?, ? "
                + "WHERE NOT EXISTS ("
                + "SELECT 1 FROM medical_history WHERE patient_id = ? AND category = ? AND details = ?"
                + ")";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, patientId);
            statement.setString(2, category);
            statement.setString(3, details);
            statement.setString(4, value(createdBy));
            statement.setString(5, value(createdAt));
            statement.setString(6, patientId);
            statement.setString(7, category);
            statement.setString(8, details);
            return statement.executeUpdate() > 0;
        }
    }

    @Override
    public int count() throws SQLException {
        String sql = "SELECT COUNT(*) FROM medical_history";
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
            System.out.println("SQLite medical history schema check failed: " + e.getMessage());
        }
    }

    private String value(String value) {
        return value == null ? "" : value;
    }
}
