package pages.patient.patient_directory;

import app.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads and filters rows for the Patient Management directory table.
 */
public class PatientDirectoryRepository {

    /**
     * Finds patients for the directory table using the supplied search and status filters.
     */
    public List<PatientListRow> findPatientsForDirectory(PatientListFilter filter) throws SQLException {
        ArrayList<PatientListRow> rows = new ArrayList<>();
        PatientListFilter safeFilter = filter == null ? new PatientListFilter() : filter;
        ArrayList<String> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT patient_id, first_name, last_name, birth_date, gender, status, priority, ")
                .append("COALESCE(blood_type, 'Unknown') AS blood_type, ")
                .append("COALESCE(phone, '') AS phone, ")
                .append("COALESCE(email, '') AS email ")
                .append("FROM patients ")
                .append("WHERE 1 = 1 ");

        if (hasText(safeFilter.getSearch())) {
            sql.append("AND (patient_id LIKE ? OR first_name LIKE ? OR last_name LIKE ? OR (first_name || ' ' || last_name) LIKE ? OR phone LIKE ? OR email LIKE ?) ");
            String like = "%" + safeFilter.getSearch().trim() + "%";
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
        }
        appendDisplayStatusFilter(sql, safeFilter.getDisplayStatus());

        sql.append("ORDER BY CASE UPPER(priority) ")
                .append("WHEN 'EMERGENCY' THEN 1 ")
                .append("WHEN 'CRITICAL' THEN 2 ")
                .append("WHEN 'HIGH' THEN 3 ")
                .append("WHEN 'WARNING' THEN 3 ")
                .append("WHEN 'NORMAL' THEN 4 ")
                .append("ELSE 6 END, datetime(updated_at) DESC, last_name, first_name, patient_id");

        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                statement.setString(i + 1, params.get(i));
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(mapPatientListRow(resultSet));
                }
            }
        }
        return rows;
    }

    /**
     * Adds the selected presentation status filter to the patient directory query.
     */
    private void appendDisplayStatusFilter(StringBuilder sql, String displayStatus) {
        if (!hasText(displayStatus) || "All".equalsIgnoreCase(displayStatus)) {
            return;
        }
        switch (displayStatus.trim().toUpperCase()) {
            case "ACTIVE":
                sql.append("AND UPPER(status) NOT IN ('DISCHARGED', 'INACTIVE', 'DEACTIVATED') ")
                        .append("AND UPPER(priority) NOT IN ('CRITICAL', 'EMERGENCY') ");
                break;
            case "CRITICAL":
                sql.append("AND UPPER(status) NOT IN ('DISCHARGED', 'INACTIVE', 'DEACTIVATED') ")
                        .append("AND UPPER(priority) IN ('CRITICAL', 'EMERGENCY') ");
                break;
            case "DISCHARGED":
                sql.append("AND UPPER(status) = 'DISCHARGED' ");
                break;
            case "ARCHIVED":
            case "INACTIVE":
                sql.append("AND UPPER(status) IN ('INACTIVE', 'DEACTIVATED') ");
                break;
            default:
                break;
        }
    }

    /**
     * Maps the current SQLite result row into the patient directory row model.
     */
    private PatientListRow mapPatientListRow(ResultSet resultSet) throws SQLException {
        return new PatientListRow(
                resultSet.getString("patient_id"),
                resultSet.getString("first_name") + " " + resultSet.getString("last_name"),
                resultSet.getString("birth_date"),
                resultSet.getString("gender"),
                resultSet.getString("status"),
                resultSet.getString("priority"),
                resultSet.getString("blood_type"),
                resultSet.getString("phone"),
                resultSet.getString("email")
        );
    }

    /**
     * Returns true when a text value contains non-blank content.
     */
    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

