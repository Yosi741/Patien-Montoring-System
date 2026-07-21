package pages.patient.patient_detail;

import app.database.DatabaseManager;
import pages.patient.model.PatientVisit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Stores and retrieves patient visit history from the SQLite patient_visits table.
 */
public class SqlitePatientVisitDao implements PatientVisitDao {

    /**
     * Finds by patient ID in SQLite.
     */
    @Override
    public List<PatientVisit> findByPatientId(String patientId) throws SQLException {
        ArrayList<PatientVisit> visits = new ArrayList<>();
        String sql = "SELECT id, patient_id, visit_date, discharge_date, status, report, created_at "
                + "FROM patient_visits WHERE patient_id = ? "
                + "ORDER BY datetime(COALESCE(visit_date, created_at)) DESC, id DESC";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, patientId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    visits.add(mapVisit(resultSet));
                }
            }
        }
        return visits;
    }

    /**
     * Finds latest active visit in SQLite.
     */
    @Override
    public Optional<PatientVisit> findLatestActiveVisit(String patientId) throws SQLException {
        String sql = "SELECT id, patient_id, visit_date, discharge_date, status, report, created_at "
                + "FROM patient_visits WHERE patient_id = ? "
                + "AND UPPER(COALESCE(status, '')) = 'ACTIVE' "
                + "AND (discharge_date IS NULL OR TRIM(discharge_date) = '') "
                + "ORDER BY datetime(COALESCE(visit_date, created_at)) DESC, id DESC LIMIT 1";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, patientId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapVisit(resultSet));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Creates visit for the patient workflow.
     */
    @Override
    public void createVisit(String patientId, String visitDate, String status, String report) throws SQLException {
        String sql = "INSERT INTO patient_visits(patient_id, visit_date, discharge_date, status, report, created_at) "
                + "VALUES(?, ?, NULL, ?, ?, CURRENT_TIMESTAMP)";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, patientId);
            statement.setString(2, visitDate);
            statement.setString(3, status == null || status.isBlank() ? "ACTIVE" : status.trim().toUpperCase());
            statement.setString(4, report == null ? "" : report.trim());
            statement.executeUpdate();
        }
    }

    /**
     * Discharges active visit while preserving its visit history.
     */
    @Override
    public boolean dischargeActiveVisit(String patientId, String dischargeDate, String report) throws SQLException {
        String sql = "UPDATE patient_visits "
                + "SET discharge_date = ?, status = 'DISCHARGED', report = ? "
                + "WHERE id = ("
                + "  SELECT id FROM patient_visits "
                + "  WHERE patient_id = ? "
                + "  AND UPPER(COALESCE(status, '')) = 'ACTIVE' "
                + "  AND (discharge_date IS NULL OR TRIM(discharge_date) = '') "
                + "  ORDER BY datetime(COALESCE(visit_date, created_at)) DESC, id DESC LIMIT 1"
                + ")";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, dischargeDate);
            statement.setString(2, report == null ? "" : report.trim());
            statement.setString(3, patientId);
            return statement.executeUpdate() > 0;
        }
    }

    /**
     * Maps visit to the corresponding application model.
     */
    private PatientVisit mapVisit(ResultSet resultSet) throws SQLException {
        return new PatientVisit(
                resultSet.getLong("id"),
                resultSet.getString("patient_id"),
                resultSet.getString("visit_date"),
                resultSet.getString("discharge_date"),
                resultSet.getString("status"),
                resultSet.getString("report"),
                resultSet.getString("created_at")
        );
    }
}
