package pages.patient.dao;

import app.Dao;
import pages.patient.VitalRecord;

import java.sql.SQLException;
import java.util.List;

public interface VitalReadingDao extends Dao<VitalRecord, String> {
    List<VitalRecord> findByPatientId(String patientId) throws SQLException;
}
