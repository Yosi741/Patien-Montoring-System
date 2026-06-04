package services;

import database.DatabaseManager;
import database.SchemaInitializer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class VitalsTrendService {

    private static final DateTimeFormatter DISPLAY_DATE_TIME = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
    private final VitalThresholdService thresholdService = new VitalThresholdService();

    public VitalsTrendService() {
        ensureSchema();
    }

    public TrendResult loadTrend(String patientId, String vitalFilter, String rangeFilter) throws SQLException {
        ArrayList<TrendReading> readings = new ArrayList<>();
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT id, patient_id, vital_type, value, unit, recorded_at, source_type, staff_user, device_id "
                             + "FROM vital_readings WHERE patient_id = ?")) {
            statement.setString(1, patientId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    TrendReading reading = mapReading(resultSet);
                    if (matchesFilter(reading.getVitalType(), vitalFilter) && matchesRange(reading.getRecordedTime(), rangeFilter)) {
                        readings.add(reading);
                    }
                }
            }
        }

        readings.sort(Comparator.comparing(TrendReading::getRecordedTime));
        return summarize(vitalFilter, rangeFilter, readings);
    }

    private TrendReading mapReading(ResultSet resultSet) throws SQLException {
        String vitalType = resultSet.getString("vital_type");
        double numericValue = parseDouble(resultSet.getString("value"));
        return new TrendReading(
                resultSet.getString("vital_type"),
                numericValue,
                resultSet.getString("value"),
                value(resultSet.getString("unit")),
                value(resultSet.getString("recorded_at")),
                parseDateTime(resultSet.getString("recorded_at")),
                value(resultSet.getString("source_type")),
                value(resultSet.getString("staff_user")),
                value(resultSet.getString("device_id")),
                thresholdService.evaluate(vitalType, numericValue)
        );
    }

    private TrendResult summarize(String vitalFilter, String rangeFilter, List<TrendReading> readings) {
        TrendResult result = new TrendResult(vitalFilter, rangeFilter);
        result.readings.addAll(readings);
        if (readings.isEmpty()) {
            return result;
        }

        double sum = 0;
        result.min = Double.MAX_VALUE;
        result.max = -Double.MAX_VALUE;
        for (TrendReading reading : readings) {
            sum += reading.getNumericValue();
            result.min = Math.min(result.min, reading.getNumericValue());
            result.max = Math.max(result.max, reading.getNumericValue());
            if (reading.getStatus() == VitalThresholdService.VitalStatus.WARNING) {
                result.warningCount++;
            } else if (reading.getStatus() == VitalThresholdService.VitalStatus.CRITICAL
                    || reading.getStatus() == VitalThresholdService.VitalStatus.EMERGENCY) {
                result.criticalCount++;
            } else {
                result.normalCount++;
            }
        }
        result.average = sum / readings.size();
        result.latest = readings.get(readings.size() - 1);
        return result;
    }

    private boolean matchesFilter(String vitalType, String vitalFilter) {
        if (vitalFilter == null || vitalFilter.equalsIgnoreCase("All")) {
            return true;
        }
        String type = vitalType == null ? "" : vitalType.toLowerCase();
        switch (vitalFilter) {
            case VitalTypeCatalog.HEART_RATE:
                return type.contains("heart");
            case VitalTypeCatalog.BLOOD_PRESSURE:
                return type.contains("systolic") || type.contains("diastolic");
            case VitalTypeCatalog.OXYGEN_SATURATION:
                return type.contains("oxygen");
            case VitalTypeCatalog.TEMPERATURE:
                return type.contains("temperature");
            case VitalTypeCatalog.SYSTOLIC_PRESSURE:
                return type.contains("systolic");
            case VitalTypeCatalog.DIASTOLIC_PRESSURE:
                return type.contains("diastolic");
            case VitalTypeCatalog.SUGAR_LEVEL:
                return type.contains("sugar") || type.contains("glucose");
            default:
                return type.equals(vitalFilter.toLowerCase());
        }
    }

    private boolean matchesRange(LocalDateTime recordedTime, String rangeFilter) {
        if (recordedTime == null || recordedTime.equals(LocalDateTime.MIN) || rangeFilter == null || rangeFilter.equalsIgnoreCase("All")) {
            return true;
        }
        LocalDateTime cutoff;
        switch (rangeFilter) {
            case "Last 24 hours":
                cutoff = LocalDateTime.now().minusHours(24);
                break;
            case "Last 7 days":
                cutoff = LocalDateTime.now().minusDays(7);
                break;
            case "Last 30 days":
                cutoff = LocalDateTime.now().minusDays(30);
                break;
            default:
                return true;
        }
        return !recordedTime.isBefore(cutoff);
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return LocalDateTime.MIN;
        }
        try {
            return LocalDateTime.parse(value, DISPLAY_DATE_TIME);
        } catch (DateTimeParseException e) {
            try {
                return LocalDateTime.parse(value.replace(" ", "T"));
            } catch (Exception ignored) {
                return LocalDateTime.MIN;
            }
        }
    }

    private double parseDouble(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void ensureSchema() {
        try {
            SchemaInitializer.initialize();
        } catch (Exception e) {
            System.out.println("SQLite vitals trend schema check failed: " + e.getMessage());
        }
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    public static class TrendResult {
        private final String vitalFilter;
        private final String rangeFilter;
        private final ArrayList<TrendReading> readings = new ArrayList<>();
        private TrendReading latest;
        private double min;
        private double max;
        private double average;
        private int normalCount;
        private int warningCount;
        private int criticalCount;

        private TrendResult(String vitalFilter, String rangeFilter) {
            this.vitalFilter = vitalFilter;
            this.rangeFilter = rangeFilter;
        }

        public String getVitalFilter() { return vitalFilter; }
        public String getRangeFilter() { return rangeFilter; }
        public List<TrendReading> getReadings() { return readings; }
        public TrendReading getLatest() { return latest; }
        public double getMin() { return min; }
        public double getMax() { return max; }
        public double getAverage() { return average; }
        public int getNormalCount() { return normalCount; }
        public int getWarningCount() { return warningCount; }
        public int getCriticalCount() { return criticalCount; }
        public boolean isEmpty() { return readings.isEmpty(); }
    }

    public static class TrendReading {
        private final String vitalType;
        private final double numericValue;
        private final String rawValue;
        private final String unit;
        private final String recordedAt;
        private final LocalDateTime recordedTime;
        private final String sourceType;
        private final String staffUser;
        private final String deviceId;
        private final VitalThresholdService.VitalStatus status;

        private TrendReading(String vitalType, double numericValue, String rawValue, String unit, String recordedAt,
                             LocalDateTime recordedTime, String sourceType, String staffUser, String deviceId,
                             VitalThresholdService.VitalStatus status) {
            this.vitalType = vitalType;
            this.numericValue = numericValue;
            this.rawValue = rawValue;
            this.unit = unit;
            this.recordedAt = recordedAt;
            this.recordedTime = recordedTime;
            this.sourceType = sourceType;
            this.staffUser = staffUser;
            this.deviceId = deviceId;
            this.status = status;
        }

        public String getVitalType() { return vitalType; }
        public double getNumericValue() { return numericValue; }
        public String getRawValue() { return rawValue; }
        public String getUnit() { return unit; }
        public String getRecordedAt() { return recordedAt; }
        public LocalDateTime getRecordedTime() { return recordedTime; }
        public String getSourceType() { return sourceType; }
        public String getStaffUser() { return staffUser; }
        public String getDeviceId() { return deviceId; }
        public VitalThresholdService.VitalStatus getStatus() { return status; }
    }
}
