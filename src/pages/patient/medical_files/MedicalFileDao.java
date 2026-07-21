package pages.patient.medical_files;

import pages.patient.medical_files.Upload.MedicalFile;

import java.sql.SQLException;

/**
 * Defines persistence operations for medical-file metadata in the SQLite medical_files table.
 */
public interface MedicalFileDao {
    /**
     * Validates and saves save.
     */
    boolean save(MedicalFile medicalFile, String extractedSummary) throws SQLException;

    /**
     * Counts count in SQLite.
     */
    int count() throws SQLException;
}
