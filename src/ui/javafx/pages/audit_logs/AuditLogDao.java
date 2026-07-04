package ui.javafx.pages.audit_logs;

import java.sql.SQLException;

public interface AuditLogDao {
    void log(String username, String action) throws SQLException;
}
