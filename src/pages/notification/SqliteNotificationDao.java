package pages.notification;

import app.database.DatabaseManager;
import app.database.SchemaInitializer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SqliteNotificationDao {

    public SqliteNotificationDao() {
        try {
            SchemaInitializer.initialize();
        } catch (Exception e) {
            System.out.println("SQLite notification schema check failed: " + e.getMessage());
        }
    }

    public long insert(NotificationWriteRecord record) throws SQLException {
        String sql = "INSERT INTO notifications(username, role, section, patient_id, severity, title, message, status, source_type, source_id) "
                + "VALUES(?, ?, ?, ?, ?, ?, ?, 'UNREAD', ?, ?)";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, blankToEmpty(record.getUsername()));
            statement.setString(2, blankToNull(record.getRole()));
            statement.setString(3, blankToNull(record.getSection()));
            statement.setString(4, blankToNull(record.getPatientId()));
            statement.setString(5, record.getSeverity());
            statement.setString(6, record.getTitle());
            statement.setString(7, record.getMessage());
            statement.setString(8, blankToNull(record.getSourceType()));
            statement.setString(9, blankToNull(record.getSourceId()));
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : -1;
            }
        }
    }

    public List<NotificationRow> findForUser(String username, String roleGroup, String section,
                                             String severity, String status, String patientSearch, String dateRange) throws SQLException {
        ArrayList<NotificationRow> rows = new ArrayList<>();
        StringBuilder sql = baseSelect();
        sql.append("WHERE (username = ? OR role = ? OR section = ? OR COALESCE(username, '') = '') ");
        ArrayList<String> params = new ArrayList<>();
        params.add(username);
        params.add(roleGroup);
        params.add(section);
        appendFilters(sql, params, severity, status, patientSearch, dateRange);
        sql.append("ORDER BY datetime(created_at) DESC, id DESC");
        queryRows(sql.toString(), params, rows);
        return rows;
    }

    public Optional<NotificationRow> findById(long id) throws SQLException {
        ArrayList<NotificationRow> rows = new ArrayList<>();
        StringBuilder sql = baseSelect();
        sql.append("WHERE id = ?");
        queryRows(sql.toString(), List.of(String.valueOf(id)), rows);
        return rows.stream().findFirst();
    }

    public int unreadCountForUser(String username, String roleGroup, String section) throws SQLException {
        String sql = "SELECT COUNT(*) FROM notifications "
                + "WHERE status = 'UNREAD' AND (username = ? OR role = ? OR section = ? OR COALESCE(username, '') = '')";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            statement.setString(2, roleGroup);
            statement.setString(3, section);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt(1) : 0;
            }
        }
    }

    public boolean markRead(long id) throws SQLException {
        String sql = "UPDATE notifications SET status = 'READ', read = 1, read_at = CURRENT_TIMESTAMP WHERE id = ? AND status <> 'DISMISSED'";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            return statement.executeUpdate() > 0;
        }
    }


    private StringBuilder baseSelect() {
        return new StringBuilder("SELECT id, username, role, section, patient_id, severity, title, message, status, "
                + "source_type, source_id, created_at, read_at FROM notifications ");
    }

    private void appendFilters(StringBuilder sql, ArrayList<String> params, String severity, String status, String patientSearch, String dateRange) {
        if (severity != null && !severity.isBlank() && !"All".equalsIgnoreCase(severity)) {
            sql.append("AND severity = ? ");
            params.add(severity);
        }
        if (status != null && !status.isBlank() && !"All".equalsIgnoreCase(status)) {
            sql.append("AND status = ? ");
            params.add(status);
        }
        if (patientSearch != null && !patientSearch.trim().isEmpty()) {
            sql.append("AND COALESCE(patient_id, '') LIKE ? ");
            params.add("%" + patientSearch.trim() + "%");
        }
        if (dateRange != null && !dateRange.isBlank() && !"All".equalsIgnoreCase(dateRange)) {
            sql.append("AND datetime(created_at) >= datetime('now', ?) ");
            if ("Today".equalsIgnoreCase(dateRange)) {
                params.add("start of day");
            } else if ("Last 7 days".equalsIgnoreCase(dateRange)) {
                params.add("-7 days");
            } else {
                params.add("-30 days");
            }
        }
    }

    private void queryRows(String sql, List<String> params, ArrayList<NotificationRow> rows) throws SQLException {
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                statement.setString(i + 1, params.get(i));
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(new NotificationRow(
                            resultSet.getLong("id"),
                            resultSet.getString("username"),
                            resultSet.getString("role"),
                            resultSet.getString("section"),
                            resultSet.getString("patient_id"),
                            resultSet.getString("severity"),
                            resultSet.getString("title"),
                            resultSet.getString("message"),
                            resultSet.getString("status"),
                            resultSet.getString("source_type"),
                            resultSet.getString("source_id"),
                            resultSet.getString("created_at"),
                            resultSet.getString("read_at")
                    ));
                }
            }
        }
    }

    private String blankToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    public static class NotificationWriteRecord {
        private final String username;
        private final String role;
        private final String section;
        private final String patientId;
        private final String severity;
        private final String title;
        private final String message;
        private final String sourceType;
        private final String sourceId;

        public NotificationWriteRecord(String username, String role, String section, String patientId, String severity,
                                       String title, String message, String sourceType, String sourceId) {
            this.username = trim(username);
            this.role = trim(role);
            this.section = trim(section);
            this.patientId = trim(patientId);
            this.severity = trim(severity);
            this.title = trim(title);
            this.message = trim(message);
            this.sourceType = trim(sourceType);
            this.sourceId = trim(sourceId);
        }

        public String getUsername() { return username; }
        public String getRole() { return role; }
        public String getSection() { return section; }
        public String getPatientId() { return patientId; }
        public String getSeverity() { return severity; }
        public String getTitle() { return title; }
        public String getMessage() { return message; }
        public String getSourceType() { return sourceType; }
        public String getSourceId() { return sourceId; }

        private static String trim(String value) {
            return value == null ? "" : value.trim();
        }
    }

    public static class NotificationRow {
        private final long id;
        private final String username;
        private final String role;
        private final String section;
        private final String patientId;
        private final String severity;
        private final String title;
        private final String message;
        private final String status;
        private final String sourceType;
        private final String sourceId;
        private final String createdAt;
        private final String readAt;

        public NotificationRow(long id, String username, String role, String section, String patientId, String severity,
                               String title, String message, String status, String sourceType, String sourceId,
                               String createdAt, String readAt) {
            this.id = id;
            this.username = username;
            this.role = role;
            this.section = section;
            this.patientId = patientId;
            this.severity = severity;
            this.title = title;
            this.message = message;
            this.status = status;
            this.sourceType = sourceType;
            this.sourceId = sourceId;
            this.createdAt = createdAt;
            this.readAt = readAt;
        }

        public long getId() { return id; }
        public String getUsername() { return username; }
        public String getRole() { return role; }
        public String getSection() { return section; }
        public String getPatientId() { return patientId; }
        public String getSeverity() { return severity; }
        public String getTitle() { return title; }
        public String getMessage() { return message; }
        public String getStatus() { return status; }
        public String getSourceType() { return sourceType; }
        public String getSourceId() { return sourceId; }
        public String getCreatedAt() { return createdAt; }
        public String getReadAt() { return readAt; }
        public String getTargetSummary() {
            if (username != null && !username.isBlank()) {
                return "User: " + username;
            }
            if (role != null && !role.isBlank()) {
                return "Role: " + role;
            }
            if (section != null && !section.isBlank()) {
                return "Section: " + section;
            }
            return "All users";
        }
    }
}
