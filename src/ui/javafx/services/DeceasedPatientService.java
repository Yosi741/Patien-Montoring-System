package ui.javafx.services;

import Data_Access_Object.SqliteDeceasedRecordDao;
import ui.javafx.patients.dao.SqlitePatientDao;
import ui.javafx.helpers.AuditAction;
import ui.javafx.helpers.AuditWriteHelper;
import ui.javafx.helpers.FormValidationHelper;
import ui.javafx.helpers.FxFileOpenHelper;
import ui.javafx.helpers.PermissionHelper;
import users.User;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

public class DeceasedPatientService {

    private static final DateTimeFormatter SQLITE_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Path CERTIFICATE_DIR = Path.of("data", "generated", "death-certificates");

    private final SqliteDeceasedRecordDao deceasedRecordDao;
    private final SqlitePatientDao patientDao;
    private final CertificateEventService certificateEventService;

    public DeceasedPatientService() {
        this(new SqliteDeceasedRecordDao(), new SqlitePatientDao(), new CertificateEventService());
    }

    public DeceasedPatientService(SqliteDeceasedRecordDao deceasedRecordDao, SqlitePatientDao patientDao) {
        this(deceasedRecordDao, patientDao, new CertificateEventService());
    }

    public DeceasedPatientService(SqliteDeceasedRecordDao deceasedRecordDao, SqlitePatientDao patientDao,
                                  CertificateEventService certificateEventService) {
        this.deceasedRecordDao = deceasedRecordDao;
        this.patientDao = patientDao;
        this.certificateEventService = certificateEventService;
    }

    public long markPatientDeceased(User currentUser, DeathRecordRequest request) throws SQLException {
        if (!PermissionHelper.canMarkPatientDeceased(currentUser)) {
            throw new SecurityException("Only Admin and Doctor users can mark a patient deceased.");
        }
        SqliteDeceasedRecordDao.DeathRecord clean = validateRequest(request, true, username(currentUser));
        SqlitePatientDao.PatientDetail patient = patientDao.findDetailById(clean.getPatientId())
                .orElseThrow(() -> new IllegalArgumentException("Patient does not exist in SQLite: " + clean.getPatientId()));
        if ("DECEASED".equalsIgnoreCase(patient.getStatus())) {
            throw new IllegalArgumentException("Patient is already marked DECEASED.");
        }
        if (deceasedRecordDao.findByPatientId(clean.getPatientId()).isPresent()) {
            throw new IllegalArgumentException("A death record already exists for this patient.");
        }
        long id = deceasedRecordDao.insertRecord(clean);
        patientDao.deactivatePatient(clean.getPatientId(), "DECEASED");
        AuditWriteHelper.write(username(currentUser), AuditAction.MARK_PATIENT_DECEASED,
                "patient_id=" + clean.getPatientId() + ", death_time=" + clean.getDeathTime());
        return id;
    }

    public void updateDeathRecord(User currentUser, long recordId, DeathRecordRequest request) throws SQLException {
        if (!PermissionHelper.canMarkPatientDeceased(currentUser)) {
            throw new SecurityException("Only Admin and Doctor users can update death records.");
        }
        SqliteDeceasedRecordDao.DeathRecord existing = getDeathRecord(recordId);
        SqliteDeceasedRecordDao.DeathRecord clean = validateRequest(
                new DeathRecordRequest(existing.getPatientId(), request.deathTime, request.pronouncedBy,
                        request.causeOfDeath, request.notes), false, username(currentUser));
        deceasedRecordDao.updateRecord(recordId, clean);
        AuditWriteHelper.write(username(currentUser), AuditAction.UPDATE_DEATH_RECORD,
                "patient_id=" + existing.getPatientId() + ", record_id=" + recordId);
    }

    public Path generateDeathCertificate(User currentUser, long recordId) throws SQLException, IOException {
        if (!PermissionHelper.canGenerateDeathCertificate(currentUser)) {
            throw new SecurityException("Only Admin and Doctor users can generate death certificates.");
        }
        SqliteDeceasedRecordDao.DeathRecord record = getDeathRecord(recordId);
        Files.createDirectories(CERTIFICATE_DIR);
        String safePatientId = safeFilePart(record.getPatientId());
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        Path output = CERTIFICATE_DIR.resolve("death_certificate_" + safePatientId + "_" + stamp + ".html").normalize();
        if (!output.startsWith(CERTIFICATE_DIR.toAbsolutePath().normalize()) && output.isAbsolute()) {
            throw new SecurityException("Certificate path is outside the allowed directory.");
        }
        String html = buildCertificateHtml(record, username(currentUser));
        Files.writeString(output, html, StandardCharsets.UTF_8);
        deceasedRecordDao.updateCertificatePath(recordId, output.toString());
        certificateEventService.notifyDeathCertificateGenerated(currentUser, getDeathRecord(recordId));
        AuditWriteHelper.write(username(currentUser), AuditAction.GENERATE_DEATH_CERTIFICATE,
                "patient_id=" + record.getPatientId() + ", record_id=" + recordId);
        return output;
    }

    public void openDeathCertificate(User currentUser, long recordId) throws SQLException, IOException {
        SqliteDeceasedRecordDao.DeathRecord record = getDeathRecord(recordId);
        Path certificate = validateCertificatePath(record.getCertificatePath());
        AuditWriteHelper.write(username(currentUser), AuditAction.OPEN_DEATH_CERTIFICATE,
                "patient_id=" + record.getPatientId() + ", record_id=" + recordId);
        FxFileOpenHelper.open(certificate);
    }

    public List<SqliteDeceasedRecordDao.DeathRecord> getDeceasedRecords(SqliteDeceasedRecordDao.RecordFilter filter) throws SQLException {
        return deceasedRecordDao.findRecords(filter);
    }

