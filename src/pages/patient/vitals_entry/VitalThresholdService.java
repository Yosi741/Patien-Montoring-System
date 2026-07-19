package pages.patient.vitals_entry;

import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class VitalThresholdService {

    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    public VitalStatus evaluate(String vitalType, double value) {
        return evaluate(vitalType, value, "");
    }

    public VitalStatus evaluate(String vitalType, double value, String patientBirthDate) {
        String type = VitalTypeCatalog.normalize(vitalType);
        AgeGroup ageGroup = ageGroup(patientBirthDate);
        if (VitalTypeCatalog.HEART_RATE.equals(type)) {
            return heartRate(value, ageGroup);
        }
        if (VitalTypeCatalog.TEMPERATURE.equals(type)) {
            return temperature(value);
        }
        if (VitalTypeCatalog.OXYGEN_SATURATION.equals(type)) {
            return oxygen(value);
        }
        if (VitalTypeCatalog.SYSTOLIC_PRESSURE.equals(type)) {
            return systolic(value, ageGroup);
        }
        if (VitalTypeCatalog.DIASTOLIC_PRESSURE.equals(type)) {
            return diastolic(value, ageGroup);
        }
        if (VitalTypeCatalog.SUGAR_LEVEL.equals(type)) {
            return range(value, 70, 140, 54, 250, 40, 350);
        }
        return VitalStatus.NORMAL;
    }

    private VitalStatus heartRate(double value, AgeGroup ageGroup) {
        switch (ageGroup) {
            case NEWBORN:
                return range(value, 100, 170, 80, 190, 60, 210);
            case INFANT:
                return range(value, 90, 160, 70, 180, 55, 200);
            case CHILD:
                return range(value, 70, 120, 55, 150, 45, 180);
            case TEEN:
                return range(value, 60, 105, 50, 130, 40, 160);
            case OLDER_ADULT:
            case ADULT:
            default:
                return range(value, 60, 100, 50, 120, 40, 150);
        }
    }

    private VitalStatus systolic(double value, AgeGroup ageGroup) {
        switch (ageGroup) {
            case NEWBORN:
                return range(value, 60, 95, 50, 110, 40, 125);
            case INFANT:
                return range(value, 70, 105, 55, 120, 45, 135);
            case CHILD:
                return range(value, 85, 120, 70, 140, 55, 160);
            case TEEN:
                return range(value, 95, 130, 80, 150, 65, 180);
            case OLDER_ADULT:
            case ADULT:
            default:
                return range(value, 90, 120, 80, 180, 65, 220);
        }
    }

    private VitalStatus diastolic(double value, AgeGroup ageGroup) {
        switch (ageGroup) {
            case NEWBORN:
                return range(value, 30, 60, 25, 75, 20, 90);
            case INFANT:
                return range(value, 35, 65, 30, 80, 20, 95);
            case CHILD:
                return range(value, 50, 80, 40, 95, 30, 110);
            case TEEN:
                return range(value, 55, 85, 45, 105, 35, 120);
            case OLDER_ADULT:
            case ADULT:
            default:
                return range(value, 60, 80, 50, 120, 35, 140);
        }
    }

    private VitalStatus temperature(double value) {
        return range(value, 36.0, 37.5, 35.0, 39.0, 34.0, 41.0);
    }

    private VitalStatus oxygen(double value) {
        if (value < 85) {
            return VitalStatus.EMERGENCY;
        }
        if (value < 90) {
            return VitalStatus.CRITICAL;
        }
        if (value < 95) {
            return VitalStatus.WARNING;
        }
        return VitalStatus.NORMAL;
    }

    private VitalStatus range(double value, double normalLow, double normalHigh,
                              double criticalLow, double criticalHigh,
                              double emergencyLow, double emergencyHigh) {
        if (value < emergencyLow || value > emergencyHigh) {
            return VitalStatus.EMERGENCY;
        }
        if (value < criticalLow || value > criticalHigh) {
            return VitalStatus.CRITICAL;
        }
        if (value < normalLow || value > normalHigh) {
            return VitalStatus.WARNING;
        }
        return VitalStatus.NORMAL;
    }

    private AgeGroup ageGroup(String birthDate) {
        LocalDate date = parseBirthDate(birthDate);
        if (date == null || date.isAfter(LocalDate.now())) {
            return AgeGroup.ADULT;
        }
        Period age = Period.between(date, LocalDate.now());
        if (age.getYears() == 0 && age.getMonths() == 0 && age.getDays() <= 28) {
            return AgeGroup.NEWBORN;
        }
        if (age.getYears() == 0) {
            return AgeGroup.INFANT;
        }
        if (age.getYears() <= 12) {
            return AgeGroup.CHILD;
        }
        if (age.getYears() <= 17) {
            return AgeGroup.TEEN;
        }
        if (age.getYears() >= 65) {
            return AgeGroup.OLDER_ADULT;
        }
        return AgeGroup.ADULT;
    }

    private LocalDate parseBirthDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim(), DISPLAY_DATE);
        } catch (DateTimeParseException e) {
            try {
                return LocalDate.parse(value.trim());
            } catch (DateTimeParseException ignored) {
                return null;
            }
        }
    }

    private enum AgeGroup {
        NEWBORN,
        INFANT,
        CHILD,
        TEEN,
        ADULT,
        OLDER_ADULT
    }

    public enum VitalStatus {
        NORMAL,
        WARNING,
        CRITICAL,
        EMERGENCY
    }
}
