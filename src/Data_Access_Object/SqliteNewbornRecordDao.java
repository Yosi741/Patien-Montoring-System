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

public class SqliteNewbornRecordDao {

    public SqliteNewbornRecordDao() {
        ensureSchema();
    }

    public void insertRecord(NewbornRecord record) throws SQLException {
        String sql = "INSERT INTO newborn_records(newborn_id, mother_patient_id, father_name, mother_name, baby_name, gender, "
                + "birth_time, birth_weight, birth_length, delivery_type, room, section, doctor_or_midwife, notes, certificate_path, created_by, created_at, updated_at) "
                + "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindRecord(statement, record);
            statement.executeUpdate();
        }
    }

    public void updateRecord(NewbornRecord record) throws SQLException {
        String sql = "UPDATE newborn_records SET mother_patient_id = ?, father_name = ?, mother_name = ?, baby_name = ?, gender = ?, "
                + "birth_time = ?, birth_weight = ?, birth_length = ?, delivery_type = ?, room = ?, section = ?, doctor_or_midwife = ?, "
                + "notes = ?, updated_at = CURRENT_TIMESTAMP WHERE newborn_id = ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value(record.getMotherPatientId()));
            statement.setString(2, value(record.getFatherName()));
            statement.setString(3, value(record.getMotherName()));
            statement.setString(4, value(record.getBabyName()));
            statement.setString(5, value(record.getGender()));
            statement.setString(6, value(record.getBirthTime()));
            statement.setDouble(7, record.getBirthWeight());
            setNullableDouble(statement, 8, record.getBirthLength());
            statement.setString(9, value(record.getDeliveryType()));
            statement.setString(10, value(record.getRoom()));
            statement.setString(11, value(record.getSection()));
            statement.setString(12, value(record.getDoctorOrMidwife()));
            statement.setString(13, value(record.getNotes()));
            statement.setString(14, value(record.getNewbornId()));
            statement.executeUpdate();
        }
    }

    public void updateCertificatePath(String newbornId, String certificatePath) throws SQLException {
        String sql = "UPDATE newborn_records SET certificate_path = ?, updated_at = CURRENT_TIMESTAMP WHERE newborn_id = ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value(certificatePath));
            statement.setString(2, value(newbornId));
            statement.executeUpdate();
        }
    }

    public void updateReviewStatus(long id, String reviewStatus, String reviewedBy, String reviewedAt,
                                   String rejectionReason) throws SQLException {
        String sql = "UPDATE newborn_records SET review_status = ?, reviewed_by = ?, reviewed_at = ?, "
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

    public boolean existsByNewbornId(String newbornId) throws SQLException {
        String sql = "SELECT 1 FROM newborn_records WHERE newborn_id = ? LIMIT 1";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, newbornId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    public Optional<NewbornRecord> findByNewbornId(String newbornId) throws SQLException {
        String sql = selectSql() + " WHERE n.newborn_id = ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, newbornId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapRecord(resultSet));
                }
            }
        }
        return Optional.empty();
    }

    public Optional<NewbornRecord> findById(long id) throws SQLException {
        String sql = selectSql() + " WHERE n.id = ?";
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

    public List<NewbornRecord> findByMother(String motherPatientId) throws SQLException {
        RecordFilter filter = new RecordFilter();
        filter.setMotherPatientId(motherPatientId);
        return findRecords(filter);
    }

    public List<NewbornRecord> findRecords(RecordFilter filter) throws SQLException {
        RecordFilter safeFilter = filter == null ? new RecordFilter() : filter;
        ArrayList<String> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder(selectSql()).append(" WHERE 1 = 1 ");
        if (hasText(safeFilter.search)) {
            sql.append("AND (n.newborn_id LIKE ? OR n.baby_name LIKE ? OR n.mother_name LIKE ? OR n.mother_patient_id LIKE ?) ");
            String like = "%" + safeFilter.search.trim() + "%";
            for (int i = 0; i < 4; i++) {
                params.add(like);
            }
        }
        if (hasText(safeFilter.motherPatientId)) {
            sql.append("AND n.mother_patient_id = ? ");
            params.add(safeFilter.motherPatientId);
        }
        if (hasText(safeFilter.section) && !"All".equalsIgnoreCase(safeFilter.section)) {
            sql.append("AND n.section = ? ");
            params.add(safeFilter.section);
        }
        if (hasText(safeFilter.gender) && !"All".equalsIgnoreCase(safeFilter.gender)) {
            sql.append("AND UPPER(n.gender) = ? ");
            params.add(safeFilter.gender.toUpperCase());
        }
        if (hasText(safeFilter.dateRange) && !"All".equalsIgnoreCase(safeFilter.dateRange)) {
            if ("Today".equalsIgnoreCase(safeFilter.dateRange)) {
                sql.append("AND datetime(n.birth_time) >= datetime('now', 'start of day') ");
            } else if ("Last 7 days".equalsIgnoreCase(safeFilter.dateRange)) {
                sql.append("AND datetime(n.birth_time) >= datetime('now', '-7 days') ");
            } else if ("Last 30 days".equalsIgnoreCase(safeFilter.dateRange)) {
                sql.append("AND datetime(n.birth_time) >= datetime('now', '-30 days') ");
            }
        }
        sql.append("ORDER BY datetime(n.birth_time) DESC, n.id DESC");
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                statement.setString(i + 1, params.get(i));
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                ArrayList<NewbornRecord> records = new ArrayList<>();
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
             ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM newborn_records")) {
            return resultSet.next() ? resultSet.getInt(1) : 0;
        }
    }

    public int countBirthsToday() throws SQLException {
        try (Connection connection = DatabaseManager.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM newborn_records WHERE date(birth_time) = date('now')")) {
            return resultSet.next() ? resultSet.getInt(1) : 0;
        }
    }

    public int countBirthsThisMonth() throws SQLException {
        return countWhere("SELECT COUNT(*) FROM newborn_records WHERE strftime('%Y-%m', birth_time) = strftime('%Y-%m', 'now')");
    }

    public int countCertificatesGenerated() throws SQLException {
        return countWhere("SELECT COUNT(*) FROM newborn_records WHERE certificate_path IS NOT NULL AND TRIM(certificate_path) <> ''");
    }

    public int countPendingCertificates() throws SQLException {
        return countWhere("SELECT COUNT(*) FROM newborn_records WHERE certificate_path IS NULL OR TRIM(certificate_path) = ''");
    }

    private int countWhere(String sql) throws SQLException {
        try (Connection connection = DatabaseManager.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            return resultSet.next() ? resultSet.getInt(1) : 0;
        }
    }

    private String selectSql() {
        return "SELECT n.id, n.newborn_id, COALESCE(n.mother_patient_id, '') AS mother_patient_id, "
                + "COALESCE(n.father_name, '') AS father_name, n.mother_name, n.baby_name, n.gender, n.birth_time, "
                + "n.birth_weight, n.birth_length, n.delivery_type, COALESCE(n.room, '') AS room, COALESCE(n.section, '') AS section, "
                + "COALESCE(n.doctor_or_midwife, '') AS doctor_or_midwife, COALESCE(n.notes, '') AS notes, "
                + "COALESCE(n.certificate_path, '') AS certificate_path, COALESCE(n.created_by, '') AS created_by, "
                + "n.created_at, n.updated_at, COALESCE(n.review_status, 'DRAFT') AS review_status, "
                + "COALESCE(n.reviewed_by, '') AS reviewed_by, COALESCE(n.reviewed_at, '') AS reviewed_at, "
                + "COALESCE(n.rejection_reason, '') AS rejection_reason FROM newborn_records n";
    }

    private void bindRecord(PreparedStatement statement, NewbornRecord record) throws SQLException {
        statement.setString(1, value(record.getNewbornId()));
        statement.setString(2, value(record.getMotherPatientId()));
        statement.setString(3, value(record.getFatherName()));
        statement.setString(4, value(record.getMotherName()));
        statement.setString(5, value(record.getBabyName()));
        statement.setString(6, value(record.getGender()));
        statement.setString(7, value(record.getBirthTime()));
        statement.setDouble(8, record.getBirthWeight());
        setNullableDouble(statement, 9, record.getBirthLength());
        statement.setString(10, value(record.getDeliveryType()));
        statement.setString(11, value(record.getRoom()));
        statement.setString(12, value(record.getSection()));
        statement.setString(13, value(record.getDoctorOrMidwife()));
        statement.setString(14, value(record.getNotes()));
        statement.setString(15, value(record.getCertificatePath()));
        statement.setString(16, value(record.getCreatedBy()));
    }

    private void setNullableDouble(PreparedStatement statement, int index, Double value) throws SQLException {
        if (value == null) {
            statement.setObject(index, null);
        } else {
            statement.setDouble(index, value);
        }
    }

    private NewbornRecord mapRecord(ResultSet resultSet) throws SQLException {
        Object length = resultSet.getObject("birth_length");
        return new NewbornRecord(
                resultSet.getLong("id"),
                resultSet.getString("newborn_id"),
                resultSet.getString("mother_patient_id"),
                resultSet.getString("father_name"),
                resultSet.getString("mother_name"),
                resultSet.getString("baby_name"),
                resultSet.getString("gender"),
                resultSet.getString("birth_time"),
                resultSet.getDouble("birth_weight"),
                length == null ? null : resultSet.getDouble("birth_length"),
                resultSet.getString("delivery_type"),
                resultSet.getString("room"),
                resultSet.getString("section"),
                resultSet.getString("doctor_or_midwife"),
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
            System.out.println("SQLite newborn schema check failed: " + e.getMessage());
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
        private String gender = "All";
        private String motherPatientId = "";

        public String getSearch() { return search; }
        public void setSearch(String search) { this.search = search == null ? "" : search; }
        public String getDateRange() { return dateRange; }
        public void setDateRange(String dateRange) { this.dateRange = dateRange == null ? "All" : dateRange; }
        public String getSection() { return section; }
        public void setSection(String section) { this.section = section == null ? "All" : section; }
        public String getGender() { return gender; }
        public void setGender(String gender) { this.gender = gender == null ? "All" : gender; }
        public String getMotherPatientId() { return motherPatientId; }
        public void setMotherPatientId(String motherPatientId) { this.motherPatientId = motherPatientId == null ? "" : motherPatientId; }
    }

    public static class NewbornRecord {
        private final long id;
        private final String newbornId;
        private final String motherPatientId;
        private final String fatherName;
        private final String motherName;
        private final String babyName;
        private final String gender;
        private final String birthTime;
        private final double birthWeight;
        private final Double birthLength;
        private final String deliveryType;
        private final String room;
        private final String section;
        private final String doctorOrMidwife;
        private final String notes;
        private final String certificatePath;
        private final String createdBy;
        private final String createdAt;
        private final String updatedAt;
        private final String reviewStatus;
        private final String reviewedBy;
        private final String reviewedAt;
        private final String rejectionReason;

        public NewbornRecord(long id, String newbornId, String motherPatientId, String fatherName, String motherName,
                             String babyName, String gender, String birthTime, double birthWeight, Double birthLength,
                             String deliveryType, String room, String section, String doctorOrMidwife, String notes,
                             String certificatePath, String createdBy, String createdAt, String updatedAt,
                             String reviewStatus, String reviewedBy, String reviewedAt, String rejectionReason) {
            this.id = id;
            this.newbornId = newbornId;
            this.motherPatientId = motherPatientId;
            this.fatherName = fatherName;
            this.motherName = motherName;
            this.babyName = babyName;
            this.gender = gender;
            this.birthTime = birthTime;
            this.birthWeight = birthWeight;
            this.birthLength = birthLength;
            this.deliveryType = deliveryType;
            this.room = room;
            this.section = section;
            this.doctorOrMidwife = doctorOrMidwife;
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

        public long getId() { return id; }
        public String getNewbornId() { return newbornId; }
        public String getMotherPatientId() { return motherPatientId; }
        public String getFatherName() { return fatherName; }
        public String getMotherName() { return motherName; }
        public String getBabyName() { return babyName; }
        public String getGender() { return gender; }
        public String getBirthTime() { return birthTime; }
        public double getBirthWeight() { return birthWeight; }
        public Double getBirthLength() { return birthLength; }
        public String getDeliveryType() { return deliveryType; }
        public String getRoom() { return room; }
        public String getSection() { return section; }
        public String getDoctorOrMidwife() { return doctorOrMidwife; }
        public String getNotes() { return notes; }
        public String getCertificatePath() { return certificatePath; }
        public String getCreatedBy() { return createdBy; }
        public String getCreatedAt() { return createdAt; }
        public String getUpdatedAt() { return updatedAt; }
        public String getReviewStatus() { return reviewStatus; }
        public String getReviewedBy() { return reviewedBy; }
        public String getReviewedAt() { return reviewedAt; }
        public String getRejectionReason() { return rejectionReason; }
        public String getCertificateStatus() { return certificatePath == null || certificatePath.isBlank() ? "Not generated" : "Generated"; }
        public String getMotherDisplay() {
            return (motherName == null ? "" : motherName) + (motherPatientId == null || motherPatientId.isBlank() ? "" : " (" + motherPatientId + ")");
        }
    }
}
