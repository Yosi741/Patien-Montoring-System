package pages.patient.vitals_entry;

import pages.alert.SqliteAlertDao;
import pages.patient.patient_detail.SqlitePatientDao;
import app.helpers.FormValidationHelper;
import app.helpers.PermissionHelper;
import pages.alert.AlertPersistenceService;
import pages.notification.NotificationCenterService;
import pages.user.User;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

public class VitalsWriteService {

    private static final DateTimeFormatter DISPLAY_DATE_TIME = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
    private static final int STABLE_READINGS_REQUIRED = 2;
    private static final int STABLE_MINUTES_REQUIRED = 30;

    private final SqliteVitalReadingDao vitalReadingDao;
    private final SqlitePatientDao patientDao;
    private final SqliteAlertDao alertDao;
    private final VitalThresholdService thresholdService;

    public VitalsWriteService() {
        this(new SqliteVitalReadingDao(), new SqlitePatientDao(), new SqliteAlertDao(), new VitalThresholdService());
    }

    public VitalsWriteService(SqliteVitalReadingDao vitalReadingDao, SqlitePatientDao patientDao, VitalThresholdService thresholdService) {
        this(vitalReadingDao, patientDao, new SqliteAlertDao(), thresholdService);
    }

    public VitalsWriteService(SqliteVitalReadingDao vitalReadingDao, SqlitePatientDao patientDao,
                              SqliteAlertDao alertDao, VitalThresholdService thresholdService) {
        this.vitalReadingDao = vitalReadingDao;
        this.patientDao = patientDao;
        this.alertDao = alertDao;
        this.thresholdService = thresholdService;
    }

    public VitalsWriteResult enterVitalReading(User currentUser, VitalsEntryRequest request) throws SQLException {
        if (!PermissionHelper.canEnterVitals(currentUser)) {
            throw new SecurityException("Only Admin, Doctor, and Nurse users can enter vitals.");
        }
        validate(request);

        String staffUser = currentUser == null ? "Unknown" : currentUser.getUsername();
        LocalDateTime recordedAt = parseDateTime(request.recordedAt);
        String recordedAtText = recordedAt.format(DISPLAY_DATE_TIME);
        String normalizedType = VitalTypeCatalog.normalize(request.vitalType);
        String unit = VitalTypeCatalog.expectedUnit(normalizedType);
        SqlitePatientDao.PatientDetail patient = patientDao.findDetailById(request.patientId)
                .orElseThrow(() -> new IllegalArgumentException("Patient does not exist in SQLite: " + request.patientId));
        String birthDate = patient.getBirthDate();

        VitalThresholdService.VitalStatus status;
        if (VitalTypeCatalog.BLOOD_PRESSURE.equals(normalizedType)) {
            double systolic = parseNumber(request.value);
            double diastolic = parseNumber(request.secondValue);
            VitalThresholdService.VitalStatus systolicStatus = thresholdService.evaluate(VitalTypeCatalog.SYSTOLIC_PRESSURE, systolic, birthDate);
            VitalThresholdService.VitalStatus diastolicStatus = thresholdService.evaluate(VitalTypeCatalog.DIASTOLIC_PRESSURE, diastolic, birthDate);
            status = worse(systolicStatus, diastolicStatus);
            vitalReadingDao.insertVitalReading(record(request.patientId, VitalTypeCatalog.SYSTOLIC_PRESSURE, request.value, unit, recordedAtText, staffUser));
            vitalReadingDao.insertVitalReading(record(request.patientId, VitalTypeCatalog.DIASTOLIC_PRESSURE, request.secondValue, unit, recordedAtText, staffUser));
        } else {
            double value = parseNumber(request.value);
            status = thresholdService.evaluate(normalizedType, value, birthDate);
            vitalReadingDao.insertVitalReading(record(request.patientId, normalizedType, request.value, unit, recordedAtText, staffUser));
        }

        if (isAbnormal(status)) {
            String severity = status.name();
            AlertPersistenceService.persistAlert(
                    request.patientId,
                    severity,
                    "JavaFX manual vitals " + severity.toLowerCase(Locale.ROOT) + ": " + normalizedType
                            + " = " + displayValue(request) + " " + unit,
                    10
            );
            new NotificationCenterService().notifyCriticalAlert(
                    request.patientId,
                    severity,
                    "JavaFX manual vitals " + severity.toLowerCase(Locale.ROOT) + ": " + normalizedType
                            + " = " + displayValue(request) + " " + unit,
                    ""
            );
            syncPatientPriority(staffUser, request.patientId, severity, normalizedType, displayValue(request), unit);
        } else {
            attemptStablePriorityDowngrade(staffUser, patient, birthDate);
        }

        return new VitalsWriteResult(status, normalizedType, displayValue(request), unit, recordedAtText);
    }