    public SqliteDeceasedRecordDao.DeathRecord getDeathRecordByPatient(String patientId) throws SQLException {
        return deceasedRecordDao.findByPatientId(patientId)
                .orElseThrow(() -> new IllegalArgumentException("No death record found for patient " + patientId));
    }

    private SqliteDeceasedRecordDao.DeathRecord getDeathRecord(long recordId) throws SQLException {
        return deceasedRecordDao.findById(recordId)
                .orElseThrow(() -> new IllegalArgumentException("Death record not found in SQLite."));
    }

    private SqliteDeceasedRecordDao.DeathRecord validateRequest(DeathRecordRequest request, boolean requirePatient, String createdBy) {
        String patientId = request == null ? "" : safe(request.patientId);
        String deathTime = normalizeDateTime(request == null ? "" : request.deathTime);
        String pronouncedBy = request == null ? "" : safe(request.pronouncedBy);
        String cause = request == null ? "" : safe(request.causeOfDeath);
        String notes = request == null ? "" : safe(request.notes);
        if (cause.isBlank()) {
            cause = "Unknown/Pending";
        }
        FormValidationHelper.ValidationResult validation = FormValidationHelper.combine(
                requirePatient ? FormValidationHelper.validatePatientId(patientId) : FormValidationHelper.ValidationResult.ok(),
                FormValidationHelper.validateDateTime("Death time", deathTime),
                FormValidationHelper.validateRequired("Pronounced by", pronouncedBy),
                FormValidationHelper.validateMaxLength("Pronounced by", pronouncedBy, 120),
                FormValidationHelper.validateRequired("Cause of death", cause),
                FormValidationHelper.validateMaxLength("Cause of death", cause, 300),
                FormValidationHelper.validateMaxLength("Notes", notes, 500)
        );
        if (!validation.isValid()) {
            throw new IllegalArgumentException(validation.getMessage());
        }
        if (parseDateTime(deathTime).isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("Death time cannot be in the future.");
        }
        return SqliteDeceasedRecordDao.DeathRecord.newRecord(patientId, deathTime, pronouncedBy, cause, notes, createdBy);
    }

    private Path validateCertificatePath(String certificatePath) throws IOException {
        if (certificatePath == null || certificatePath.isBlank()) {
            throw new IllegalArgumentException("Certificate has not been generated yet.");
        }
        Path base = CERTIFICATE_DIR.toAbsolutePath().normalize();
        Path path = Path.of(certificatePath).toAbsolutePath().normalize();
        if (!path.startsWith(base)) {
            throw new SecurityException("Certificate path is outside data/generated/death-certificates.");
        }
        if (!Files.exists(path)) {
            throw new IOException("Certificate file is missing: " + path);
        }
        return path;
    }

    private String buildCertificateHtml(SqliteDeceasedRecordDao.DeathRecord record, String generatedBy) {
        return "<!DOCTYPE html>\n"
                + "<html><head><meta charset=\"UTF-8\"><title>Death Certificate</title>"
                + "<style>body{font-family:Arial,sans-serif;margin:48px;color:#263238;} "
                + "h1{color:#0D47A1;} .box{border:1px solid #CFD8DC;padding:18px;margin:14px 0;} "
                + "dt{font-weight:bold;color:#607D8B;} dd{margin:0 0 12px 0;}</style></head><body>"
                + "<h1>Generated Local Death Certificate</h1>"
                + "<p>Prototype-generated certificate based on local database records.</p>"
                + "<div class=\"box\"><dl>"
                + field("Patient ID", record.getPatientId())
                + field("Patient Name", record.getPatientName())
                + field("Section", record.getSection())
                + field("Death Time", record.getDeathTime())
                + field("Pronounced By", record.getPronouncedBy())
                + field("Cause Of Death", record.getCauseOfDeath())
                + field("Notes", record.getNotes())
                + field("Generated By", generatedBy)
                + field("Generated At", LocalDateTime.now().format(SQLITE_DATE_TIME))
                + "</dl></div>"
                + "<p>This local hospital prototype certificate is generated from SQLite local database records. It is not an official government or clinical certificate.</p>"
                + "</body></html>";
    }

    private String field(String label, String value) {
        return "<dt>" + escape(label) + "</dt><dd>" + escape(value == null || value.isBlank() ? "-" : value) + "</dd>";
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private String normalizeDateTime(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return parseDateTime(value.trim()).format(SQLITE_DATE_TIME);
    }

    private LocalDateTime parseDateTime(String value) {
        try {
            return LocalDateTime.parse(value, SQLITE_DATE_TIME);
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDateTime.parse(value.replace(" ", "T"));
            } catch (DateTimeParseException ignoredAgain) {
                return LocalDateTime.parse(value, DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"));
            }
        }
    }

    private String safeFilePart(String value) {
        String clean = safe(value).replaceAll("[^A-Za-z0-9_-]", "_");
        return clean.isBlank() ? "patient" : clean;
    }

    private String username(User currentUser) {
        return currentUser == null || currentUser.getUsername() == null || currentUser.getUsername().isBlank()
                ? "Unknown"
                : currentUser.getUsername();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    public static class DeathRecordRequest {
        private final String patientId;
        private final String deathTime;
        private final String pronouncedBy;
        private final String causeOfDeath;
        private final String notes;

        public DeathRecordRequest(String patientId, String deathTime, String pronouncedBy, String causeOfDeath, String notes) {
            this.patientId = patientId;
            this.deathTime = deathTime;
            this.pronouncedBy = pronouncedBy;
            this.causeOfDeath = causeOfDeath;
            this.notes = notes;
        }
    }
}
