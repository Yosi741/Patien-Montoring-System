package pages.patient.patient_registration;

import app.helpers.FormValidationHelper;
import app.helpers.PermissionHelper;
import pages.patient.patient_details.PatientDetailsRepository;
import pages.patient.patient_details.RelatedRecordCounts;
import pages.patient.patient_details.PatientVisitService;
import pages.user.User;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Set;

/**
 * Validates and saves Add/Edit Patient form data, including returning-patient visits.
 */
public class PatientRegistrationService {

    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final Set<String> VALID_STATUSES = Set.of("ACTIVE", "DISCHARGED");
    private static final Set<String> VALID_PRIORITIES = Set.of("NORMAL", "HIGH", "CRITICAL", "EMERGENCY");
    private static final Set<String> VALID_BLOOD_TYPES = Set.of("UNKNOWN", "A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-");

    private final PatientRegistrationRepository patientRegistrationRepository;
    private final PatientDetailsRepository patientDetailsRepository;
    private final PatientVisitService patientVisitService;

    /**
     * Creates the service with the dependencies used by the patient workflow.
     */
    public PatientRegistrationService() {
        this(new PatientRegistrationRepository(), new PatientDetailsRepository(), new PatientVisitService());
    }

    /**
     * Creates the service with the dependencies used by the patient workflow.
     */
    public PatientRegistrationService(PatientRegistrationRepository patientRegistrationRepository) {
        this(patientRegistrationRepository, new PatientDetailsRepository(), new PatientVisitService());
    }

    /**
     * Creates the service with the dependencies used by the patient workflow.
     */
    public PatientRegistrationService(PatientRegistrationRepository patientRegistrationRepository,
                                      PatientDetailsRepository patientDetailsRepository,
                                      PatientVisitService patientVisitService) {
        this.patientRegistrationRepository = patientRegistrationRepository;
        this.patientDetailsRepository = patientDetailsRepository;
        this.patientVisitService = patientVisitService;
    }

    /**
     * Creates a new patient and starts an active clinic visit.
     */
    public void createNewPatient(User currentUser, PatientRegistrationData patient) throws SQLException {
        requireWritePermission(currentUser);
        validatePatient(patient, true);
        patientRegistrationRepository.createPatient(clean(patient));
        patientVisitService.ensureActiveVisit(patient.getPatientId(), patient.getDiagnosis());
    }

    /**
     * Updates an existing patient record.
     */
    public void updateExistingPatient(User currentUser, PatientRegistrationData patient) throws SQLException {
        requireWritePermission(currentUser);
        validatePatient(patient, false);
        if (!patientRegistrationRepository.patientIdExists(patient.getPatientId())) {
            throw new IllegalArgumentException("Patient does not exist in SQLite: " + patient.getPatientId());
        }
        patientRegistrationRepository.updateExistingPatient(clean(patient));
    }

    /**
     * Discharges patient while preserving its visit history.
     */
    public void dischargePatient(User currentUser, String patientId, String dischargeSummary) throws SQLException {
        if (!PermissionHelper.canDeactivatePatient(currentUser)) {
            throw new SecurityException("Only Admin and Doctor users can discharge or deactivate patients.");
        }
        FormValidationHelper.ValidationResult validation = FormValidationHelper.validatePatientId(patientId);
        if (!validation.isValid()) {
            throw new IllegalArgumentException(validation.getMessage());
        }
        if (!patientRegistrationRepository.patientIdExists(patientId)) {
            throw new IllegalArgumentException("Patient does not exist in SQLite: " + patientId);
        }
        patientDetailsRepository.deactivatePatientRecord(patientId, "DISCHARGED");
        patientVisitService.dischargeVisit(patientId, dischargeSummary);
    }

    /**
     * Returns related record counts used by the patient workflow.
     */
    public RelatedRecordCounts getRelatedRecordCounts(User currentUser, String patientId) throws SQLException {
        if (!PermissionHelper.canDeletePatient(currentUser)) {
            throw new SecurityException("Only Admin users can delete patients.");
        }
        FormValidationHelper.ValidationResult validation = FormValidationHelper.validatePatientId(patientId);
        if (!validation.isValid()) {
            throw new IllegalArgumentException(validation.getMessage());
        }
        if (!patientRegistrationRepository.patientIdExists(patientId)) {
            throw new IllegalArgumentException("Patient does not exist in SQLite: " + patientId);
        }
        return patientDetailsRepository.countPatientRelatedRecords(patientId);
    }

    /**
     * Deletes patient after the required checks.
     */
    public void deletePatient(User currentUser, String patientId) throws SQLException {
        if (!PermissionHelper.canDeletePatient(currentUser)) {
            throw new SecurityException("Only Admin users can delete patients.");
        }
        FormValidationHelper.ValidationResult validation = FormValidationHelper.validatePatientId(patientId);
        if (!validation.isValid()) {
            throw new IllegalArgumentException(validation.getMessage());
        }
        if (!patientRegistrationRepository.patientIdExists(patientId)) {
            throw new IllegalArgumentException("Patient does not exist in SQLite: " + patientId);
        }
        boolean deleted = patientDetailsRepository.deletePatientAndRelatedRecords(patientId);
        if (!deleted) {
            throw new IllegalStateException("Patient could not be deleted.");
        }
    }

