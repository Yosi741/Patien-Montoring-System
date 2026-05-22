package services;

public class VitalThresholdService {

    public VitalStatus evaluate(String vitalType, double value) {
        String type = VitalTypeCatalog.normalize(vitalType);
        if (VitalTypeCatalog.HEART_RATE.equals(type)) {
            return range(value, 60, 100, 50, 120);
        }
        if (VitalTypeCatalog.TEMPERATURE.equals(type)) {
            return range(value, 36.0, 37.5, 35.0, 38.5);
        }
        if (VitalTypeCatalog.OXYGEN_SATURATION.equals(type)) {
            if (value < 90) {
                return VitalStatus.CRITICAL;
            }
            if (value < 95) {
                return VitalStatus.WARNING;
            }
            return VitalStatus.NORMAL;
        }
        if (VitalTypeCatalog.SYSTOLIC_PRESSURE.equals(type)) {
            return range(value, 90, 120, 80, 180);
        }
        if (VitalTypeCatalog.DIASTOLIC_PRESSURE.equals(type)) {
            return range(value, 60, 80, 50, 120);
        }
        if (VitalTypeCatalog.SUGAR_LEVEL.equals(type)) {
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
