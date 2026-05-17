package dao;

import database.DatabaseManager;
import database.SchemaInitializer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SqliteDeviceDao {

    public SqliteDeviceDao() {
        ensureSchema();
    }

    public void insertDevice(DeviceRecord device) throws SQLException {
        String sql = "INSERT INTO devices(device_id, name, type, serial, status, patient_id, notes, updated_at) "
                + "VALUES(?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindDevice(statement, device);
            statement.executeUpdate();
        }
    }

    public void updateDevice(DeviceRecord device) throws SQLException {
        String sql = "UPDATE devices SET name = ?, type = ?, serial = ?, status = ?, patient_id = ?, notes = ?, updated_at = CURRENT_TIMESTAMP "
                + "WHERE device_id = ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value(device.getName()));
            statement.setString(2, value(device.getType()));
            statement.setString(3, value(device.getSerial()));
            statement.setString(4, value(device.getStatus()));
            statement.setString(5, value(device.getPatientId()));
            statement.setString(6, value(device.getNotes()));
            statement.setString(7, device.getDeviceId());
            statement.executeUpdate();
        }
    }

    public void deactivateDevice(String deviceId) throws SQLException {
        String sql = "UPDATE devices SET status = 'INACTIVE', patient_id = '', updated_at = CURRENT_TIMESTAMP WHERE device_id = ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, deviceId);
            statement.executeUpdate();
        }
    }

    public void assignDeviceToPatient(String deviceId, String patientId) throws SQLException {
        String sql = "UPDATE devices SET status = 'ASSIGNED', patient_id = ?, updated_at = CURRENT_TIMESTAMP WHERE device_id = ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, patientId);
            statement.setString(2, deviceId);
            statement.executeUpdate();
        }
    }

    public void unassignDevice(String deviceId) throws SQLException {
        String sql = "UPDATE devices SET status = 'AVAILABLE', patient_id = '', updated_at = CURRENT_TIMESTAMP WHERE device_id = ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, deviceId);
            statement.executeUpdate();
        }
    }

    public boolean existsByDeviceId(String deviceId) throws SQLException {
        String sql = "SELECT 1 FROM devices WHERE device_id = ? LIMIT 1";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, deviceId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    public boolean serialExists(String serial, String excludeDeviceId) throws SQLException {
        if (serial == null || serial.isBlank()) {
            return false;
        }
        String sql = "SELECT 1 FROM devices WHERE UPPER(COALESCE(serial, '')) = ? AND device_id <> ? LIMIT 1";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, serial.trim().toUpperCase());
            statement.setString(2, excludeDeviceId == null ? "" : excludeDeviceId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    public Optional<DeviceRecord> findByDeviceId(String deviceId) throws SQLException {
        String sql = rowSelect() + " WHERE d.device_id = ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, deviceId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapDevice(resultSet));
                }
            }
        }
        return Optional.empty();
    }

    public List<DeviceRecord> findDevices(String search, String type, String status, String patientId) throws SQLException {
        ArrayList<DeviceRecord> devices = new ArrayList<>();
        StringBuilder sql = new StringBuilder(rowSelect()).append(" WHERE 1 = 1 ");
        ArrayList<String> params = new ArrayList<>();
        if (hasText(search)) {
            sql.append("AND (d.device_id LIKE ? OR d.name LIKE ? OR d.serial LIKE ? OR d.patient_id LIKE ? OR p.first_name LIKE ? OR p.last_name LIKE ?) ");
            String like = "%" + search.trim() + "%";
            for (int i = 0; i < 6; i++) {
                params.add(like);
            }
        }
        if (hasText(type) && !"All".equalsIgnoreCase(type)) {
            sql.append("AND d.type = ? ");
            params.add(type);
        }
        if (hasText(status) && !"All".equalsIgnoreCase(status)) {
            sql.append("AND UPPER(d.status) = ? ");
            params.add(status.toUpperCase());
        }
        if (hasText(patientId)) {
            sql.append("AND d.patient_id = ? ");
            params.add(patientId);
        }
        sql.append("ORDER BY CASE UPPER(d.status) WHEN 'ASSIGNED' THEN 1 WHEN 'AVAILABLE' THEN 2 WHEN 'MAINTENANCE' THEN 3 ELSE 4 END, d.type, d.name");
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                statement.setString(i + 1, params.get(i));
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    devices.add(mapDevice(resultSet));
                }
            }
        }
        return devices;
    }

    public List<DeviceRecord> findByPatientId(String patientId) throws SQLException {
        return findDevices("", "All", "All", patientId);
    }

    private String rowSelect() {
        return "SELECT d.device_id, d.name, d.type, d.serial, d.status, COALESCE(d.patient_id, '') AS patient_id, "
                + "COALESCE(TRIM(p.first_name || ' ' || p.last_name), '') AS patient_name, "
                + "COALESCE(d.notes, '') AS notes, COALESCE(d.updated_at, '') AS updated_at "
                + "FROM devices d LEFT JOIN patients p ON p.patient_id = d.patient_id";
    }

    private void bindDevice(PreparedStatement statement, DeviceRecord device) throws SQLException {
        statement.setString(1, value(device.getDeviceId()));
        statement.setString(2, value(device.getName()));
        statement.setString(3, value(device.getType()));
        statement.setString(4, value(device.getSerial()));
        statement.setString(5, value(device.getStatus()));
        statement.setString(6, value(device.getPatientId()));
        statement.setString(7, value(device.getNotes()));
    }

    private DeviceRecord mapDevice(ResultSet resultSet) throws SQLException {
        return new DeviceRecord(
                resultSet.getString("device_id"),
                resultSet.getString("name"),
                resultSet.getString("type"),
                resultSet.getString("serial"),
                resultSet.getString("status"),
                resultSet.getString("patient_id"),
                resultSet.getString("patient_name"),
                resultSet.getString("notes"),
                resultSet.getString("updated_at")
        );
    }

    private void ensureSchema() {
        try {
            SchemaInitializer.initialize();
        } catch (Exception e) {
            System.out.println("SQLite device schema check failed: " + e.getMessage());
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String value(String value) {
        return value == null ? "" : value.trim();
    }

    public static class DeviceRecord {
        private final String deviceId;
        private final String name;
        private final String type;
        private final String serial;
        private final String status;
        private final String patientId;
        private final String patientName;
        private final String notes;
        private final String updatedAt;

        public DeviceRecord(String deviceId, String name, String type, String serial, String status,
                            String patientId, String patientName, String notes, String updatedAt) {
            this.deviceId = deviceId;
            this.name = name;
            this.type = type;
            this.serial = serial;
            this.status = status;
            this.patientId = patientId;
            this.patientName = patientName;
            this.notes = notes;
            this.updatedAt = updatedAt;
        }

        public String getDeviceId() { return deviceId; }
        public String getName() { return name; }
        public String getType() { return type; }
        public String getSerial() { return serial; }
        public String getStatus() { return status; }
        public String getPatientId() { return patientId; }
        public String getPatientName() { return patientName == null || patientName.isBlank() ? "-" : patientName; }
        public String getNotes() { return notes; }
        public String getUpdatedAt() { return updatedAt; }
    }
}
