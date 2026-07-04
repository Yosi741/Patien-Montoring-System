package ui.javafx.pages.patients.dao;

import ui.javafx.pages.patients.medical_files.MedicalFile;

import java.sql.SQLException;

public interface MedicalFileDao {
    boolean save(MedicalFile medicalFile, String extractedSummary) throws SQLException;

    int count() throws SQLException;
}
