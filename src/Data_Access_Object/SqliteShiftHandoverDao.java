package Data_Access_Object;

import database.DatabaseManager;
import database.SchemaInitializer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class SqliteShiftHandoverDao implements ShiftHandoverDao {

    public SqliteShiftHandoverDao() {
        ensureSchema();
    }

    @Override
    public boolean saveNote(String patientId, String fromUser, String toSection, String note, String createdAt) throws SQLException {
        String sql = "INSERT INTO shift_handover_notes(patient_id, from_user, to_section, note, created_at) "
                + "SELECT ?, ?, ?, ?, ? "
                + "WHERE NOT EXISTS ("
                + "SELECT 1 FROM shift_handover_notes "
                + "WHERE COALESCE(patient_id, '') = ? AND from_user = ? AND to_section = ? AND note = ? AND created_at = ?"
                + ")";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value(patientId));
            statement.setString(2, value(fromUser));
            statement.setString(3, value(toSection));
            statement.setString(4, note);
            statement.setString(5, value(createdAt));
            statement.setString(6, value(patientId));
            statement.setString(7, value(fromUser));
            statement.setString(8, value(toSection));
            statement.setString(9, note);
            statement.setString(10, value(createdAt));
            return statement.executeUpdate() > 0;
        }
    }

    @Override
    public int count() throws SQLException {
        String sql = "SELECT COUNT(*) FROM shift_handover_notes";
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
            System.out.println("SQLite shift handover schema check failed: " + e.getMessage());
        }
    }

    private String value(String value) {
        return value == null ? "" : value;
    }
}
