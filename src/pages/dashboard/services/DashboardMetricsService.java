package pages.dashboard.services;

import app.database.DatabaseManager;
import app.database.SchemaInitializer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DashboardMetricsService {

    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public DashboardMetricsService() {
        ensureSchema();
    }

    public DashboardMetrics loadMetrics() throws SQLException {
        try (Connection connection = DatabaseManager.getConnection()) {
            LocalDate today = LocalDate.now();
            String displayToday = today.format(DISPLAY_DATE) + "%";
            String isoToday = today.format(ISO_DATE) + "%";

            DashboardMetrics metrics = new DashboardMetrics();
            metrics.totalPatients = count(connection, "SELECT COUNT(*) FROM patients");
            metrics.activePatients = count(connection, "SELECT COUNT(*) FROM patients WHERE UPPER(status) NOT IN ('DISCHARGED', 'INACTIVE')");
            metrics.criticalEmergencyPatients = count(connection,
                    "SELECT COUNT(*) FROM patients WHERE UPPER(priority) IN ('CRITICAL', 'EMERGENCY')");
            metrics.activeAlerts = count(connection, "SELECT COUNT(*) FROM alerts WHERE UPPER(status) = 'ACTIVE'");
            metrics.acknowledgedAlertsToday = countToday(connection,
                    "SELECT COUNT(*) FROM alerts WHERE UPPER(status) = 'ACKNOWLEDGED' AND (acknowledged_at LIKE ? OR acknowledged_at LIKE ?)",
                    displayToday, isoToday);
            metrics.resolvedAlertsToday = countToday(connection,
                    "SELECT COUNT(*) FROM alerts WHERE UPPER(status) = 'RESOLVED' AND (updated_at LIKE ? OR updated_at LIKE ?)",
                    displayToday, isoToday);
            metrics.importedMedicalFiles = count(connection, "SELECT COUNT(*) FROM medical_files");
            metrics.recentVitalsToday = countToday(connection,
                    "SELECT COUNT(*) FROM vital_readings WHERE recorded_at LIKE ? OR recorded_at LIKE ?",
                    displayToday, isoToday);
            metrics.appointmentsToday = count(connection, "SELECT COUNT(*) FROM appointments WHERE date(start_time) = date('now') "
                    + "OR substr(start_time, 1, 10) = strftime('%d-%m-%Y', 'now')");
            metrics.pendingReminders = count(connection, "SELECT COUNT(*) FROM reminders WHERE UPPER(status) = 'PENDING'");
            metrics.overdueReminders = count(connection, "SELECT COUNT(*) FROM reminders WHERE UPPER(status) = 'OVERDUE'");
            metrics.upcomingRemindersToday = count(connection,
                    "SELECT COUNT(*) FROM reminders WHERE UPPER(status) = 'PENDING' "
                            + "AND (date(due_time) = date('now') OR substr(due_time, 1, 10) = strftime('%d-%m-%Y', 'now'))");
            metrics.priorityCounts.put("NORMAL", count(connection, "SELECT COUNT(*) FROM patients WHERE UPPER(priority) = 'NORMAL'"));
            metrics.priorityCounts.put("HIGH", count(connection, "SELECT COUNT(*) FROM patients WHERE UPPER(priority) = 'HIGH' OR UPPER(priority) = 'WARNING'"));
            metrics.priorityCounts.put("CRITICAL", count(connection, "SELECT COUNT(*) FROM patients WHERE UPPER(priority) = 'CRITICAL'"));
            metrics.priorityCounts.put("EMERGENCY", count(connection, "SELECT COUNT(*) FROM patients WHERE UPPER(priority) = 'EMERGENCY'"));
            metrics.activeAlertSeverityCounts.put("WARNING", count(connection,
                    "SELECT COUNT(*) FROM alerts WHERE UPPER(status) = 'ACTIVE' AND UPPER(severity) = 'WARNING'"));
            metrics.activeAlertSeverityCounts.put("CRITICAL", count(connection,
                    "SELECT COUNT(*) FROM alerts WHERE UPPER(status) = 'ACTIVE' AND UPPER(severity) = 'CRITICAL'"));
            metrics.activeAlertSeverityCounts.put("EMERGENCY", count(connection,
                    "SELECT COUNT(*) FROM alerts WHERE UPPER(status) = 'ACTIVE' AND UPPER(severity) = 'EMERGENCY'"));
            metrics.recentAlerts.addAll(loadRecentAlerts(connection));
            metrics.latestVitals.addAll(loadLatestVitals(connection));
            return metrics;
        }
    }

    private List<RecentAlert> loadRecentAlerts(Connection connection) throws SQLException {
        ArrayList<RecentAlert> alerts = new ArrayList<>();
        String sql = "SELECT a.id, a.patient_id, COALESCE(TRIM(p.first_name || ' ' || p.last_name), '') AS patient_name, "
                + "a.severity, a.status, a.message, a.created_at "
                + "FROM alerts a LEFT JOIN patients p ON p.patient_id = a.patient_id "
                + "ORDER BY a.id DESC LIMIT 5";
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                alerts.add(new RecentAlert(
                        resultSet.getLong("id"),
                        value(resultSet.getString("patient_id")),
                        value(resultSet.getString("patient_name")),
                        value(resultSet.getString("severity")),
                        value(resultSet.getString("status")),
                        value(resultSet.getString("message")),
                        value(resultSet.getString("created_at"))
                ));
            }
        }
        return alerts;
    }

    private List<LatestVital> loadLatestVitals(Connection connection) throws SQLException {
        ArrayList<LatestVital> vitals = new ArrayList<>();
        String sql = "SELECT v.id, v.patient_id, COALESCE(TRIM(p.first_name || ' ' || p.last_name), '') AS patient_name, "
                + "v.vital_type, v.value, v.unit, v.recorded_at "
                + "FROM vital_readings v LEFT JOIN patients p ON p.patient_id = v.patient_id "
                + "ORDER BY v.id DESC LIMIT 6";
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                vitals.add(new LatestVital(
                        value(resultSet.getString("patient_id")),
                        value(resultSet.getString("patient_name")),
                        value(resultSet.getString("vital_type")),
                        value(resultSet.getString("value")),
                        value(resultSet.getString("unit")),
                        value(resultSet.getString("recorded_at"))
                ));
            }
        }
        return vitals;
    }

    private int count(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            return resultSet.next() ? resultSet.getInt(1) : 0;
        }
    }

    private int countToday(Connection connection, String sql, String displayToday, String isoToday) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, displayToday);
            statement.setString(2, isoToday);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt(1) : 0;
            }
        }
    }

    private void ensureSchema() {
        try {
            SchemaInitializer.initialize();
        } catch (Exception e) {
            System.out.println("SQLite dashboard schema check failed: " + e.getMessage());
        }
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    public static class DashboardMetrics {
        private int totalPatients;
        private int activePatients;
        private int criticalEmergencyPatients;
        private int activeAlerts;
        private int acknowledgedAlertsToday;
        private int resolvedAlertsToday;
        private int importedMedicalFiles;
        private int recentVitalsToday;
        private int appointmentsToday;
        private int pendingReminders;
        private int overdueReminders;
        private int upcomingRemindersToday;
        private final LinkedHashMap<String, Integer> priorityCounts = new LinkedHashMap<>();
        private final LinkedHashMap<String, Integer> activeAlertSeverityCounts = new LinkedHashMap<>();
        private final ArrayList<RecentAlert> recentAlerts = new ArrayList<>();
        private final ArrayList<LatestVital> latestVitals = new ArrayList<>();

        public int getTotalPatients() { return totalPatients; }

        public int getActivePatients() { return activePatients; }
        public int getCriticalEmergencyPatients() { return criticalEmergencyPatients; }
        public int getActiveAlerts() { return activeAlerts; }
        public int getAcknowledgedAlertsToday() { return acknowledgedAlertsToday; }
        public int getResolvedAlertsToday() { return resolvedAlertsToday; }
        public int getImportedMedicalFiles() { return importedMedicalFiles; }
        public int getRecentVitalsToday() { return recentVitalsToday; }
        public int getAppointmentsToday() { return appointmentsToday; }
        public int getPendingReminders() { return pendingReminders; }
        public int getOverdueReminders() { return overdueReminders; }
        public int getUpcomingRemindersToday() { return upcomingRemindersToday; }
        public Map<String, Integer> getPriorityCounts() { return priorityCounts; }
        public Map<String, Integer> getActiveAlertSeverityCounts() { return activeAlertSeverityCounts; }
        public List<RecentAlert> getRecentAlerts() { return recentAlerts; }
        public List<LatestVital> getLatestVitals() { return latestVitals; }
    }

    public static class RecentAlert {
        private final long id;
        private final String patientId;
        private final String patientName;
        private final String severity;
        private final String status;
        private final String message;
        private final String createdAt;

        public RecentAlert(long id, String patientId, String patientName, String severity, String status, String message, String createdAt) {
            this.id = id;
            this.patientId = patientId;
            this.patientName = patientName;
            this.severity = severity;
            this.status = status;
            this.message = message;
            this.createdAt = createdAt;
        }

        public long getId() { return id; }
        public String getPatientId() { return patientId; }
        public String getPatientName() { return patientName == null || patientName.isBlank() ? "Unknown patient" : patientName; }
        public String getSeverity() { return severity; }
        public String getStatus() { return status; }
        public String getMessage() { return message; }
        public String getCreatedAt() { return createdAt; }
    }

    public static class LatestVital {
        private final String patientId;
        private final String patientName;
        private final String vitalType;
        private final String value;
        private final String unit;
        private final String recordedAt;

        public LatestVital(String patientId, String patientName, String vitalType, String value, String unit, String recordedAt) {
            this.patientId = patientId;
            this.patientName = patientName;
            this.vitalType = vitalType;
            this.value = value;
            this.unit = unit;
            this.recordedAt = recordedAt;
        }

        public String getPatientId() { return patientId; }
        public String getPatientName() { return patientName == null || patientName.isBlank() ? "Unknown patient" : patientName; }
        public String getVitalType() { return vitalType; }
        public String getValue() { return value; }
        public String getUnit() { return unit; }
        public String getRecordedAt() { return recordedAt; }
    }
}
