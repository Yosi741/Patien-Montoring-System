package services;

import dao.SqliteMedicationDao;
import dao.SqliteMedicationCatalogDao;
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
import java.util.Optional;
import java.util.Set;

public class MedicationWriteService {

    private static final DateTimeFormatter DISPLAY_DATE_TIME = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
    private static final Set<String> EVENT_STATUSES = Set.of("GIVEN", "MISSED", "DELAYED");
    private static final Set<String> ROUTES = Set.of("Oral", "IV", "IM", "SC", "Inhalation", "Topical", "Eye drops", "Ear drops", "Other");
    private static final Set<String> FREQUENCIES = Set.of("Once daily", "Twice daily", "Three times daily",
            "Every 6 hours", "Every 8 hours", "Every 12 hours", "Weekly", "As needed", "Other");

    private final SqliteMedicationDao medicationDao;
    private final SqlitePatientDao patientDao;
    private final SqliteMedicationCatalogDao catalogDao;

    public MedicationWriteService() {
        this(new SqliteMedicationDao(), new SqlitePatientDao(), new SqliteMedicationCatalogDao());
    }

    public MedicationWriteService(SqliteMedicationDao medicationDao, SqlitePatientDao patientDao) {
        this(medicationDao, patientDao, new SqliteMedicationCatalogDao());
    }

