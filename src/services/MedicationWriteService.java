package services;

import dao.SqliteMedicationDao;
import dao.SqlitePatientDao;
import ui.javafx.helpers.AuditAction;
import ui.javafx.helpers.AuditWriteHelper;
import ui.javafx.helpers.FormValidationHelper;
import ui.javafx.helpers.PermissionHelper;
import users.User;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;

public class MedicationWriteService {

    private static final DateTimeFormatter LEGACY_DATE_TIME = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
    private static final Set<String> EVENT_STATUSES = Set.of("GIVEN", "MISSED", "DELAYED");

    private final SqliteMedicationDao medicationDao;
    private final SqlitePatientDao patientDao;

    public MedicationWriteService() {
        this(new SqliteMedicationDao(), new SqlitePatientDao());
    }

    public MedicationWriteService(SqliteMedicationDao medicationDao, SqlitePatientDao patientDao) {
        this.medicationDao = medicationDao;
        this.patientDao = patientDao;
    }

    public long addMedication(User currentUser, MedicationRequest request) throws SQLException {
        requireMedicationManagePermission(currentUser);
        validateMedication(request, 0);
        long id = medicationDao.insertMedication(clean(request, 0));
        AuditWriteHelper.write(username(currentUser), AuditAction.ADD_MEDICATION,
                "patient_id=" + request.patientId + ", medication=" + request.name + ", dose=" + request.dose);
        return id;
    }

    public void updateMedication(User currentUser, MedicationRequest request) throws SQLException {
        requireMedicationManagePermission(currentUser);
        if (request.id <= 0) {
            throw new IllegalArgumentException("Medication ID is required for update.");
        }
        validateMedication(request, request.id);
        medicationDao.updateMedication(clean(request, request.id));
        AuditWriteHelper.write(username(currentUser), AuditAction.UPDATE_MEDICATION,
                "patient_id=" + request.patientId + ", medication=" + request.name + ", dose=" + request.dose);
    }

    public void discontinueMedication(User currentUser, long medicationId) throws SQLException {
        requireMedicationManagePermission(currentUser);
        SqliteMedicationDao.MedicationRecord medication = medicationDao.findMedicationById(medicationId)
                .orElseThrow(() -> new IllegalArgumentException("Medication not found in SQLite: " + medicationId));
        medicationDao.discontinueMedication(medicationId);
        AuditWriteHelper.write(username(currentUser), AuditAction.DISCONTINUE_MEDICATION,
                "patient_id=" + medication.getPatientId() + ", medication=" + medication.getName() + ", dose=" + medication.getDose());
    }

    public void recordMedicationGiven(User currentUser, MedicationEventRequest request) throws SQLException {
        if (!PermissionHelper.canGiveMedication(currentUser)) {
            throw new SecurityException("Only Admin, Doctor, and Nurse users can record medication administration.");
        }
        validateMedicationEvent(request);
        SqliteMedicationDao.MedicationRecord medication = medicationDao.findMedicationById(request.medicationId)
                .orElseThrow(() -> new IllegalArgumentException("Medication not found in SQLite: " + request.medicationId));
        if (!medication.isActive()) {
            throw new IllegalArgumentException("Cannot record administration for inactive/discontinued medication.");
        }

        String status = normalizeEventStatus(request.status);
        String notes = "Status: " + status + (hasText(request.notes) ? " | " + request.notes.trim() : "");
        medicationDao.insertMedicationEvent(
                request.medicationId,
                medication.getPatientId(),
                username(currentUser),
                parseDateTime(request.givenAt).format(LEGACY_DATE_TIME),
                notes
        );
        AuditWriteHelper.write(username(currentUser), AuditAction.GIVE_MEDICATION,
                "patient_id=" + medication.getPatientId() + ", medication=" + medication.getName()
                        + ", dose=" + medication.getDose() + ", status=" + status);
    }

    public List<SqliteMedicationDao.MedicationRecord> findActiveMedicationsForPatient(String patientId) throws SQLException {
        return medicationDao.findActiveMedicationsForPatient(patientId);
    }

