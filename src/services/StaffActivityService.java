package services;

import database.DatabaseManager;
import database.SchemaInitializer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StaffActivityService {

    private static final DateTimeFormatter DISPLAY_DATE_TIME = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
    private static final Pattern PATIENT_ID_PATTERN = Pattern.compile("(?i)(?:patient(?: id)?|detail for|alerts for)\\s+([A-Za-z0-9_-]+)");

    public StaffActivityService() {
        ensureSchema();
    }

    public StaffActivityOverview loadOverview(ActivityFilter filter, ViewerScope scope) throws SQLException {
        List<ActivityRow> activities = new ArrayList<>();
        Map<String, Integer> activeAlertsBySection;
        List<HandoverRow> handovers;

        try (Connection connection = DatabaseManager.getConnection()) {
            activeAlertsBySection = queryActiveAlertsBySection(connection, scope);
            handovers = queryHandoverRows(connection, scope);
            activities.addAll(queryAuditRows(connection, scope));
            activities.addAll(queryAlertActivityRows(connection, scope));
            activities.addAll(handoverActivityRows(handovers));
        }

        activities.removeIf(row -> !matchesFilter(row, filter));
        activities.sort(Comparator.comparing(ActivityRow::getSortTime, Comparator.nullsLast(Comparator.naturalOrder())).reversed()
                .thenComparing(ActivityRow::getId, Comparator.reverseOrder()));

        return new StaffActivityOverview(
                countLoginsToday(activities),
                countAlertAcknowledgementsToday(activities),
                countPatientViewsToday(activities),
                countSyncOperationsToday(activities),
                countRecentStaffActionsToday(activities),
                activeAlertsBySection,
                activities,
                handovers
        );
    }

    private List<ActivityRow> queryAuditRows(Connection connection, ViewerScope scope) throws SQLException {
        ArrayList<ActivityRow> rows = new ArrayList<>();
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT l.id, l.username, COALESCE(u.role, 'Unknown') AS role, l.action, l.created_at ")
                .append("FROM audit_logs l LEFT JOIN users u ON u.username = l.username WHERE 1 = 1 ");
        ArrayList<String> params = new ArrayList<>();
        if (!scope.isAdmin()) {
            sql.append("AND l.username = ? ");
            params.add(scope.getUsername());
        }
        sql.append("ORDER BY l.id DESC LIMIT 500");

        try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                statement.setString(i + 1, params.get(i));
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String action = value(resultSet.getString("action"));
                    rows.add(new ActivityRow(
                            resultSet.getLong("id"),
                            value(resultSet.getString("created_at")),
                            value(resultSet.getString("username")),
                            roleGroup(resultSet.getString("role")),
                            actionType(action),
                            action,
                            relatedPatientId(action),
                            "audit_logs",
                            String.valueOf(resultSet.getLong("id"))
                    ));
                }
            }
        }
        return rows;
    }

    private List<ActivityRow> queryAlertActivityRows(Connection connection, ViewerScope scope) throws SQLException {
        ArrayList<ActivityRow> rows = new ArrayList<>();
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT a.id, a.patient_id, COALESCE(p.section, 'Unassigned') AS section, ")
                .append("a.severity, a.message, a.status, a.created_at, a.updated_at, a.acknowledged_by, a.acknowledged_at, ")
                .append("COALESCE(u.role, 'Unknown') AS acknowledged_role ")
                .append("FROM alerts a ")
                .append("LEFT JOIN patients p ON p.patient_id = a.patient_id ")
                .append("LEFT JOIN users u ON u.username = a.acknowledged_by ")
                .append("WHERE 1 = 1 ");
        ArrayList<String> params = new ArrayList<>();
        if (!scope.isAdmin()) {
            sql.append("AND COALESCE(p.section, '') = ? ");
            params.add(scope.getSection());
        }
        sql.append("ORDER BY a.id DESC LIMIT 500");

        try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                statement.setString(i + 1, params.get(i));
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String patientId = value(resultSet.getString("patient_id"));
                    String severity = value(resultSet.getString("severity"));
                    String status = value(resultSet.getString("status"));
                    String message = value(resultSet.getString("message"));
                    String acknowledgedBy = value(resultSet.getString("acknowledged_by"));
                    String acknowledgedAt = value(resultSet.getString("acknowledged_at"));

                    if (!acknowledgedBy.isBlank()) {
                        rows.add(new ActivityRow(
                                resultSet.getLong("id"),
                                fallback(acknowledgedAt, resultSet.getString("updated_at")),
                                acknowledgedBy,
                                roleGroup(resultSet.getString("acknowledged_role")),
                                "ALERT",
                                "Acknowledged " + severity + " alert for patient " + fallback(patientId, "-") + ": " + message,
                                patientId,
                                "alerts",
                                String.valueOf(resultSet.getLong("id"))
                        ));
                    } else if ("ACTIVE".equalsIgnoreCase(status)) {
                        rows.add(new ActivityRow(
                                resultSet.getLong("id"),
                                value(resultSet.getString("created_at")),
                                "System",
                                "SYSTEM",
                                "ALERT",
                                "Active " + severity + " alert for patient " + fallback(patientId, "-") + ": " + message,
                                patientId,
                                "alerts",
                                String.valueOf(resultSet.getLong("id"))
                        ));
                    }
                }
            }
        }
        return rows;
    }

    private List<HandoverRow> queryHandoverRows(Connection connection, ViewerScope scope) throws SQLException {
        ArrayList<HandoverRow> rows = new ArrayList<>();
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT id, patient_id, from_user, to_section, note, created_at FROM shift_handover_notes WHERE 1 = 1 ");
        ArrayList<String> params = new ArrayList<>();
        if (!scope.isAdmin()) {
            sql.append("AND to_section = ? ");
            params.add(scope.getSection());
        }
        sql.append("ORDER BY id DESC LIMIT 25");

        try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                statement.setString(i + 1, params.get(i));
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(new HandoverRow(
                            resultSet.getLong("id"),
                            value(resultSet.getString("patient_id")),
                            value(resultSet.getString("from_user")),
                            value(resultSet.getString("to_section")),
                            value(resultSet.getString("note")),
                            value(resultSet.getString("created_at"))
                    ));
                }
            }
        }
        return rows;
    }

    private List<ActivityRow> handoverActivityRows(List<HandoverRow> handovers) {
        ArrayList<ActivityRow> rows = new ArrayList<>();
        for (HandoverRow handover : handovers) {
            rows.add(new ActivityRow(
                    handover.getId(),
                    handover.getCreatedAt(),
                    fallback(handover.getFromUser(), "Unknown"),
                    "STAFF",
                    "HANDOVER",
                    "Shift handover to " + fallback(handover.getToSection(), "-") + ": " + handover.getNote(),
                    handover.getPatientId(),
                    "shift_handover_notes",
                    String.valueOf(handover.getId())
            ));
        }
        return rows;
    }

    private Map<String, Integer> queryActiveAlertsBySection(Connection connection, ViewerScope scope) throws SQLException {
        LinkedHashMap<String, Integer> rows = new LinkedHashMap<>();
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT COALESCE(NULLIF(TRIM(p.section), ''), 'Unassigned') AS section, COUNT(*) AS count ")
                .append("FROM alerts a LEFT JOIN patients p ON p.patient_id = a.patient_id ")
                .append("WHERE UPPER(a.status) = 'ACTIVE' ");
        ArrayList<String> params = new ArrayList<>();
        if (!scope.isAdmin()) {
            sql.append("AND COALESCE(p.section, '') = ? ");
            params.add(scope.getSection());
        }
        sql.append("GROUP BY COALESCE(NULLIF(TRIM(p.section), ''), 'Unassigned') ORDER BY count DESC, section");

        try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                statement.setString(i + 1, params.get(i));
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows.put(resultSet.getString("section"), resultSet.getInt("count"));
                }
            }
        }
        return rows;
    }

    private boolean matchesFilter(ActivityRow row, ActivityFilter filter) {
        if (filter == null) {
            return true;
        }
        String search = normalize(filter.getSearch());
        if (!search.isBlank()
                && !normalize(row.getUsername()).contains(search)
                && !normalize(row.getDescription()).contains(search)
                && !normalize(row.getRelatedPatientId()).contains(search)) {
            return false;
        }
        if (!isAll(filter.getRole()) && !row.getRole().equalsIgnoreCase(filter.getRole())) {
            return false;
        }
        if (!isAll(filter.getActionType()) && !row.getActionType().equalsIgnoreCase(filter.getActionType())) {
            return false;
        }
        return inRange(row.getSortTime(), filter.getDateRange());
    }

    private boolean inRange(LocalDateTime time, String dateRange) {
        if (time == null || time.equals(LocalDateTime.MIN) || isAll(dateRange)) {
            return true;
        }
        LocalDateTime now = LocalDateTime.now();
        if ("Today".equalsIgnoreCase(dateRange)) {
            return time.toLocalDate().equals(LocalDate.now());
        }
        if ("Last 7 days".equalsIgnoreCase(dateRange)) {
            return !time.isBefore(now.minusDays(7));
        }
        if ("Last 30 days".equalsIgnoreCase(dateRange)) {
            return !time.isBefore(now.minusDays(30));
        }
        return true;
    }

    private int countLoginsToday(List<ActivityRow> rows) {
        return countToday(rows, "LOGIN");
    }

    private int countAlertAcknowledgementsToday(List<ActivityRow> rows) {
        int count = 0;
        for (ActivityRow row : rows) {
            if (isToday(row.getSortTime()) && row.getActionType().equals("ALERT")
                    && normalize(row.getDescription()).contains("acknowledg")) {
                count++;
            }
        }
        return count;
    }

    private int countPatientViewsToday(List<ActivityRow> rows) {
        int count = 0;
        for (ActivityRow row : rows) {
            if (isToday(row.getSortTime()) && row.getActionType().equals("PATIENT")
                    && normalize(row.getDescription()).contains("detail")) {
                count++;
            }
        }
        return count;
    }

    private int countSyncOperationsToday(List<ActivityRow> rows) {
        return countToday(rows, "SYNC");
    }

    private int countRecentStaffActionsToday(List<ActivityRow> rows) {
        int count = 0;
        for (ActivityRow row : rows) {
            if (isToday(row.getSortTime())) {
                count++;
            }
        }
        return count;
    }

    private int countToday(List<ActivityRow> rows, String actionType) {
        int count = 0;
        for (ActivityRow row : rows) {
            if (isToday(row.getSortTime()) && row.getActionType().equals(actionType)) {
                count++;
            }
        }
        return count;
    }

    private boolean isToday(LocalDateTime time) {
        return time != null && !time.equals(LocalDateTime.MIN) && time.toLocalDate().equals(LocalDate.now());
    }

    private String actionType(String action) {
        String upper = value(action).toUpperCase(Locale.ROOT);
        if (upper.contains("LOGIN")) {
            return "LOGIN";
        }
        if (upper.contains("LOGOUT")) {
            return "LOGOUT";
        }
        if (upper.contains("ALERT") || upper.contains("ACKNOWLEDGE")) {
            return "ALERT";
        }
        if (upper.contains("PATIENT")) {
            return "PATIENT";
        }
        if (upper.contains("SYNC") || upper.contains("MIGRATION")) {
            return "SYNC";
        }
        if (upper.contains("HANDOVER")) {
            return "HANDOVER";
        }
        return "SYSTEM";
    }

    private String relatedPatientId(String action) {
        Matcher matcher = PATIENT_ID_PATTERN.matcher(value(action));
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    private String roleGroup(String role) {
        String upper = value(role).toUpperCase(Locale.ROOT);
        if (upper.contains("ADMIN")) {
            return "ADMIN";
        }
        if (upper.contains("DOCTOR") || upper.contains("MEDICAL") || upper.contains("DEPARTMENT HEAD")) {
            return "DOCTOR";
        }
        if (upper.contains("NURSE") || upper.contains("NURSING")) {
            return "NURSE";
        }
        if (upper.isBlank() || upper.equals("UNKNOWN")) {
            return "UNKNOWN";
        }
        return "STAFF";
    }

    private boolean isAll(String value) {
        return value == null || value.isBlank() || "All".equalsIgnoreCase(value);
    }

    private String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return LocalDateTime.MIN;
        }
        try {
            return LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDateTime.parse(value.replace(" ", "T"));
            } catch (DateTimeParseException ignoredAgain) {
                try {
                    return LocalDateTime.parse(value, DISPLAY_DATE_TIME);
                } catch (DateTimeParseException ignoredThird) {
                    return LocalDateTime.MIN;
                }
            }
        }
    }

    private void ensureSchema() {
        try {
            SchemaInitializer.initialize();
        } catch (Exception e) {
            System.out.println("SQLite staff activity schema check failed: " + e.getMessage());
        }
    }

    public static class ActivityFilter {
        private final String search;
        private final String role;
        private final String actionType;
        private final String dateRange;

        public ActivityFilter(String search, String role, String actionType, String dateRange) {
            this.search = search;
            this.role = role;
            this.actionType = actionType;
            this.dateRange = dateRange;
        }

        public String getSearch() { return search; }
        public String getRole() { return role; }
        public String getActionType() { return actionType; }
        public String getDateRange() { return dateRange; }
    }

    public static class ViewerScope {
        private final String username;
        private final String role;
        private final String section;
        private final boolean admin;

        public ViewerScope(String username, String role, String section, boolean admin) {
            this.username = username;
            this.role = role;
            this.section = section;
            this.admin = admin;
        }

        public String getUsername() { return username; }
        public String getRole() { return role; }
        public String getSection() { return section; }
        public boolean isAdmin() { return admin; }
    }

    public static class ActivityRow {
        private final long id;
        private final String time;
        private final String username;
        private final String role;
        private final String actionType;
        private final String description;
        private final String relatedPatientId;
        private final String sourceTable;
        private final String sourceId;
        private final LocalDateTime sortTime;

        public ActivityRow(long id, String time, String username, String role, String actionType,
                           String description, String relatedPatientId, String sourceTable, String sourceId) {
            this.id = id;
            this.time = time;
            this.username = username;
            this.role = role;
            this.actionType = actionType;
            this.description = description;
            this.relatedPatientId = relatedPatientId;
            this.sourceTable = sourceTable;
            this.sourceId = sourceId;
            this.sortTime = parseDateTime(time);
        }

        public long getId() { return id; }
        public String getTime() { return time; }
        public String getUsername() { return username; }
        public String getRole() { return role; }
        public String getActionType() { return actionType; }
        public String getDescription() { return description; }
        public String getRelatedPatientId() { return relatedPatientId == null || relatedPatientId.isBlank() ? "-" : relatedPatientId; }
        public String getSourceTable() { return sourceTable; }
        public String getSourceId() { return sourceId; }
        private LocalDateTime getSortTime() { return sortTime; }
    }

    public static class HandoverRow {
        private final long id;
        private final String patientId;
        private final String fromUser;
        private final String toSection;
        private final String note;
        private final String createdAt;

        public HandoverRow(long id, String patientId, String fromUser, String toSection, String note, String createdAt) {
            this.id = id;
            this.patientId = patientId;
            this.fromUser = fromUser;
            this.toSection = toSection;
            this.note = note;
            this.createdAt = createdAt;
        }

        public long getId() { return id; }
        public String getPatientId() { return patientId == null || patientId.isBlank() ? "-" : patientId; }
        public String getFromUser() { return fromUser; }
        public String getToSection() { return toSection; }
        public String getNote() { return note; }
        public String getCreatedAt() { return createdAt; }
    }

    public static class StaffActivityOverview {
        private final int loginsToday;
        private final int alertAcknowledgementsToday;
        private final int patientDetailViewsToday;
        private final int syncOperationsToday;
        private final int recentStaffActionsToday;
        private final Map<String, Integer> activeAlertsBySection;
        private final List<ActivityRow> activities;
        private final List<HandoverRow> handovers;

        public StaffActivityOverview(int loginsToday, int alertAcknowledgementsToday, int patientDetailViewsToday,
                                     int syncOperationsToday, int recentStaffActionsToday,
                                     Map<String, Integer> activeAlertsBySection,
                                     List<ActivityRow> activities, List<HandoverRow> handovers) {
            this.loginsToday = loginsToday;
            this.alertAcknowledgementsToday = alertAcknowledgementsToday;
            this.patientDetailViewsToday = patientDetailViewsToday;
            this.syncOperationsToday = syncOperationsToday;
            this.recentStaffActionsToday = recentStaffActionsToday;
            this.activeAlertsBySection = activeAlertsBySection;
            this.activities = activities;
            this.handovers = handovers;
        }

        public int getLoginsToday() { return loginsToday; }
        public int getAlertAcknowledgementsToday() { return alertAcknowledgementsToday; }
        public int getPatientDetailViewsToday() { return patientDetailViewsToday; }
        public int getSyncOperationsToday() { return syncOperationsToday; }
        public int getRecentStaffActionsToday() { return recentStaffActionsToday; }
        public Map<String, Integer> getActiveAlertsBySection() { return activeAlertsBySection; }
        public List<ActivityRow> getActivities() { return activities; }
        public List<HandoverRow> getHandovers() { return handovers; }
    }
}
