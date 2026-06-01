package database;

// import ai_Prototype.AIAdviceEngine;
import dao.SqliteAiNoteDao;
import dao.SqliteMedicalFileDao;
import dao.SqliteMedicalHistoryDao;
import dao.SqliteMedicationDao;
import dao.SqlitePatientDao;
import dao.SqliteShiftHandoverDao;
import dao.SqliteUserDao;
import dao.SqliteVitalReadingDao;
import models.MedicalFile;
import models.Patient;
import models.VitalRecord;
import security.PasswordHasher;
import users.User;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.sql.SQLException;
import java.util.ArrayList;

public class SqliteMigrationService {

    private final SqliteUserDao userDao = new SqliteUserDao();
    private final SqlitePatientDao patientDao = new SqlitePatientDao();
    private final SqliteVitalReadingDao vitalReadingDao = new SqliteVitalReadingDao();
    private final SqliteAiNoteDao aiNoteDao = new SqliteAiNoteDao();
    private final SqliteMedicalFileDao medicalFileDao = new SqliteMedicalFileDao();
    private final SqliteMedicationDao medicationDao = new SqliteMedicationDao();
    private final SqliteMedicalHistoryDao medicalHistoryDao = new SqliteMedicalHistoryDao();
    private final SqliteShiftHandoverDao shiftHandoverDao = new SqliteShiftHandoverDao();

    public MigrationResult migrateFromTextFiles() {
        MigrationResult result = new MigrationResult();
        result.append("Starting SQLite migration from text-file storage.");

        try {
            SchemaInitializer.initialize();
            migrateUsers(result);
            migratePatients(result);
            migrateVitalReadings(result);
            migrateAiNotes(result);
            migrateMedicalFiles(result);
            migratePatientContext(result);
            migrateShiftHandoverNotes(result);
            result.success = true;
            result.append("SQLite migration completed.");
        } catch (Exception e) {
            result.success = false;
            result.append("SQLite migration failed: " + e.getMessage());
        }

        System.out.println(result.getSummary());
        return result;
    }

    public MigrationResult migrateIfNeeded() {
        try {
            SchemaInitializer.initialize();
            if (userDao.count() == 0 || patientDao.count() == 0) {
                return migrateFromTextFiles();
            }
            MigrationResult result = new MigrationResult();
            result.success = true;
            result.append("SQLite migration skipped. Users and patients already exist in SQLite.");
            System.out.println(result.getSummary());
            return result;
        } catch (SQLException e) {
            MigrationResult result = new MigrationResult();
            result.success = false;
            result.append("SQLite migration check failed: " + e.getMessage());
            System.out.println(result.getSummary());
            return result;
        }
    }

    private void migrateUsers(MigrationResult result) throws SQLException {
        ArrayList<User> users = UserStorage.loadUsers();
        int imported = 0;
        for (User user : users) {
            String password = user.getPassword();
            String hash = PasswordHasher.isHashed(password)
                    ? password
                    : PasswordHasher.hash(password.toCharArray());
            userDao.saveHashed(user.getUsername(), hash, user.getRole(), user.getSection());
            imported++;
        }
        result.usersImported = imported;
        result.append("Users imported/upserted: " + imported);
    }

    private void migratePatients(MigrationResult result) throws SQLException {
        ArrayList<Patient> patients = FileStorage.loadPatients();
        long legacyModified = new File("data/patients.txt").lastModified();
        int imported = 0;
        int skippedNewerSqlite = 0;
        for (Patient patient : patients) {
            if (patientDao.hasNewerSqliteUpdate(patient.getPatientId(), legacyModified)) {
                skippedNewerSqlite++;
                continue;
            }
            patientDao.save(patient);
            imported++;
        }
        result.patientsImported = imported;
        result.append("Patients imported/upserted: " + imported + ", skipped newer SQLite edits: " + skippedNewerSqlite);
    }

    private void migrateVitalReadings(MigrationResult result) throws SQLException {
        ArrayList<VitalRecord> records = VitalStorage.loadAllRecords();
        int imported = 0;
        for (VitalRecord record : records) {
            vitalReadingDao.save(record);
            imported++;
        }
        result.vitalsImported = imported;
        result.append("Vital readings checked/imported: " + imported);
    }

    /*private void migrateAiNotes(MigrationResult result) throws SQLException {
        ArrayList<String[]> rows = AIAdviceEngine.loadAllAdviceRows();
        int imported = 0;
        int skipped = 0;
        for (String[] row : rows) {
            String patientId = row[0];
            if (!patientExists(patientId)) {
                skipped++;
                continue;
            }
            String createdAt = row[1];
            String sourceTitle = row[2];
            String note = row[3];
            if (aiNoteDao.saveLegacyNote(patientId, sourceTitle, note, createdAt, riskScoreFor(note))) {
                imported++;
            }
        }
        result.aiNotesImported = imported;
        result.aiNotesChecked = rows.size();
        result.append("AI notes checked: " + rows.size() + ", inserted: " + imported + ", skipped orphaned patients: " + skipped);
    }

    private void migrateMedicalFiles(MigrationResult result) throws SQLException {
        ArrayList<MedicalFile> files = MedicalFileStorage.loadAllFiles();
        int checked = 0;
        int skipped = 0;
        for (MedicalFile file : files) {
            if (!patientExists(file.getPatientId())) {
                skipped++;
                continue;
            }
            medicalFileDao.save(file, "Imported legacy medical file metadata.");
            checked++;
        }
        result.medicalFilesChecked = checked;
        result.append("Medical files checked/upserted: " + checked + ", skipped orphaned patients: " + skipped);
    }

     */