    private void validate(VitalsEntryRequest request) throws SQLException {
        FormValidationHelper.ValidationResult validation = FormValidationHelper.combine(
                FormValidationHelper.validatePatientId(request.patientId),
                FormValidationHelper.validateRequired("Vital type", request.vitalType),
                FormValidationHelper.validateRequired("Value", request.value),
                FormValidationHelper.validateDateTime("Recorded time", request.recordedAt)
        );
        if (!validation.isValid()) {
            throw new IllegalArgumentException(validation.getMessage());
        }
        if (!patientDao.existsByPatientId(request.patientId)) {
            throw new IllegalArgumentException("Patient does not exist in SQLite: " + request.patientId);
        }
        LocalDateTime recordedAt = parseDateTime(request.recordedAt);
        if (recordedAt.isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("Recorded time cannot be in the future.");
        }

        String type = VitalTypeCatalog.normalize(request.vitalType);
        validateUnit(type, request.unit);
        if (VitalTypeCatalog.BLOOD_PRESSURE.equals(type)) {
            FormValidationHelper.ValidationResult bpValidation = FormValidationHelper.validateRequired("Diastolic value", request.secondValue);
            if (!bpValidation.isValid()) {
                throw new IllegalArgumentException(bpValidation.getMessage());
            }
            validateRange("Systolic pressure", request.value, 50, 260);
            validateRange("Diastolic pressure", request.secondValue, 30, 160);
            return;
        }

        switch (type) {
            case VitalTypeCatalog.HEART_RATE:
                validateRange("Heart rate", request.value, 20, 250);
                break;
            case VitalTypeCatalog.OXYGEN_SATURATION:
                validateRange("Oxygen saturation", request.value, 50, 100);
                break;
            case VitalTypeCatalog.TEMPERATURE:
                validateRange("Temperature", request.value, 30, 45);
                break;
            case VitalTypeCatalog.SYSTOLIC_PRESSURE:
                validateRange("Systolic pressure", request.value, 50, 260);
                break;
            case VitalTypeCatalog.DIASTOLIC_PRESSURE:
                validateRange("Diastolic pressure", request.value, 30, 160);
                break;
            case VitalTypeCatalog.SUGAR_LEVEL:
                validateRange("Sugar level", request.value, 20, 600);
                break;
            default:
                throw new IllegalArgumentException("Unsupported vital type: " + request.vitalType);
        }
    }

    private void validateRange(String label, String value, double min, double max) {
        FormValidationHelper.ValidationResult validation = FormValidationHelper.validateNumeric(label, value, min, max);
        if (!validation.isValid()) {
            throw new IllegalArgumentException(validation.getMessage());
        }
    }

    private void validateUnit(String vitalType, String unit) {
        String expected = VitalTypeCatalog.expectedUnit(vitalType);
        if (unit == null || unit.isBlank()) {
            return;
        }
        if (!expected.equals(unit.trim())) {
            throw new IllegalArgumentException("Unit for " + vitalType + " must be " + expected + ".");
        }
    }

    private VitalRecord record(String patientId, String vitalType, String value, String unit, String recordedAt, String staffUser) {
        return new VitalRecord("", patientId.trim(), vitalType, value.trim(), unit, recordedAt, "Manual", staffUser, "", "", "", "");
    }

    private LocalDateTime parseDateTime(String value) {
        try {
            return LocalDateTime.parse(value.trim(), DISPLAY_DATE_TIME);
        } catch (DateTimeParseException e) {
            return LocalDateTime.parse(value.trim().replace(" ", "T"));
        }
    }

    private double parseNumber(String value) {
        return Double.parseDouble(value.trim());
    }

    private VitalThresholdService.VitalStatus worse(VitalThresholdService.VitalStatus first, VitalThresholdService.VitalStatus second) {
        return statusRank(first) >= statusRank(second) ? first : second;
    }

    private boolean isAbnormal(VitalThresholdService.VitalStatus status) {
        return status != VitalThresholdService.VitalStatus.NORMAL;
    }

    private int statusRank(VitalThresholdService.VitalStatus status) {
        if (status == VitalThresholdService.VitalStatus.EMERGENCY) {
            return 3;
        }
        if (status == VitalThresholdService.VitalStatus.CRITICAL) {
            return 2;
        }
        if (status == VitalThresholdService.VitalStatus.WARNING) {
            return 1;
        }
        return 0;
    }

    private void syncPatientPriority(String staffUser, String patientId, String severity, String vitalType, String value, String unit) throws SQLException {
        String priority = priorityForSeverity(severity);
        patientDao.updatePriorityIfHigher(patientId, priority);
    }

