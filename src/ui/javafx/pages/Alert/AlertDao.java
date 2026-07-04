package ui.javafx.pages.Alert;

import app.Dao;

import java.sql.SQLException;
import java.util.List;

public interface AlertDao extends Dao<Alert, String> {
    List<Alert> findActiveAlerts() throws SQLException;

    List<Alert> findByPatientId(String patientId) throws SQLException;
}
