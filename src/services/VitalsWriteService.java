package services;

import dao.SqlitePatientDao;
import dao.SqliteVitalReadingDao;
import models.VitalRecord;
import ui.javafx.helpers.AuditAction;
import ui.javafx.helpers.AuditWriteHelper;
import ui.javafx.helpers.FormValidationHelper;
import ui.javafx.helpers.PermissionHelper;
import users.User;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

public class VitalsWriteService {

    private static final DateTimeFormatter LEGACY_DATE_TIME = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

    private final SqliteVitalReadingDao vitalReadingDao;
    private final SqlitePatientDao patientDao;
    private final VitalThresholdService thresholdService;

    public VitalsWriteService() {
        this(new SqliteVitalReadingDao(), new SqlitePatientDao(), new VitalThresholdService());
    }

    public VitalsWriteService(SqliteVitalReadingDao vitalReadingDao, SqlitePatientDao patientDao, VitalThresholdService thresholdService) {
        this.vitalReadingDao = vitalReadingDao;
        this.patientDao = patientDao;
        this.thresholdService = thresholdService;
    }

    public VitalsWriteResult enterVitalReading(User currentUser, VitalsEntryRequest request) throws SQLException {
        if (!PermissionHelper.canEnterVitals(currentUser)) {
            throw new SecurityException("Only Admin, Doctor, and Nurse users can enter vitals.");
        }
        validate(request);

        String staffUser = currentUser == null ? "Unknown" : currentUser.getUsername();
        LocalDateTime recordedAt = parseDateTime(request.recordedAt);
        String recordedAtText = recordedAt.format(LEGACY_DATE_TIME);
        String normalizedType = normalizeType(request.vitalType);
        String unit = expectedUnit(normalizedType);

        VitalThresholdService.VitalStatus status;
        if ("Blood Pressure".equals(normalizedType)) {
            double systolic = parseNumber(request.value);
            double diastolic = parseNumber(request.secondValue);
            VitalThresholdService.VitalStatus systolicStatus = thresholdService.evaluate("Systolic Pressure", systolic);
            VitalThresholdService.VitalStatus diastolicStatus = thresholdService.evaluate("Diastolic Pressure", diastolic);
            status = worse(systolicStatus, diastolicStatus);
            vitalReadingDao.insertVitalReading(record(request.patientId, "Systolic Pressure", request.value, unit, recordedAtText, staffUser));
            vitalReadingDao.insertVitalReading(record(request.patientId, "Diastolic Pressure", request.secondValue, unit, recordedAtText, staffUser));
        } else {
            double value = parseNumber(request.value);
            String storedType = storedType(normalizedType);
            status = thresholdService.evaluate(storedType, value);
            vitalReadingDao.insertVitalReading(record(request.patientId, storedType, request.value, unit, recordedAtText, staffUser));
        }

        if (status == VitalThresholdService.VitalStatus.WARNING || status == VitalThresholdService.VitalStatus.CRITICAL) {
            String severity = status == VitalThresholdService.VitalStatus.CRITICAL ? "CRITICAL" : "WARNING";
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
        }

        AuditWriteHelper.write(
                staffUser,
                AuditAction.ENTER_VITALS,
                "patient_id=" + request.patientId + ", vital=" + normalizedType + ", value=" + displayValue(request)
                        + " " + unit + ", status=" + status
        );

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

        String type = normalizeType(request.vitalType);
        validateUnit(type, request.unit);
        if ("Blood Pressure".equals(type)) {
            FormValidationHelper.ValidationResult bpValidation = FormValidationHelper.validateRequired("Diastolic value", request.secondValue);
            if (!bpValidation.isValid()) {
                throw new IllegalArgumentException(bpValidation.getMessage());
            }
            validateRange("Systolic pressure", request.value, 50, 260);
            validateRange("Diastolic pressure", request.secondValue, 30, 160);
            return;
        }

        switch (type) {
            case "Heart Rate":
                validateRange("Heart rate", request.value, 20, 250);
                break;
            case "Oxygen":
                validateRange("Oxygen saturation", request.value, 50, 100);
                break;
            case "Temperature":
                validateRange("Temperature", request.value, 30, 45);
                break;
            case "Sugar Level":
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
        String expected = expectedUnit(vitalType);
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
            return LocalDateTime.parse(value.trim(), LEGACY_DATE_TIME);
        } catch (DateTimeParseException e) {
            return LocalDateTime.parse(value.trim().replace(" ", "T"));
        }
    }

    private String normalizeType(String vitalType) {
        if (vitalType == null) {
            return "";
        }
        String type = vitalType.trim().toLowerCase(Locale.ROOT);
        if (type.contains("heart")) {
            return "Heart Rate";
        }
        if (type.contains("blood")) {
            return "Blood Pressure";
        }
        if (type.contains("oxygen")) {
            return "Oxygen";
        }
        if (type.contains("temperature")) {
            return "Temperature";
        }
        if (type.contains("sugar") || type.contains("glucose")) {
            return "Sugar Level";
        }
        return vitalType.trim();
    }

    private String storedType(String vitalType) {
        if ("Oxygen".equals(vitalType)) {
            return "Oxygen Saturation";
        }
        return vitalType;
    }

    private String expectedUnit(String vitalType) {
        switch (vitalType) {
            case "Heart Rate":
                return "bpm";
            case "Blood Pressure":
                return "mmHg";
            case "Oxygen":
                return "%";
            case "Temperature":
                return "C";
            case "Sugar Level":
                return "mg/dL";
            default:
                return "";
        }
    }

    private double parseNumber(String value) {
        return Double.parseDouble(value.trim());
    }

    private VitalThresholdService.VitalStatus worse(VitalThresholdService.VitalStatus first, VitalThresholdService.VitalStatus second) {
        if (first == VitalThresholdService.VitalStatus.CRITICAL || second == VitalThresholdService.VitalStatus.CRITICAL) {
            return VitalThresholdService.VitalStatus.CRITICAL;
        }
        if (first == VitalThresholdService.VitalStatus.WARNING || second == VitalThresholdService.VitalStatus.WARNING) {
            return VitalThresholdService.VitalStatus.WARNING;
        }
        return VitalThresholdService.VitalStatus.NORMAL;
    }

    private String displayValue(VitalsEntryRequest request) {
        if ("Blood Pressure".equals(normalizeType(request.vitalType))) {
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
