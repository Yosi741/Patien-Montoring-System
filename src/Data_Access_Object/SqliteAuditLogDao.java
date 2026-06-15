package Data_Access_Object;

import database.DatabaseManager;
import database.SchemaInitializer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class SqliteAuditLogDao implements AuditLogDao {

    public SqliteAuditLogDao() {
        ensureSchema();
    }

    @Override
    public void log(String username, String action) throws SQLException {
        String sql = "INSERT INTO audit_logs(username, action) VALUES(?, ?)";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username == null || username.isBlank() ? "Unknown" : username);
            statement.setString(2, action == null || action.isBlank() ? "JavaFX action" : action);
            statement.executeUpdate();
        }
    }

    public List<AuditLogRow> findRows(String search, String dateRange, String actionType) throws SQLException {
        ArrayList<AuditLogRow> rows = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT id, username, action, created_at FROM audit_logs WHERE 1 = 1 ");
        ArrayList<String> params = new ArrayList<>();

        if (search != null && !search.trim().isEmpty()) {
            sql.append("AND (username LIKE ? OR action LIKE ?) ");
            String like = "%" + search.trim() + "%";
            params.add(like);
            params.add(like);
        }
        if (actionType != null && !actionType.isBlank() && !"All".equalsIgnoreCase(actionType)) {
            if ("ALERT".equalsIgnoreCase(actionType)) {
                sql.append("AND (UPPER(action) LIKE ? OR UPPER(action) LIKE ?) ");
                params.add("%ALERT%");
                params.add("%ACKNOWLEDGE%");
            } else {
                sql.append("AND UPPER(action) LIKE ? ");
                params.add("%" + actionType.toUpperCase() + "%");
            }
        }
        if (dateRange != null && !dateRange.isBlank() && !"All".equalsIgnoreCase(dateRange)) {
            sql.append("AND datetime(created_at) >= datetime('now', ?) ");
            params.add(rangeModifier(dateRange));
        }

        sql.append("ORDER BY id DESC");

        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                statement.setString(i + 1, params.get(i));
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(new AuditLogRow(
                            resultSet.getLong("id"),
                            resultSet.getString("username"),
                            resultSet.getString("action"),
                            resultSet.getString("created_at")
                    ));
                }
            }
        }
        return rows;
    }

    private void ensureSchema() {
        try {
            SchemaInitializer.initialize();
        } catch (Exception e) {
            System.out.println("SQLite audit log schema check failed: " + e.getMessage());
        }
    }

    private String rangeModifier(String dateRange) {
        if ("Today".equalsIgnoreCase(dateRange)) {
            return "start of day";
        }
        if ("Last 7 days".equalsIgnoreCase(dateRange)) {
            return "-7 days";
        }
        if ("Last 30 days".equalsIgnoreCase(dateRange)) {
            return "-30 days";
        }
        return "-100 years";
    }

    public static class AuditLogRow {
        private final long id;
        private final String username;
        private final String action;
        private final String createdAt;

        public AuditLogRow(long id, String username, String action, String createdAt) {
            this.id = id;
            this.username = username;
            this.action = action;
            this.createdAt = createdAt;
        }

        public long getId() { return id; }
        public String getUsername() { return username; }
        public String getAction() { return action; }
        public String getCreatedAt() { return createdAt; }
    }
}
