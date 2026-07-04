package ui.javafx.pages.medications;

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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MedicationCatalogService {

    private final SqliteMedicationCatalogDao catalogDao;
    private final SqliteMedicationDao medicationDao;

    public MedicationCatalogService() {
        this(new SqliteMedicationCatalogDao(), new SqliteMedicationDao());
    }

    public MedicationCatalogService(SqliteMedicationCatalogDao catalogDao) {
        this(catalogDao, new SqliteMedicationDao());
    }

    public MedicationCatalogService(SqliteMedicationCatalogDao catalogDao, SqliteMedicationDao medicationDao) {
        this.catalogDao = catalogDao;
        this.medicationDao = medicationDao;
    }

    public List<SqliteMedicationCatalogDao.MedicationCatalogRecord> searchMedicationsByName(String text) throws SQLException {
        return catalogDao.searchCatalog(text, true);
    }

    public SqliteMedicationCatalogDao.MedicationCatalogRecord getMedicationCatalogItem(long id) throws SQLException {
        return catalogDao.findCatalogItemById(id)
                .orElseThrow(() -> new IllegalArgumentException("Medication catalog item not found: " + id));
    }

    public long createCatalogMedication(User currentUser, CatalogMedicationRequest request) throws SQLException {
        requireManagePermission(currentUser);
        validateCatalogMedication(request, 0);
        long id = catalogDao.insertCatalogItem(toRecord(0, request, request.active));
        AuditWriteHelper.write(username(currentUser), AuditAction.CREATE_CATALOG_MEDICATION,
                "medication=" + request.name + ", form_type=" + request.formType);
        return id;
    }

    public void updateCatalogMedication(User currentUser, long id, CatalogMedicationRequest request) throws SQLException {
        requireManagePermission(currentUser);
        if (id <= 0) {
            throw new IllegalArgumentException("Catalog medication ID is required.");
        }
        validateCatalogMedication(request, id);
        catalogDao.updateCatalogItem(toRecord(id, request, request.active));
        AuditWriteHelper.write(username(currentUser), AuditAction.UPDATE_CATALOG_MEDICATION,
                "medication_id=" + id + ", medication=" + request.name + ", form_type=" + request.formType);
    }

    public void deactivateCatalogMedication(User currentUser, long id) throws SQLException {
        requireManagePermission(currentUser);
        SqliteMedicationCatalogDao.MedicationCatalogRecord existing = getMedicationCatalogItem(id);
        SqliteMedicationCatalogDao.MedicationCatalogRecord inactive = new SqliteMedicationCatalogDao.MedicationCatalogRecord(
                existing.getId(),
                existing.getName(),
                existing.getFormType(),
                existing.getDefaultRoute(),
                existing.getDefaultFrequency(),
                existing.getDefaultUnit(),
                existing.getAllowedUnits(),
                existing.getAllowedRoutes(),
                existing.getMinSingleDose(),
                existing.getMaxSingleDose(),
                existing.getMaxDailyDose(),
                existing.getMinIntervalMinutes(),
                existing.getMinIntervalHours(),
                existing.isRequiresDoctorOverride(),
                existing.getDangerNotes(),
                existing.getNotes(),
                false,
                existing.getCreatedAt(),
                existing.getUpdatedAt()
        );
        catalogDao.updateCatalogItem(inactive);
        AuditWriteHelper.write(username(currentUser), AuditAction.DEACTIVATE_CATALOG_MEDICATION,
                "medication_id=" + id + ", medication=" + existing.getName());
    }

    public List<String> getAllowedUnitsForFormType(String formType) {
        String normalized = normalizeFormType(formType);
        if ("TABLET".equals(normalized)) {
            return List.of("mg", "mcg", "tablet");
        }
        if ("CAPSULE".equals(normalized)) {
            return List.of("mg", "mcg", "capsule");
        }
        if ("LIQUID".equals(normalized)) {
            return List.of("mg", "mL", "mg/mL");
        }
        if ("INJECTION".equals(normalized)) {
            return List.of("mg", "mL", "units");
        }
        if ("INHALER".equals(normalized)) {
            return List.of("mcg", "puff");
        }
        if ("CREAM".equals(normalized)) {
            return List.of("g", "%");
        }
        if ("DROPS".equals(normalized)) {
            return List.of("drop", "mL");
        }
        return List.of("mg", "mL", "unit");
    }

    public List<String> getAllowedRoutesForFormType(String formType) {
        String normalized = normalizeFormType(formType);
        if ("TABLET".equals(normalized) || "CAPSULE".equals(normalized) || "LIQUID".equals(normalized)) {
            return List.of("Oral");
        }
        if ("INJECTION".equals(normalized)) {
            return List.of("IV", "IM", "SC");
        }
        if ("INHALER".equals(normalized)) {
            return List.of("Inhalation");
        }
        if ("CREAM".equals(normalized)) {
            return List.of("Topical");
        }
        if ("DROPS".equals(normalized)) {
            return List.of("Oral", "Topical", "Other");
        }
        return List.of("Oral", "IV", "IM", "SC", "Inhalation", "Topical", "Other");
    }

    public long createInteractionRule(User currentUser, InteractionRuleRequest request) throws SQLException {
        requireManagePermission(currentUser);
        validateInteractionRule(request);
        SqliteMedicationCatalogDao.MedicationCatalogRecord medicationA = getMedicationCatalogItem(request.medicationAId);
        SqliteMedicationCatalogDao.MedicationCatalogRecord medicationB = getMedicationCatalogItem(request.medicationBId);
        long id = catalogDao.insertInteraction(new SqliteMedicationCatalogDao.MedicationInteractionRecord(
                0,
                medicationA.getId(),
                medicationB.getId(),
                medicationA.getName(),
                medicationB.getName(),
                normalizeSeverity(request.severity),
                Math.max(0, request.minWaitMinutes),
                trim(request.notes),
                trim(request.notes),
                request.active,
                "",
                ""
        ));
        return id;
    }

    public void deactivateInteractionRule(User currentUser, long interactionId) throws SQLException {
        requireManagePermission(currentUser);
        catalogDao.deactivateInteraction(interactionId);
    }

    public List<SqliteMedicationCatalogDao.MedicationInteractionRecord> getInteractionsForMedication(long catalogMedicationId) throws SQLException {
        return catalogDao.findInteractionsForMedication(catalogMedicationId);
    }

    public List<SqliteMedicationCatalogDao.MedicationInteractionRecord> listActiveInteractions() throws SQLException {
        return catalogDao.listActiveInteractions();
    }

    public List<InteractionCheckResult> checkMedicationInteractions(String patientId,
                                                                    SqliteMedicationDao.MedicationRecord selectedMedication) throws SQLException {
        return checkMedicationInteractions(patientId, selectedMedication, LocalDateTime.now());
    }

    public List<InteractionCheckResult> checkMedicationInteractions(String patientId,
                                                                    SqliteMedicationDao.MedicationRecord selectedMedication,
                                                                    LocalDateTime givenAt) throws SQLException {
        ArrayList<InteractionCheckResult> results = new ArrayList<>();
        if (selectedMedication == null || selectedMedication.getCatalogMedicationId() == null
                || selectedMedication.getCatalogMedicationId() <= 0) {
            return results;
        }
        Set<Long> seenInteractionIds = new HashSet<>();
        for (SqliteMedicationDao.MedicationEventRecord event : medicationDao.findRecentMedicationEventsForPatient(patientId, 100)) {
            if (!"GIVEN".equalsIgnoreCase(trim(event.getStatus())) || event.getMedicationId() == selectedMedication.getId()) {
                continue;
            }
            SqliteMedicationDao.MedicationRecord otherMedication = medicationDao.findMedicationById(event.getMedicationId()).orElse(null);
            if (otherMedication == null || otherMedication.getCatalogMedicationId() == null || otherMedication.getCatalogMedicationId() <= 0) {
                continue;
            }
            SqliteMedicationCatalogDao.MedicationInteractionRecord interaction = catalogDao
                    .findActiveInteractionBetween(selectedMedication.getCatalogMedicationId(), otherMedication.getCatalogMedicationId())
                    .orElse(null);
            if (interaction == null || seenInteractionIds.contains(interaction.getId())) {
                continue;
            }
            LocalDateTime eventTime = parseDateTime(event.getGivenAt());
            long elapsed = Math.max(0, Duration.between(eventTime, givenAt).toMinutes());
            if (interaction.getMinWaitMinutes() > 0 && elapsed >= interaction.getMinWaitMinutes()) {
                continue;
            }
            seenInteractionIds.add(interaction.getId());
            results.add(new InteractionCheckResult(
                    interaction,
                    otherMedication,
                    event.getGivenAt(),
                    elapsed,
                    interactionMessage(interaction, otherMedication, event.getGivenAt(), elapsed)
            ));
        }
        return results;
    }

    public void validateCatalogMedication(CatalogMedicationRequest request, long excludeId) throws SQLException {
        if (request == null) {
            throw new IllegalArgumentException("Medication catalog request is required.");
        }
        FormValidationHelper.ValidationResult validation = FormValidationHelper.combine(
                FormValidationHelper.validateRequired("Medication name", request.name),
                FormValidationHelper.validateRequired("Form type", request.formType),
                FormValidationHelper.validateRequired("Default unit", request.defaultUnit),
                FormValidationHelper.validateRequired("Allowed units", request.allowedUnits),
                FormValidationHelper.validateRequired("Allowed routes", request.allowedRoutes),
                FormValidationHelper.validateMaxLength("Medication name", request.name, 120),
                FormValidationHelper.validateMaxLength("Allowed units", request.allowedUnits, 180),
                FormValidationHelper.validateMaxLength("Allowed routes", request.allowedRoutes, 180),
                FormValidationHelper.validateMaxLength("Danger notes", request.dangerNotes, 500)
        );
        if (!validation.isValid()) {
            throw new IllegalArgumentException(validation.getMessage());
        }
        String formType = normalizeFormType(request.formType);
        if (!FORM_TYPES.contains(formType)) {
            throw new IllegalArgumentException("Form type must be selected from the approved catalog form list.");
        }
        if (catalogDao.catalogNameExists(request.name, excludeId)) {
            throw new IllegalArgumentException("Medication catalog name already exists.");
        }

        Double minSingle = parseOptionalPositive("Min single dose", request.minSingleDose, true);
        Double maxSingle = parseOptionalPositive("Max single dose", request.maxSingleDose, true);
        Double maxDaily = parseOptionalPositive("Max daily dose", request.maxDailyDose, true);
        Double minInterval = parseOptionalPositive("Minimum interval minutes", request.minimumIntervalMinutes, false);
        if (minSingle != null && maxSingle != null && maxSingle < minSingle) {
            throw new IllegalArgumentException("Max single dose cannot be less than min single dose.");
        }
        if (maxSingle != null && maxDaily != null && maxDaily < maxSingle) {
            throw new IllegalArgumentException("Max daily dose cannot be less than max single dose.");
        }
        if (minInterval != null && minInterval < 0) {
            throw new IllegalArgumentException("Minimum interval cannot be negative.");
        }
    }

    private SqliteMedicationCatalogDao.MedicationCatalogRecord toRecord(long id, CatalogMedicationRequest request, boolean active) {
        Double minIntervalMinutes = parseOptionalDouble(request.minimumIntervalMinutes);
        Double minIntervalHours = minIntervalMinutes == null ? null : minIntervalMinutes / 60.0;
        return new SqliteMedicationCatalogDao.MedicationCatalogRecord(
                id,
                trim(request.name),
                normalizeFormType(request.formType),
                firstCsvValue(request.allowedRoutes),
                "",
                trim(request.defaultUnit),
                trim(request.allowedUnits),
                trim(request.allowedRoutes),
                parseOptionalDouble(request.minSingleDose),
                parseOptionalDouble(request.maxSingleDose),
                parseOptionalDouble(request.maxDailyDose),
                minIntervalMinutes,
                minIntervalHours,
                request.requiresDoctorOverride,
                trim(request.dangerNotes),
                trim(request.dangerNotes),
                active,
                "",
                ""
        );
    }

    private void validateInteractionRule(InteractionRuleRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Interaction rule request is required.");
        }
        if (request.medicationAId <= 0 || request.medicationBId <= 0) {
            throw new IllegalArgumentException("Both medications are required for an interaction rule.");
        }
        if (request.medicationAId == request.medicationBId) {
            throw new IllegalArgumentException("Interaction rule medications must be different.");
        }
        normalizeSeverity(request.severity);
        if (request.minWaitMinutes < 0) {
            throw new IllegalArgumentException("Minimum wait minutes cannot be negative.");
        }
        if (!hasText(request.notes)) {
            throw new IllegalArgumentException("Interaction notes are required.");
        }
    }

    private String normalizeSeverity(String severity) {
        String normalized = hasText(severity) ? severity.trim().toUpperCase(Locale.ROOT) : "WARNING";
        if (!"WARNING".equals(normalized) && !"DANGEROUS".equals(normalized)) {
            throw new IllegalArgumentException("Interaction severity must be WARNING or DANGEROUS.");
        }
        return normalized;
    }

    private String interactionMessage(SqliteMedicationCatalogDao.MedicationInteractionRecord interaction,
                                      SqliteMedicationDao.MedicationRecord otherMedication,
                                      String lastGivenAt,
                                      long elapsedMinutes) {
        String waitText = interaction.getMinWaitMinutes() > 0
                ? " Minimum wait: " + interaction.getMinWaitMinutes() + " minutes; elapsed: " + elapsedMinutes + " minutes."
                : "";
        return interaction.getSeverity() + " interaction with " + otherMedication.getName()
                + " last given at " + lastGivenAt + "." + waitText + " " + interaction.getMessage();
    }

    private LocalDateTime parseDateTime(String value) {
        if (!hasText(value)) {
            return LocalDateTime.MIN;
        }
        try {
            return LocalDateTime.parse(value.trim(), DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"));
        } catch (DateTimeParseException e) {
            try {
                return LocalDateTime.parse(value.trim(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            } catch (DateTimeParseException ignored) {
                return LocalDateTime.parse(value.trim().replace(" ", "T"));
            }
        }
    }

    private void requireManagePermission(User currentUser) {
        if (!PermissionHelper.canManageMedicationCatalog(currentUser)) {
            throw new SecurityException("Only Admin and Doctor users can manage medication catalog entries.");
        }
    }

    private Double parseOptionalPositive(String label, String value, boolean strictPositive) {
        if (!hasText(value)) {
            return null;
        }
        try {
            double number = Double.parseDouble(value.trim());
            if (strictPositive && number <= 0) {
                throw new IllegalArgumentException(label + " must be positive when provided.");
            }
            if (!strictPositive && number < 0) {
                throw new IllegalArgumentException(label + " cannot be negative.");
            }
            return number;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(label + " must be numeric.");
        }
    }

    private Double parseOptionalDouble(String value) {
        return hasText(value) ? Double.parseDouble(value.trim()) : null;
    }

    private String firstCsvValue(String value) {
        if (!hasText(value)) {
            return "";
        }
        String[] parts = value.split(",");
        return parts.length == 0 ? "" : parts[0].trim();
    }

    private String normalizeFormType(String formType) {
        return hasText(formType) ? formType.trim().toUpperCase(Locale.ROOT) : "OTHER";
    }

    private String username(User currentUser) {
        return currentUser == null || currentUser.getUsername() == null || currentUser.getUsername().isBlank()
                ? "Unknown"
                : currentUser.getUsername();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static final List<String> FORM_TYPES = List.of(
            "TABLET", "CAPSULE", "LIQUID", "INJECTION", "INHALER", "CREAM", "DROPS", "OTHER"
    );

    public static class CatalogMedicationRequest {
        private final String name;
        private final String formType;
        private final String defaultUnit;
        private final String allowedUnits;
        private final String allowedRoutes;
        private final String minSingleDose;
        private final String maxSingleDose;
        private final String maxDailyDose;
        private final String minimumIntervalMinutes;
        private final boolean requiresDoctorOverride;
        private final String dangerNotes;
        private final boolean active;

        public CatalogMedicationRequest(String name, String formType, String defaultUnit, String allowedUnits,
                                        String allowedRoutes, String minSingleDose, String maxSingleDose,
                                        String maxDailyDose, String minimumIntervalMinutes,
                                        boolean requiresDoctorOverride, String dangerNotes, boolean active) {
            this.name = name;
            this.formType = formType;
            this.defaultUnit = defaultUnit;
            this.allowedUnits = allowedUnits;
            this.allowedRoutes = allowedRoutes;
            this.minSingleDose = minSingleDose;
            this.maxSingleDose = maxSingleDose;
            this.maxDailyDose = maxDailyDose;
            this.minimumIntervalMinutes = minimumIntervalMinutes;
            this.requiresDoctorOverride = requiresDoctorOverride;
            this.dangerNotes = dangerNotes;
            this.active = active;
        }
    }

    public static class InteractionRuleRequest {
        private final long medicationAId;
        private final long medicationBId;
        private final String severity;
        private final int minWaitMinutes;
        private final String notes;
        private final boolean active;

        public InteractionRuleRequest(long medicationAId, long medicationBId, String severity,
                                      int minWaitMinutes, String notes, boolean active) {
            this.medicationAId = medicationAId;
            this.medicationBId = medicationBId;
            this.severity = severity;
            this.minWaitMinutes = minWaitMinutes;
            this.notes = notes;
            this.active = active;
        }
    }

    public static class InteractionCheckResult {
        private final SqliteMedicationCatalogDao.MedicationInteractionRecord interaction;
        private final SqliteMedicationDao.MedicationRecord interactingMedication;
        private final String lastGivenAt;
        private final long elapsedMinutes;
        private final String message;

        public InteractionCheckResult(SqliteMedicationCatalogDao.MedicationInteractionRecord interaction,
                                      SqliteMedicationDao.MedicationRecord interactingMedication,
                                      String lastGivenAt, long elapsedMinutes, String message) {
            this.interaction = interaction;
            this.interactingMedication = interactingMedication;
            this.lastGivenAt = lastGivenAt;
            this.elapsedMinutes = elapsedMinutes;
            this.message = message;
        }

        public SqliteMedicationCatalogDao.MedicationInteractionRecord getInteraction() { return interaction; }
        public SqliteMedicationDao.MedicationRecord getInteractingMedication() { return interactingMedication; }
        public String getLastGivenAt() { return lastGivenAt; }
        public long getElapsedMinutes() { return elapsedMinutes; }
        public String getMessage() { return message; }
        public boolean isDangerous() { return "DANGEROUS".equalsIgnoreCase(interaction.getSeverity()); }
    }
}
