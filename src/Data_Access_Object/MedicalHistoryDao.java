package dao;

import java.sql.SQLException;

public interface MedicalHistoryDao {
    boolean saveEntry(String patientId, String category, String details, String createdBy, String createdAt) throws SQLException;

    int count() throws SQLException;
}
