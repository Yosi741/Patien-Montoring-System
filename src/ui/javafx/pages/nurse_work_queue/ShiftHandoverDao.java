package ui.javafx.pages.nurse_work_queue;

import java.sql.SQLException;

public interface ShiftHandoverDao {
    boolean saveNote(String patientId, String fromUser, String toSection, String note, String createdAt) throws SQLException;

    int count() throws SQLException;
}
