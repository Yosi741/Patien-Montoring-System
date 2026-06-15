package Data_Access_Object;

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

public class SqliteAppointmentDao {

    public SqliteAppointmentDao() {
        ensureSchema();
    }

    public long insertAppointment(AppointmentRecord appointment) throws SQLException {
        String sql = "INSERT INTO appointments(patient_id, title, appointment_type, start_time, end_time, location, "
                + "assigned_staff, status, notes, created_by, updated_at) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bindMutableFields(statement, appointment);
            statement.setString(10, value(appointment.getCreatedBy()));
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        return -1L;
    }

    public void updateAppointment(AppointmentRecord appointment) throws SQLException {
        String sql = "UPDATE appointments SET patient_id = ?, title = ?, appointment_type = ?, start_time = ?, end_time = ?, "
                + "location = ?, assigned_staff = ?, status = ?, notes = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindMutableFields(statement, appointment);
            statement.setLong(10, appointment.getId());
            statement.executeUpdate();
        }
    }

    public void updateStatus(long appointmentId, String status) throws SQLException {
        String sql = "UPDATE appointments SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status);
            statement.setLong(2, appointmentId);
            statement.executeUpdate();
        }
    }

    public Optional<AppointmentRecord> findById(long appointmentId) throws SQLException {
        String sql = "SELECT id, patient_id, title, appointment_type, start_time, end_time, location, assigned_staff, "
                + "status, notes, created_by, created_at, updated_at FROM appointments WHERE id = ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, appointmentId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapRecord(resultSet));
                }
            }
        }
        return Optional.empty();
    }

    public List<AppointmentRow> findAppointments(String search, String type, String status, String patientId) throws SQLException {
        ArrayList<AppointmentRow> appointments = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT a.id, a.patient_id, COALESCE(TRIM(p.first_name || ' ' || p.last_name), '') AS patient_name, "
                + "a.title, a.appointment_type, a.start_time, a.end_time, a.location, a.assigned_staff, a.status, "
                + "a.notes, a.created_by, a.created_at, a.updated_at "
                + "FROM appointments a LEFT JOIN patients p ON p.patient_id = a.patient_id WHERE 1=1 ");
        ArrayList<String> params = new ArrayList<>();
        addFilters(sql, params, search, type, status, patientId);
        sql.append("ORDER BY datetime(a.start_time) DESC, a.id DESC");
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            bindParams(statement, params);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    appointments.add(mapRow(resultSet));
                }
            }
        }
        return appointments;
    }

    public int countToday() throws SQLException {
        return count("SELECT COUNT(*) FROM appointments WHERE date(start_time) = date('now') "
                + "OR substr(start_time, 1, 10) = strftime('%d-%m-%Y', 'now')");
    }

    public int countUpcomingSurgeries() throws SQLException {
        return count("SELECT COUNT(*) FROM appointments WHERE UPPER(appointment_type) = 'SURGERY' "
                + "AND UPPER(status) = 'SCHEDULED' AND datetime(start_time) >= datetime('now')");
    }

    public int countCancelledOrMissed() throws SQLException {
        return count("SELECT COUNT(*) FROM appointments WHERE UPPER(status) IN ('CANCELLED', 'MISSED')");
    }

    private void addFilters(StringBuilder sql, List<String> params, String search, String type, String status, String patientId) {
        if (hasText(patientId)) {
            sql.append("AND a.patient_id = ? ");
            params.add(patientId.trim());
        }
        if (hasText(type) && !"All".equalsIgnoreCase(type)) {
            sql.append("AND UPPER(a.appointment_type) = ? ");
            params.add(type.trim().toUpperCase());
        }
        if (hasText(status) && !"All".equalsIgnoreCase(status)) {
            sql.append("AND UPPER(a.status) = ? ");
            params.add(status.trim().toUpperCase());
        }
        if (hasText(search)) {
            sql.append("AND (UPPER(a.patient_id) LIKE ? OR UPPER(a.title) LIKE ? OR UPPER(a.location) LIKE ? "
                    + "OR UPPER(a.assigned_staff) LIKE ? OR UPPER(COALESCE(p.first_name || ' ' || p.last_name, '')) LIKE ?) ");
            String like = "%" + search.trim().toUpperCase() + "%";
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
        }
    }

    private void bindMutableFields(PreparedStatement statement, AppointmentRecord appointment) throws SQLException {
        statement.setString(1, appointment.getPatientId());
        statement.setString(2, value(appointment.getTitle()));
        statement.setString(3, value(appointment.getAppointmentType()));
        statement.setString(4, value(appointment.getStartTime()));
        statement.setString(5, value(appointment.getEndTime()));
        statement.setString(6, value(appointment.getLocation()));
        statement.setString(7, value(appointment.getAssignedStaff()));
        statement.setString(8, value(appointment.getStatus()));
        statement.setString(9, value(appointment.getNotes()));
    }

    private void bindParams(PreparedStatement statement, List<String> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            statement.setString(i + 1, params.get(i));
        }
    }

    private AppointmentRecord mapRecord(ResultSet resultSet) throws SQLException {
        return new AppointmentRecord(
                resultSet.getLong("id"),
                resultSet.getString("patient_id"),
                resultSet.getString("title"),
                resultSet.getString("appointment_type"),
                resultSet.getString("start_time"),
                resultSet.getString("end_time"),
                resultSet.getString("location"),
                resultSet.getString("assigned_staff"),
                resultSet.getString("status"),
                resultSet.getString("notes"),
                resultSet.getString("created_by"),
                resultSet.getString("created_at"),
                resultSet.getString("updated_at")
        );
    }

    private AppointmentRow mapRow(ResultSet resultSet) throws SQLException {
        return new AppointmentRow(
                resultSet.getLong("id"),
                resultSet.getString("patient_id"),
                resultSet.getString("patient_name"),
                resultSet.getString("title"),
                resultSet.getString("appointment_type"),
                resultSet.getString("start_time"),
                resultSet.getString("end_time"),
                resultSet.getString("location"),
                resultSet.getString("assigned_staff"),
                resultSet.getString("status"),
                resultSet.getString("notes"),
                resultSet.getString("created_by"),
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
            System.out.println("SQLite appointment schema check failed: " + e.getMessage());
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String value(String value) {
        return value == null ? "" : value.trim();
    }

    public static class AppointmentRecord {
        private final long id;
        private final String patientId;
        private final String title;
        private final String appointmentType;
        private final String startTime;
        private final String endTime;
        private final String location;
        private final String assignedStaff;
        private final String status;
        private final String notes;
        private final String createdBy;
        private final String createdAt;
        private final String updatedAt;

        public AppointmentRecord(long id, String patientId, String title, String appointmentType, String startTime,
                                 String endTime, String location, String assignedStaff, String status, String notes,
                                 String createdBy, String createdAt, String updatedAt) {
            this.id = id;
            this.patientId = patientId;
            this.title = title;
            this.appointmentType = appointmentType;
            this.startTime = startTime;
            this.endTime = endTime;
            this.location = location;
            this.assignedStaff = assignedStaff;
            this.status = status;
            this.notes = notes;
            this.createdBy = createdBy;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
        }

        public long getId() { return id; }
        public String getPatientId() { return patientId; }
        public String getTitle() { return title; }
        public String getAppointmentType() { return appointmentType; }
        public String getStartTime() { return startTime; }
        public String getEndTime() { return endTime; }
        public String getLocation() { return location; }
        public String getAssignedStaff() { return assignedStaff; }
        public String getStatus() { return status; }
        public String getNotes() { return notes; }
        public String getCreatedBy() { return createdBy; }
        public String getCreatedAt() { return createdAt; }
        public String getUpdatedAt() { return updatedAt; }
    }

    public static class AppointmentRow extends AppointmentRecord {
        private final String patientName;

        public AppointmentRow(long id, String patientId, String patientName, String title, String appointmentType,
                              String startTime, String endTime, String location, String assignedStaff, String status,
                              String notes, String createdBy, String createdAt, String updatedAt) {
            super(id, patientId, title, appointmentType, startTime, endTime, location, assignedStaff, status,
                    notes, createdBy, createdAt, updatedAt);
            this.patientName = patientName;
        }

        public String getPatientName() {
            return patientName == null || patientName.isBlank() ? "Unknown patient" : patientName;
        }
    }
}
