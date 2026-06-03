package ai_Prototype;

import dao.SqliteAiNoteDao;
import models.Patient;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

public class AIAdviceEngine {

    public static void saveAdvice(String patientId, String source, ArrayList<String> notes) {
        if (notes == null || notes.isEmpty()) {
            return;
        }
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"));
        SqliteAiNoteDao dao = new SqliteAiNoteDao();
        for (String note : notes) {
            try {
                dao.saveNote(patientId, source, note, time, 0);
            } catch (Exception e) {
                System.out.println("SQLite AI advice save skipped: " + e.getMessage());
            }
        }
    }

    public static ArrayList<String> getLatestAdvice(String patientId, int limit) {
        ArrayList<String> notes = new ArrayList<>();
        try {
            for (dao.ClinicalTimelineDao.TimelineEvent event : new dao.ClinicalTimelineDao().findEvents(patientId, "AI notes only", "")) {
                notes.add(event.getEventTime() + " - " + event.getDescription() + " (" + event.getTitle() + ")");
                if (notes.size() >= limit) {
                    break;
                }
            }
        } catch (Exception e) {
            System.out.println("SQLite AI advice load skipped: " + e.getMessage());
        }
        return notes;
    }

    public static ArrayList<String> generatePatientAdvice(Patient patient) {
        ArrayList<String> notes = new ArrayList<>();

        String risk = AIAnalysis.analyzeRisk(patient.getVitalSign());
        notes.add("Risk level: " + risk + ". This is rule-based support only and must be reviewed by authorized staff.");

        for (String recommendation : AIAnalysis.getRecommendations(patient.getVitalSign())) {
            notes.add("Recommended action: " + recommendation);
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

        if (risk.equals("Critical") || risk.equals("Warning")) {
            notes.add("Doctor review needed: Yes.");
        } else {
            notes.add("Doctor review needed: Routine review unless symptoms or staff concern are present.");
        }

        saveAdvice(patient.getPatientId(), "Patient AI Advice", notes);
        return notes;
    }

    public static ArrayList<String[]> loadAllAdviceRows() {
        return new ArrayList<>();
    }
}
