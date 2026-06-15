package Data_Access_Object;

import models.Patient;

import java.sql.SQLException;
import java.util.List;

public interface PatientDao extends Dao<Patient, String> {
    List<Patient> findBySection(String section) throws SQLException;

    List<Patient> findActivePatients() throws SQLException;
}
