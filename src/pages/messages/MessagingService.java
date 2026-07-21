package pages.messages;

import pages.patient.Add_Edit_Patient_Dao;
import pages.user.profile_settings.SqliteUserDao;
import app.helpers.FormValidationHelper;
import app.helpers.PermissionHelper;
import pages.user.User;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Validates message recipients and linked patients before saving clinic messages.
 */
public class MessagingService {

    private final SqliteMessageDao messageDao;
    private final SqliteUserDao userDao;
    private final Add_Edit_Patient_Dao patientDao;

    /**
     * Creates the service with the dependencies used by the messaging workflow.
     */
    public MessagingService() {
        this(new SqliteMessageDao(), new SqliteUserDao(), new Add_Edit_Patient_Dao());
    }

    /**
     * Creates the service with the dependencies used by the messaging workflow.
     */
    public MessagingService(SqliteMessageDao messageDao, SqliteUserDao userDao, Add_Edit_Patient_Dao patientDao) {
        this.messageDao = messageDao;
        this.userDao = userDao;
        this.patientDao = patientDao;
    }

    /**
     * Sends message to the selected recipient through SQLite messaging.
     */
    public long sendMessage(User sender, SqliteMessageDao.MessageWriteRecord record) throws SQLException {
        require(PermissionHelper.canComposeMessage(sender), "Login is required to send internal messages.");
        if (record != null && !record.getRecipientUsername().isBlank()
                && record.getRecipientUsername().equalsIgnoreCase(username(sender))) {
            throw new IllegalArgumentException("You cannot send a message to yourself.");
        }
        validate(record);
        validateTargetPermission(sender, record);
        return messageDao.insert(record);
    }

    /**
     * Returns received messages visible to the current user.
     */
    public List<SqliteMessageDao.MessageRow> inbox(User currentUser, String search, String status) throws SQLException {
        return messageDao.findInbox(username(currentUser), PermissionHelper.roleGroup(currentUser), section(currentUser), search, status)
                .stream()
                .filter(row -> !isRequestMessage(row))
                .toList();
    }

    /**
     * Returns messages sent by the current user.
     */
    public List<SqliteMessageDao.MessageRow> sent(User currentUser, String search, String status) throws SQLException {
        return messageDao.findSent(username(currentUser), search, status)
                .stream()
                .filter(row -> !isRequestMessage(row))
                .toList();
    }

    /**
     * Validates and submits requests.
     */
    public List<SqliteMessageDao.MessageRow> requests(User currentUser, String search, String requestFilter) throws SQLException {
        Map<Long, SqliteMessageDao.MessageRow> byId = new LinkedHashMap<>();
        for (SqliteMessageDao.MessageRow row : messageDao.findInbox(username(currentUser),
                PermissionHelper.roleGroup(currentUser), section(currentUser), search, "All")) {
            if (isRequestMessage(row) && matchesRequestFilter(row, requestFilter)) {
                byId.put(row.getId(), row);
            }
        }
        for (SqliteMessageDao.MessageRow row : messageDao.findSent(username(currentUser), search, "All")) {
            if (isRequestMessage(row) && matchesRequestFilter(row, requestFilter)) {
                byId.put(row.getId(), row);
            }
        }
        return new ArrayList<>(byId.values());
    }

    /**
     * Validates and submits type.
     */
    public String requestType(SqliteMessageDao.MessageRow row) {
        String text = messageText(row);
        if (containsVisitRequestText(text)) {
            return "VISIT_REQUEST";
        }
        if (text.contains("treatment review")) {
            return "TREATMENT_REVIEW";
        }
        if (text.contains("request")) {
            return "REQUEST";
        }
        return "INTERNAL_REQUEST";
    }

    /**
     * Determines whether is request message for the current record or user.
     */
    public boolean isRequestMessage(SqliteMessageDao.MessageRow row) {
        String text = messageText(row);
        return text.contains("request")
                || containsVisitRequestText(text)
                || text.contains("treatment review");
    }

