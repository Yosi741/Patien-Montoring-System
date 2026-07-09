package pages.patient.dao;

import app.database.CrudDao;
import pages.patient.Patient;

import java.sql.SQLException;
import java.util.List;

public interface PatientDao extends CrudDao<Patient, String> {
    List<Patient> findBySection(String section) throws SQLException;

}
