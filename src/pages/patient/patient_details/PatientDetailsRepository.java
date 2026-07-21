package pages.patient.patient_details;

import app.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Loads patient file details and patient-linked record metadata from SQLite.
 */
public class PatientDetailsRepository {

    /**
     * Finds the full patient details by patient ID.
     */
    public Optional<PatientDetail> findPatientDetailsById(String patientId) throws SQLException {
        String sql = "SELECT patient_id, first_name, last_name, birth_date, gender, status, priority, "
                + "COALESCE(blood_type, 'Unknown') AS blood_type, diagnosis, COALESCE(allergies, 'Unknown') AS allergies, "
                + "COALESCE(phone, '') AS phone, COALESCE(email, '') AS email, COALESCE(address, '') AS address, "
                + "COALESCE(emergency_contact_name, '') AS emergency_contact_name, COALESCE(emergency_contact_phone, '') AS emergency_contact_phone "
                + "FROM patients WHERE patient_id = ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, patientId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapPatientDetail(resultSet));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Marks a patient inactive or discharged without deleting visit history.
     */
    public void deactivatePatientRecord(String patientId, String status) throws SQLException {
        String sql = "UPDATE patients SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE patient_id = ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status == null || status.isBlank() ? "DISCHARGED" : status);
            statement.setString(2, patientId);
            statement.executeUpdate();
        }
    }

    /**
     * Counts records linked to a patient file before delete confirmation.
     */
    public RelatedRecordCounts countPatientRelatedRecords(String patientId) throws SQLException {
        String normalizedPatientId = value(patientId);
        try (Connection connection = DatabaseManager.getConnection()) {
            return new RelatedRecordCounts(
                    countByPatientId(connection, "patient_visits", normalizedPatientId),
                    countByPatientId(connection, "vital_readings", normalizedPatientId),
                    countByPatientId(connection, "appointments", normalizedPatientId),
                    countByPatientId(connection, "medical_files", normalizedPatientId),
                    countByPatientId(connection, "billing_records", normalizedPatientId),
                    countByPatientId(connection, "alerts", normalizedPatientId),
                    countByPatientId(connection, "notifications", normalizedPatientId),
                    countByPatientId(connection, "messages", normalizedPatientId)
            );
        }
    }

