package pages.patient.patient_vitals;

import app.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Stores and retrieves patient vital readings from the SQLite vital_readings table.
 */
public class SqliteVitalReadingDao implements VitalReadingDao {

    /**
     * Finds by ID in SQLite.
     */
    @Override
    public Optional<VitalRecord> findById(String recordId) throws SQLException {
        String sql = "SELECT id, patient_id, vital_type, value, unit, recorded_at, source_type, staff_user FROM vital_readings WHERE id = ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, recordId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapRecord(resultSet));
                }
            }
        }
        return Optional.empty();
    }


    /**
     * Finds by patient ID in SQLite.
     */
    @Override
    public List<VitalRecord> findByPatientId(String patientId) throws SQLException {
        ArrayList<VitalRecord> records = new ArrayList<>();
        String sql = "SELECT id, patient_id, vital_type, value, unit, recorded_at, source_type, staff_user FROM vital_readings WHERE patient_id = ? ORDER BY recorded_at DESC";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, patientId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    records.add(mapRecord(resultSet));
                }
            }
        }
        return records;
    }

    /**
     * Finds by patient ID and type in SQLite.
     */
    public List<VitalRecord> findByPatientIdAndType(String patientId, String vitalType) throws SQLException {
        if (vitalType == null || vitalType.isBlank() || vitalType.equalsIgnoreCase("All")) {
            return findByPatientId(patientId);
        }

        ArrayList<VitalRecord> records = new ArrayList<>();
        String sql = "SELECT id, patient_id, vital_type, value, unit, recorded_at, source_type, staff_user FROM vital_readings WHERE patient_id = ? AND vital_type = ? ORDER BY recorded_at DESC";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, patientId);
            statement.setString(2, vitalType);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    records.add(mapRecord(resultSet));
                }
            }
        }
        return records;
    }

    /**
     * Validates and saves save.
     */
    @Override
    public void save(VitalRecord record) throws SQLException {
        String sql = "INSERT INTO vital_readings(patient_id, vital_type, value, unit, recorded_at, source_type, staff_user) "
                + "SELECT ?, ?, ?, ?, ?, ?, ? "
                + "WHERE NOT EXISTS ("
                + "SELECT 1 FROM vital_readings WHERE patient_id = ? AND vital_type = ? AND value = ? AND recorded_at = ? AND source_type = ?"
                + ")";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, record.getPatientId());
            statement.setString(2, record.getVitalType());
            statement.setString(3, record.getValue());
            statement.setString(4, record.getUnit());
            statement.setString(5, record.getDateTime());
            statement.setString(6, record.getSourceType());
            statement.setString(7, record.getStaffName());
            statement.setString(8, record.getPatientId());
            statement.setString(9, record.getVitalType());
            statement.setString(10, record.getValue());
            statement.setString(11, record.getDateTime());
            statement.setString(12, record.getSourceType());
            statement.executeUpdate();
        }
    }

    /**
     * Inserts vital reading into SQLite.
     */
    public void insertVitalReading(VitalRecord record) throws SQLException {
        String sql = "INSERT INTO vital_readings(patient_id, vital_type, value, unit, recorded_at, source_type, staff_user) "
                + "VALUES(?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, record.getPatientId());
            statement.setString(2, record.getVitalType());
            statement.setString(3, record.getValue());
            statement.setString(4, record.getUnit());
            statement.setString(5, record.getDateTime());
            statement.setString(6, record.getSourceType());
            statement.setString(7, record.getStaffName());
            statement.executeUpdate();
        }
    }



    /**
     * Finds recent by patient ID in SQLite.
     */
    public List<VitalRecord> findRecentByPatientId(String patientId, int limit) throws SQLException {
        ArrayList<VitalRecord> records = new ArrayList<>();
        String sql = "SELECT id, patient_id, vital_type, value, unit, recorded_at, source_type, staff_user FROM vital_readings WHERE patient_id = ? ORDER BY id DESC LIMIT ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, patientId);
            statement.setInt(2, Math.max(1, limit));
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    records.add(mapRecord(resultSet));
                }
            }
        }
        return records;
    }


    /**
     * Maps record to the corresponding application model.
     */
    private VitalRecord mapRecord(ResultSet resultSet) throws SQLException {
        return new VitalRecord(
                String.valueOf(resultSet.getLong("id")),
                resultSet.getString("patient_id"),
                resultSet.getString("vital_type"),
                resultSet.getString("value"),
                resultSet.getString("unit"),
                resultSet.getString("recorded_at"),
                resultSet.getString("source_type"),
                resultSet.getString("staff_user")
        );
    }
}

