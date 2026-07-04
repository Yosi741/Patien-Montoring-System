package ui.javafx.pages.newborns;

import ui.javafx.pages.certificates.CertificateEventService;
import pages.patient.dao.SqlitePatientDao;
import ui.javafx.pages.audit_logs.AuditAction;
import ui.javafx.pages.audit_logs.AuditWriteHelper;
import app.helpers.FormValidationHelper;
import app.helpers.FxFileOpenHelper;
import app.helpers.PermissionHelper;
import pages.user.User;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

public class NewbornService {

    private static final DateTimeFormatter SQLITE_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Path CERTIFICATE_DIR = Path.of("data", "generated", "birth-certificates");
    private static final Set<String> DELIVERY_TYPES = Set.of("NATURAL", "C_SECTION", "ASSISTED", "UNKNOWN");
    private static final Set<String> GENDERS = Set.of("MALE", "FEMALE", "UNKNOWN");

    private final SqliteNewbornRecordDao newbornDao;
    private final SqlitePatientDao patientDao;
    private final CertificateEventService certificateEventService;

    public NewbornService() {
        this(new SqliteNewbornRecordDao(), new SqlitePatientDao(), new CertificateEventService());
    }

    public NewbornService(SqliteNewbornRecordDao newbornDao, SqlitePatientDao patientDao) {
        this(newbornDao, patientDao, new CertificateEventService());
    }

    public NewbornService(SqliteNewbornRecordDao newbornDao, SqlitePatientDao patientDao,
                          CertificateEventService certificateEventService) {
        this.newbornDao = newbornDao;
        this.patientDao = patientDao;
        this.certificateEventService = certificateEventService;
    }

    public void createNewbornRecord(User currentUser, NewbornRecordRequest request) throws SQLException {
        if (!PermissionHelper.canManageNewbornRecords(currentUser)) {
            throw new SecurityException("Only Admin, Doctor, and Nurse users can create newborn records.");
        }
        SqliteNewbornRecordDao.NewbornRecord record = validateRequest(request, true, username(currentUser));
        if (newbornDao.existsByNewbornId(record.getNewbornId())) {
            throw new IllegalArgumentException("Newborn ID already exists in SQLite.");
        }
        newbornDao.insertRecord(record);
        AuditWriteHelper.write(username(currentUser), AuditAction.CREATE_NEWBORN_RECORD,
                "newborn_id=" + record.getNewbornId() + ", mother_patient_id=" + record.getMotherPatientId());
        if (!record.getMotherPatientId().isBlank()) {
            AuditWriteHelper.write(username(currentUser), AuditAction.LINK_NEWBORN_TO_MOTHER,
                    "newborn_id=" + record.getNewbornId() + ", mother_patient_id=" + record.getMotherPatientId());
        }
    }

    public void updateNewbornRecord(User currentUser, NewbornRecordRequest request) throws SQLException {
        if (!PermissionHelper.canManageNewbornRecords(currentUser)) {
            throw new SecurityException("Only Admin, Doctor, and Nurse users can update newborn records.");
        }
        SqliteNewbornRecordDao.NewbornRecord record = validateRequest(request, false, username(currentUser));
        if (!newbornDao.existsByNewbornId(record.getNewbornId())) {
            throw new IllegalArgumentException("Newborn record does not exist in SQLite.");
        }
        newbornDao.updateRecord(record);
        AuditWriteHelper.write(username(currentUser), AuditAction.UPDATE_NEWBORN_RECORD,
                "newborn_id=" + record.getNewbornId());
        if (!record.getMotherPatientId().isBlank()) {
            AuditWriteHelper.write(username(currentUser), AuditAction.LINK_NEWBORN_TO_MOTHER,
                    "newborn_id=" + record.getNewbornId() + ", mother_patient_id=" + record.getMotherPatientId());
        }
    }

    public Path generateBirthCertificate(User currentUser, String newbornId) throws SQLException, IOException {
        if (!PermissionHelper.canGenerateBirthCertificate(currentUser)) {
            throw new SecurityException("Only Admin and Doctor users can generate birth certificates.");
        }
        SqliteNewbornRecordDao.NewbornRecord record = getNewbornRecordById(newbornId);
        Files.createDirectories(CERTIFICATE_DIR);
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        Path output = CERTIFICATE_DIR.resolve("birth_certificate_" + safeFilePart(record.getNewbornId()) + "_" + stamp + ".html").normalize();
        Files.writeString(output, buildCertificateHtml(record, username(currentUser)), StandardCharsets.UTF_8);
        newbornDao.updateCertificatePath(record.getNewbornId(), output.toString());
        certificateEventService.notifyBirthCertificateGenerated(currentUser, getNewbornRecordById(record.getNewbornId()));
        AuditWriteHelper.write(username(currentUser), AuditAction.GENERATE_BIRTH_CERTIFICATE,
                "newborn_id=" + record.getNewbornId());
        return output;
    }

