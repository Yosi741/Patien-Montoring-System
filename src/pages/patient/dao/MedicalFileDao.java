package pages.patient.dao;

import pages.patient.MedicalFile;

import java.sql.SQLException;

public interface MedicalFileDao {
    boolean save(MedicalFile medicalFile, String extractedSummary) throws SQLException;

    int count() throws SQLException;
}
