package ui.javafx.patients.dao;

import models.MedicalFile;

import java.sql.SQLException;

public interface MedicalFileDao {
    boolean save(MedicalFile medicalFile, String extractedSummary) throws SQLException;

    int count() throws SQLException;
}
