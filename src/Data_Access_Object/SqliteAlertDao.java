package Data_Access_Object;

import database.DatabaseManager;
import database.SchemaInitializer;
import models.Alert;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SqliteAlertDao implements AlertDao {

    public SqliteAlertDao() {
        ensureSchema();
    }

    @Override
    public Optional<Alert> findById(String id) throws SQLException {
        String sql = "SELECT severity, message FROM alerts WHERE id = ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapAlert(resultSet));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public List<Alert> findAll() throws SQLException {
        ArrayList<Alert> alerts = new ArrayList<>();
        String sql = "SELECT severity, message FROM alerts ORDER BY created_at DESC";
        try (Connection connection = DatabaseManager.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                alerts.add(mapAlert(resultSet));
            }
        }
        return alerts;
    }

    @Override
    public List<Alert> findActiveAlerts() throws SQLException {
        ArrayList<Alert> alerts = new ArrayList<>();
        String sql = "SELECT severity, message FROM alerts WHERE status = 'ACTIVE' ORDER BY created_at DESC";
        try (Connection connection = DatabaseManager.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                alerts.add(mapAlert(resultSet));
            }
        }
        return alerts;
    }

    @Override
    public List<Alert> findByPatientId(String patientId) throws SQLException {
        ArrayList<Alert> alerts = new ArrayList<>();
        String sql = "SELECT severity, message FROM alerts WHERE patient_id = ? ORDER BY created_at DESC";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, patientId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    alerts.add(mapAlert(resultSet));
                }
            }
        }
        return alerts;
    }

    @Override
    public void save(Alert alert) throws SQLException {
        String sql = "INSERT INTO alerts(severity, message, status) VALUES(?, ?, 'ACTIVE')";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, alert.getLevel());
            statement.setString(2, alert.getMessage());
            statement.executeUpdate();
        }
    }

    public void saveForPatient(String patientId, Alert alert) throws SQLException {
        String sql = "INSERT INTO alerts(patient_id, severity, message, status) VALUES(?, ?, ?, 'ACTIVE')";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, patientId);
            statement.setString(2, alert.getLevel());
            statement.setString(3, alert.getMessage());
            statement.executeUpdate();
        }
    }

    public boolean insertActiveAlertIfNotDuplicate(String patientId, String severity, String message, int cooldownMinutes) throws SQLException {
        if (hasRecentDuplicate(patientId, severity, message, cooldownMinutes)) {
            return false;
        }

        String sql = "INSERT INTO alerts(patient_id, severity, message, status, created_at, updated_at, cooldown_until) VALUES(?, ?, ?, 'ACTIVE', ?, ?, ?)";
        LocalDateTime now = LocalDateTime.now();
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, patientId);
            statement.setString(2, severity);
            statement.setString(3, message);
            statement.setString(4, format(now));
            statement.setString(5, format(now));
            statement.setString(6, format(now.plusMinutes(cooldownMinutes)));
            return statement.executeUpdate() > 0;
        }
    }

    public boolean hasRecentDuplicate(String patientId, String severity, String message, int cooldownMinutes) throws SQLException {
        String sql = "SELECT created_at, cooldown_until FROM alerts "
                + "WHERE patient_id = ? AND UPPER(severity) = ? AND message = ? AND status = 'ACTIVE' "
                + "ORDER BY id DESC LIMIT 1";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, patientId);
            statement.setString(2, severity == null ? "" : severity.toUpperCase());
            statement.setString(3, message);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return false;
                }

                LocalDateTime now = LocalDateTime.now();
                LocalDateTime cooldownUntil = parseDateTime(resultSet.getString("cooldown_until"));
                if (cooldownUntil != null) {
                    return cooldownUntil.isAfter(now);
                }

                LocalDateTime createdAt = parseDateTime(resultSet.getString("created_at"));
                return createdAt != null && createdAt.plusMinutes(cooldownMinutes).isAfter(now);
            }
        }
    }

    public List<AlertRow> findAlertRows(String severity, String status, String search) throws SQLException {
        return findAlertRows(severity, status, search, null);
    }

    public List<AlertRow> findAlertRows(String severity, String status, String search, String patientIdFilter) throws SQLException {
        ArrayList<AlertRow> rows = new ArrayList<>();
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT a.id, a.patient_id, ")
                .append("COALESCE(TRIM(p.first_name || ' ' || p.last_name), '') AS patient_name, ")
                .append("a.severity, a.message, a.status, a.created_at, a.updated_at, a.acknowledged_by, a.acknowledged_at ")
                .append("FROM alerts a ")
                .append("LEFT JOIN patients p ON p.patient_id = a.patient_id ")
                .append("WHERE 1 = 1 ");

        ArrayList<String> params = new ArrayList<>();
        if (severity != null && !severity.isBlank() && !"All".equalsIgnoreCase(severity)) {
            sql.append("AND UPPER(a.severity) = ? ");
            params.add(severity.toUpperCase());
        }
        if (status != null && !status.isBlank() && !"All".equalsIgnoreCase(status)) {
            sql.append("AND UPPER(a.status) = ? ");
            params.add(status.toUpperCase());
        }
        if (patientIdFilter != null && !patientIdFilter.isBlank()) {
            sql.append("AND a.patient_id = ? ");
            params.add(patientIdFilter);
        }
        if (search != null && !search.trim().isEmpty()) {
            sql.append("AND (a.patient_id LIKE ? OR p.first_name LIKE ? OR p.last_name LIKE ? OR (p.first_name || ' ' || p.last_name) LIKE ?) ");
            String like = "%" + search.trim() + "%";
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
        }
        sql.append("ORDER BY a.created_at DESC, a.id DESC");

        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                statement.setString(i + 1, params.get(i));
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(mapRow(resultSet));
                }
            }
        }
        return rows;
    }

    public int countActiveForPatient(String patientId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM alerts WHERE patient_id = ? AND UPPER(status) = 'ACTIVE'";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, patientId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt(1) : 0;
            }
        }
    }

    public int countActiveCriticalEmergencyForPatient(String patientId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM alerts WHERE patient_id = ? AND UPPER(status) = 'ACTIVE' "
                + "AND UPPER(severity) IN ('CRITICAL', 'EMERGENCY')";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, patientId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt(1) : 0;
            }
        }
    }

    public Optional<AlertRow> findLatestByPatientId(String patientId) throws SQLException {
        String sql = "SELECT a.id, a.patient_id, "
                + "COALESCE(TRIM(p.first_name || ' ' || p.last_name), '') AS patient_name, "
                + "a.severity, a.message, a.status, a.created_at, a.updated_at, a.acknowledged_by, a.acknowledged_at "
                + "FROM alerts a "
                + "LEFT JOIN patients p ON p.patient_id = a.patient_id "
                + "WHERE a.patient_id = ? "
                + "ORDER BY a.id DESC LIMIT 1";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, patientId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapRow(resultSet));
                }
            }
        }
        return Optional.empty();
    }

    public Optional<AlertRow> findAlertRowById(long id) throws SQLException {
        String sql = "SELECT a.id, a.patient_id, "
                + "COALESCE(TRIM(p.first_name || ' ' || p.last_name), '') AS patient_name, "
                + "a.severity, a.message, a.status, a.created_at, a.updated_at, a.acknowledged_by, a.acknowledged_at "
                + "FROM alerts a "
                + "LEFT JOIN patients p ON p.patient_id = a.patient_id "
                + "WHERE a.id = ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapRow(resultSet));
                }
            }
        }
        return Optional.empty();
    }

    public void acknowledge(long id, String username) throws SQLException {
        String timestamp = now();
        String sql = "UPDATE alerts SET status = 'ACKNOWLEDGED', acknowledged_by = ?, acknowledged_at = ?, updated_at = ? WHERE id = ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username == null || username.isBlank() ? "Unknown" : username);
            statement.setString(2, timestamp);
            statement.setString(3, timestamp);
            statement.setLong(4, id);
            statement.executeUpdate();
        }
    }

    public void resolve(long id, String username) throws SQLException {
        String timestamp = now();
        String sql = "UPDATE alerts SET status = 'RESOLVED', acknowledged_by = COALESCE(NULLIF(acknowledged_by, ''), ?), "
                + "acknowledged_at = COALESCE(NULLIF(acknowledged_at, ''), ?), updated_at = ? WHERE id = ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username == null || username.isBlank() ? "Unknown" : username);
            statement.setString(2, timestamp);
            statement.setString(3, timestamp);
            statement.setLong(4, id);
            statement.executeUpdate();
        }
    }

    public boolean updateLatestActiveAlertStatus(String patientId, String status, String username) throws SQLException {
        String timestamp = now();
        String sql = "UPDATE alerts SET status = ?, acknowledged_by = COALESCE(NULLIF(acknowledged_by, ''), ?), "
                + "acknowledged_at = COALESCE(NULLIF(acknowledged_at, ''), ?), updated_at = ? "
                + "WHERE id = (SELECT id FROM alerts WHERE patient_id = ? AND status = 'ACTIVE' ORDER BY id DESC LIMIT 1)";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status);
            statement.setString(2, username == null || username.isBlank() ? "System" : username);
            statement.setString(3, timestamp);
            statement.setString(4, timestamp);
            statement.setString(5, patientId);
            return statement.executeUpdate() > 0;
        }
    }

    public boolean acknowledgeLatestActiveAlert(String patientId, String username) throws SQLException {
        return updateLatestActiveAlertStatus(patientId, "ACKNOWLEDGED", username);
    }

    public boolean resolveLatestActiveAlert(String patientId, String username) throws SQLException {
        return updateLatestActiveAlertStatus(patientId, "RESOLVED", username);
    }

    public int count() throws SQLException {
        String sql = "SELECT COUNT(*) FROM alerts";
        try (Connection connection = DatabaseManager.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            return resultSet.next() ? resultSet.getInt(1) : 0;
        }
    }

    public void seedDemoAlertsIfEmpty() throws SQLException {
        if (count() > 0) {
            return;
        }

        List<DemoAlert> demoAlerts = demoAlerts();
        String sql = "INSERT INTO alerts(patient_id, severity, message, status, created_at, updated_at) VALUES(?, ?, ?, ?, ?, ?)";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (DemoAlert alert : demoAlerts) {
                statement.setString(1, alert.patientId);
                statement.setString(2, alert.severity);
                statement.setString(3, alert.message);
                statement.setString(4, alert.status);
                statement.setString(5, alert.createdAt);
                statement.setString(6, alert.createdAt);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    @Override
    public void deleteById(String id) throws SQLException {
        String sql = "DELETE FROM alerts WHERE id = ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id);
            statement.executeUpdate();
        }
    }

    private Alert mapAlert(ResultSet resultSet) throws SQLException {
        return new Alert(resultSet.getString("message"), resultSet.getString("severity"));
    }

    private void ensureSchema() {
        try {
            SchemaInitializer.initialize();
        } catch (Exception e) {
            System.out.println("SQLite alert schema check failed: " + e.getMessage());
        }
    }

    private AlertRow mapRow(ResultSet resultSet) throws SQLException {
        return new AlertRow(
                resultSet.getLong("id"),
                value(resultSet.getString("patient_id")),
                value(resultSet.getString("patient_name")),
                value(resultSet.getString("severity")),
                value(resultSet.getString("message")),
                value(resultSet.getString("status")),
                value(resultSet.getString("created_at")),
                value(resultSet.getString("updated_at")),
                value(resultSet.getString("acknowledged_by")),
                value(resultSet.getString("acknowledged_at"))
        );
    }

    private List<DemoAlert> demoAlerts() {
        ArrayList<DemoAlert> alerts = new ArrayList<>();
        String current = now();
        alerts.add(new DemoAlert("034862177", "CRITICAL", "Oxygen saturation and temperature require immediate review.", "ACTIVE", current));
        alerts.add(new DemoAlert("147852369", "EMERGENCY", "Patient trend suggests urgent bedside assessment is needed.", "ACTIVE", current));
        alerts.add(new DemoAlert("123654987", "WARNING", "Blood pressure trend should be reviewed during next clinical round.", "ACKNOWLEDGED", current));
        return alerts;
    }

    private String now() {
        return format(LocalDateTime.now());
    }

    private String format(LocalDateTime dateTime) {
        return dateTime.format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"));
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value, DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"));
        } catch (DateTimeParseException e) {
            try {
                return LocalDateTime.parse(value.replace(" ", "T"));
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private static class DemoAlert {
        private final String patientId;
        private final String severity;
        private final String message;
        private final String status;
        private final String createdAt;

        private DemoAlert(String patientId, String severity, String message, String status, String createdAt) {
            this.patientId = patientId;
            this.severity = severity;
            this.message = message;
            this.status = status;
            this.createdAt = createdAt;
        }
    }

    public static class AlertRow {
        private final long id;
        private final String patientId;
        private final String patientName;
        private final String severity;
        private final String message;
        private final String status;
        private final String createdAt;
        private final String updatedAt;
        private final String acknowledgedBy;
        private final String acknowledgedAt;

        public AlertRow(long id, String patientId, String patientName, String severity, String message,
                        String status, String createdAt, String updatedAt, String acknowledgedBy, String acknowledgedAt) {
            this.id = id;
            this.patientId = patientId;
            this.patientName = patientName;
            this.severity = severity;
            this.message = message;
            this.status = status;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
            this.acknowledgedBy = acknowledgedBy;
            this.acknowledgedAt = acknowledgedAt;
        }

        public long getId() { return id; }
        public String getPatientId() { return patientId; }
        public String getPatientName() { return patientName == null || patientName.isBlank() ? "Unknown patient" : patientName; }
        public String getSeverity() { return severity; }
        public String getMessage() { return message; }
        public String getStatus() { return status; }
        public String getCreatedAt() { return createdAt; }
        public String getUpdatedAt() { return updatedAt == null || updatedAt.isBlank() ? createdAt : updatedAt; }
        public String getAcknowledgedBy() { return acknowledgedBy == null || acknowledgedBy.isBlank() ? "Not acknowledged" : acknowledgedBy; }
        public String getAcknowledgedAt() { return acknowledgedAt == null || acknowledgedAt.isBlank() ? "-" : acknowledgedAt; }
    }
}