    private void migratePatientContext(MigrationResult result) throws SQLException {
        ArrayList<Patient> patients = FileStorage.loadPatients();
        int historyInserted = 0;
        int medicationEventsInserted = 0;

        for (Patient patient : patients) {
            if (hasText(patient.getDiagnosis())) {
                historyInserted += medicalHistoryDao.saveEntry(
                        patient.getPatientId(), "Imported legacy diagnosis", patient.getDiagnosis(), "Legacy migration", "") ? 1 : 0;
            }
            if (hasText(patient.getMedicalHistory())) {
                historyInserted += medicalHistoryDao.saveEntry(
                        patient.getPatientId(), "Imported legacy medical history", patient.getMedicalHistory(), "Legacy migration", "") ? 1 : 0;
            }
            if (hasText(patient.getAllergies())) {
                historyInserted += medicalHistoryDao.saveEntry(
                        patient.getPatientId(), "Imported legacy allergies", patient.getAllergies(), "Legacy migration", "") ? 1 : 0;
            }
            if (hasText(patient.getFamilyHistory())) {
                historyInserted += medicalHistoryDao.saveEntry(
                        patient.getPatientId(), "Imported legacy family history", patient.getFamilyHistory(), "Legacy migration", "") ? 1 : 0;
            }

            medicationEventsInserted += migrateMedicationText(
                    patient.getPatientId(),
                    "Imported legacy current medications",
                    patient.getCurrentMedications(),
                    true);
            medicationEventsInserted += migrateMedicationText(
                    patient.getPatientId(),
                    "Imported legacy past medications",
                    patient.getPastMedications(),
                    false);
        }

        result.medicalHistoryImported = historyInserted;
        result.medicationEventsImported = medicationEventsInserted;
        result.append("Medical history entries inserted: " + historyInserted);
        result.append("Medication events inserted: " + medicationEventsInserted);
    }

    private int migrateMedicationText(String patientId, String medicationName, String medicationText, boolean active) throws SQLException {
        if (!hasText(medicationText)) {
            return 0;
        }
        long medicationId = medicationDao.saveMedication(patientId, medicationName, "", "", "", active);
        boolean inserted = medicationDao.saveMedicationEvent(
                medicationId,
                patientId,
                "Legacy migration",
                "",
                medicationText);
        return inserted ? 1 : 0;
    }

    private void migrateShiftHandoverNotes(MigrationResult result) throws SQLException {
        File file = findShiftHandoverFile();
        if (file == null) {
            result.append("Shift handover legacy file not found; skipped.");
            return;
        }

        int checked = 0;
        int inserted = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] data = line.split("\\|", -1);
                if (data.length >= 5) {
                    checked++;
                    if (shiftHandoverDao.saveNote(data[0], data[1], data[2], data[3], data[4])) {
                        inserted++;
                    }
                }
            }
        } catch (Exception e) {
            result.append("Shift handover migration skipped due to read error: " + e.getMessage());
            return;
        }
        result.shiftHandoverChecked = checked;
        result.shiftHandoverImported = inserted;
        result.append("Shift handover notes checked: " + checked + ", inserted: " + inserted);
    }

    private File findShiftHandoverFile() {
        String[] candidates = {
                "data/shift_handover_notes.txt",
                "data/shift_handover.txt",
                "data/handover_notes.txt"
        };
        for (String candidate : candidates) {
            File file = new File(candidate);
            if (file.exists()) {
                return file;
            }
        }
        return null;
    }

    private int riskScoreFor(String note) {
        String lower = note == null ? "" : note.toLowerCase();
        if (lower.contains("critical") || lower.contains("immediate") || lower.contains("urgent")) {
            return 80;
        }
        if (lower.contains("warning") || lower.contains("review") || lower.contains("elevated") || lower.contains("low")) {
            return 50;
        }
        return 0;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean patientExists(String patientId) throws SQLException {
        return patientId != null && !patientId.isBlank() && patientDao.findById(patientId).isPresent();
    }

    public static class MigrationResult {
        private boolean success;
        private int usersImported;
        private int patientsImported;
        private int vitalsImported;
        private int aiNotesChecked;
        private int aiNotesImported;
        private int medicalFilesChecked;
        private int medicalHistoryImported;
        private int medicationEventsImported;
        private int shiftHandoverChecked;
        private int shiftHandoverImported;
        private final StringBuilder log = new StringBuilder();

        public boolean isSuccess() { return success; }
        public int getUsersImported() { return usersImported; }
        public int getPatientsImported() { return patientsImported; }
        public int getVitalsImported() { return vitalsImported; }
        public int getAiNotesChecked() { return aiNotesChecked; }
        public int getAiNotesImported() { return aiNotesImported; }
        public int getMedicalFilesChecked() { return medicalFilesChecked; }
        public int getMedicalHistoryImported() { return medicalHistoryImported; }
        public int getMedicationEventsImported() { return medicationEventsImported; }
        public int getShiftHandoverChecked() { return shiftHandoverChecked; }
        public int getShiftHandoverImported() { return shiftHandoverImported; }

        public String getSummary() {
            return log.toString();
        }

        private void append(String message) {
            log.append("[SQLite Migration] ").append(message).append(System.lineSeparator());
        }
    }
}
