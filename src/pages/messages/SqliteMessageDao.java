package pages.messages;

import app.database.DatabaseManager;
import app.database.SchemaInitializer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Reads and writes internal clinic messages in the SQLite messages table.
 */
public class SqliteMessageDao {

    /**
     * Creates the SQLite DAO and initializes any schema support it requires.
     */
    public SqliteMessageDao() {
        try {
            SchemaInitializer.initialize();
        } catch (Exception e) {
            System.out.println("SQLite message schema check failed: " + e.getMessage());
        }
    }

    /**
     * Inserts insert into SQLite.
     */
    public long insert(MessageWriteRecord record) throws SQLException {
        String sql = "INSERT INTO messages(sender_username, recipient_username, recipient_role, recipient_section, patient_id, subject, body, priority, status) "
                + "VALUES(?, ?, ?, ?, ?, ?, ?, ?, 'SENT')";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, record.getSenderUsername());
            statement.setString(2, blankToNull(record.getRecipientUsername()));
            statement.setString(3, blankToNull(record.getRecipientRole()));
            statement.setString(4, blankToNull(record.getRecipientSection()));
            statement.setString(5, blankToNull(record.getPatientId()));
            statement.setString(6, record.getSubject());
            statement.setString(7, record.getBody());
            statement.setString(8, record.getPriority());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : -1;
            }
        }
    }

    /**
     * Finds inbox in SQLite.
     */
    public List<MessageRow> findInbox(String username, String roleGroup, String section, String search, String status) throws SQLException {
        ArrayList<MessageRow> rows = new ArrayList<>();
        StringBuilder sql = baseSelect();
        sql.append("WHERE (LOWER(recipient_username) = LOWER(?) OR LOWER(recipient_role) = LOWER(?) OR LOWER(recipient_section) = LOWER(?)) ");
        ArrayList<String> params = new ArrayList<>();
        params.add(username);
        params.add(roleGroup);
        params.add(section);
        appendFilters(sql, params, search, status);
        sql.append("ORDER BY datetime(created_at) DESC, id DESC");
        queryRows(sql.toString(), params, rows);
        return rows;
    }

    /**
     * Finds sent in SQLite.
     */
    public List<MessageRow> findSent(String senderUsername, String search, String status) throws SQLException {
        ArrayList<MessageRow> rows = new ArrayList<>();
        StringBuilder sql = baseSelect();
        sql.append("WHERE LOWER(sender_username) = LOWER(?) ");
        ArrayList<String> params = new ArrayList<>();
        params.add(senderUsername);
        appendFilters(sql, params, search, status);
        sql.append("ORDER BY datetime(created_at) DESC, id DESC");
        queryRows(sql.toString(), params, rows);
        return rows;
    }

    /**
     * Finds by ID in SQLite.
     */
    public Optional<MessageRow> findById(long id) throws SQLException {
        ArrayList<MessageRow> rows = new ArrayList<>();
        StringBuilder sql = baseSelect();
        sql.append("WHERE id = ?");
        queryRows(sql.toString(), List.of(String.valueOf(id)), rows);
        return rows.stream().findFirst();
    }

    /**
     * Marks read with its new workflow state.
     */
    public boolean markRead(long id, String username) throws SQLException {
        String sql = "UPDATE messages SET status = 'READ', read_at = CURRENT_TIMESTAMP "
                + "WHERE id = ? AND status <> 'ARCHIVED' AND (LOWER(recipient_username) = LOWER(?) OR recipient_username IS NULL)";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            statement.setString(2, username);
            return statement.executeUpdate() > 0;
        }
    }

    /**
     * Archives archive while preserving its stored history.
     */
    public boolean archive(long id, String username) throws SQLException {
        String sql = "UPDATE messages SET status = 'ARCHIVED' "
                + "WHERE id = ? AND (LOWER(sender_username) = LOWER(?) OR LOWER(recipient_username) = LOWER(?) OR recipient_username IS NULL)";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            statement.setString(2, username);
            statement.setString(3, username);
            return statement.executeUpdate() > 0;
        }
    }

    /**
     * Returns the shared SELECT clause used by this SQLite DAO.
     */
    private StringBuilder baseSelect() {
        return new StringBuilder("SELECT id, sender_username, recipient_username, recipient_role, recipient_section, patient_id, "
                + "subject, body, priority, status, created_at, read_at FROM messages ");
    }

    /**
     * Counts unread records visible to the current user.
     */
    public int unreadInboxCount(String username, String roleGroup, String section) throws SQLException {
        String sql = "SELECT COUNT(*) FROM messages WHERE status = 'SENT' "
                + "AND (LOWER(recipient_username) = LOWER(?) OR LOWER(recipient_role) = LOWER(?) OR LOWER(recipient_section) = LOWER(?))";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            statement.setString(2, roleGroup);
            statement.setString(3, section);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt(1) : 0;
            }
        }
    }

    /**
     * Appends filters to the current query or result.
     */
    private void appendFilters(StringBuilder sql, ArrayList<String> params, String search, String status) {
        if (search != null && !search.trim().isEmpty()) {
            sql.append("AND (subject LIKE ? OR body LIKE ? OR sender_username LIKE ? OR COALESCE(patient_id, '') LIKE ?) ");
            String like = "%" + search.trim() + "%";
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
        }
        if (status != null && !status.isBlank() && !"All".equalsIgnoreCase(status)) {
            sql.append("AND status = ? ");
            params.add(status);
        }
    }

    /**
     * Queries rows from SQLite.
     */
    private void queryRows(String sql, List<String> params, ArrayList<MessageRow> rows) throws SQLException {
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                statement.setString(i + 1, params.get(i));
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(mapRow(resultSet));
                }
            }
        }
    }

    /**
     * Builds the JavaFX row used to display map row.
     */
    private MessageRow mapRow(ResultSet resultSet) throws SQLException {
        return new MessageRow(
                resultSet.getLong("id"),
                resultSet.getString("sender_username"),
                resultSet.getString("recipient_username"),
                resultSet.getString("recipient_role"),
                resultSet.getString("recipient_section"),
                resultSet.getString("patient_id"),
                resultSet.getString("subject"),
                resultSet.getString("body"),
                resultSet.getString("priority"),
                resultSet.getString("status"),
                resultSet.getString("created_at"),
                resultSet.getString("read_at")
        );
    }

    /**
     * Normalizes blank to null to the workflow fallback value.
     */
    private String blankToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    public static class MessageWriteRecord {
        private final String senderUsername;
        private final String recipientUsername;
        private final String recipientRole;
        private final String recipientSection;
        private final String patientId;
        private final String subject;
        private final String body;
        private final String priority;

        /**
         * Creates a message write record from the supplied record values.
         */
        public MessageWriteRecord(String senderUsername, String recipientUsername, String recipientRole, String recipientSection,
                                  String patientId, String subject, String body, String priority) {
            this.senderUsername = trim(senderUsername);
            this.recipientUsername = trim(recipientUsername);
            this.recipientRole = trim(recipientRole);
            this.recipientSection = trim(recipientSection);
            this.patientId = trim(patientId);
            this.subject = trim(subject);
            this.body = trim(body);
            this.priority = trim(priority);
        }

        public String getSenderUsername() { return senderUsername; }
        public String getRecipientUsername() { return recipientUsername; }
        public String getRecipientRole() { return recipientRole; }
        public String getRecipientSection() { return recipientSection; }
        public String getPatientId() { return patientId; }
        public String getSubject() { return subject; }
        public String getBody() { return body; }
        public String getPriority() { return priority; }

        /**
         * Trims trim while preserving null handling.
         */
        private static String trim(String value) {
            return value == null ? "" : value.trim();
        }
    }

    public static class MessageRow {
        private final long id;
        private final String senderUsername;
        private final String recipientUsername;
        private final String recipientRole;
        private final String recipientSection;
        private final String patientId;
        private final String subject;
        private final String body;
        private final String priority;
        private final String status;
        private final String createdAt;
        private final String readAt;

        /**
         * Creates a message row from the supplied record values.
         */
        public MessageRow(long id, String senderUsername, String recipientUsername, String recipientRole,
                          String recipientSection, String patientId, String subject, String body,
                          String priority, String status, String createdAt, String readAt) {
            this.id = id;
            this.senderUsername = senderUsername;
            this.recipientUsername = recipientUsername;
            this.recipientRole = recipientRole;
            this.recipientSection = recipientSection;
            this.patientId = patientId;
            this.subject = subject;
            this.body = body;
            this.priority = priority;
            this.status = status;
            this.createdAt = createdAt;
            this.readAt = readAt;
        }

        public long getId() { return id; }
        public String getSenderUsername() { return senderUsername; }
        public String getPatientId() { return patientId; }
        public String getSubject() { return subject; }
        public String getBody() { return body; }
        public String getPriority() { return priority; }
        public String getStatus() { return status; }
        public String getCreatedAt() { return createdAt; }
        public String getReadAt() { return readAt; }
        /**
         * Returns target summary used by the messaging workflow.
         */
        public String getTargetSummary() {
            if (recipientUsername != null && !recipientUsername.isBlank()) {
                return "User: " + recipientUsername;
            }
            if (recipientRole != null && !recipientRole.isBlank()) {
                return "Role: " + recipientRole;
            }
            if (recipientSection != null && !recipientSection.isBlank()) {
                return "Section: " + recipientSection;
            }
            return "-";
        }
    }
}
