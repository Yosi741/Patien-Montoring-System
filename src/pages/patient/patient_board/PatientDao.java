package pages.patient.patient_board;

import app.database.CrudDao;
import pages.patient.patient_detail.Patient;

import java.sql.SQLException;
import java.util.List;

public interface PatientDao extends CrudDao<Patient, String> {
    List<Patient> findBySection(String section) throws SQLException;

}
