package pages.patient.dao;

import app.database.CrudDao;
import pages.patient.VitalRecord;

import java.sql.SQLException;
import java.util.List;

public interface VitalReadingDao extends CrudDao<VitalRecord, String> {
    List<VitalRecord> findByPatientId(String patientId) throws SQLException;
}