    private void requireMedicationManagePermission(User currentUser) {
        if (!PermissionHelper.canAddMedication(currentUser)) {
            throw new SecurityException("Only Admin and Doctor users can add, edit, or discontinue medications.");
        }
    }

    private void validateMedication(MedicationRequest request, long excludeMedicationId) throws SQLException {
        FormValidationHelper.ValidationResult validation = FormValidationHelper.combine(
                FormValidationHelper.validatePatientId(request.patientId),
                FormValidationHelper.validateRequired("Medication name", request.name),
                FormValidationHelper.validateRequired("Dose", request.dose),
                FormValidationHelper.validateRequired("Route", request.route),
                FormValidationHelper.validateRequired("Frequency", request.frequency),
                FormValidationHelper.validateMaxLength("Medication name", request.name, 120),
                FormValidationHelper.validateMaxLength("Dose", request.dose, 80),
                FormValidationHelper.validateMaxLength("Route", request.route, 60),
                FormValidationHelper.validateMaxLength("Frequency", request.frequency, 80)
        );
        if (!validation.isValid()) {
            throw new IllegalArgumentException(validation.getMessage());
        }
        if (!patientDao.existsByPatientId(request.patientId)) {
            throw new IllegalArgumentException("Patient does not exist in SQLite: " + request.patientId);
        }
        if (request.active && medicationDao.hasDuplicateActiveMedication(request.patientId, request.name, request.dose, excludeMedicationId)) {
            throw new IllegalArgumentException("An active medication with the same patient, name, and dose already exists.");
        }
    }

    private void validateMedicationEvent(MedicationEventRequest request) {
        FormValidationHelper.ValidationResult validation = FormValidationHelper.combine(
                FormValidationHelper.validateRequired("Medication", String.valueOf(request.medicationId)),
                FormValidationHelper.validateDateTime("Given time", request.givenAt),
                FormValidationHelper.validateMaxLength("Notes", request.notes, 300)
        );
        if (!validation.isValid()) {
            throw new IllegalArgumentException(validation.getMessage());
        }
        LocalDateTime givenAt = parseDateTime(request.givenAt);
        if (givenAt.isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("Given time cannot be in the future.");
        }
        normalizeEventStatus(request.status);
    }

    private SqliteMedicationDao.MedicationRecord clean(MedicationRequest request, long id) {
        return new SqliteMedicationDao.MedicationRecord(
                id,
                trim(request.patientId),
                trim(request.name),
                trim(request.dose),
                trim(request.route),
                trim(request.frequency),
                request.active
        );
    }

    private LocalDateTime parseDateTime(String value) {
        try {
            return LocalDateTime.parse(value.trim(), LEGACY_DATE_TIME);
        } catch (DateTimeParseException e) {
            return LocalDateTime.parse(value.trim().replace(" ", "T"));
        }
    }

    private String normalizeEventStatus(String status) {
        String normalized = hasText(status) ? status.trim().toUpperCase() : "GIVEN";
        if (!EVENT_STATUSES.contains(normalized)) {
            throw new IllegalArgumentException("Medication event status must be GIVEN, MISSED, or DELAYED.");
        }
        return normalized;
    }

    private String username(User currentUser) {
        return currentUser == null || currentUser.getUsername() == null || currentUser.getUsername().isBlank()
                ? "Unknown"
                : currentUser.getUsername();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    public static class MedicationRequest {
        private final long id;
        private final String patientId;
        private final String name;
        private final String dose;
        private final String route;
        private final String frequency;
        private final boolean active;

        public MedicationRequest(long id, String patientId, String name, String dose, String route, String frequency, boolean active) {
            this.id = id;
            this.patientId = patientId;
            this.name = name;
            this.dose = dose;
            this.route = route;
            this.frequency = frequency;
            this.active = active;
        }
    }

    public static class MedicationEventRequest {
        private final long medicationId;
        private final String givenAt;
        private final String status;
        private final String notes;

        public MedicationEventRequest(long medicationId, String givenAt, String status, String notes) {
            this.medicationId = medicationId;
            this.givenAt = givenAt;
            this.status = status;
            this.notes = notes;
        }
    }
}