    /**
     * Marks read with its new workflow state.
     */
    public void markRead(User currentUser, long messageId) throws SQLException {
        messageDao.markRead(messageId, username(currentUser));
    }

    /**
     * Archives archive while preserving its stored history.
     */
    public void archive(User currentUser, long messageId) throws SQLException {
        messageDao.archive(messageId, username(currentUser));
    }

    /**
     * Validates validate against the active business rules.
     */
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

    /**
     * Validates target permission against the active business rules.
     */
    private void validateTargetPermission(User sender, SqliteMessageDao.MessageWriteRecord record) {
        require(PermissionHelper.canComposeMessage(sender), "Login is required to send internal messages.");
    }

    /**
     * Determines whether the current value matches request filter.
     */
    private boolean matchesRequestFilter(SqliteMessageDao.MessageRow row, String requestFilter) {
        String filter = requestFilter == null || requestFilter.isBlank() ? "All Requests" : requestFilter;
        if ("All Requests".equalsIgnoreCase(filter)) {
            return true;
        }
        if ("Pending".equalsIgnoreCase(filter)) {
            return "SENT".equalsIgnoreCase(row.getStatus());
        }
        if ("Read".equalsIgnoreCase(filter)) {
            return "READ".equalsIgnoreCase(row.getStatus());
        }
        if ("Archived".equalsIgnoreCase(filter)) {
            return "ARCHIVED".equalsIgnoreCase(row.getStatus());
        }
        if ("High Priority".equalsIgnoreCase(filter)) {
            return "HIGH".equalsIgnoreCase(row.getPriority()) || "URGENT".equalsIgnoreCase(row.getPriority());
        }
        return true;
    }

    /**
     * Returns formatted display text for message text.
     */
    private String messageText(SqliteMessageDao.MessageRow row) {
        if (row == null) {
            return "";
        }
        return ((row.getSubject() == null ? "" : row.getSubject()) + "\n"
                + (row.getBody() == null ? "" : row.getBody()) + "\n"
                + (row.getPriority() == null ? "" : row.getPriority())).toLowerCase(Locale.ROOT);
    }

    /**
     * Determines whether the normalized content contains visit request text.
     */
    private boolean containsVisitRequestText(String text) {
        return text.contains("visit request")
                || text.contains("schedule visit")
                || text.contains("follow-up")
                || text.contains("follow up")
                || text.contains("checkup")
                || text.contains("check-up");
    }

    /**
     * Validates priority against the active business rules.
     */
    private FormValidationHelper.ValidationResult validatePriority(String priority) {
        String value = priority == null ? "" : priority.trim().toUpperCase(Locale.ROOT);
        return value.equals("NORMAL") || value.equals("HIGH") || value.equals("URGENT")
                ? FormValidationHelper.ValidationResult.ok()
                : FormValidationHelper.ValidationResult.error("Priority must be NORMAL, HIGH, or URGENT.");
    }

    /**
     * Counts targets.
     */
    private int countTargets(SqliteMessageDao.MessageWriteRecord record) {
        int count = 0;
        if (!record.getRecipientUsername().isBlank()) count++;
        if (!record.getRecipientRole().isBlank()) count++;
        if (!record.getRecipientSection().isBlank()) count++;
        return count;
    }


    /**
     * Enforces valid before the protected operation continues.
     */
    private void requireValid(FormValidationHelper.ValidationResult validation) {
        if (!validation.isValid()) {
            throw new IllegalArgumentException(validation.getMessage());
        }
    }

    /**
     * Enforces require before the protected operation continues.
     */
    private void require(boolean condition, String message) {
        if (!condition) {
            throw new SecurityException(message);
        }
    }

    /**
     * Returns the username associated with the current session or workflow record.
     */
    private String username(User user) {
        return user == null || user.getUsername() == null || user.getUsername().isBlank() ? "Unknown" : user.getUsername();
    }

    /**
     * Returns the clinic section associated with the current session or message.
     */
    private String section(User user) {
        return user == null || user.getSection() == null || user.getSection().isBlank() ? "All" : user.getSection();
    }
}
