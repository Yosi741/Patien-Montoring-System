package pages.patient.patient_detail;

import pages.patient.patient_board.PatientVisitDao;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

public class PatientVisitService {

    private static final DateTimeFormatter SQLITE_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final PatientVisitDao patientVisitDao;

    public PatientVisitService() {
        this(new SqlitePatientVisitDao());
    }

    public PatientVisitService(PatientVisitDao patientVisitDao) {
        this.patientVisitDao = patientVisitDao;
    }

    public List<PatientVisit> getVisitHistory(String patientId) throws SQLException {
        return patientVisitDao.findByPatientId(patientId);
    }

    public void ensureActiveVisit(String patientId, String openingReport) throws SQLException {
        Optional<PatientVisit> existingVisit = patientVisitDao.findLatestActiveVisit(patientId);
        if (existingVisit.isPresent()) {
            return;
        }
        patientVisitDao.createVisit(patientId, now(), "ACTIVE", blank(openingReport));
    }

    public void dischargeVisit(String patientId, String dischargeSummary) throws SQLException {
        String summary = defaultDischargeSummary(dischargeSummary);
        boolean updated = patientVisitDao.dischargeActiveVisit(patientId, now(), summary);
        if (!updated) {
            patientVisitDao.createVisit(patientId, now(), "DISCHARGED", summary);
            patientVisitDao.dischargeActiveVisit(patientId, now(), summary);
        }
    }

    private String now() {
        return LocalDateTime.now().format(SQLITE_DATE_TIME);
    }

    private String blank(String value) {
        return value == null ? "" : value.trim();
    }

    private String defaultDischargeSummary(String value) {
        String trimmed = blank(value);
        return trimmed.isEmpty() ? "Visit closed without a summary." : trimmed;
    }
}