    public void openBirthCertificate(User currentUser, String newbornId) throws SQLException, IOException {
        SqliteNewbornRecordDao.NewbornRecord record = getNewbornRecordById(newbornId);
        Path certificate = validateCertificatePath(record.getCertificatePath());
        AuditWriteHelper.write(username(currentUser), AuditAction.OPEN_BIRTH_CERTIFICATE,
                "newborn_id=" + record.getNewbornId());
        FxFileOpenHelper.open(certificate);
    }

    public List<SqliteNewbornRecordDao.NewbornRecord> getNewbornRecords(SqliteNewbornRecordDao.RecordFilter filter) throws SQLException {
        return newbornDao.findRecords(filter);
    }

    public SqliteNewbornRecordDao.NewbornRecord getNewbornRecordById(String newbornId) throws SQLException {
        return newbornDao.findByNewbornId(newbornId)
                .orElseThrow(() -> new IllegalArgumentException("Newborn record not found in SQLite: " + newbornId));
    }

    public List<SqliteNewbornRecordDao.NewbornRecord> getNewbornsByMother(String motherPatientId) throws SQLException {
        return newbornDao.findByMother(motherPatientId);
    }

    private SqliteNewbornRecordDao.NewbornRecord validateRequest(NewbornRecordRequest request, boolean create, String createdBy) throws SQLException {
        String newbornId = safe(request == null ? "" : request.newbornId);
        String motherPatientId = safe(request == null ? "" : request.motherPatientId);
        String fatherName = safe(request == null ? "" : request.fatherName);
        String motherName = safe(request == null ? "" : request.motherName);
        String babyName = safe(request == null ? "" : request.babyName);
        String gender = normalizeChoice(request == null ? "" : request.gender, "UNKNOWN");
        String birthTime = normalizeDateTime(request == null ? "" : request.birthTime);
        double birthWeight = parseDouble("Birth weight", request == null ? "" : request.birthWeight);
        Double birthLength = parseOptionalDouble("Birth length", request == null ? "" : request.birthLength);
        String deliveryType = normalizeChoice(request == null ? "" : request.deliveryType, "UNKNOWN");
        String room = safe(request == null ? "" : request.room);
        String section = safe(request == null ? "" : request.section);
        String doctorOrMidwife = safe(request == null ? "" : request.doctorOrMidwife);
        String notes = safe(request == null ? "" : request.notes);

        if (!motherPatientId.isBlank()) {
            SqlitePatientDao.PatientDetail mother = patientDao.findDetailById(motherPatientId)
                    .orElseThrow(() -> new IllegalArgumentException("Mother patient ID does not exist in SQLite: " + motherPatientId));
            if (motherName.isBlank()) {
                motherName = mother.getName();
            }
        }

        FormValidationHelper.ValidationResult validation = FormValidationHelper.combine(
                FormValidationHelper.validateRequired("Newborn ID", newbornId),
                FormValidationHelper.validateNineDigitId("Newborn ID", newbornId),
                motherPatientId.isBlank()
                        ? FormValidationHelper.ValidationResult.ok()
                        : FormValidationHelper.validatePatientId(motherPatientId),
                FormValidationHelper.validateRequired("Baby name", babyName),
                FormValidationHelper.validatePersonName("Baby name", babyName),
                FormValidationHelper.validateMaxLength("Baby name", babyName, 120),
                FormValidationHelper.validateRequired("Mother name", motherName),
                FormValidationHelper.validatePersonName("Mother name", motherName),
                FormValidationHelper.validateMaxLength("Mother name", motherName, 120),
                FormValidationHelper.validateRequired("Gender", gender),
                FormValidationHelper.validateDateTime("Birth time", birthTime),
                FormValidationHelper.validateRequired("Section", section),
                fatherName.isBlank()
                        ? FormValidationHelper.ValidationResult.ok()
                        : FormValidationHelper.validatePersonName("Father name", fatherName),
                FormValidationHelper.validateMaxLength("Father name", fatherName, 120),
                FormValidationHelper.validateMaxLength("Room", room, 40),
                FormValidationHelper.validateMaxLength("Section", section, 80),
                FormValidationHelper.validateMaxLength("Doctor/midwife", doctorOrMidwife, 120),
                FormValidationHelper.validateMaxLength("Notes", notes, 500)
        );
        if (!validation.isValid()) {
            throw new IllegalArgumentException(validation.getMessage());
        }
        if (!GENDERS.contains(gender)) {
            throw new IllegalArgumentException("Gender must be MALE, FEMALE, or UNKNOWN.");
        }
        if (!DELIVERY_TYPES.contains(deliveryType)) {
            throw new IllegalArgumentException("Delivery type must be NATURAL, C_SECTION, ASSISTED, or UNKNOWN.");
        }
        LocalDateTime parsedBirthTime = parseDateTime(birthTime);
        if (parsedBirthTime.isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("Birth time cannot be in the future.");
        }
        if (ChronoUnit.DAYS.between(parsedBirthTime, LocalDateTime.now()) > 28) {
            throw new IllegalArgumentException("Newborn birth time must be within the last 28 days for this demo workflow.");
        }
        if (birthWeight <= 0) {
            throw new IllegalArgumentException("Birth weight must be positive.");
        }
        if (birthLength != null && birthLength <= 0) {
            throw new IllegalArgumentException("Birth length must be positive when provided.");
        }
        return new SqliteNewbornRecordDao.NewbornRecord(0, newbornId, motherPatientId, fatherName, motherName,
                babyName, gender, birthTime, birthWeight, birthLength, deliveryType, room, section,
                doctorOrMidwife, notes, "", createdBy, "", "", "DRAFT", "", "", "");
    }

