package pages.patient.medical_files;

import app.database.DatabaseManager;
import app.database.SchemaInitializer;
import pages.patient.medical_files.MedicalFile;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Stores and queries uploaded-file metadata in the SQLite medical_files table.
 */
public class SqliteMedicalFileDao implements MedicalFileDao {

    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * Creates the SQLite DAO and initializes any schema support it requires.
     */
    public SqliteMedicalFileDao() {
        ensureSchema();
    }

    /**
     * Validates and saves save.
     */
    @Override
    public boolean save(MedicalFile medicalFile, String extractedSummary) throws SQLException {
        String sql = "INSERT INTO medical_files(file_id, patient_id, original_name, stored_path, file_type, uploaded_by, uploaded_at, extracted_summary, file_size, notes) "
                + "VALUES(?, ?, ?, ?, ?, ?, ?, ?, 0, '') "
                + "ON CONFLICT(file_id) DO UPDATE SET "
                + "patient_id = excluded.patient_id, "
                + "original_name = excluded.original_name, "
                + "stored_path = excluded.stored_path, "
                + "file_type = excluded.file_type, "
                + "uploaded_by = excluded.uploaded_by, "
                + "uploaded_at = excluded.uploaded_at, "
                + "extracted_summary = CASE "
                + "WHEN medical_files.extracted_summary IS NULL OR medical_files.extracted_summary = '' THEN excluded.extracted_summary "
                + "ELSE medical_files.extracted_summary END";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, medicalFile.getFileId());
            statement.setString(2, medicalFile.getPatientId());
            statement.setString(3, medicalFile.getOriginalName());
            statement.setString(4, medicalFile.getStoredPath());
            statement.setString(5, medicalFile.getFileType());
            statement.setString(6, medicalFile.getUploadedBy());
            statement.setString(7, medicalFile.getUploadedAt());
            statement.setString(8, extractedSummary == null ? "" : extractedSummary);
            return statement.executeUpdate() > 0;
        }
    }

    /**
     * Inserts uploaded file into SQLite.
     */
    public boolean insertUploadedFile(MedicalFileRecord file) throws SQLException {
        String sql = "INSERT INTO medical_files(file_id, patient_id, original_name, stored_path, file_type, uploaded_by, uploaded_at, extracted_summary, file_size, notes) "
                + "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, file.getFileId());
            statement.setString(2, file.getPatientId());
            statement.setString(3, file.getOriginalName());
            statement.setString(4, file.getStoredPath());
            statement.setString(5, file.getFileType());
            statement.setString(6, file.getUploadedBy());
            statement.setString(7, file.getUploadedAt());
            statement.setString(8, value(file.getExtractedSummary()));
            statement.setLong(9, file.getFileSize());
            statement.setString(10, value(file.getNotes()));
            return statement.executeUpdate() > 0;
        }
    }

    /**
     * Determines whether has recent duplicate for the current record or user.
     */
    public boolean hasRecentDuplicate(String patientId, String originalName, long fileSize, String uploadedAt) throws SQLException {
        String datePrefix = uploadedAt == null || uploadedAt.length() < 10 ? "" : uploadedAt.substring(0, 10);
        String sql = "SELECT 1 FROM medical_files WHERE patient_id = ? AND original_name = ? AND file_size = ? "
                + "AND substr(uploaded_at, 1, 10) = ? LIMIT 1";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, patientId);
            statement.setString(2, originalName);
            statement.setLong(3, fileSize);
            statement.setString(4, datePrefix);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    /**
     * Finds files in SQLite.
     */
    public List<MedicalFileRecord> findFiles(String search, String category, String dateRange, String uploadedBy, String patientIdFilter) throws SQLException {
        ArrayList<MedicalFileRecord> files = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT mf.id, mf.file_id, mf.patient_id, "
                + "COALESCE(TRIM(p.first_name || ' ' || p.last_name), '') AS patient_name, "
                + "mf.original_name, mf.stored_path, mf.file_type, mf.uploaded_by, mf.uploaded_at, "
                + "mf.extracted_summary, mf.file_size, mf.notes "
                + "FROM medical_files mf LEFT JOIN patients p ON p.patient_id = mf.patient_id WHERE 1=1 ");
        ArrayList<String> params = new ArrayList<>();
        if (patientIdFilter != null && !patientIdFilter.isBlank()) {
            sql.append("AND mf.patient_id = ? ");
            params.add(patientIdFilter.trim());
        }
        if (category != null && !category.isBlank() && !"All".equalsIgnoreCase(category)) {
            sql.append("AND UPPER(mf.file_type) = ? ");
            params.add(category.trim().toUpperCase());
        }
        if (uploadedBy != null && !uploadedBy.isBlank() && !"All".equalsIgnoreCase(uploadedBy)) {
            sql.append("AND UPPER(mf.uploaded_by) LIKE ? ");
            params.add("%" + uploadedBy.trim().toUpperCase() + "%");
        }
        addDateRange(sql, dateRange);
        if (search != null && !search.isBlank()) {
            sql.append("AND (UPPER(mf.patient_id) LIKE ? OR UPPER(mf.original_name) LIKE ? OR UPPER(mf.uploaded_by) LIKE ? "
                    + "OR UPPER(COALESCE(p.first_name || ' ' || p.last_name, '')) LIKE ?) ");
            String like = "%" + search.trim().toUpperCase() + "%";
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
        }
        sql.append("ORDER BY datetime(").append(uploadedAtSqlExpression()).append(") DESC, mf.id DESC");
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                statement.setString(i + 1, params.get(i));
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    files.add(mapRecord(resultSet));
                }
            }
        }
        return files;
    }





    /**
     * Finds by file ID in SQLite.
     */
    public Optional<MedicalFileRecord> findByFileId(String fileId) throws SQLException {
        String sql = "SELECT mf.id, mf.file_id, mf.patient_id, COALESCE(TRIM(p.first_name || ' ' || p.last_name), '') AS patient_name, "
                + "mf.original_name, mf.stored_path, mf.file_type, mf.uploaded_by, mf.uploaded_at, mf.extracted_summary, mf.file_size, mf.notes "
                + "FROM medical_files mf LEFT JOIN patients p ON p.patient_id = mf.patient_id WHERE mf.file_id = ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, fileId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapRecord(resultSet));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Deletes by file ID after the required checks.
     */
    public boolean deleteByFileId(String fileId) throws SQLException {
        String sql = "DELETE FROM medical_files WHERE file_id = ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, fileId == null ? "" : fileId.trim());
            return statement.executeUpdate() > 0;
        }
    }

    /**
     * Counts count in SQLite.
     */
    @Override
    public int count() throws SQLException {
        String sql = "SELECT COUNT(*) FROM medical_files";
        try (Connection connection = DatabaseManager.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            return resultSet.next() ? resultSet.getInt(1) : 0;
        }
    }

    /**
     * Ensures schema exists before continuing.
     */
    private void ensureSchema() {
        try {
            SchemaInitializer.initialize();
        } catch (Exception e) {
            System.out.println("SQLite medical file schema check failed: " + e.getMessage());
        }
    }

    /**
     * Adds date range to the current patient workflow.
     */
    private void addDateRange(StringBuilder sql, String dateRange) {
        if (dateRange == null || dateRange.isBlank() || "All".equalsIgnoreCase(dateRange)) {
            return;
        }
        LocalDate today = LocalDate.now();
        int days;
        if ("Today".equalsIgnoreCase(dateRange)) {
            days = 0;
        } else if ("Last 7 days".equalsIgnoreCase(dateRange)) {
            days = 7;
        } else if ("Last 30 days".equalsIgnoreCase(dateRange)) {
            days = 30;
        } else {
            return;
        }
        String displayDate = today.minusDays(days).format(DISPLAY_DATE);
        String iso = today.minusDays(days).format(ISO_DATE);
        if (days == 0) {
            sql.append("AND date(").append(uploadedAtSqlExpression()).append(") = date('").append(today.format(ISO_DATE)).append("') ");
        } else {
            sql.append("AND date(").append(uploadedAtSqlExpression()).append(") >= date('").append(iso).append("') ");
        }
    }

    /**
     * Uploads uploaded at sql expression and stores its metadata in SQLite.
     */
    private String uploadedAtSqlExpression() {
        return "CASE "
                + "WHEN length(mf.uploaded_at) >= 10 AND substr(mf.uploaded_at, 3, 1) = '-' "
                + "THEN substr(mf.uploaded_at, 7, 4) || '-' || substr(mf.uploaded_at, 4, 2) || '-' || substr(mf.uploaded_at, 1, 2) || substr(mf.uploaded_at, 11) "
                + "ELSE mf.uploaded_at END";
    }

    /**
     * Maps record to the corresponding application model.
     */
    private MedicalFileRecord mapRecord(ResultSet resultSet) throws SQLException {
        return new MedicalFileRecord(
                resultSet.getLong("id"),
                resultSet.getString("file_id"),
                resultSet.getString("patient_id"),
                resultSet.getString("patient_name"),
                resultSet.getString("original_name"),
                resultSet.getString("stored_path"),
                resultSet.getString("file_type"),
                resultSet.getString("uploaded_by"),
                resultSet.getString("uploaded_at"),
                resultSet.getString("extracted_summary"),
                resultSet.getLong("file_size"),
                resultSet.getString("notes")
        );
    }

    /**
     * Reads value safely from the current SQLite row.
     */
    private String value(String value) {
        return value == null ? "" : value;
    }

    public static class MedicalFileRecord {
        private final long id;
        private final String fileId;
        private final String patientId;
        private final String patientName;
        private final String originalName;
        private final String storedPath;
        private final String fileType;
        private final String uploadedBy;
        private final String uploadedAt;
        private final String extractedSummary;
        private final long fileSize;
        private final String notes;

        /**
         * Creates a medical file record from the supplied record values.
         */
        public MedicalFileRecord(long id, String fileId, String patientId, String patientName, String originalName,
                                 String storedPath, String fileType, String uploadedBy, String uploadedAt,
                                 String extractedSummary, long fileSize, String notes) {
            this.id = id;
            this.fileId = fileId;
            this.patientId = patientId;
            this.patientName = patientName;
            this.originalName = originalName;
            this.storedPath = storedPath;
            this.fileType = fileType;
            this.uploadedBy = uploadedBy;
            this.uploadedAt = uploadedAt;
            this.extractedSummary = extractedSummary;
            this.fileSize = fileSize;
            this.notes = notes;
        }

        public long getId() { return id; }
        public String getFileId() { return fileId; }
        public String getPatientId() { return patientId; }
        public String getPatientName() { return patientName == null || patientName.isBlank() ? "Unknown patient" : patientName; }
        public String getOriginalName() { return originalName; }
        public String getStoredPath() { return storedPath; }
        public String getFileType() { return fileType; }
        public String getUploadedBy() { return uploadedBy; }
        public String getUploadedAt() { return uploadedAt; }
        public String getExtractedSummary() { return extractedSummary; }
        public long getFileSize() { return fileSize; }
        public String getNotes() { return notes; }
    }
}

