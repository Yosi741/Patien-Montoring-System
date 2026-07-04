package pages.scheduling;

import app.DatabaseManager;
import app.SchemaInitializer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SqliteReminderDao {

    public SqliteReminderDao() {
        ensureSchema();
    }

    public long insertReminder(ReminderRecord reminder) throws SQLException {
        String sql = "INSERT INTO reminders(patient_id, medication_id, reminder_type, title, due_time, repeat_rule, status, "
                + "assigned_to, created_by, notes, updated_at) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bindMutableFields(statement, reminder);
            statement.setString(9, value(reminder.getCreatedBy()));
            statement.setString(10, value(reminder.getNotes()));
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        return -1L;
    }

    public void updateReminder(ReminderRecord reminder) throws SQLException {
        String sql = "UPDATE reminders SET patient_id = ?, medication_id = ?, reminder_type = ?, title = ?, due_time = ?, "
                + "repeat_rule = ?, status = ?, assigned_to = ?, notes = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindMutableFields(statement, reminder);
            statement.setString(9, value(reminder.getNotes()));
            statement.setLong(10, reminder.getId());
            statement.executeUpdate();
        }
    }

    public void updateStatus(long reminderId, String status) throws SQLException {
        String sql = "UPDATE reminders SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status);
            statement.setLong(2, reminderId);
            statement.executeUpdate();
        }
    }

    public boolean updateStatusIfCurrent(long reminderId, String status, String... allowedCurrentStatuses) throws SQLException {
        StringBuilder sql = new StringBuilder("UPDATE reminders SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?");
        if (allowedCurrentStatuses != null && allowedCurrentStatuses.length > 0) {
            sql.append(" AND UPPER(status) IN (");
            for (int i = 0; i < allowedCurrentStatuses.length; i++) {
                if (i > 0) {
                    sql.append(", ");
                }
                sql.append("?");
            }
            sql.append(")");
        }
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            statement.setString(1, status);
            statement.setLong(2, reminderId);
            if (allowedCurrentStatuses != null) {
                for (int i = 0; i < allowedCurrentStatuses.length; i++) {
                    statement.setString(i + 3, allowedCurrentStatuses[i] == null ? "" : allowedCurrentStatuses[i].toUpperCase());
                }
            }
            return statement.executeUpdate() > 0;
        }
    }

    public Optional<ReminderRecord> findById(long reminderId) throws SQLException {
        String sql = "SELECT id, patient_id, medication_id, reminder_type, title, due_time, repeat_rule, status, "
                + "assigned_to, created_by, notes, created_at, updated_at FROM reminders WHERE id = ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, reminderId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapRecord(resultSet));
                }
            }
        }
        return Optional.empty();
    }

    public List<ReminderRow> findReminders(String search, String type, String status, String patientId) throws SQLException {
        ArrayList<ReminderRow> reminders = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT r.id, r.patient_id, COALESCE(TRIM(p.first_name || ' ' || p.last_name), '') AS patient_name, "
                + "r.medication_id, COALESCE(m.name, '') AS medication_name, r.reminder_type, r.title, r.due_time, "
                + "r.repeat_rule, r.status, r.assigned_to, r.created_by, r.notes, r.created_at, r.updated_at "
                + "FROM reminders r LEFT JOIN patients p ON p.patient_id = r.patient_id "
                + "LEFT JOIN medications m ON m.id = r.medication_id WHERE 1=1 ");
        ArrayList<String> params = new ArrayList<>();
        addFilters(sql, params, search, type, status, patientId);
        sql.append("ORDER BY datetime(r.due_time) DESC, r.id DESC");
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            bindParams(statement, params);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    reminders.add(mapRow(resultSet));
                }
            }
        }
        return reminders;
    }

    public int countOverdue() throws SQLException {
        return count("SELECT COUNT(*) FROM reminders WHERE UPPER(status) = 'OVERDUE'");
    }

    public int countUpcomingToday() throws SQLException {
        return count("SELECT COUNT(*) FROM reminders WHERE UPPER(status) IN ('PENDING', 'OVERDUE') "
                + "AND (date(due_time) = date('now') OR substr(due_time, 1, 10) = strftime('%d-%m-%Y', 'now'))");
    }

    public int countMedicationToday() throws SQLException {
        return count("SELECT COUNT(*) FROM reminders WHERE UPPER(reminder_type) = 'MEDICATION' "
                + "AND (date(due_time) = date('now') OR substr(due_time, 1, 10) = strftime('%d-%m-%Y', 'now'))");
    }

    public int countCancelledOrMissed() throws SQLException {
        return count("SELECT COUNT(*) FROM reminders WHERE UPPER(status) IN ('CANCELLED', 'MISSED')");
    }

    public int countPending() throws SQLException {
        return count("SELECT COUNT(*) FROM reminders WHERE UPPER(status) = 'PENDING'");
    }

    private void addFilters(StringBuilder sql, List<String> params, String search, String type, String status, String patientId) {
        if (hasText(patientId)) {
            sql.append("AND r.patient_id = ? ");
            params.add(patientId.trim());
        }
        if (hasText(type) && !"All".equalsIgnoreCase(type)) {
            sql.append("AND UPPER(r.reminder_type) = ? ");
            params.add(type.trim().toUpperCase());
        }
        if (hasText(status) && !"All".equalsIgnoreCase(status)) {
            sql.append("AND UPPER(r.status) = ? ");
            params.add(status.trim().toUpperCase());
        }
        if (hasText(search)) {
            sql.append("AND (UPPER(r.patient_id) LIKE ? OR UPPER(r.title) LIKE ? OR UPPER(r.assigned_to) LIKE ? "
                    + "OR UPPER(COALESCE(m.name, '')) LIKE ? OR UPPER(COALESCE(p.first_name || ' ' || p.last_name, '')) LIKE ?) ");
            String like = "%" + search.trim().toUpperCase() + "%";
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
        }
    }

    private void bindMutableFields(PreparedStatement statement, ReminderRecord reminder) throws SQLException {
        statement.setString(1, reminder.getPatientId());
        if (reminder.getMedicationId() == null || reminder.getMedicationId() <= 0) {
            statement.setNull(2, java.sql.Types.INTEGER);
        } else {
            statement.setLong(2, reminder.getMedicationId());
        }
        statement.setString(3, value(reminder.getReminderType()));
        statement.setString(4, value(reminder.getTitle()));
        statement.setString(5, value(reminder.getDueTime()));
        statement.setString(6, value(reminder.getRepeatRule()));
        statement.setString(7, value(reminder.getStatus()));
        statement.setString(8, value(reminder.getAssignedTo()));
    }

    private void bindParams(PreparedStatement statement, List<String> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            statement.setString(i + 1, params.get(i));
        }
    }

    private ReminderRecord mapRecord(ResultSet resultSet) throws SQLException {
        Long medicationId = resultSet.getObject("medication_id") == null ? null : resultSet.getLong("medication_id");
        return new ReminderRecord(
                resultSet.getLong("id"),
                resultSet.getString("patient_id"),
                medicationId,
                resultSet.getString("reminder_type"),
                resultSet.getString("title"),
                resultSet.getString("due_time"),
                resultSet.getString("repeat_rule"),
                resultSet.getString("status"),
                resultSet.getString("assigned_to"),
                resultSet.getString("created_by"),
                resultSet.getString("notes"),
                resultSet.getString("created_at"),
                resultSet.getString("updated_at")
        );
    }

    private ReminderRow mapRow(ResultSet resultSet) throws SQLException {
        Long medicationId = resultSet.getObject("medication_id") == null ? null : resultSet.getLong("medication_id");
        return new ReminderRow(
                resultSet.getLong("id"),
                resultSet.getString("patient_id"),
                resultSet.getString("patient_name"),
                medicationId,
                resultSet.getString("medication_name"),
                resultSet.getString("reminder_type"),
                resultSet.getString("title"),
                resultSet.getString("due_time"),
                resultSet.getString("repeat_rule"),
                resultSet.getString("status"),
                resultSet.getString("assigned_to"),
                resultSet.getString("created_by"),
                resultSet.getString("notes"),
                resultSet.getString("created_at"),
                resultSet.getString("updated_at")
        );
    }

    private int count(String sql) throws SQLException {
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
            System.out.println("SQLite reminder schema check failed: " + e.getMessage());
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String value(String value) {
        return value == null ? "" : value.trim();
    }

    public static class ReminderRecord {
        private final long id;
        private final String patientId;
        private final Long medicationId;
        private final String reminderType;
        private final String title;
        private final String dueTime;
        private final String repeatRule;
        private final String status;
        private final String assignedTo;
        private final String createdBy;
        private final String notes;
        private final String createdAt;
        private final String updatedAt;

        public ReminderRecord(long id, String patientId, Long medicationId, String reminderType, String title,
                              String dueTime, String repeatRule, String status, String assignedTo, String createdBy,
                              String notes, String createdAt, String updatedAt) {
            this.id = id;
            this.patientId = patientId;
            this.medicationId = medicationId;
            this.reminderType = reminderType;
            this.title = title;
            this.dueTime = dueTime;
            this.repeatRule = repeatRule;
            this.status = status;
            this.assignedTo = assignedTo;
            this.createdBy = createdBy;
            this.notes = notes;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
        }

        public long getId() { return id; }
        public String getPatientId() { return patientId; }
        public Long getMedicationId() { return medicationId; }
        public String getReminderType() { return reminderType; }
        public String getTitle() { return title; }
        public String getDueTime() { return dueTime; }
        public String getRepeatRule() { return repeatRule; }
        public String getStatus() { return status; }
        public String getAssignedTo() { return assignedTo; }
        public String getCreatedBy() { return createdBy; }
        public String getNotes() { return notes; }
        public String getCreatedAt() { return createdAt; }
        public String getUpdatedAt() { return updatedAt; }
    }

    public static class ReminderRow extends ReminderRecord {
        private final String patientName;
        private final String medicationName;

        public ReminderRow(long id, String patientId, String patientName, Long medicationId, String medicationName,
                           String reminderType, String title, String dueTime, String repeatRule, String status,
                           String assignedTo, String createdBy, String notes, String createdAt, String updatedAt) {
            super(id, patientId, medicationId, reminderType, title, dueTime, repeatRule, status, assignedTo, createdBy,
                    notes, createdAt, updatedAt);
            this.patientName = patientName;
            this.medicationName = medicationName;
        }

        public String getPatientName() {
            return patientName == null || patientName.isBlank() ? "Unknown patient" : patientName;
        }

        public String getMedicationName() {
            return medicationName == null || medicationName.isBlank() ? "-" : medicationName;
        }
    }
}
