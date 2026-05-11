package ai;

import models.VitalSign;

public class AIAnalysis {

    public static String analyzeRisk(
            VitalSign vitalSign
    ) {

        if (vitalSign == null) {
            return "No Data";
        }

        if (
                vitalSign.getTemperature() >= 39 ||
                        vitalSign.getHeartRate() >= 120 ||
                        vitalSign.getOxygenLevel() < 90
        ) {

            return "Critical";

        }

        if (
                vitalSign.getTemperature() >= 38 ||
                        vitalSign.getHeartRate() > 100 ||
                        vitalSign.getSystolicPressure() >= 140
        ) {

            return "Warning";

        }

        return "Normal";

    }

}