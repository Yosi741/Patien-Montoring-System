package pages.patient.dao;

import app.database.DatabaseManager;
import pages.patient.VitalRecord;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SqliteVitalReadingDao implements VitalReadingDao {

    @Override
    public Optional<VitalRecord> findById(String recordId) throws SQLException {
        String sql = "SELECT * FROM vital_readings WHERE id = ?";
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


    @Override
    public List<VitalRecord> findByPatientId(String patientId) throws SQLException {
        ArrayList<VitalRecord> records = new ArrayList<>();
        String sql = "SELECT * FROM vital_readings WHERE patient_id = ? ORDER BY recorded_at DESC";
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

    public List<VitalRecord> findByPatientIdAndType(String patientId, String vitalType) throws SQLException {
        if (vitalType == null || vitalType.isBlank() || vitalType.equalsIgnoreCase("All")) {
            return findByPatientId(patientId);
        }

        ArrayList<VitalRecord> records = new ArrayList<>();
        String sql = "SELECT * FROM vital_readings WHERE patient_id = ? AND vital_type = ? ORDER BY recorded_at DESC";
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

    @Override
    public void save(VitalRecord record) throws SQLException {
        String sql = "INSERT INTO vital_readings(patient_id, vital_type, value, unit, recorded_at, source_type, staff_user, device_id) "
                + "SELECT ?, ?, ?, ?, ?, ?, ?, ? "
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
            statement.setString(8, record.getDeviceId());
            statement.setString(9, record.getPatientId());
            statement.setString(10, record.getVitalType());
            statement.setString(11, record.getValue());
            statement.setString(12, record.getDateTime());
            statement.setString(13, record.getSourceType());
            statement.executeUpdate();
        }
    }

    public void insertVitalReading(VitalRecord record) throws SQLException {
        String sql = "INSERT INTO vital_readings(patient_id, vital_type, value, unit, recorded_at, source_type, staff_user, device_id) "
                + "VALUES(?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, record.getPatientId());
            statement.setString(2, record.getVitalType());
            statement.setString(3, record.getValue());
            statement.setString(4, record.getUnit());
            statement.setString(5, record.getDateTime());
            statement.setString(6, record.getSourceType());
            statement.setString(7, record.getStaffName());
            statement.setString(8, record.getDeviceId());
            statement.executeUpdate();
        }
    }

    public Optional<VitalRecord> findLatestByPatientId(String patientId) throws SQLException {
        String sql = "SELECT * FROM vital_readings WHERE patient_id = ? ORDER BY recorded_at DESC, id DESC LIMIT 1";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, patientId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapRecord(resultSet));
                }
            }
        }
        return Optional.empty();
    }

    public List<VitalRecord> findRecentByPatientId(String patientId, int limit) throws SQLException {
        ArrayList<VitalRecord> records = new ArrayList<>();
        String sql = "SELECT * FROM vital_readings WHERE patient_id = ? ORDER BY id DESC LIMIT ?";
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


    private VitalRecord mapRecord(ResultSet resultSet) throws SQLException {
        return new VitalRecord(
                String.valueOf(resultSet.getLong("id")),
                resultSet.getString("patient_id"),
                resultSet.getString("vital_type"),
                resultSet.getString("value"),
                resultSet.getString("unit"),
                resultSet.getString("recorded_at"),
                resultSet.getString("source_type"),
                resultSet.getString("staff_user"),
                resultSet.getString("device_id"),
                "",
                "",
                ""
        );
    }
}
