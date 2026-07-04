package ui.javafx.pages.patients.services;

import java.util.List;
import java.util.Locale;

public final class VitalTypeCatalog {

    public static final String HEART_RATE = "Heart Rate";
    public static final String TEMPERATURE = "Temperature";
    public static final String OXYGEN_SATURATION = "Oxygen Saturation";
    public static final String SYSTOLIC_PRESSURE = "Systolic Pressure";
    public static final String DIASTOLIC_PRESSURE = "Diastolic Pressure";
    public static final String SUGAR_LEVEL = "Sugar Level";
    public static final String BLOOD_PRESSURE = "Blood Pressure";

    private VitalTypeCatalog() {
    }

    public static List<String> javaFxEntryTypes() {
        return List.of(
                HEART_RATE,
                TEMPERATURE,
                OXYGEN_SATURATION,
                SYSTOLIC_PRESSURE,
                DIASTOLIC_PRESSURE,
                SUGAR_LEVEL
        );
    }

    public static List<String> javaFxFilterTypes() {
        return List.of(
                "All",
                HEART_RATE,
                TEMPERATURE,
                OXYGEN_SATURATION,
                SYSTOLIC_PRESSURE,
                DIASTOLIC_PRESSURE,
                SUGAR_LEVEL
        );
    }

    public static String normalize(String vitalType) {
        if (vitalType == null) {
            return "";
        }
        String type = vitalType.trim().toLowerCase(Locale.ROOT);
        if (type.contains("heart")) {
            return HEART_RATE;
        }
        if (type.contains("systolic")) {
            return SYSTOLIC_PRESSURE;
        }
        if (type.contains("diastolic")) {
            return DIASTOLIC_PRESSURE;
        }
        if (type.contains("blood")) {
            return BLOOD_PRESSURE;
        }
        if (type.contains("oxygen") || type.equals("spo2") || type.contains("spo2")) {
            return OXYGEN_SATURATION;
        }
        if (type.contains("temperature") || type.equals("temp")) {
            return TEMPERATURE;
        }
        if (type.contains("sugar") || type.contains("glucose")) {
            return SUGAR_LEVEL;
        }
        return vitalType.trim();
    }

    public static boolean isSupportedSingleReading(String vitalType) {
        String normalized = normalize(vitalType);
        return HEART_RATE.equals(normalized)
                || TEMPERATURE.equals(normalized)
                || OXYGEN_SATURATION.equals(normalized)
                || SYSTOLIC_PRESSURE.equals(normalized)
                || DIASTOLIC_PRESSURE.equals(normalized)
                || SUGAR_LEVEL.equals(normalized);
    }

    public static String expectedUnit(String vitalType) {
        switch (normalize(vitalType)) {
            case HEART_RATE:
                return "bpm";
            case TEMPERATURE:
                return "C";
            case OXYGEN_SATURATION:
                return "%";
            case SYSTOLIC_PRESSURE:
            case DIASTOLIC_PRESSURE:
            case BLOOD_PRESSURE:
                return "mmHg";
            case SUGAR_LEVEL:
                return "mg/dL";
            default:
                return "";
        }
    }
}
