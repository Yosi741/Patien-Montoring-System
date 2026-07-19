package pages.patient.medical_files;

import java.sql.SQLException;

public interface MedicalFileDao {
    boolean save(MedicalFile medicalFile, String extractedSummary) throws SQLException;

    int count() throws SQLException;
}
