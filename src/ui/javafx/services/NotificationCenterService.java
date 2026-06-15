package ui.javafx.services;

import Data_Access_Object.SqliteNotificationDao;
import Data_Access_Object.SqliteMessageDao;
import ui.javafx.helpers.AuditAction;
import ui.javafx.helpers.AuditWriteHelper;
import ui.javafx.helpers.FormValidationHelper;
import ui.javafx.helpers.PermissionHelper;
import users.User;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class NotificationCenterService {

    private final SqliteNotificationDao notificationDao;
    private final SqliteMessageDao messageDao;

    public NotificationCenterService() {
        this(new SqliteNotificationDao(), new SqliteMessageDao());
    }

    public NotificationCenterService(SqliteNotificationDao notificationDao) {
        this(notificationDao, new SqliteMessageDao());
    }

    public NotificationCenterService(SqliteNotificationDao notificationDao, SqliteMessageDao messageDao) {
        this.notificationDao = notificationDao;
        this.messageDao = messageDao;
    }

    public long createNotification(SqliteNotificationDao.NotificationWriteRecord record) throws SQLException {
        validate(record);
        return notificationDao.insert(record);
    }

    public List<SqliteNotificationDao.NotificationRow> findForCurrentUser(User user, String severity, String status,
                                                                          String patientSearch, String dateRange) throws SQLException {
        require(PermissionHelper.canViewNotifications(user), "Login is required to view notifications.");
        ArrayList<SqliteNotificationDao.NotificationRow> rows = new ArrayList<>(notificationDao.findForUser(
                username(user), PermissionHelper.roleGroup(user), section(user),
                severity, status, patientSearch, dateRange));
        rows.addAll(findMessageRows(user, severity, status, patientSearch, dateRange));
        rows.sort(Comparator
                .comparing((SqliteNotificationDao.NotificationRow row) -> parseDateTime(row.getCreatedAt()), Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(SqliteNotificationDao.NotificationRow::getId, Comparator.reverseOrder()));
        return rows;
    }

    public int unreadCount(User user) {
        try {
            if (!PermissionHelper.canViewNotifications(user)) {
                return 0;
            }
            return notificationDao.unreadCountForUser(username(user), PermissionHelper.roleGroup(user), section(user))
                    + messageDao.unreadInboxCount(username(user), PermissionHelper.roleGroup(user), section(user));
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

    public void markRead(User user, SqliteNotificationDao.NotificationRow row) throws SQLException {
        require(PermissionHelper.canViewNotifications(user), "Login is required to update notifications.");
        if (isMessageRow(row)) {
            long messageId = parseSourceId(row);
            messageDao.markRead(messageId, username(user));
            AuditWriteHelper.write(username(user), AuditAction.READ_MESSAGE, "message_id=" + messageId);
            return;
        }
        markRead(user, row.getId());
    }

    public void dismiss(User user, long id) throws SQLException {
        require(PermissionHelper.canViewNotifications(user), "Login is required to update notifications.");
        notificationDao.dismiss(id);
        AuditWriteHelper.write(username(user), AuditAction.DISMISS_NOTIFICATION, "notification_id=" + id);
    }

    public void dismiss(User user, SqliteNotificationDao.NotificationRow row) throws SQLException {
        require(PermissionHelper.canViewNotifications(user), "Login is required to update notifications.");
        if (isMessageRow(row)) {
            long messageId = parseSourceId(row);
            messageDao.archive(messageId, username(user));
            AuditWriteHelper.write(username(user), AuditAction.ARCHIVE_MESSAGE, "message_id=" + messageId);
            return;
        }
        dismiss(user, row.getId());
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

    private List<SqliteNotificationDao.NotificationRow> findMessageRows(User user, String severity, String status,
                                                                        String patientSearch, String dateRange) throws SQLException {
        ArrayList<SqliteNotificationDao.NotificationRow> rows = new ArrayList<>();
        List<SqliteMessageDao.MessageRow> messages = messageDao.findInbox(
                username(user),
                PermissionHelper.roleGroup(user),
                section(user),
                "",
                "All"
        );
        for (SqliteMessageDao.MessageRow message : messages) {
            SqliteNotificationDao.NotificationRow row = toMessageNotificationRow(user, message);
            if (matchesFilters(row, severity, status, patientSearch, dateRange)) {
                rows.add(row);
            }
        }
        return rows;
    }

    private SqliteNotificationDao.NotificationRow toMessageNotificationRow(User user, SqliteMessageDao.MessageRow message) {
        return new SqliteNotificationDao.NotificationRow(
                -message.getId(),
                username(user),
                "",
                "",
                nullTo(message.getPatientId(), ""),
                prioritySeverity(message.getPriority()),
                nullTo(message.getSubject(), "Message"),
                "From " + nullTo(message.getSenderUsername(), "Unknown") + ": " + nullTo(message.getBody(), ""),
                messageStatus(message.getStatus()),
                "MESSAGE",
                String.valueOf(message.getId()),
                message.getCreatedAt(),
                message.getReadAt()
        );
    }

    private boolean matchesFilters(SqliteNotificationDao.NotificationRow row, String severity, String status,
                                   String patientSearch, String dateRange) {
        if (severity != null && !severity.isBlank() && !"All".equalsIgnoreCase(severity)
                && !severity.equalsIgnoreCase(row.getSeverity())) {
            return false;
        }
        if (status != null && !status.isBlank() && !"All".equalsIgnoreCase(status)
                && !status.equalsIgnoreCase(row.getStatus())) {
            return false;
        }
        if (patientSearch != null && !patientSearch.trim().isEmpty()) {
            String patientId = nullTo(row.getPatientId(), "");
            if (!patientId.contains(patientSearch.trim())) {
                return false;
            }
        }
        if (dateRange != null && !dateRange.isBlank() && !"All".equalsIgnoreCase(dateRange)) {
            LocalDate date = parseDate(row.getCreatedAt());
            if (date == null) {
                return false;
            }
            LocalDate today = LocalDate.now();
            if ("Today".equalsIgnoreCase(dateRange)) {
                return date.equals(today);
            }
            if ("Last 7 days".equalsIgnoreCase(dateRange)) {
                return !date.isBefore(today.minusDays(7));
            }
            return !date.isBefore(today.minusDays(30));
        }
        return true;
    }

    private String prioritySeverity(String priority) {
        String value = priority == null ? "" : priority.trim().toUpperCase(Locale.ROOT);
        if ("URGENT".equals(value) || "HIGH".equals(value)) {
            return "WARNING";
        }
        return "INFO";
    }

    private String messageStatus(String status) {
        if ("READ".equalsIgnoreCase(status)) {
            return "READ";
        }
        if ("ARCHIVED".equalsIgnoreCase(status)) {
            return "DISMISSED";
        }
        return "UNREAD";
    }

    private boolean isMessageRow(SqliteNotificationDao.NotificationRow row) {
        return row != null && "MESSAGE".equalsIgnoreCase(row.getSourceType());
    }

    private long parseSourceId(SqliteNotificationDao.NotificationRow row) {
        try {
            return Long.parseLong(row.getSourceId());
        } catch (Exception e) {
            throw new IllegalArgumentException("Message source is missing or invalid.");
        }
    }

    private LocalDate parseDate(String value) {
        LocalDateTime dateTime = parseDateTime(value);
        return dateTime == null ? null : dateTime.toLocalDate();
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (DateTimeFormatter formatter : List.of(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"))) {
            try {
                return LocalDateTime.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
                // Try the next known local format.
            }
        }
        try {
            return LocalDate.parse(value.substring(0, Math.min(10, value.length()))).atStartOfDay();
        } catch (Exception ignored) {
            return null;
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
