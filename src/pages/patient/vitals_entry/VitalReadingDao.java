package pages.patient.vitals_entry;

import app.database.CrudDao;

import java.sql.SQLException;
import java.util.List;

public interface VitalReadingDao extends CrudDao<VitalRecord, String> {
    List<VitalRecord> findByPatientId(String patientId) throws SQLException;
}
