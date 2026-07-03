package ui.javafx.patients.dao;

import Data_Access_Object.Dao;
import models.VitalRecord;

import java.sql.SQLException;
import java.util.List;

public interface VitalReadingDao extends Dao<VitalRecord, String> {
    List<VitalRecord> findByPatientId(String patientId) throws SQLException;
}