    public MedicationWriteService(SqliteMedicationDao medicationDao, SqlitePatientDao patientDao, SqliteMedicationCatalogDao catalogDao) {
        this.medicationDao = medicationDao;
        this.patientDao = patientDao;
        this.catalogDao = catalogDao;
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
                parseDateTime(request.givenAt).format(DISPLAY_DATE_TIME),
                notes
        );
        AuditWriteHelper.write(username(currentUser), AuditAction.GIVE_MEDICATION,
                "patient_id=" + medication.getPatientId() + ", medication=" + medication.getName()
                        + ", dose=" + medication.getDose() + ", status=" + status);
    }

    public List<SqliteMedicationDao.MedicationRecord> findActiveMedicationsForPatient(String patientId) throws SQLException {
        return medicationDao.findActiveMedicationsForPatient(patientId);
    }

    public Optional<SqliteMedicationDao.MedicationRecord> findMedicationById(long medicationId) throws SQLException {
        return medicationDao.findMedicationById(medicationId);
    }

    private void requireMedicationManagePermission(User currentUser) {
        if (!PermissionHelper.canAddMedication(currentUser)) {
            throw new SecurityException("Only Admin and Doctor users can add, edit, or discontinue medications.");
        }
    }

    private void validateMedication(MedicationRequest request, long excludeMedicationId) throws SQLException {
        FormValidationHelper.ValidationResult validation = FormValidationHelper.combine(
                FormValidationHelper.validatePatientId(request.patientId),
                request.catalogMedicationId != null && request.catalogMedicationId > 0
                        ? FormValidationHelper.ValidationResult.ok()
                        : FormValidationHelper.validateRequired("Medication name", request.name),
                FormValidationHelper.validateRequired("Dose amount", request.doseAmount),
                FormValidationHelper.validateRequired("Dose unit", request.doseUnit),
                FormValidationHelper.validateRequired("Route", request.route),
                FormValidationHelper.validateRequired("Frequency", request.frequency),
                FormValidationHelper.validateMaxLength("Medication name", request.name, 120),
                FormValidationHelper.validateMaxLength("Dose amount", request.doseAmount, 40),
                FormValidationHelper.validateMaxLength("Dose unit", request.doseUnit, 40),
                FormValidationHelper.validateMaxLength("Route", request.route, 60),
                FormValidationHelper.validateMaxLength("Frequency", request.frequency, 80)
        );
        if (!validation.isValid()) {
            throw new IllegalArgumentException(validation.getMessage());
        }
        if (!ROUTES.contains(trim(request.route))) {
            throw new IllegalArgumentException("Route must be selected from the approved route list.");
        }
        if (!FREQUENCIES.contains(trim(request.frequency))) {
            throw new IllegalArgumentException("Frequency must be selected from the approved frequency list.");
        }
        Double doseAmount = parsePositiveDoseAmount(request.doseAmount);
        if (doseAmount == null) {
            throw new IllegalArgumentException("Dose amount is required and must be a positive number.");
        }
        if (!hasText(request.doseUnit)) {
            throw new IllegalArgumentException("Dose unit is required.");
        }
        SqliteMedicationCatalogDao.MedicationCatalogRecord catalogItem = null;
        String medicationName = trim(request.name);
        if (request.catalogMedicationId != null && request.catalogMedicationId > 0) {
            catalogItem = catalogDao.findCatalogItemById(request.catalogMedicationId)
                    .orElseThrow(() -> new IllegalArgumentException("Selected catalog medication does not exist."));
            medicationName = trim(catalogItem.getName());
            if (!catalogItem.isActive()) {
                throw new IllegalArgumentException("Selected catalog medication is inactive.");
            }
            if (!csvContains(catalogItem.getAllowedUnits(), request.doseUnit)) {
                throw new IllegalArgumentException("Dose unit is not allowed for the selected catalog medication.");
            }
            if (!csvContains(catalogItem.getAllowedRoutes(), request.route)) {
                throw new IllegalArgumentException("Route is not allowed for the selected catalog medication.");
            }
        }
        if (!patientDao.existsByPatientId(request.patientId)) {
            throw new IllegalArgumentException("Patient does not exist in SQLite: " + request.patientId);
        }
        String doseText = formatDose(doseAmount, request.doseUnit);
        if (request.active && medicationDao.hasDuplicateActiveMedication(request.patientId, medicationName, doseText, excludeMedicationId)) {
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
        SqliteMedicationCatalogDao.MedicationCatalogRecord catalogItem = findCatalogItemOrNull(request.catalogMedicationId);
        String medicationName = catalogItem == null ? trim(request.name) : trim(catalogItem.getName());
        Double doseAmount = parsePositiveDoseAmount(request.doseAmount);
        String doseUnit = trim(request.doseUnit);
        String doseText = formatDose(doseAmount, doseUnit);
        return new SqliteMedicationDao.MedicationRecord(
                id,
                trim(request.patientId),
                request.catalogMedicationId,
                medicationName,
                doseText,
                doseAmount,
                doseUnit,
                trim(request.route),
                trim(request.frequency),
                request.active
        );
    }

    private LocalDateTime parseDateTime(String value) {
        try {
            return LocalDateTime.parse(value.trim(), DISPLAY_DATE_TIME);
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

    private SqliteMedicationCatalogDao.MedicationCatalogRecord findCatalogItemOrNull(Long catalogMedicationId) {
        if (catalogMedicationId == null || catalogMedicationId <= 0) {
            return null;
        }
        try {
            return catalogDao.findCatalogItemById(catalogMedicationId).orElse(null);
        } catch (SQLException e) {
            return null;
        }
    }

    private Double parsePositiveDoseAmount(String value) {
        if (!hasText(value)) {
            return null;
        }
        try {
            double number = Double.parseDouble(value.trim());
            return number > 0 ? number : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String formatDose(Double amount, String unit) {
        if (amount == null) {
            return "";
        }
        String amountText = amount == Math.rint(amount) ? String.valueOf(amount.longValue()) : String.valueOf(amount);
        return hasText(unit) ? amountText + " " + unit.trim() : amountText;
    }

    private boolean csvContains(String csv, String value) {
        if (!hasText(csv) || !hasText(value)) {
            return false;
        }
        String normalized = value.trim().toUpperCase();
        for (String part : csv.split(",")) {
            if (part.trim().toUpperCase().equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    public static class MedicationRequest {
        private final long id;
        private final String patientId;
        private final Long catalogMedicationId;
        private final String name;
        private final String dose;
        private final String doseAmount;
        private final String doseUnit;
        private final String route;
        private final String frequency;
        private final boolean active;

        public MedicationRequest(long id, String patientId, String name, String dose, String route, String frequency, boolean active) {
            this(id, patientId, null, name, dose, parseDoseAmountText(dose), parseDoseUnitText(dose), route, frequency, active);
        }

        public MedicationRequest(long id, String patientId, Long catalogMedicationId, String name, String dose,
                                 String doseAmount, String doseUnit, String route, String frequency, boolean active) {
            this.id = id;
            this.patientId = patientId;
            this.catalogMedicationId = catalogMedicationId;
            this.name = name;
            this.dose = dose;
            this.doseAmount = doseAmount;
            this.doseUnit = doseUnit;
            this.route = route;
            this.frequency = frequency;
            this.active = active;
        }

        private static String parseDoseAmountText(String dose) {
            if (dose == null || dose.isBlank()) {
                return "";
            }
            return dose.trim().split("\\s+")[0];
        }

        private static String parseDoseUnitText(String dose) {
            if (dose == null || dose.isBlank()) {
                return "";
            }
            String trimmed = dose.trim();
            int firstSpace = trimmed.indexOf(' ');
            return firstSpace < 0 ? "" : trimmed.substring(firstSpace + 1).trim();
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
