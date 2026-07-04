package ui.javafx.pages.Alert;

import pages.patient.Patient;

public class AlertPersistenceService {

    private static final int DEFAULT_COOLDOWN_MINUTES = 10;
    private static final SqliteAlertDao ALERT_DAO = new SqliteAlertDao();

    private AlertPersistenceService() {
    }

    public static void persistCriticalPatientAlert(Patient patient) {
        if (patient == null) {
            return;
        }

        String message = "Critical alert active for patient "
                + patient.getName()
                + " in "
                + patient.getSection()
                + " room "
                + patient.getRoom();

        persistAlert(patient.getPatientId(), "CRITICAL", message, DEFAULT_COOLDOWN_MINUTES);
    }

    public static void persistAlert(String patientId, String severity, String message, int cooldownMinutes) {
        try {
            boolean inserted = ALERT_DAO.insertActiveAlertIfNotDuplicate(patientId, severity, message, cooldownMinutes);
            if (!inserted) {
                System.out.println("SQLite alert persistence skipped duplicate active alert for patient: " + patientId);
            }
        } catch (Exception e) {
            System.out.println("SQLite alert persistence failed: " + e.getMessage());
        }
    }

    public static void markAcknowledged(String patientId, String username) {
        updateLifecycle(patientId, username, "ACKNOWLEDGED");
        AlertSoundService.stopAlertSound();
    }

    public static void markStopped(String patientId, String username) {
        updateLifecycle(patientId, username, "ACKNOWLEDGED");
        AlertSoundService.stopAlertSound();
    }

    public static void markResolved(String patientId, String username) {
        updateLifecycle(patientId, username, "RESOLVED");
        AlertSoundService.stopAlertSound();
    }

    private static void updateLifecycle(String patientId, String username, String status) {
        try {
            if (patientId == null || patientId.isBlank()) {
                return;
            }
            boolean updated = ALERT_DAO.updateLatestActiveAlertStatus(patientId, status, username);
            if (!updated) {
                System.out.println("SQLite alert lifecycle sync found no ACTIVE alert for patient: " + patientId);
            }
        } catch (Exception e) {
            System.out.println("SQLite alert lifecycle sync failed: " + e.getMessage());
        }
    }
}
