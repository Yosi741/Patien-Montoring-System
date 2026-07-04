package pages.patient.services;

import pages.patient.dao.SqlitePatientDao;
import pages.audit_log.AuditAction;
import pages.audit_log.AuditWriteHelper;
import app.helpers.FormValidationHelper;
import app.helpers.PermissionHelper;
import pages.user.User;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Set;

public class PatientWriteService {

    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final Set<String> VALID_STATUSES = Set.of("ACTIVE", "DISCHARGED", "DECEASED");
    private static final Set<String> VALID_PRIORITIES = Set.of("NORMAL", "HIGH", "CRITICAL", "EMERGENCY");
    private static final Set<String> VALID_BLOOD_TYPES = Set.of("UNKNOWN", "A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-");

    private final SqlitePatientDao patientDao;

    public PatientWriteService() {
        this(new SqlitePatientDao());
    }

    public PatientWriteService(SqlitePatientDao patientDao) {
        this.patientDao = patientDao;
    }

    public void createPatient(User currentUser, SqlitePatientDao.PatientWriteRecord patient) throws SQLException {
        requireWritePermission(currentUser);
        validatePatient(patient, true);
        patientDao.insertPatient(clean(patient));
        AuditWriteHelper.write(currentUser.getUsername(), AuditAction.CREATE_PATIENT, "patient_id=" + patient.getPatientId());
    }

    public void updatePatient(User currentUser, SqlitePatientDao.PatientWriteRecord patient) throws SQLException {
        requireWritePermission(currentUser);
        validatePatient(patient, false);
        if (!patientDao.existsByPatientId(patient.getPatientId())) {
            throw new IllegalArgumentException("Patient does not exist in SQLite: " + patient.getPatientId());
        }
        patientDao.updatePatient(clean(patient));
        AuditWriteHelper.write(currentUser.getUsername(), AuditAction.UPDATE_PATIENT, "patient_id=" + patient.getPatientId());
    }

    public void deactivateOrDischargePatient(User currentUser, String patientId) throws SQLException {
        if (!PermissionHelper.canDeactivatePatient(currentUser)) {
            throw new SecurityException("Only Admin and Doctor users can discharge or deactivate patients.");
        }
        FormValidationHelper.ValidationResult validation = FormValidationHelper.validatePatientId(patientId);
        if (!validation.isValid()) {
            throw new IllegalArgumentException(validation.getMessage());
        }
        if (!patientDao.existsByPatientId(patientId)) {
            throw new IllegalArgumentException("Patient does not exist in SQLite: " + patientId);
        }
        patientDao.deactivatePatient(patientId, "DISCHARGED");
        AuditWriteHelper.write(currentUser.getUsername(), AuditAction.DISCHARGE_PATIENT, "patient_id=" + patientId);
    }

    private void requireWritePermission(User currentUser) {
        if (!PermissionHelper.canCreatePatient(currentUser) && !PermissionHelper.canUpdatePatient(currentUser)) {
            throw new SecurityException("Only Admin and Doctor users can add or edit patients.");
        }
    }

    private void validatePatient(SqlitePatientDao.PatientWriteRecord patient, boolean create) throws SQLException {
        FormValidationHelper.ValidationResult validation = FormValidationHelper.combine(
                FormValidationHelper.validateRequired("Patient ID", patient.getPatientId()),
                FormValidationHelper.validatePatientId(patient.getPatientId()),
                FormValidationHelper.validateRequired("Birth date", patient.getBirthDate()),
                FormValidationHelper.validateRequired("First name", patient.getFirstName()),
                FormValidationHelper.validateRequired("Last name", patient.getLastName()),
                FormValidationHelper.validatePersonName("First name", patient.getFirstName()),
                FormValidationHelper.validatePersonName("Last name", patient.getLastName()),
                FormValidationHelper.validateMaxLength("First name", patient.getFirstName(), 60),
                FormValidationHelper.validateMaxLength("Last name", patient.getLastName(), 60),
                FormValidationHelper.validateMaxLength("Gender", patient.getGender(), 40),
                FormValidationHelper.validateMaxLength("Blood type", patient.getBloodType(), 10),
                FormValidationHelper.validateMaxLength("Section", patient.getSection(), 80),
                FormValidationHelper.validateMaxLength("Room", patient.getRoom(), 30),
                FormValidationHelper.validateMaxLength("Assigned doctor", patient.getAssignedDoctorUsername(), 64),
                FormValidationHelper.validateMaxLength("Assigned nurse/staff", patient.getAssignedStaffUsername(), 64),
                FormValidationHelper.validateMaxLength("Diagnosis", patient.getDiagnosis(), 500)
        );
        if (!validation.isValid()) {
            throw new IllegalArgumentException(validation.getMessage());
        }
        validateBirthDate(patient.getBirthDate());
        validateChoice("Status", normalize(patient.getStatus()), VALID_STATUSES);
        validateChoice("Priority", normalize(patient.getPriority()), VALID_PRIORITIES);
        validateChoice("Blood type", normalizeBloodType(patient.getBloodType()).toUpperCase(Locale.ROOT), VALID_BLOOD_TYPES);
        if (create && patientDao.existsByPatientId(patient.getPatientId())) {
            throw new IllegalArgumentException("Patient ID already exists in SQLite.");
        }
    }

    private void validateBirthDate(String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        LocalDate date = parseBirthDate(value);
        if (date.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Birth date cannot be in the future.");
        }
        if (date.getYear() < 1900) {
            throw new IllegalArgumentException("Birth year cannot be before 1900.");
        }
        if (date.isBefore(LocalDate.now().minusYears(130))) {
            throw new IllegalArgumentException("Patient age cannot exceed 130 years.");
        }
    }

    private void validateChoice(String label, String value, Set<String> validValues) {
        if (!validValues.contains(value)) {
            throw new IllegalArgumentException(label + " must be one of: " + String.join(", ", validValues));
        }
    }

    private SqlitePatientDao.PatientWriteRecord clean(SqlitePatientDao.PatientWriteRecord patient) {
        return new SqlitePatientDao.PatientWriteRecord(
                trim(patient.getPatientId()),
                trim(patient.getFirstName()),
                trim(patient.getLastName()),
                trim(patient.getBirthDate()),
                trim(patient.getGender()),
                trim(patient.getSection()),
                trim(patient.getRoom()),
                normalize(patient.getStatus()),
                normalize(patient.getPriority()),
                normalizeBloodType(patient.getBloodType()),
                trim(patient.getDiagnosis()),
                trim(patient.getAssignedDoctorUsername()),
                trim(patient.getAssignedStaffUsername())
        );
    }

    private LocalDate parseBirthDate(String value) {
        String trimmed = value.trim();
        try {
            return LocalDate.parse(trimmed, DISPLAY_DATE);
        } catch (DateTimeParseException e) {
            try {
                return LocalDate.parse(trimmed);
            } catch (DateTimeParseException ignored) {
                throw new IllegalArgumentException("Birth date must use format dd-MM-yyyy.");
            }
        }
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? "ACTIVE" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeBloodType(String value) {
        String normalized = value == null || value.isBlank() ? "Unknown" : value.trim();
        return "UNKNOWN".equalsIgnoreCase(normalized) ? "Unknown" : normalized.toUpperCase(Locale.ROOT);
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