    private String priorityForSeverity(String severity) {
        if ("EMERGENCY".equalsIgnoreCase(severity)) {
            return "EMERGENCY";
        }
        if ("CRITICAL".equalsIgnoreCase(severity)) {
            return "CRITICAL";
        }
        if ("WARNING".equalsIgnoreCase(severity)) {
            return "HIGH";
        }
        return "NORMAL";
    }

    private void attemptStablePriorityDowngrade(String staffUser, SqlitePatientDao.PatientDetail patient, String birthDate) throws SQLException {
        if (patient == null || patient.getPatientId() == null || patient.getPatientId().isBlank()) {
            return;
        }
        if (alertDao.countActiveCriticalEmergencyForPatient(patient.getPatientId()) > 0) {
            return;
        }

        List<VitalRecord> recentReadings = vitalReadingDao.findRecentByPatientId(patient.getPatientId(), 12);
        LocalDateTime firstNormalTime = null;
        LocalDateTime secondNormalTime = null;
        int normalGroups = 0;
        String lastTimestamp = "";

        for (VitalRecord reading : recentReadings) {
            LocalDateTime readingTime = parseDateTime(reading.getDateTime());
            String timestamp = reading.getDateTime() == null ? "" : reading.getDateTime().trim();
            VitalThresholdService.VitalStatus readingStatus = evaluateExistingReading(reading, birthDate);
            if (readingStatus != VitalThresholdService.VitalStatus.NORMAL) {
                return;
            }
            if (!timestamp.equals(lastTimestamp)) {
                normalGroups++;
                if (firstNormalTime == null) {
                    firstNormalTime = readingTime;
                } else if (secondNormalTime == null) {
                    secondNormalTime = readingTime;
                }
                lastTimestamp = timestamp;
            }
            if (normalGroups >= STABLE_READINGS_REQUIRED) {
                break;
            }
        }

        if (normalGroups < STABLE_READINGS_REQUIRED || firstNormalTime == null || secondNormalTime == null) {
            return;
        }
        long minutes = Math.abs(ChronoUnit.MINUTES.between(firstNormalTime, secondNormalTime));
        if (minutes < STABLE_MINUTES_REQUIRED) {
            return;
        }

        String nextPriority = nextLowerPriority(patient.getPriority());
        if (nextPriority.isBlank()) {
            return;
        }
        // Demo decision-support rule. Real hospitals use local clinical protocols.
        patientDao.updatePriorityIfLower(patient.getPatientId(), nextPriority);
    }

    private VitalThresholdService.VitalStatus evaluateExistingReading(VitalRecord reading, String birthDate) {
        try {
            return thresholdService.evaluate(reading.getVitalType(), parseNumber(reading.getValue()), birthDate);
        } catch (Exception e) {
            return VitalThresholdService.VitalStatus.WARNING;
        }
    }

    private String nextLowerPriority(String priority) {
        if ("EMERGENCY".equalsIgnoreCase(priority)) {
            return "CRITICAL";
        }
        if ("CRITICAL".equalsIgnoreCase(priority)) {
            return "HIGH";
        }
        if ("HIGH".equalsIgnoreCase(priority) || "WARNING".equalsIgnoreCase(priority)) {
            return "NORMAL";
        }
        return "";
    }

    private String displayValue(VitalsEntryRequest request) {
        if (VitalTypeCatalog.BLOOD_PRESSURE.equals(VitalTypeCatalog.normalize(request.vitalType))) {
            return request.value.trim() + "/" + request.secondValue.trim();
        }
        return request.value.trim();
    }

    public static class VitalsEntryRequest {
        private final String patientId;
        private final String vitalType;
        private final String value;
        private final String secondValue;
        private final String unit;
        private final String recordedAt;

        public VitalsEntryRequest(String patientId, String vitalType, String value, String secondValue, String unit, String recordedAt) {
            this.patientId = patientId;
            this.vitalType = vitalType;
            this.value = value;
            this.secondValue = secondValue;
            this.unit = unit;
            this.recordedAt = recordedAt;
        }
    }

    public static class VitalsWriteResult {
        private final VitalThresholdService.VitalStatus status;
        private final String vitalType;
        private final String value;
        private final String unit;
        private final String recordedAt;

        public VitalsWriteResult(VitalThresholdService.VitalStatus status, String vitalType, String value, String unit, String recordedAt) {
            this.status = status;
            this.vitalType = vitalType;
            this.value = value;
            this.unit = unit;
            this.recordedAt = recordedAt;
        }

        public VitalThresholdService.VitalStatus getStatus() { return status; }
        public String getVitalType() { return vitalType; }
        public String getValue() { return value; }
        public String getUnit() { return unit; }
        public String getRecordedAt() { return recordedAt; }
    }
}