    /**
     * Reactivates returning patient for a returning clinic visit.
     */
    public void reactivateReturningPatient(User currentUser, PatientRegistrationData patient) throws SQLException {
        requireWritePermission(currentUser);
        validatePatient(patient, false);
        patientRegistrationRepository.findExistingPatientById(patient.getPatientId())
                .orElseThrow(() -> new IllegalArgumentException("Patient does not exist in SQLite: " + patient.getPatientId()));

        PatientRegistrationData activeVisitRecord = new PatientRegistrationData(
                patient.getPatientId(),
                patient.getFirstName(),
                patient.getLastName(),
                patient.getBirthDate(),
                patient.getGender(),
                "ACTIVE",
                patient.getPriority(),
                patient.getBloodType(),
                patient.getDiagnosis(),
                patient.getAllergies(),
                patient.getPhone(),
                patient.getEmail(),
                patient.getAddress(),
                patient.getEmergencyContactName(),
                patient.getEmergencyContactPhone()
        );
        patientRegistrationRepository.updateExistingPatient(clean(activeVisitRecord));
        patientVisitService.ensureActiveVisit(patient.getPatientId(), patient.getDiagnosis());
    }

    /**
     * Enforces write permission before the protected operation continues.
     */
    private void requireWritePermission(User currentUser) {
        if (!PermissionHelper.canCreatePatient(currentUser) && !PermissionHelper.canUpdatePatient(currentUser)) {
            throw new SecurityException("Only Admin, Doctor, Nurse, or Secretary users can add or edit patients.");
        }
    }

    /**
     * Validates patient against the active business rules.
     */
    private void validatePatient(PatientRegistrationData patient, boolean create) throws SQLException {
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
                FormValidationHelper.validateMaxLength("Diagnosis", patient.getDiagnosis(), 500),
                FormValidationHelper.validateMaxLength("Allergies", patient.getAllergies(), 500),
                FormValidationHelper.validateMaxLength("Phone", patient.getPhone(), 30),
                FormValidationHelper.validateMaxLength("Email", patient.getEmail(), 120),
                FormValidationHelper.validateMaxLength("Address", patient.getAddress(), 240),
                FormValidationHelper.validateMaxLength("Emergency contact name", patient.getEmergencyContactName(), 80),
                FormValidationHelper.validateMaxLength("Emergency contact phone", patient.getEmergencyContactPhone(), 30)
        );
        if (!validation.isValid()) {
            throw new IllegalArgumentException(validation.getMessage());
        }
        validateOptionalEmail(patient.getEmail());
        validateBirthDate(patient.getBirthDate());
        validateChoice("Status", normalize(patient.getStatus()), VALID_STATUSES);
        validateChoice("Priority", normalize(patient.getPriority()), VALID_PRIORITIES);
        validateChoice("Blood type", normalizeBloodType(patient.getBloodType()).toUpperCase(Locale.ROOT), VALID_BLOOD_TYPES);
        if (create && patientRegistrationRepository.patientIdExists(patient.getPatientId())) {
            throw new IllegalArgumentException("A patient file already exists for this ID. Use Check ID to load the existing profile.");
        }
    }

    /**
     * Validates birth date against the active business rules.
     */
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

    /**
     * Validates choice against the active business rules.
     */
    private void validateChoice(String label, String value, Set<String> validValues) {
        if (!validValues.contains(value)) {
            throw new IllegalArgumentException(label + " must be one of: " + String.join(", ", validValues));
        }
    }

    /**
     * Trims and normalizes clean before storage or comparison.
     */
    private PatientRegistrationData clean(PatientRegistrationData patient) {
        return new PatientRegistrationData(
                trim(patient.getPatientId()),
                trim(patient.getFirstName()),
                trim(patient.getLastName()),
                trim(patient.getBirthDate()),
                trim(patient.getGender()),
                normalize(patient.getStatus()),
                normalize(patient.getPriority()),
                normalizeBloodType(patient.getBloodType()),
                trim(patient.getDiagnosis()),
                normalizeAllergies(patient.getAllergies()),
                trim(patient.getPhone()),
                trim(patient.getEmail()),
                trim(patient.getAddress()),
                trim(patient.getEmergencyContactName()),
                trim(patient.getEmergencyContactPhone())
        );
    }

    /**
     * Parses birth date without exposing format failures to the caller.
     */
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

    /**
     * Normalizes normalize to the stored application format.
     */
    private String normalize(String value) {
        return value == null || value.isBlank() ? "ACTIVE" : value.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * Normalizes blood type to the stored application format.
     */
    private String normalizeBloodType(String value) {
        String normalized = value == null || value.isBlank() ? "Unknown" : value.trim();
        return "UNKNOWN".equalsIgnoreCase(normalized) ? "Unknown" : normalized.toUpperCase(Locale.ROOT);
    }

    /**
     * Trims trim while preserving null handling.
     */
    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * Normalizes allergies to the stored application format.
     */
    private String normalizeAllergies(String value) {
        return value == null || value.isBlank() ? "Unknown" : value.trim();
    }

    /**
     * Validates optional email against the active business rules.
     */
    private void validateOptionalEmail(String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (!value.contains("@")) {
            throw new IllegalArgumentException("Email must contain @ when provided.");
        }
    }
}




