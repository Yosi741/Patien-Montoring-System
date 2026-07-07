package pages.patient.dao;

import pages.patient.PatientVisit;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public interface PatientVisitDao {

    List<PatientVisit> findByPatientId(String patientId) throws SQLException;

    Optional<PatientVisit> findLatestActiveVisit(String patientId) throws SQLException;

    void createVisit(String patientId, String visitDate, String status, String report) throws SQLException;

    boolean dischargeActiveVisit(String patientId, String dischargeDate, String report) throws SQLException;
}
