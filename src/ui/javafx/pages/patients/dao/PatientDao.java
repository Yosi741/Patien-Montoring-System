package ui.javafx.pages.patients.dao;

import app.Dao;
import ui.javafx.pages.patients.Patient;

import java.sql.SQLException;
import java.util.List;

public interface PatientDao extends Dao<Patient, String> {
    List<Patient> findBySection(String section) throws SQLException;

    List<Patient> findActivePatients() throws SQLException;
}