    private Path validateCertificatePath(String certificatePath) throws IOException {
        if (certificatePath == null || certificatePath.isBlank()) {
            throw new IllegalArgumentException("Birth certificate has not been generated yet.");
        }
        Path base = CERTIFICATE_DIR.toAbsolutePath().normalize();
        Path path = Path.of(certificatePath).toAbsolutePath().normalize();
        if (!path.startsWith(base)) {
            throw new SecurityException("Certificate path is outside data/generated/birth-certificates.");
        }
        if (!Files.exists(path)) {
            throw new IOException("Certificate file is missing: " + path);
        }
        return path;
    }

    private String buildCertificateHtml(SqliteNewbornRecordDao.NewbornRecord record, String generatedBy) {
        return "<!DOCTYPE html>\n<html><head><meta charset=\"UTF-8\"><title>Birth Certificate</title>"
                + "<style>body{font-family:Arial,sans-serif;margin:48px;color:#263238;} h1{color:#0D47A1;} "
                + ".box{border:1px solid #CFD8DC;padding:18px;margin:14px 0;} dt{font-weight:bold;color:#607D8B;} dd{margin:0 0 12px 0;}</style>"
                + "</head><body><h1>Generated Local Birth Certificate</h1>"
                + "<p>Prototype-generated certificate based on local database records.</p><div class=\"box\"><dl>"
                + field("Newborn ID", record.getNewbornId())
                + field("Baby Name", record.getBabyName())
                + field("Gender", record.getGender())
                + field("Birth Time", record.getBirthTime())
                + field("Birth Weight", record.getBirthWeight() + " kg")
                + field("Birth Length", record.getBirthLength() == null ? "-" : record.getBirthLength() + " cm")
                + field("Mother", record.getMotherDisplay())
                + field("Father", record.getFatherName())
                + field("Delivery Type", record.getDeliveryType())
                + field("Room / Section", record.getRoom() + " / " + record.getSection())
                + field("Doctor / Midwife", record.getDoctorOrMidwife())
                + field("Generated By", generatedBy)
                + field("Generated At", LocalDateTime.now().format(SQLITE_DATE_TIME))
                + "</dl></div><p>This local hospital prototype certificate is generated from SQLite local database records. It is not an official government or clinical certificate.</p></body></html>";
    }

    private String field(String label, String value) {
        return "<dt>" + escape(label) + "</dt><dd>" + escape(value == null || value.isBlank() ? "-" : value) + "</dd>";
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
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

    private double parseDouble(String label, String value) {
        try {
            return Double.parseDouble(safe(value));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(label + " must be numeric.");
        }
    }

    private Double parseOptionalDouble(String label, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return parseDouble(label, value);
    }

    private String normalizeChoice(String value, String fallback) {
        String normalized = safe(value).toUpperCase();
        return normalized.isBlank() ? fallback : normalized;
    }

    private String safeFilePart(String value) {
        String clean = safe(value).replaceAll("[^A-Za-z0-9_-]", "_");
        return clean.isBlank() ? "newborn" : clean;
    }

    private String username(User currentUser) {
        return currentUser == null || currentUser.getUsername() == null || currentUser.getUsername().isBlank()
                ? "Unknown"
                : currentUser.getUsername();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    public static class NewbornRecordRequest {
        private final String newbornId;
        private final String babyName;
        private final String gender;
        private final String birthTime;
        private final String birthWeight;
        private final String birthLength;
        private final String motherPatientId;
        private final String motherName;
        private final String fatherName;
        private final String deliveryType;
        private final String room;
        private final String section;
        private final String doctorOrMidwife;
        private final String notes;

        public NewbornRecordRequest(String newbornId, String babyName, String gender, String birthTime,
                                    String birthWeight, String birthLength, String motherPatientId, String motherName,
                                    String fatherName, String deliveryType, String room, String section,
                                    String doctorOrMidwife, String notes) {
            this.newbornId = newbornId;
            this.babyName = babyName;
            this.gender = gender;
            this.birthTime = birthTime;
            this.birthWeight = birthWeight;
            this.birthLength = birthLength;
            this.motherPatientId = motherPatientId;
            this.motherName = motherName;
            this.fatherName = fatherName;
            this.deliveryType = deliveryType;
            this.room = room;
            this.section = section;
            this.doctorOrMidwife = doctorOrMidwife;
            this.notes = notes;
        }
    }
}
