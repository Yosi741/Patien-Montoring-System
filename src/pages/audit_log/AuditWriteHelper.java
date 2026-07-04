package pages.audit_log;

import app.helpers.FormValidationHelper;

import java.sql.SQLException;

public final class AuditWriteHelper {

    private static final AuditWriteService AUDIT_WRITE_SERVICE = new AuditWriteService();

    private AuditWriteHelper() {
    }

    public static void write(String username, String action) throws SQLException {
        write(username, action, "");
    }

    public static void write(String username, String action, String detail) throws SQLException {
        FormValidationHelper.ValidationResult validation = FormValidationHelper.combine(
                FormValidationHelper.validateRequired("Audit action", action),
                FormValidationHelper.validateMaxLength("Audit action", action, 80),
                FormValidationHelper.validateMaxLength("Audit detail", detail, 220)
        );
        if (!validation.isValid()) {
            throw new IllegalArgumentException(validation.getMessage());
        }
        AUDIT_WRITE_SERVICE.createAuditEvent(username, action, detail);
    }
}
