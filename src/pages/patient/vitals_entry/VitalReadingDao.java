package pages.patient.vitals_entry;

import app.database.CrudDao;

import java.sql.SQLException;
import java.util.List;

/**
 * Defines persistence operations for patient vital readings and timeline queries.
 */
public interface VitalReadingDao extends CrudDao<VitalRecord, String> {
    /**
     * Finds by patient ID in SQLite.
     */
    List<VitalRecord> findByPatientId(String patientId) throws SQLException;
}
