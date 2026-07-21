package pages.alert;

/**
 * Persists patient and vital alerts while applying the shared duplicate-suppression rules.
 */
public class AlertPersistenceService {

    private static final SqliteAlertDao ALERT_DAO = new SqliteAlertDao();

    /**
     * Creates the service with the dependencies used by the alert workflow.
     */
    private AlertPersistenceService() {
    }

    /**
     * Persists alert in SQLite using the active workflow rules.
     */
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

}
