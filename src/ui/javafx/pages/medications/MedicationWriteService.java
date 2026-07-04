package ui.javafx.pages.medications;

import pages.patient.dao.SqlitePatientDao;
import ui.javafx.pages.audit_logs.AuditAction;
import ui.javafx.pages.audit_logs.AuditWriteHelper;
import app.helpers.FormValidationHelper;
import app.helpers.PermissionHelper;
import pages.user.User;

import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
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
    private final MedicationCatalogService catalogService;

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
        this.catalogService = new MedicationCatalogService(catalogDao, medicationDao);
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
        SqliteMedicationDao.MedicationRecord medication = medicationDao.findMedicationById(request.medicationId)
                .orElseThrow(() -> new IllegalArgumentException("Medication not found in SQLite: " + request.medicationId));
        if (!medication.isActive()) {
            throw new IllegalArgumentException("Cannot record administration for inactive/discontinued medication.");
        }
        String safetyStatus = validateMedicationEvent(request, medication, currentUser);

        String status = normalizeEventStatus(request.status);
        Double givenAmount = parsePositiveDoseAmount(request.givenAmount);
        String notes = "Status: " + status + (hasText(request.notes) ? " | " + request.notes.trim() : "");
        medicationDao.insertMedicationEvent(new SqliteMedicationDao.MedicationEventRecord(
                0,
                request.medicationId,
                medication.getPatientId(),
                username(currentUser),
                parseDateTime(request.givenAt).format(DISPLAY_DATE_TIME),
                notes,
                status,
                givenAmount,
                trim(request.givenUnit),
                trim(request.route),
                request.overrideUsed,
                trim(request.overrideReason),
                safetyStatus,
                ""
        ));
        AuditWriteHelper.write(username(currentUser), AuditAction.GIVE_MEDICATION,
                "patient_id=" + medication.getPatientId() + ", medication=" + medication.getName()
                        + ", given=" + formatDose(givenAmount, request.givenUnit) + ", status=" + status);
    }

    public List<SqliteMedicationDao.MedicationRecord> findActiveMedicationsForPatient(String patientId) throws SQLException {
        return medicationDao.findActiveMedicationsForPatient(patientId);
    }

    public Optional<SqliteMedicationDao.MedicationRecord> findMedicationById(long medicationId) throws SQLException {
        return medicationDao.findMedicationById(medicationId);
    }

    public MedicationSafetyContext getMedicationSafetyContext(long medicationId) throws SQLException {
        SqliteMedicationDao.MedicationRecord medication = medicationDao.findMedicationById(medicationId)
                .orElseThrow(() -> new IllegalArgumentException("Medication not found in SQLite: " + medicationId));
        SqliteMedicationCatalogDao.MedicationCatalogRecord catalog = findCatalogItemOrNull(medication.getCatalogMedicationId());
        List<SqliteMedicationDao.MedicationEventRecord> recent =
                medicationDao.findRecentMedicationEvents(medication.getPatientId(), medicationId, 1);
        SqliteMedicationDao.MedicationEventRecord latest = recent.isEmpty() ? null : recent.get(0);
        return new MedicationSafetyContext(
                medication,
                catalog,
                latest == null ? "" : latest.getGivenAt(),
                catalog == null ? null : catalog.getMaxSingleDose(),
                catalog == null ? null : catalog.getMaxDailyDose(),
                catalog == null ? null : catalog.getMinIntervalMinutes()
        );
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

    private String validateMedicationEvent(MedicationEventRequest request, SqliteMedicationDao.MedicationRecord medication,
                                           User currentUser) throws SQLException {
        FormValidationHelper.ValidationResult validation = FormValidationHelper.combine(
                FormValidationHelper.validateRequired("Medication", String.valueOf(request.medicationId)),
                FormValidationHelper.validateDateTime("Given time", request.givenAt),
                FormValidationHelper.validateRequired("Given amount", request.givenAmount),
                FormValidationHelper.validateRequired("Given unit", request.givenUnit),
                FormValidationHelper.validateRequired("Route", request.route),
                FormValidationHelper.validateMaxLength("Notes", request.notes, 300)
        );
        if (!validation.isValid()) {
            throw new IllegalArgumentException(validation.getMessage());
        }
        SqlitePatientDao.PatientDetail patient = patientDao.findDetailById(medication.getPatientId())
                .orElseThrow(() -> new IllegalArgumentException("Patient does not exist in SQLite: " + medication.getPatientId()));
        if ("DECEASED".equalsIgnoreCase(trim(patient.getStatus()))) {
            throw new IllegalArgumentException("Cannot record medication administration for a deceased patient.");
        }
        LocalDateTime givenAt = parseDateTime(request.givenAt);
        if (givenAt.isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("Given time cannot be in the future.");
        }
        normalizeEventStatus(request.status);
        if (!ROUTES.contains(trim(request.route))) {
            throw new IllegalArgumentException("Route must be selected from the approved route list.");
        }
        Double givenAmount = parsePositiveDoseAmount(request.givenAmount);
        if (givenAmount == null) {
            throw new IllegalArgumentException("Given amount is required and must be a positive number.");
        }
        if (!hasText(request.givenUnit)) {
            throw new IllegalArgumentException("Given unit is required.");
        }

        SqliteMedicationCatalogDao.MedicationCatalogRecord catalogItem = findCatalogItemOrNull(medication.getCatalogMedicationId());
        if (catalogItem != null) {
            if (!csvContains(catalogItem.getAllowedUnits(), request.givenUnit)) {
                throw new IllegalArgumentException("Given unit is not allowed for this medication catalog item.");
            }
            if (!csvContains(catalogItem.getAllowedRoutes(), request.route)) {
                throw new IllegalArgumentException("Route is not allowed for this medication catalog item.");
            }
        } else if (hasText(medication.getDoseUnit()) && !trim(medication.getDoseUnit()).equalsIgnoreCase(trim(request.givenUnit))) {
            throw new IllegalArgumentException("Given unit must match the medication order unit.");
        }

        ArrayList<SafetyViolation> violations = evaluateSafetyViolations(request, medication, catalogItem, givenAt, givenAmount);
        if (violations.isEmpty()) {
            return evaluateInteractionSafety(request, medication, currentUser, givenAt);
        }
        boolean canOverride = PermissionHelper.canAddMedication(currentUser);
        if (!request.overrideUsed || !canOverride || !hasText(request.overrideReason)) {
            for (SafetyViolation violation : violations) {
                auditSafetyDecision(currentUser, medication, request, violation, false);
            }
            throw new IllegalArgumentException(violations.get(0).message);
        }
        for (SafetyViolation violation : violations) {
            auditSafetyDecision(currentUser, medication, request, violation, true);
        }
        evaluateInteractionSafety(request, medication, currentUser, givenAt);
        return "OVERRIDE";
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
            try {
                return LocalDateTime.parse(value.trim(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            } catch (DateTimeParseException ignored) {
                return LocalDateTime.parse(value.trim().replace(" ", "T"));
            }
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

    private ArrayList<SafetyViolation> evaluateSafetyViolations(MedicationEventRequest request,
                                                                SqliteMedicationDao.MedicationRecord medication,
                                                                SqliteMedicationCatalogDao.MedicationCatalogRecord catalogItem,
                                                                LocalDateTime givenAt,
                                                                Double givenAmount) throws SQLException {
        ArrayList<SafetyViolation> violations = new ArrayList<>();
        if (catalogItem == null) {
            return violations;
        }
        String unit = trim(request.givenUnit);
        if (catalogItem.getMaxSingleDose() != null && givenAmount > catalogItem.getMaxSingleDose()) {
            violations.add(new SafetyViolation(
                    SafetyType.DOSE,
                    "Given dose exceeds max single dose. Ordered medication: " + medication.getName()
                            + ". Given: " + formatDose(givenAmount, unit)
                            + ". Max single dose: " + formatDose(catalogItem.getMaxSingleDose(), unit) + "."
            ));
        }
        if (catalogItem.getMaxDailyDose() != null) {
            double todayTotal = todayAdministeredTotal(medication, givenAt, unit);
            double projectedTotal = todayTotal + givenAmount;
            if (projectedTotal > catalogItem.getMaxDailyDose()) {
                violations.add(new SafetyViolation(
                        SafetyType.DOSE,
                        "Given dose exceeds max daily dose. Today so far: " + formatDose(todayTotal, unit)
                                + ". New dose: " + formatDose(givenAmount, unit)
                                + ". Projected total: " + formatDose(projectedTotal, unit)
                                + ". Max daily dose: " + formatDose(catalogItem.getMaxDailyDose(), unit) + "."
                ));
            }
        }
        if (catalogItem.getMinIntervalMinutes() != null && catalogItem.getMinIntervalMinutes() > 0) {
            SqliteMedicationDao.MedicationEventRecord latest = latestGivenEventBefore(medication, givenAt);
            if (latest != null) {
                LocalDateTime latestTime = parseDateTime(latest.getGivenAt());
                long elapsed = Duration.between(latestTime, givenAt).toMinutes();
                long required = Math.round(catalogItem.getMinIntervalMinutes());
                if (elapsed < required) {
                    long remaining = Math.max(0, required - elapsed);
                    violations.add(new SafetyViolation(
                            SafetyType.INTERVAL,
                            "Medication was given too recently. Last given: " + latest.getGivenAt()
                                    + ". Minimum interval: " + required + " minutes. Remaining wait time: "
                                    + remaining + " minutes."
                    ));
                }
            }
        }
        return violations;
    }

    private double todayAdministeredTotal(SqliteMedicationDao.MedicationRecord medication, LocalDateTime givenAt, String unit) throws SQLException {
        double total = 0;
        for (SqliteMedicationDao.MedicationEventRecord event :
                medicationDao.findMedicationEventsForPatientMedication(medication.getPatientId(), medication.getId())) {
            if (!"GIVEN".equalsIgnoreCase(trim(event.getStatus()))) {
                continue;
            }
            if (!trim(event.getGivenUnit()).equalsIgnoreCase(unit)) {
                continue;
            }
            LocalDateTime eventTime;
            try {
                eventTime = parseDateTime(event.getGivenAt());
            } catch (Exception e) {
                continue;
            }
            if (eventTime.toLocalDate().equals(givenAt.toLocalDate()) && event.getGivenAmount() != null) {
                total += event.getGivenAmount();
            }
        }
        return total;
    }

    private SqliteMedicationDao.MedicationEventRecord latestGivenEventBefore(SqliteMedicationDao.MedicationRecord medication,
                                                                             LocalDateTime givenAt) throws SQLException {
        SqliteMedicationDao.MedicationEventRecord latest = null;
        LocalDateTime latestTime = null;
        for (SqliteMedicationDao.MedicationEventRecord event :
                medicationDao.findMedicationEventsForPatientMedication(medication.getPatientId(), medication.getId())) {
            if (!"GIVEN".equalsIgnoreCase(trim(event.getStatus()))) {
                continue;
            }
            LocalDateTime eventTime;
            try {
                eventTime = parseDateTime(event.getGivenAt());
            } catch (Exception e) {
                continue;
            }
            if (eventTime.isAfter(givenAt)) {
                continue;
            }
            if (latestTime == null || eventTime.isAfter(latestTime)) {
                latestTime = eventTime;
                latest = event;
            }
        }
        return latest;
    }

    private void auditSafetyDecision(User currentUser, SqliteMedicationDao.MedicationRecord medication,
                                     MedicationEventRequest request, SafetyViolation violation, boolean override) throws SQLException {
        String action;
        if (violation.type == SafetyType.INTERVAL) {
            action = override ? AuditAction.MEDICATION_INTERVAL_OVERRIDE : AuditAction.MEDICATION_INTERVAL_BLOCKED;
        } else {
            action = override ? AuditAction.MEDICATION_DOSE_OVERRIDE : AuditAction.MEDICATION_DOSE_BLOCKED;
        }
        String detail = "patient_id=" + medication.getPatientId()
                + ", medication=" + medication.getName()
                + ", given=" + formatDose(parsePositiveDoseAmount(request.givenAmount), request.givenUnit)
                + (override ? ", reason=" + trim(request.overrideReason) : "");
        AuditWriteHelper.write(username(currentUser), action, truncate(detail, 220));
    }

    private String evaluateInteractionSafety(MedicationEventRequest request,
                                             SqliteMedicationDao.MedicationRecord medication,
                                             User currentUser,
                                             LocalDateTime givenAt) throws SQLException {
        List<MedicationCatalogService.InteractionCheckResult> interactions =
                catalogService.checkMedicationInteractions(medication.getPatientId(), medication, givenAt);
        if (interactions.isEmpty()) {
            return request.overrideUsed ? "OVERRIDE" : "OK";
        }

        boolean hasWarning = false;
        for (MedicationCatalogService.InteractionCheckResult interaction : interactions) {
            if (!interaction.isDangerous()) {
                hasWarning = true;
                auditInteractionDecision(currentUser, medication, request, interaction, AuditAction.MEDICATION_INTERACTION_WARNING);
            }
        }

        MedicationCatalogService.InteractionCheckResult dangerous = null;
        for (MedicationCatalogService.InteractionCheckResult interaction : interactions) {
            if (interaction.isDangerous()) {
                dangerous = interaction;
                break;
            }
        }
        if (dangerous == null) {
            return hasWarning ? "WARNING" : (request.overrideUsed ? "OVERRIDE" : "OK");
        }

        boolean canOverride = PermissionHelper.canAddMedication(currentUser);
        if (!request.overrideUsed || !canOverride || !hasText(request.overrideReason)) {
            auditInteractionDecision(currentUser, medication, request, dangerous, AuditAction.MEDICATION_INTERACTION_BLOCKED);
            throw new IllegalArgumentException(dangerous.getMessage());
        }
        auditInteractionDecision(currentUser, medication, request, dangerous, AuditAction.MEDICATION_INTERACTION_OVERRIDE);
        return "OVERRIDE";
    }

    private void auditInteractionDecision(User currentUser,
                                          SqliteMedicationDao.MedicationRecord medication,
                                          MedicationEventRequest request,
                                          MedicationCatalogService.InteractionCheckResult interaction,
                                          String action) throws SQLException {
        String detail = "patient_id=" + medication.getPatientId()
                + ", medication=" + medication.getName()
                + ", interacting=" + interaction.getInteractingMedication().getName()
                + ", severity=" + interaction.getInteraction().getSeverity()
                + ", wait=" + interaction.getInteraction().getMinWaitMinutes()
                + (AuditAction.MEDICATION_INTERACTION_OVERRIDE.equals(action) ? ", reason=" + trim(request.overrideReason) : "");
        AuditWriteHelper.write(username(currentUser), action, truncate(detail, 220));
    }

    private enum SafetyType {
        DOSE,
        INTERVAL
    }

    private static class SafetyViolation {
        private final SafetyType type;
        private final String message;

        private SafetyViolation(SafetyType type, String message) {
            this.type = type;
            this.message = message;
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 3)) + "...";
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
        private final String givenAmount;
        private final String givenUnit;
        private final String route;
        private final boolean overrideUsed;
        private final String overrideReason;

        public MedicationEventRequest(long medicationId, String givenAt, String status, String notes) {
            this(medicationId, givenAt, status, notes, "", "", "", false, "");
        }

        public MedicationEventRequest(long medicationId, String givenAt, String status, String notes,
                                      String givenAmount, String givenUnit, String route,
                                      boolean overrideUsed, String overrideReason) {
            this.medicationId = medicationId;
            this.givenAt = givenAt;
            this.status = status;
            this.notes = notes;
            this.givenAmount = givenAmount;
            this.givenUnit = givenUnit;
            this.route = route;
            this.overrideUsed = overrideUsed;
            this.overrideReason = overrideReason;
        }
    }

    public static class MedicationSafetyContext {
        private final SqliteMedicationDao.MedicationRecord medication;
        private final SqliteMedicationCatalogDao.MedicationCatalogRecord catalog;
        private final String lastGivenAt;
        private final Double maxSingleDose;
        private final Double maxDailyDose;
        private final Double minimumIntervalMinutes;

        public MedicationSafetyContext(SqliteMedicationDao.MedicationRecord medication,
                                       SqliteMedicationCatalogDao.MedicationCatalogRecord catalog,
                                       String lastGivenAt, Double maxSingleDose, Double maxDailyDose,
                                       Double minimumIntervalMinutes) {
            this.medication = medication;
            this.catalog = catalog;
            this.lastGivenAt = lastGivenAt;
            this.maxSingleDose = maxSingleDose;
            this.maxDailyDose = maxDailyDose;
            this.minimumIntervalMinutes = minimumIntervalMinutes;
        }

        public SqliteMedicationDao.MedicationRecord getMedication() { return medication; }
        public SqliteMedicationCatalogDao.MedicationCatalogRecord getCatalog() { return catalog; }
        public String getLastGivenAt() { return lastGivenAt; }
        public Double getMaxSingleDose() { return maxSingleDose; }
        public Double getMaxDailyDose() { return maxDailyDose; }
        public Double getMinimumIntervalMinutes() { return minimumIntervalMinutes; }
    }
}
