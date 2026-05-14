package ai;

import database.MedicalFileStorage;
import database.VitalStorage;
import models.MedicalFile;
import models.Patient;
import models.VitalRecord;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

public class AIAdviceEngine {

    private static final String NOTES_FILE = "data/ai_notes.txt";
    private static final String DELIMITER = "\\|";

    public static void saveAdvice(String patientId, String source, ArrayList<String> notes) {
        if (notes == null || notes.isEmpty()) {
            return;
        }

        try {
            new File("data").mkdirs();
            PrintWriter writer = new PrintWriter(new FileWriter(NOTES_FILE, true));
            String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"));

            for (String note : notes) {
                writer.println(escape(patientId) + "|" + escape(time) + "|" + escape(source) + "|" + escape(note));
            }

            writer.close();
        } catch (Exception e) {
            System.out.println("Error saving AI advice: " + e.getMessage());
        }
    }

    public static ArrayList<String> getLatestAdvice(String patientId, int limit) {
        ArrayList<String> notes = new ArrayList<>();

        try {
            File file = new File(NOTES_FILE);
            if (!file.exists()) {
                return notes;
            }

            BufferedReader reader = new BufferedReader(new FileReader(file));
            String line;

            while ((line = reader.readLine()) != null) {
                String[] data = line.split(DELIMITER, -1);
                if (data.length == 4 && data[0].equals(patientId)) {
                    notes.add(data[1] + " - " + data[3] + " (" + data[2] + ")");
                }
            }

            reader.close();
        } catch (Exception e) {
            System.out.println("Error loading AI advice: " + e.getMessage());
        }

        ArrayList<String> latest = new ArrayList<>();
        for (int i = Math.max(0, notes.size() - limit); i < notes.size(); i++) {
            latest.add(notes.get(i));
        }
        return latest;
    }

    public static ArrayList<String> generatePatientAdvice(Patient patient) {
        ArrayList<String> notes = new ArrayList<>();

        String risk = AIAnalysis.analyzeRisk(patient.getVitalSign());
        notes.add("Risk level: " + risk + ". This is rule-based support only and must be reviewed by authorized staff.");

        for (String recommendation : AIAnalysis.getRecommendations(patient.getVitalSign())) {
            notes.add("Recommended action: " + recommendation);
        }

        ArrayList<VitalRecord> records = VitalStorage.getRecordsForPatient(patient.getPatientId());
        if (records.size() >= 10) {
            notes.add("Trend review: Multiple previous readings exist. Compare current values with history before deciding device error or clinical decline.");
        }

        if (!patient.getDiagnosis().isBlank()) {
            notes.add("Known diagnosis/history concern: " + patient.getDiagnosis());
        }
        if (!patient.getCurrentMedications().isBlank()) {
            notes.add("Medication context: Check whether current medications may affect heart rate, blood pressure, or temperature.");
        }
        if (!patient.getAllergies().isBlank()) {
            notes.add("Allergy safety: Review allergies before any medication change.");
        }
        if (!patient.getMedicalHistory().isBlank()) {
            notes.add("History context: Review previous visits and doctor notes before final clinical decision.");
        }

        ArrayList<MedicalFile> files = MedicalFileStorage.getFilesForPatient(patient.getPatientId());
        if (!files.isEmpty()) {
            notes.add("Uploaded files available: Review " + files.size() + " patient file(s) before final decision.");
        }

        if (risk.equals("Critical") || risk.equals("Warning")) {
            notes.add("Doctor review needed: Yes.");
        } else {
            notes.add("Doctor review needed: Routine review unless symptoms or staff concern are present.");
        }

        saveAdvice(patient.getPatientId(), "Patient AI Advice", notes);
        return notes;
    }

    public static ArrayList<String[]> loadAllAdviceRows() {
        ArrayList<String[]> rows = new ArrayList<>();

        try {
            File file = new File(NOTES_FILE);
            if (!file.exists()) {
                return rows;
            }

            BufferedReader reader = new BufferedReader(new FileReader(file));
            String line;

            while ((line = reader.readLine()) != null) {
                String[] data = line.split(DELIMITER, -1);
                if (data.length == 4) {
                    rows.add(data);
                }
            }

            reader.close();
        } catch (Exception e) {
            System.out.println("Error loading AI advice rows: " + e.getMessage());
        }

        return rows;
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("|", " ");
    }
}