    /**
     * Deletes a patient and its dependent local records inside one SQLite transaction.
     */
    public boolean deletePatientAndRelatedRecords(String patientId) throws SQLException {
        String normalizedPatientId = value(patientId);
        try (Connection connection = DatabaseManager.getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                deleteByPatientId(connection, "messages", normalizedPatientId);
                deleteByPatientId(connection, "notifications", normalizedPatientId);
                deleteByPatientId(connection, "alerts", normalizedPatientId);
                deleteByPatientId(connection, "billing_records", normalizedPatientId);
                deleteByPatientId(connection, "appointments", normalizedPatientId);
                deleteByPatientId(connection, "medical_files", normalizedPatientId);
                deleteByPatientId(connection, "vital_readings", normalizedPatientId);
                deleteByPatientId(connection, "patient_visits", normalizedPatientId);

                boolean deleted;
                try (PreparedStatement deletePatient = connection.prepareStatement(
                        "DELETE FROM patients WHERE patient_id = ?")) {
                    deletePatient.setString(1, normalizedPatientId);
                    deleted = deletePatient.executeUpdate() > 0;
                }
                connection.commit();
                return deleted;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }
        }
    }

    /**
     * Raises patient priority only when the requested priority is more urgent.
     */
    public boolean updatePatientPriorityIfHigher(String patientId, String requestedPriority) throws SQLException {
        Optional<PatientDetail> detail = findPatientDetailsById(patientId);
        if (detail.isEmpty()) {
            return false;
        }

        PatientDetail patient = detail.get();
        if (isTerminalStatus(patient.getStatus())) {
            return false;
        }

        String normalizedPriority = normalizePriority(requestedPriority);
        if (priorityRank(normalizedPriority) <= priorityRank(patient.getPriority())) {
            return false;
        }

        String sql = "UPDATE patients SET priority = ?, updated_at = CURRENT_TIMESTAMP WHERE patient_id = ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalizedPriority);
            statement.setString(2, patientId);
            return statement.executeUpdate() > 0;
        }
    }

    /**
     * Lowers patient priority only when stable clinical readings permit it.
     */
    public boolean updatePatientPriorityIfLower(String patientId, String requestedPriority) throws SQLException {
        Optional<PatientDetail> detail = findPatientDetailsById(patientId);
        if (detail.isEmpty()) {
            return false;
        }

        PatientDetail patient = detail.get();
        if (isTerminalStatus(patient.getStatus())) {
            return false;
        }

        String normalizedPriority = normalizePriority(requestedPriority);
        if (priorityRank(normalizedPriority) >= priorityRank(patient.getPriority())) {
            return false;
        }

        String sql = "UPDATE patients SET priority = ?, updated_at = CURRENT_TIMESTAMP WHERE patient_id = ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalizedPriority);
            statement.setString(2, patientId);
            return statement.executeUpdate() > 0;
        }
    }

    /**
     * Maps the current SQLite row into a full patient detail object.
     */
    private PatientDetail mapPatientDetail(ResultSet resultSet) throws SQLException {
        return new PatientDetail(
                resultSet.getString("patient_id"),
                resultSet.getString("first_name"),
                resultSet.getString("last_name"),
                resultSet.getString("birth_date"),
                resultSet.getString("gender"),
                resultSet.getString("status"),
                resultSet.getString("priority"),
                resultSet.getString("blood_type"),
                resultSet.getString("diagnosis"),
                resultSet.getString("allergies"),
                resultSet.getString("phone"),
                resultSet.getString("email"),
                resultSet.getString("address"),
                resultSet.getString("emergency_contact_name"),
                resultSet.getString("emergency_contact_phone")
        );
    }

    /**
     * Counts rows in a patient-linked table.
     */
    private int countByPatientId(Connection connection, String tableName, String patientId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM " + tableName + " WHERE patient_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, patientId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt(1) : 0;
            }
        }
    }

    /**
     * Deletes rows in a patient-linked table.
     */
    private void deleteByPatientId(Connection connection, String tableName, String patientId) throws SQLException {
        String sql = "DELETE FROM " + tableName + " WHERE patient_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, patientId);
            statement.executeUpdate();
        }
    }

    /**
     * Trims a nullable value for SQLite comparison.
     */
    private String value(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * Returns true for statuses that should not receive priority changes.
     */
    private boolean isTerminalStatus(String status) {
        if (status == null) {
            return false;
        }
        String normalized = status.trim().toUpperCase();
        return "DISCHARGED".equals(normalized)
                || "INACTIVE".equals(normalized)
                || "DEACTIVATED".equals(normalized);
    }

    /**
     * Normalizes patient priority to the stored application format.
     */
    private String normalizePriority(String priority) {
        if (priority == null || priority.isBlank()) {
            return "NORMAL";
        }
        String normalized = priority.trim().toUpperCase();
        if ("WARNING".equals(normalized)) {
            return "HIGH";
        }
        if ("EMERGENCY".equals(normalized)
                || "CRITICAL".equals(normalized)
                || "HIGH".equals(normalized)
                || "NORMAL".equals(normalized)) {
            return normalized;
        }
        return "NORMAL";
    }

    /**
     * Resolves priority urgency rank for comparisons.
     */
    private int priorityRank(String priority) {
        String normalized = normalizePriority(priority);
        switch (normalized) {
            case "EMERGENCY":
                return 4;
            case "CRITICAL":
                return 3;
            case "HIGH":
                return 2;
            default:
                return 1;
        }
    }
}



