package ai_Prototype;

import models.VitalSign;

import java.util.ArrayList;

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

    public static ArrayList<String> getRecommendations(VitalSign vitalSign) {
        ArrayList<String> recommendations = new ArrayList<>();

        if (vitalSign == null) {
            recommendations.add("No vital signs recorded yet. Add vitals to begin clinical monitoring.");
            return recommendations;
        }

        if (vitalSign.getOxygenLevel() < 90) {
            recommendations.add("Immediate respiratory assessment recommended.");
        } else if (vitalSign.getOxygenLevel() < 94) {
            recommendations.add("Check oxygen saturation again and monitor breathing pattern.");
        }

        if (vitalSign.getTemperature() >= 39) {
            recommendations.add("Critical fever range detected. Notify doctor and monitor infection signs.");
        } else if (vitalSign.getTemperature() >= 38) {
            recommendations.add("Possible fever, monitor infection signs.");
        }

        if (vitalSign.getHeartRate() >= 120) {
            recommendations.add("High heart rate detected. Assess pain, fever, anxiety, and hydration.");
        } else if (vitalSign.getHeartRate() > 100) {
            recommendations.add("Recheck pulse and continue close monitoring.");
        }

        if (vitalSign.getSystolicPressure() >= 160 || vitalSign.getDiastolicPressure() >= 100) {
            recommendations.add("High blood pressure detected. Recheck BP and notify doctor if persistent.");
        } else if (vitalSign.getSystolicPressure() >= 140 || vitalSign.getDiastolicPressure() >= 90) {
            recommendations.add("Elevated blood pressure. Recheck BP and monitor trends.");
        }

        if (recommendations.isEmpty()) {
            recommendations.add("Vitals are currently within expected monitoring range.");
        }

        return recommendations;
    }

}
