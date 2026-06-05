package services;

import dao.SqliteNotificationDao;
import ui.javafx.helpers.AuditAction;
import ui.javafx.helpers.AuditWriteHelper;
import ui.javafx.helpers.FormValidationHelper;
import ui.javafx.helpers.PermissionHelper;
import users.User;

import java.sql.SQLException;
import java.util.List;
import java.util.Locale;

public class NotificationCenterService {

    private final SqliteNotificationDao notificationDao;

    public NotificationCenterService() {
        this(new SqliteNotificationDao());
    }

    public NotificationCenterService(SqliteNotificationDao notificationDao) {
        this.notificationDao = notificationDao;
    }

    public long createNotification(SqliteNotificationDao.NotificationWriteRecord record) throws SQLException {
        validate(record);
        return notificationDao.insert(record);
    }

    public List<SqliteNotificationDao.NotificationRow> findForCurrentUser(User user, String severity, String status,
                                                                          String patientSearch, String dateRange) throws SQLException {
        require(PermissionHelper.canViewNotifications(user), "Login is required to view notifications.");
        return notificationDao.findForUser(username(user), PermissionHelper.roleGroup(user), section(user),
                severity, status, patientSearch, dateRange);
    }

    public int unreadCount(User user) {
        try {
            if (!PermissionHelper.canViewNotifications(user)) {
                return 0;
            }
            return notificationDao.unreadCountForUser(username(user), PermissionHelper.roleGroup(user), section(user));
        } catch (Exception e) {
            System.out.println("SQLite unread notification count failed: " + e.getMessage());
            return 0;
        }
    }

    public void markRead(User user, long id) throws SQLException {
        require(PermissionHelper.canViewNotifications(user), "Login is required to update notifications.");
        notificationDao.markRead(id);
        AuditWriteHelper.write(username(user), AuditAction.MARK_NOTIFICATION_READ, "notification_id=" + id);
    }

    public void dismiss(User user, long id) throws SQLException {
        require(PermissionHelper.canViewNotifications(user), "Login is required to update notifications.");
        notificationDao.dismiss(id);
        AuditWriteHelper.write(username(user), AuditAction.DISMISS_NOTIFICATION, "notification_id=" + id);
    }

    public void notifyCriticalAlert(String patientId, String severity, String message, String sourceId) {
        try {
            createNotification(new SqliteNotificationDao.NotificationWriteRecord(
                    "",
                    "DOCTOR",
                    "",
                    patientId,
                    "WARNING".equalsIgnoreCase(severity) ? "WARNING" : "CRITICAL",
                    "Patient alert: " + nullTo(patientId, "Unknown patient"),
                    message,
                    "ALERT",
                    sourceId
            ));
            createNotification(new SqliteNotificationDao.NotificationWriteRecord(
                    "",
                    "NURSE",
                    "",
                    patientId,
                    "WARNING".equalsIgnoreCase(severity) ? "WARNING" : "CRITICAL",
                    "Patient alert: " + nullTo(patientId, "Unknown patient"),
                    message,
                    "ALERT",
                    sourceId
            ));
        } catch (Exception e) {
            System.out.println("SQLite alert notification skipped: " + e.getMessage());
        }
    }

    public void notifyOverdueReminder(String patientId, String title, long reminderId) {
        try {
            createNotification(new SqliteNotificationDao.NotificationWriteRecord(
                    "",
                    "NURSE",
                    "",
                    patientId,
                    "WARNING",
                    "Overdue reminder",
                    title == null || title.isBlank() ? "A reminder is overdue." : title,
                    "REMINDER",
                    String.valueOf(reminderId)
            ));
        } catch (Exception e) {
            System.out.println("SQLite overdue reminder notification skipped: " + e.getMessage());
        }
    }

    private void validate(SqliteNotificationDao.NotificationWriteRecord record) {
        FormValidationHelper.ValidationResult validation = FormValidationHelper.combine(
                FormValidationHelper.validateRequired("Severity", record.getSeverity()),
                validateSeverity(record.getSeverity()),
                FormValidationHelper.validateRequired("Title", record.getTitle()),
                FormValidationHelper.validateMaxLength("Title", record.getTitle(), 140),
                FormValidationHelper.validateRequired("Message", record.getMessage()),
                FormValidationHelper.validateMaxLength("Message", record.getMessage(), 2000)
        );
        if (!validation.isValid()) {
            throw new IllegalArgumentException(validation.getMessage());
        }
    }

    private FormValidationHelper.ValidationResult validateSeverity(String severity) {
        String value = severity == null ? "" : severity.trim().toUpperCase(Locale.ROOT);
        return value.equals("INFO") || value.equals("WARNING") || value.equals("CRITICAL")
                ? FormValidationHelper.ValidationResult.ok()
                : FormValidationHelper.ValidationResult.error("Severity must be INFO, WARNING, or CRITICAL.");
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new SecurityException(message);
        }
    }

    private String username(User user) {
        return user == null || user.getUsername() == null || user.getUsername().isBlank() ? "Unknown" : user.getUsername();
    }

    private String section(User user) {
        return user == null || user.getSection() == null || user.getSection().isBlank() ? "All" : user.getSection();
    }

    private String nullTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
