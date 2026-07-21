package pages.alert;

import app.database.DatabaseManager;
import app.database.SchemaInitializer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/**
 * Reads and writes alert lifecycle data in the SQLite alerts table.
 * It also supports duplicate checks, acknowledgement, resolution, and patient alert summaries.
 */
public class SqliteAlertDao implements AlertDao {

    /**
     * Creates the SQLite DAO and initializes any schema support it requires.
     */
    public SqliteAlertDao() {
        ensureSchema();
    }

    /**
     * Finds by ID in SQLite.
     */
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



    /**
     * Validates and saves save.
     */
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



    /**
     * Inserts active alert if not duplicate into SQLite.
     */
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

    /**
     * Determines whether has recent duplicate for the current record or user.
     */
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

    /**
     * Counts active for patient in SQLite.
     */
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

    /**
     * Counts active critical emergency for patient in SQLite.
     */
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

    /**
     * Finds latest by patient ID in SQLite.
     */
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




    /**
     * Resolves resolve for the current workflow.
     */
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


    /**
     * Maps alert to the corresponding application model.
     */
    private Alert mapAlert(ResultSet resultSet) throws SQLException {
        return new Alert(resultSet.getString("message"), resultSet.getString("severity"));
    }

    /**
     * Ensures schema exists before continuing.
     */
    private void ensureSchema() {
        try {
            SchemaInitializer.initialize();
        } catch (Exception e) {
            System.out.println("SQLite alert schema check failed: " + e.getMessage());
        }
    }

    /**
     * Builds the JavaFX row used to display map row.
     */
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



    /**
     * Returns the current timestamp in the SQLite storage format.
     */
    private String now() {
        return format(LocalDateTime.now());
    }

    /**
     * Formats format for display.
     */
    private String format(LocalDateTime dateTime) {
        return dateTime.format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"));
    }

    /**
     * Parses date time without exposing format failures to the caller.
     */
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

    /**
     * Reads value safely from the current SQLite row.
     */
    private String value(String value) {
        return value == null ? "" : value;
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

        /**
         * Creates a alert row from the supplied record values.
         */
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
        public String getSeverity() { return severity; }
        public String getMessage() { return message; }
        public String getStatus() { return status; }
        public String getCreatedAt() { return createdAt; }
    }
}
