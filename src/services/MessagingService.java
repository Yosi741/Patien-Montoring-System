package services;

import dao.SqliteMessageDao;
import dao.SqlitePatientDao;
import dao.SqliteUserDao;
import ui.javafx.helpers.AuditAction;
import ui.javafx.helpers.AuditWriteHelper;
import ui.javafx.helpers.FormValidationHelper;
import ui.javafx.helpers.PermissionHelper;
import users.User;

import java.sql.SQLException;
import java.util.List;
import java.util.Locale;

public class MessagingService {

    private final SqliteMessageDao messageDao;
    private final SqliteUserDao userDao;
    private final SqlitePatientDao patientDao;

    public MessagingService() {
        this(new SqliteMessageDao(), new SqliteUserDao(), new SqlitePatientDao());
    }

    public MessagingService(SqliteMessageDao messageDao, SqliteUserDao userDao, SqlitePatientDao patientDao) {
        this.messageDao = messageDao;
        this.userDao = userDao;
        this.patientDao = patientDao;
    }

    public long sendMessage(User sender, SqliteMessageDao.MessageWriteRecord record) throws SQLException {
        require(PermissionHelper.canComposeMessage(sender), "Only Admin, Doctor, and Nurse users can compose messages in JavaFX.");
        validate(record);
        validateTargetPermission(sender, record);
        long id = messageDao.insert(record);
        AuditWriteHelper.write(username(sender), AuditAction.SEND_MESSAGE,
                "message_id=" + id + ", target=" + targetSummary(record) + ", subject=" + record.getSubject());
        return id;
    }

    public List<SqliteMessageDao.MessageRow> inbox(User currentUser, String search, String status) throws SQLException {
        return messageDao.findInbox(username(currentUser), PermissionHelper.roleGroup(currentUser), section(currentUser), search, status);
    }

    public List<SqliteMessageDao.MessageRow> sent(User currentUser, String search, String status) throws SQLException {
        return messageDao.findSent(username(currentUser), search, status);
    }

    public void markRead(User currentUser, long messageId) throws SQLException {
        messageDao.markRead(messageId, username(currentUser));
        AuditWriteHelper.write(username(currentUser), AuditAction.READ_MESSAGE, "message_id=" + messageId);
    }

    public void archive(User currentUser, long messageId) throws SQLException {
        messageDao.archive(messageId, username(currentUser));
        AuditWriteHelper.write(username(currentUser), AuditAction.ARCHIVE_MESSAGE, "message_id=" + messageId);
    }

    private void validate(SqliteMessageDao.MessageWriteRecord record) throws SQLException {
        FormValidationHelper.ValidationResult validation = FormValidationHelper.combine(
                FormValidationHelper.validateRequired("Subject", record.getSubject()),
                FormValidationHelper.validateMaxLength("Subject", record.getSubject(), 120),
                FormValidationHelper.validateRequired("Body", record.getBody()),
                FormValidationHelper.validateMaxLength("Body", record.getBody(), 4000),
                FormValidationHelper.validateRequired("Priority", record.getPriority()),
                validatePriority(record.getPriority())
        );
        requireValid(validation);
        int targetCount = countTargets(record);
        if (targetCount != 1) {
            throw new IllegalArgumentException("Choose exactly one recipient target: user, role, or section.");
        }
        if (!record.getRecipientUsername().isBlank() && !userDao.usernameExists(record.getRecipientUsername())) {
            throw new IllegalArgumentException("Recipient user does not exist in SQLite: " + record.getRecipientUsername());
        }
        if (!record.getPatientId().isBlank() && !patientDao.existsByPatientId(record.getPatientId())) {
            throw new IllegalArgumentException("Linked patient does not exist in SQLite: " + record.getPatientId());
        }
    }

    private void validateTargetPermission(User sender, SqliteMessageDao.MessageWriteRecord record) {
        String senderRole = PermissionHelper.roleGroup(sender);
        if ("ADMIN".equals(senderRole)) {
            return;
        }
        if ("DOCTOR".equals(senderRole)) {
            if (!record.getRecipientRole().isBlank()) {
                String target = record.getRecipientRole().toUpperCase(Locale.ROOT);
                require(target.equals("ADMIN") || target.equals("DOCTOR") || target.equals("NURSE"),
                        "Doctors can message Admin, Doctor, Nurse roles, or their section.");
            }
            if (!record.getRecipientSection().isBlank()) {
                require(record.getRecipientSection().equalsIgnoreCase(section(sender)),
                        "Doctors can message only their own section.");
            }
            return;
        }
        if ("NURSE".equals(senderRole)) {
            if (!record.getRecipientRole().isBlank()) {
                String target = record.getRecipientRole().toUpperCase(Locale.ROOT);
                require(target.equals("ADMIN") || target.equals("DOCTOR") || target.equals("NURSE"),
                        "Nurses can message Admin, Doctor, or Nurse roles.");
            }
            if (!record.getRecipientSection().isBlank()) {
                require(record.getRecipientSection().equalsIgnoreCase(section(sender)),
                        "Nurses can message only their own section.");
            }
            return;
        }
        throw new SecurityException("This role cannot compose messages.");
    }

    private FormValidationHelper.ValidationResult validatePriority(String priority) {
        String value = priority == null ? "" : priority.trim().toUpperCase(Locale.ROOT);
        return value.equals("NORMAL") || value.equals("HIGH") || value.equals("URGENT")
                ? FormValidationHelper.ValidationResult.ok()
                : FormValidationHelper.ValidationResult.error("Priority must be NORMAL, HIGH, or URGENT.");
    }

    private int countTargets(SqliteMessageDao.MessageWriteRecord record) {
        int count = 0;
        if (!record.getRecipientUsername().isBlank()) count++;
        if (!record.getRecipientRole().isBlank()) count++;
        if (!record.getRecipientSection().isBlank()) count++;
        return count;
    }

    private String targetSummary(SqliteMessageDao.MessageWriteRecord record) {
        if (!record.getRecipientUsername().isBlank()) return "user:" + record.getRecipientUsername();
        if (!record.getRecipientRole().isBlank()) return "role:" + record.getRecipientRole();
        return "section:" + record.getRecipientSection();
    }

    private void requireValid(FormValidationHelper.ValidationResult validation) {
        if (!validation.isValid()) {
            throw new IllegalArgumentException(validation.getMessage());
        }
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
}
