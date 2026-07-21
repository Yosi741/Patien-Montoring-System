package pages.patient.patient_details;

import pages.patient.patient_details.PatientVisit;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Defines persistence operations for visit history stored in the SQLite patient_visits table.
 */
public interface PatientVisitDao {

    /**
     * Finds by patient ID in SQLite.
     */
    List<PatientVisit> findByPatientId(String patientId) throws SQLException;

    /**
     * Finds latest active visit in SQLite.
     */
    Optional<PatientVisit> findLatestActiveVisit(String patientId) throws SQLException;

    /**
     * Creates visit for the patient workflow.
     */
    void createVisit(String patientId, String visitDate, String status, String report) throws SQLException;

    /**
     * Discharges active visit while preserving its visit history.
     */
    boolean dischargeActiveVisit(String patientId, String dischargeDate, String report) throws SQLException;
}



