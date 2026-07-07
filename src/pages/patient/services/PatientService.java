package pages.patient.services;

import pages.patient.Patient;
import users.Session;

import java.util.ArrayList;

public class PatientService {

    public static void savePatientChanges(Patient patient) {
    }

    public static void applyExtractedMedicalInfo(Patient patient, ArrayList<String> extractedItems) {
        for (String item : extractedItems) {
            String lower = item.toLowerCase();
            String value = stripPrefix(item);

            if (lower.startsWith("diagnosis")) {
                patient.setDiagnosis(append(patient.getDiagnosis(), value));
            } else if (lower.startsWith("medication")) {
                patient.setMedicalHistory(append(patient.getMedicalHistory(), "Treatment note: " + value));
            } else if (lower.startsWith("allergy")) {
                patient.setAllergies(append(patient.getAllergies(), value));
            } else if (lower.startsWith("family history")) {
                patient.setFamilyHistory(append(patient.getFamilyHistory(), value));
            } else {
                patient.setMedicalHistory(append(patient.getMedicalHistory(), item));
            }
        }

        savePatientChanges(patient);
    }

    private static String append(String current, String addition) {
        if (addition == null || addition.isBlank()) {
            return current;
        }
        if (current == null || current.isBlank()) {
            return addition.trim();
        }
        return current + "\n" + addition.trim();
    }

    private static String stripPrefix(String item) {
        int colon = item.indexOf(":");
        if (colon >= 0 && colon + 1 < item.length()) {
            return item.substring(colon + 1).trim();
        }
        return item.trim();
    }

}
