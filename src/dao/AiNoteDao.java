package dao;

import java.sql.SQLException;

public interface AiNoteDao {
    boolean saveLegacyNote(String patientId, String sourceTitle, String note, String createdAt, int riskScore) throws SQLException;

    int count() throws SQLException;
}
