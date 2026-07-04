package ui.javafx.pages.patients.dao;

import app.Dao;
import ui.javafx.pages.patients.vitals_entry.VitalRecord;

import java.sql.SQLException;
import java.util.List;

public interface VitalReadingDao extends Dao<VitalRecord, String> {
    List<VitalRecord> findByPatientId(String patientId) throws SQLException;
}
