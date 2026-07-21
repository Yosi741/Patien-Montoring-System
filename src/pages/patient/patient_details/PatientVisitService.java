package pages.patient.patient_details;

import pages.patient.patient_details.PatientVisitDao;
import pages.patient.patient_details.SqlitePatientVisitDao;
import pages.patient.patient_details.PatientVisit;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * Maintains active visits, visit history, and discharge summaries through the patient-visit DAO.
 */
public class PatientVisitService {

    private static final DateTimeFormatter SQLITE_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final PatientVisitDao patientVisitDao;

    /**
     * Creates the service with the dependencies used by the patient workflow.
     */
    public PatientVisitService() {
        this(new SqlitePatientVisitDao());
    }

    /**
     * Creates the service with the dependencies used by the patient workflow.
     */
    public PatientVisitService(PatientVisitDao patientVisitDao) {
        this.patientVisitDao = patientVisitDao;
    }

    public List<PatientVisit> getVisitHistory(String patientId) throws SQLException {
        return patientVisitDao.findByPatientId(patientId);
    }

    /**
     * Ensures active visit exists before continuing.
     */
    public void ensureActiveVisit(String patientId, String openingReport) throws SQLException {
        Optional<PatientVisit> existingVisit = patientVisitDao.findLatestActiveVisit(patientId);
        if (existingVisit.isPresent()) {
            return;
        }
        patientVisitDao.createVisit(patientId, now(), "ACTIVE", blank(openingReport));
    }

    /**
     * Discharges visit while preserving its visit history.
     */
    public void dischargeVisit(String patientId, String dischargeSummary) throws SQLException {
        String summary = defaultDischargeSummary(dischargeSummary);
        boolean updated = patientVisitDao.dischargeActiveVisit(patientId, now(), summary);
        if (!updated) {
            patientVisitDao.createVisit(patientId, now(), "DISCHARGED", summary);
            patientVisitDao.dischargeActiveVisit(patientId, now(), summary);
        }
    }

    /**
     * Returns the current timestamp in the SQLite storage format.
     */
    private String now() {
        return LocalDateTime.now().format(SQLITE_DATE_TIME);
    }

    /**
     * Normalizes blank blank to the workflow fallback value.
     */
    private String blank(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * Returns the default discharge summary used by this workflow.
     */
    private String defaultDischargeSummary(String value) {
        String trimmed = blank(value);
        return trimmed.isEmpty() ? "Visit closed without a summary." : trimmed;
    }
}



