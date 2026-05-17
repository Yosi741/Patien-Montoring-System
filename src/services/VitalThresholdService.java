package services;

public class VitalThresholdService {

    public VitalStatus evaluate(String vitalType, double value) {
        String type = vitalType == null ? "" : vitalType.toLowerCase();
        if (type.contains("heart")) {
            return range(value, 60, 100, 50, 120);
        }
        if (type.contains("temperature")) {
            return range(value, 36.0, 37.5, 35.0, 38.5);
        }
        if (type.contains("oxygen")) {
            if (value < 90) {
                return VitalStatus.CRITICAL;
            }
            if (value < 95) {
                return VitalStatus.WARNING;
            }
            return VitalStatus.NORMAL;
        }
        if (type.contains("systolic")) {
            return range(value, 90, 120, 80, 180);
        }
        if (type.contains("diastolic")) {
            return range(value, 60, 80, 50, 120);
        }
        if (type.contains("sugar") || type.contains("glucose")) {
            return range(value, 70, 140, 54, 250);
        }
        return VitalStatus.NORMAL;
    }

    private VitalStatus range(double value, double normalLow, double normalHigh, double criticalLow, double criticalHigh) {
        if (value < criticalLow || value > criticalHigh) {
            return VitalStatus.CRITICAL;
        }
        if (value < normalLow || value > normalHigh) {
            return VitalStatus.WARNING;
        }
        return VitalStatus.NORMAL;
    }

    public enum VitalStatus {
        NORMAL,
        WARNING,
        CRITICAL
    }
}
