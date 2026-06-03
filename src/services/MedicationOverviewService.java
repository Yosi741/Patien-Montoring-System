package services;

import database.DatabaseManager;
import database.SchemaInitializer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MedicationOverviewService {

    private static final DateTimeFormatter DISPLAY_DATE_TIME = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

    public MedicationOverviewService() {
        ensureSchema();
    }

    public MedicationOverview loadOverview(MedicationFilter filter) throws SQLException {
        List<MedicationRow> medications;
        List<MedicationEventRow> events;
        try (Connection connection = DatabaseManager.getConnection()) {
            medications = queryMedications(connection, filter);
            events = queryMedicationEvents(connection, filter);
        }

        events.removeIf(event -> !inRange(event.getSortTime(), filter == null ? "All" : filter.getEventDateRange()));
        events.sort(Comparator.comparing(MedicationEventRow::getSortTime, Comparator.nullsLast(Comparator.naturalOrder())).reversed()
                .thenComparing(MedicationEventRow::getId, Comparator.reverseOrder()));

        return new MedicationOverview(
                countActiveMedications(medications),
                countEventsToday(events),
                0,
                countPatientsWithActiveMedications(medications),
                latestEventTime(events),
                medications,
                events
        );
    }

    public List<String> findRoutes() throws SQLException {
        ArrayList<String> routes = new ArrayList<>();
        String sql = "SELECT DISTINCT route FROM medications WHERE route IS NOT NULL AND TRIM(route) <> '' ORDER BY route COLLATE NOCASE";
        try (Connection connection = DatabaseManager.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                routes.add(resultSet.getString("route"));
            }
        }
        return routes;
    }

    private List<MedicationRow> queryMedications(Connection connection, MedicationFilter filter) throws SQLException {
        ArrayList<MedicationRow> rows = new ArrayList<>();
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT m.id, m.patient_id, COALESCE(TRIM(p.first_name || ' ' || p.last_name), '') AS patient_name, ")
                .append("m.name, m.dose, m.route, m.frequency, m.active ")
                .append("FROM medications m LEFT JOIN patients p ON p.patient_id = m.patient_id WHERE 1 = 1 ");
        ArrayList<String> params = new ArrayList<>();
        appendCommonFilters(sql, params, filter, "m", "p");
        if (filter != null && !isAll(filter.getActiveStatus())) {
            sql.append("AND m.active = ? ");
            params.add("Active".equalsIgnoreCase(filter.getActiveStatus()) ? "1" : "0");
        }
        if (filter != null && !isAll(filter.getRoute())) {
            sql.append("AND m.route = ? ");
            params.add(filter.getRoute());
        }
        sql.append("ORDER BY m.active DESC, p.last_name COLLATE NOCASE, p.first_name COLLATE NOCASE, m.name COLLATE NOCASE");

        try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            bind(statement, params);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(new MedicationRow(
                            resultSet.getLong("id"),
                            value(resultSet.getString("patient_id")),
                            fallback(resultSet.getString("patient_name"), "Unknown patient"),
                            value(resultSet.getString("name")),
                            value(resultSet.getString("dose")),
                            value(resultSet.getString("route")),
                            value(resultSet.getString("frequency")),
                            resultSet.getInt("active") == 1
                    ));
                }
            }
        }
        return rows;
    }

    private List<MedicationEventRow> queryMedicationEvents(Connection connection, MedicationFilter filter) throws SQLException {
        ArrayList<MedicationEventRow> rows = new ArrayList<>();
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT e.id, e.patient_id, COALESCE(TRIM(p.first_name || ' ' || p.last_name), '') AS patient_name, ")
                .append("COALESCE(m.name, 'Medication administered') AS medication_name, ")
                .append("COALESCE(m.dose, '') AS dose, COALESCE(m.route, '') AS route, ")
                .append("e.given_by, e.given_at, e.notes ")
                .append("FROM medication_events e ")
                .append("LEFT JOIN medications m ON m.id = e.medication_id ")
                .append("LEFT JOIN patients p ON p.patient_id = e.patient_id WHERE 1 = 1 ");
        ArrayList<String> params = new ArrayList<>();
        appendCommonFilters(sql, params, filter, "m", "p");
        if (filter != null && !isAll(filter.getRoute())) {
            sql.append("AND m.route = ? ");
            params.add(filter.getRoute());
        }
        sql.append("ORDER BY e.id DESC LIMIT 500");

        try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            bind(statement, params);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(new MedicationEventRow(
                            resultSet.getLong("id"),
                            value(resultSet.getString("patient_id")),
                            fallback(resultSet.getString("patient_name"), "Unknown patient"),
                            value(resultSet.getString("medication_name")),
                            value(resultSet.getString("dose")),
                            value(resultSet.getString("route")),
                            value(resultSet.getString("given_by")),
                            value(resultSet.getString("given_at")),
                            value(resultSet.getString("notes"))
                    ));
                }
            }
        }
        return rows;
    }

    private void appendCommonFilters(StringBuilder sql, ArrayList<String> params, MedicationFilter filter,
                                     String medicationAlias, String patientAlias) {
        if (filter == null) {
            return;
        }
        if (filter.getPatientId() != null && !filter.getPatientId().isBlank()) {
            sql.append("AND ").append(medicationAlias).append(".patient_id = ? ");
            params.add(filter.getPatientId());
        }
        if (filter.getSearch() != null && !filter.getSearch().trim().isEmpty()) {
            String like = "%" + filter.getSearch().trim() + "%";
            sql.append("AND (").append(medicationAlias).append(".patient_id LIKE ? ")
                    .append("OR ").append(medicationAlias).append(".name LIKE ? ")
                    .append("OR ").append(patientAlias).append(".first_name LIKE ? ")
                    .append("OR ").append(patientAlias).append(".last_name LIKE ? ")
                    .append("OR (").append(patientAlias).append(".first_name || ' ' || ")
                    .append(patientAlias).append(".last_name) LIKE ?) ");
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
        }
    }

    private void bind(PreparedStatement statement, ArrayList<String> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            statement.setString(i + 1, params.get(i));
        }
    }

    private int countActiveMedications(List<MedicationRow> rows) {
        int count = 0;
        for (MedicationRow row : rows) {
            if (row.isActive()) {
                count++;
            }
        }
        return count;
    }

    private int countEventsToday(List<MedicationEventRow> rows) {
        int count = 0;
        for (MedicationEventRow row : rows) {
            if (row.getSortTime() != null && !row.getSortTime().equals(LocalDateTime.MIN)
                    && row.getSortTime().toLocalDate().equals(LocalDate.now())) {
                count++;
            }
        }
        return count;
    }

    private int countPatientsWithActiveMedications(List<MedicationRow> rows) {
        Set<String> patientIds = new LinkedHashSet<>();
        for (MedicationRow row : rows) {
            if (row.isActive() && !row.getPatientId().isBlank()) {
                patientIds.add(row.getPatientId());
            }
        }
        return patientIds.size();
    }

    private String latestEventTime(List<MedicationEventRow> rows) {
        if (rows.isEmpty()) {
            return "-";
        }
        return rows.get(0).getGivenAt();
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

    private boolean isAll(String value) {
        return value == null || value.isBlank() || "All".equalsIgnoreCase(value);
    }

    private String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private void ensureSchema() {
        try {
            SchemaInitializer.initialize();
        } catch (Exception e) {
            System.out.println("SQLite medication overview schema check failed: " + e.getMessage());
        }
    }

    public static class MedicationFilter {
        private final String search;
        private final String activeStatus;
        private final String route;
        private final String eventDateRange;
        private final String patientId;

        public MedicationFilter(String search, String activeStatus, String route, String eventDateRange, String patientId) {
            this.search = search;
            this.activeStatus = activeStatus;
            this.route = route;
            this.eventDateRange = eventDateRange;
            this.patientId = patientId;
        }

        public String getSearch() { return search; }
        public String getActiveStatus() { return activeStatus; }
        public String getRoute() { return route; }
        public String getEventDateRange() { return eventDateRange; }
        public String getPatientId() { return patientId; }
    }

    public static class MedicationOverview {
        private final int activeMedicationCount;
        private final int medicationEventsToday;
        private final int missedOverduePlaceholderCount;
        private final int patientsWithActiveMedications;
        private final String latestMedicationEventTime;
        private final List<MedicationRow> medications;
        private final List<MedicationEventRow> events;

        public MedicationOverview(int activeMedicationCount, int medicationEventsToday, int missedOverduePlaceholderCount,
                                  int patientsWithActiveMedications, String latestMedicationEventTime,
                                  List<MedicationRow> medications, List<MedicationEventRow> events) {
            this.activeMedicationCount = activeMedicationCount;
            this.medicationEventsToday = medicationEventsToday;
            this.missedOverduePlaceholderCount = missedOverduePlaceholderCount;
            this.patientsWithActiveMedications = patientsWithActiveMedications;
            this.latestMedicationEventTime = latestMedicationEventTime;
            this.medications = medications;
            this.events = events;
        }

        public int getActiveMedicationCount() { return activeMedicationCount; }
        public int getMedicationEventsToday() { return medicationEventsToday; }
        public int getMissedOverduePlaceholderCount() { return missedOverduePlaceholderCount; }
        public int getPatientsWithActiveMedications() { return patientsWithActiveMedications; }
        public String getLatestMedicationEventTime() { return latestMedicationEventTime; }
        public List<MedicationRow> getMedications() { return medications; }
        public List<MedicationEventRow> getEvents() { return events; }
    }

    public static class MedicationRow {
        private final long id;
        private final String patientId;
        private final String patientName;
        private final String medicationName;
        private final String dose;
        private final String route;
        private final String frequency;
        private final boolean active;

        public MedicationRow(long id, String patientId, String patientName, String medicationName, String dose,
                             String route, String frequency, boolean active) {
            this.id = id;
            this.patientId = patientId;
            this.patientName = patientName;
            this.medicationName = medicationName;
            this.dose = dose;
            this.route = route;
            this.frequency = frequency;
            this.active = active;
        }

        public long getId() { return id; }
        public String getPatientId() { return patientId; }
        public String getPatientName() { return patientName; }
        public String getMedicationName() { return medicationName; }
        public String getDose() { return dose; }
        public String getRoute() { return route; }
        public String getFrequency() { return frequency; }
        public boolean isActive() { return active; }
        public String getActiveStatus() { return active ? "Active" : "Inactive"; }
    }

    public static class MedicationEventRow {
        private final long id;
        private final String patientId;
        private final String patientName;
        private final String medicationName;
        private final String dose;
        private final String route;
        private final String givenBy;
        private final String givenAt;
        private final String notes;
        private final LocalDateTime sortTime;

        public MedicationEventRow(long id, String patientId, String patientName, String medicationName, String dose,
                                  String route, String givenBy, String givenAt, String notes) {
            this.id = id;
            this.patientId = patientId;
            this.patientName = patientName;
            this.medicationName = medicationName;
            this.dose = dose;
            this.route = route;
            this.givenBy = givenBy;
            this.givenAt = givenAt;
            this.notes = notes;
            this.sortTime = parseDateTime(givenAt);
        }

        public long getId() { return id; }
        public String getPatientId() { return patientId; }
        public String getPatientName() { return patientName; }
        public String getMedicationName() { return medicationName; }
        public String getDose() { return dose; }
        public String getRoute() { return route; }
        public String getGivenBy() { return givenBy; }
        public String getGivenAt() { return givenAt; }
        public String getNotes() { return notes; }
        private LocalDateTime getSortTime() { return sortTime; }
    }
}
