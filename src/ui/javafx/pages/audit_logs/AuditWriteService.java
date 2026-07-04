package ui.javafx.pages.audit_logs;

import java.sql.SQLException;

public class AuditWriteService {

    private final SqliteAuditLogDao auditLogDao;

    public AuditWriteService() {
        this(new SqliteAuditLogDao());
    }

    public AuditWriteService(SqliteAuditLogDao auditLogDao) {
        this.auditLogDao = auditLogDao;
    }

    public void createAuditEvent(String username, String action, String detail) throws SQLException {
        auditLogDao.log(username, formatAction(action, detail));
    }

    private String formatAction(String action, String detail) {
        String safeAction = action == null || action.isBlank() ? "JAVAFX_WRITE_ACTION" : action.trim();
        if (detail == null || detail.isBlank()) {
            return safeAction;
        }
        return safeAction + " - " + detail.trim();
    }
}
