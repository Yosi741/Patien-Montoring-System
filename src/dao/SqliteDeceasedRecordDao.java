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

public class SqliteDeceasedRecordDao {

    public SqliteDeceasedRecordDao() {
        ensureSchema();
    }

    public long insertRecord(DeathRecord record) throws SQLException {
        String sql = "INSERT INTO deceased_records(patient_id, death_time, pronounced_by, cause_of_death, notes, certificate_path, created_by, created_at, updated_at) "
                + "VALUES(?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bindRecord(statement, record);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : 0;
            }
        }
    }

    public void updateRecord(long id, DeathRecord record) throws SQLException {
        String sql = "UPDATE deceased_records SET death_time = ?, pronounced_by = ?, cause_of_death = ?, notes = ?, updated_at = CURRENT_TIMESTAMP "
                + "WHERE id = ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value(record.getDeathTime()));
            statement.setString(2, value(record.getPronouncedBy()));
            statement.setString(3, value(record.getCauseOfDeath()));
            statement.setString(4, value(record.getNotes()));
            statement.setLong(5, id);
            statement.executeUpdate();
        }
    }

    public void updateCertificatePath(long id, String certificatePath) throws SQLException {
        String sql = "UPDATE deceased_records SET certificate_path = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value(certificatePath));
            statement.setLong(2, id);
            statement.executeUpdate();
        }
    }

    public void updateReviewStatus(long id, String reviewStatus, String reviewedBy, String reviewedAt,
                                   String rejectionReason) throws SQLException {
        String sql = "UPDATE deceased_records SET review_status = ?, reviewed_by = ?, reviewed_at = ?, "
                + "rejection_reason = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value(reviewStatus));
            statement.setString(2, value(reviewedBy));
            statement.setString(3, value(reviewedAt));
            statement.setString(4, value(rejectionReason));
            statement.setLong(5, id);
            statement.executeUpdate();
        }
    }

    public Optional<DeathRecord> findById(long id) throws SQLException {
        String sql = selectSql() + " WHERE d.id = ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapRecord(resultSet));
                }
            }
        }
        return Optional.empty();
    }

    public Optional<DeathRecord> findByPatientId(String patientId) throws SQLException {
        String sql = selectSql() + " WHERE d.patient_id = ?";
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

    public List<DeathRecord> findRecords(RecordFilter filter) throws SQLException {
        RecordFilter safeFilter = filter == null ? new RecordFilter() : filter;
        ArrayList<String> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder(selectSql()).append(" WHERE 1 = 1 ");
        if (hasText(safeFilter.search)) {
            sql.append("AND (d.patient_id LIKE ? OR p.first_name LIKE ? OR p.last_name LIKE ? OR (p.first_name || ' ' || p.last_name) LIKE ?) ");
            String like = "%" + safeFilter.search.trim() + "%";
            for (int i = 0; i < 4; i++) {
                params.add(like);
            }
        }
        if (hasText(safeFilter.section) && !"All".equalsIgnoreCase(safeFilter.section)) {
            sql.append("AND p.section = ? ");
            params.add(safeFilter.section);
        }
        if (hasText(safeFilter.dateRange) && !"All".equalsIgnoreCase(safeFilter.dateRange)) {
            if ("Today".equalsIgnoreCase(safeFilter.dateRange)) {
                sql.append("AND datetime(d.death_time) >= datetime('now', 'start of day') ");
            } else if ("Last 7 days".equalsIgnoreCase(safeFilter.dateRange)) {
                sql.append("AND datetime(d.death_time) >= datetime('now', '-7 days') ");
            } else if ("Last 30 days".equalsIgnoreCase(safeFilter.dateRange)) {
                sql.append("AND datetime(d.death_time) >= datetime('now', '-30 days') ");
            }
        }
        sql.append("ORDER BY datetime(d.death_time) DESC, d.id DESC");
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                statement.setString(i + 1, params.get(i));
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                ArrayList<DeathRecord> records = new ArrayList<>();
                while (resultSet.next()) {
                    records.add(mapRecord(resultSet));
                }
                return records;
            }
        }
    }

    public int count() throws SQLException {
        try (Connection connection = DatabaseManager.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM deceased_records")) {
            return resultSet.next() ? resultSet.getInt(1) : 0;
        }
    }

    public int countCertificatesGenerated() throws SQLException {
        return countWhere("SELECT COUNT(*) FROM deceased_records WHERE certificate_path IS NOT NULL AND TRIM(certificate_path) <> ''");
    }

    public int countPendingCertificates() throws SQLException {
        return countWhere("SELECT COUNT(*) FROM deceased_records WHERE certificate_path IS NULL OR TRIM(certificate_path) = ''");
    }

    public int countDeathsThisMonth() throws SQLException {
        return countWhere("SELECT COUNT(*) FROM deceased_records WHERE strftime('%Y-%m', death_time) = strftime('%Y-%m', 'now')");
    }

    private int countWhere(String sql) throws SQLException {
        try (Connection connection = DatabaseManager.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            return resultSet.next() ? resultSet.getInt(1) : 0;
        }
    }

    private String selectSql() {
        return "SELECT d.id, d.patient_id, COALESCE(TRIM(p.first_name || ' ' || p.last_name), '') AS patient_name, "
                + "COALESCE(p.section, '') AS section, d.death_time, d.pronounced_by, d.cause_of_death, "
                + "COALESCE(d.notes, '') AS notes, COALESCE(d.certificate_path, '') AS certificate_path, "
                + "COALESCE(d.created_by, '') AS created_by, d.created_at, d.updated_at, "
                + "COALESCE(d.review_status, 'DRAFT') AS review_status, COALESCE(d.reviewed_by, '') AS reviewed_by, "
                + "COALESCE(d.reviewed_at, '') AS reviewed_at, COALESCE(d.rejection_reason, '') AS rejection_reason "
                + "FROM deceased_records d LEFT JOIN patients p ON p.patient_id = d.patient_id";
    }

    private void bindRecord(PreparedStatement statement, DeathRecord record) throws SQLException {
        statement.setString(1, value(record.getPatientId()));
        statement.setString(2, value(record.getDeathTime()));
        statement.setString(3, value(record.getPronouncedBy()));
        statement.setString(4, value(record.getCauseOfDeath()));
        statement.setString(5, value(record.getNotes()));
        statement.setString(6, value(record.getCertificatePath()));
        statement.setString(7, value(record.getCreatedBy()));
    }

    private DeathRecord mapRecord(ResultSet resultSet) throws SQLException {
        return new DeathRecord(
                resultSet.getLong("id"),
                resultSet.getString("patient_id"),
                resultSet.getString("patient_name"),
                resultSet.getString("section"),
                resultSet.getString("death_time"),
                resultSet.getString("pronounced_by"),
                resultSet.getString("cause_of_death"),
                resultSet.getString("notes"),
                resultSet.getString("certificate_path"),
                resultSet.getString("created_by"),
                resultSet.getString("created_at"),
                resultSet.getString("updated_at"),
                resultSet.getString("review_status"),
                resultSet.getString("reviewed_by"),
                resultSet.getString("reviewed_at"),
                resultSet.getString("rejection_reason")
        );
    }

    private void ensureSchema() {
        try {
            SchemaInitializer.initialize();
        } catch (Exception e) {
            System.out.println("SQLite deceased record schema check failed: " + e.getMessage());
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String value(String value) {
        return value == null ? "" : value.trim();
    }

    public static class RecordFilter {
        private String search = "";
        private String dateRange = "All";
        private String section = "All";

        public String getSearch() { return search; }
        public void setSearch(String search) { this.search = search == null ? "" : search; }
        public String getDateRange() { return dateRange; }
        public void setDateRange(String dateRange) { this.dateRange = dateRange == null ? "All" : dateRange; }
        public String getSection() { return section; }
        public void setSection(String section) { this.section = section == null ? "All" : section; }
    }

    public static class DeathRecord {
        private final long id;
        private final String patientId;
        private final String patientName;
        private final String section;
        private final String deathTime;
        private final String pronouncedBy;
        private final String causeOfDeath;
        private final String notes;
        private final String certificatePath;
        private final String createdBy;
        private final String createdAt;
        private final String updatedAt;
        private final String reviewStatus;
        private final String reviewedBy;
        private final String reviewedAt;
        private final String rejectionReason;

        public DeathRecord(long id, String patientId, String patientName, String section, String deathTime,
                           String pronouncedBy, String causeOfDeath, String notes, String certificatePath,
                           String createdBy, String createdAt, String updatedAt, String reviewStatus,
                           String reviewedBy, String reviewedAt, String rejectionReason) {
            this.id = id;
            this.patientId = patientId;
            this.patientName = patientName;
            this.section = section;
            this.deathTime = deathTime;
            this.pronouncedBy = pronouncedBy;
            this.causeOfDeath = causeOfDeath;
            this.notes = notes;
            this.certificatePath = certificatePath;
            this.createdBy = createdBy;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
            this.reviewStatus = reviewStatus == null || reviewStatus.isBlank() ? "DRAFT" : reviewStatus;
            this.reviewedBy = reviewedBy;
            this.reviewedAt = reviewedAt;
            this.rejectionReason = rejectionReason;
        }

        public static DeathRecord newRecord(String patientId, String deathTime, String pronouncedBy,
                                            String causeOfDeath, String notes, String createdBy) {
            return new DeathRecord(0, patientId, "", "", deathTime, pronouncedBy, causeOfDeath,
                    notes, "", createdBy, "", "", "DRAFT", "", "", "");
        }

        public long getId() { return id; }
        public String getPatientId() { return patientId; }
        public String getPatientName() { return patientName == null || patientName.isBlank() ? "Unknown patient" : patientName; }
        public String getSection() { return section; }
        public String getDeathTime() { return deathTime; }
        public String getPronouncedBy() { return pronouncedBy; }
        public String getCauseOfDeath() { return causeOfDeath; }
        public String getNotes() { return notes; }
        public String getCertificatePath() { return certificatePath; }
        public String getCreatedBy() { return createdBy; }
        public String getCreatedAt() { return createdAt; }
        public String getUpdatedAt() { return updatedAt; }
        public String getReviewStatus() { return reviewStatus; }
        public String getReviewedBy() { return reviewedBy; }
        public String getReviewedAt() { return reviewedAt; }
        public String getRejectionReason() { return rejectionReason; }
        public String getCertificateStatus() {
            return certificatePath == null || certificatePath.isBlank() ? "Not generated" : "Generated";
        }
    }
}
