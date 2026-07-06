package pages.clinical_timeline;

import app.DatabaseManager;
import app.SchemaInitializer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public class ClinicalTimelineDao {

    private static final DateTimeFormatter DISPLAY_DATE_TIME = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

    public ClinicalTimelineDao() {
        ensureSchema();
    }

    public List<TimelineEvent> findEvents(String patientId, String eventType, String searchText) throws SQLException {
        ArrayList<TimelineEvent> events = new ArrayList<>();
        try (Connection connection = DatabaseManager.getConnection()) {
            addVitalEvents(connection, patientId, events);
            addAlertEvents(connection, patientId, events);
            addMedicalFileEvents(connection, patientId, events);
            addMedicalHistoryEvents(connection, patientId, events);
            addShiftHandoverEvents(connection, patientId, events);
        }

        String normalizedType = normalize(eventType);
        String normalizedSearch = normalize(searchText);
        events.removeIf(event -> !matchesType(event, normalizedType) || !matchesSearch(event, normalizedSearch));
        events.sort(Comparator.comparing(TimelineEvent::getSortTime, Comparator.nullsLast(Comparator.naturalOrder())).reversed()
                .thenComparing(TimelineEvent::getSourceId, Comparator.nullsLast(Comparator.reverseOrder())));
        return events;
    }

    public Optional<TimelineEventDetail> findEventDetail(TimelineEvent event) throws SQLException {
        if (event == null) {
            return Optional.empty();
        }
        try (Connection connection = DatabaseManager.getConnection()) {
            switch (event.getSourceTable()) {
                case "vital_readings":
                    return findVitalDetail(connection, event);
                case "alerts":
                    return findAlertDetail(connection, event);
                case "medical_files":
                    return findMedicalFileDetail(connection, event);
                case "medical_history":
                    return findMedicalHistoryDetail(connection, event);
                case "shift_handover_notes":
                    return findShiftHandoverDetail(connection, event);
                default:
                    return Optional.of(fallbackDetail(event, "No source-specific detail query is available for this event type."));
            }
        }
    }

    private Optional<TimelineEventDetail> findVitalDetail(Connection connection, TimelineEvent event) throws SQLException {
        String sql = "SELECT id, patient_id, vital_type, value, unit, recorded_at, source_type, staff_user, device_id "
                + "FROM vital_readings WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, event.getSourceId());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                LinkedHashMap<String, String> fields = baseFields(event, resultSet.getString("patient_id"), resultSet.getString("recorded_at"));
                field(fields, "Vital Type", resultSet.getString("vital_type"));
                field(fields, "Value", resultSet.getString("value"));
                field(fields, "Unit", resultSet.getString("unit"));
                field(fields, "Source Type", resultSet.getString("source_type"));
                field(fields, "Staff User", resultSet.getString("staff_user"));
                field(fields, "Device ID", resultSet.getString("device_id"));
                String description = joinDetails("Recorded " + value(resultSet.getString("vital_type")),
                        value(resultSet.getString("value")) + " " + value(resultSet.getString("unit")),
                        "Source: " + fallback(resultSet.getString("source_type"), "Manual/imported"));
                return Optional.of(new TimelineEventDetail(event.getEventType(), event.getTitle(), resultSet.getString("patient_id"),
                        resultSet.getString("recorded_at"), event.getSourceTable(), event.getSourceId(), description, "", "", fields));
            }
        }
    }

    private Optional<TimelineEventDetail> findAlertDetail(Connection connection, TimelineEvent event) throws SQLException {
        String sql = "SELECT id, patient_id, severity, message, status, created_at, updated_at, acknowledged_by, acknowledged_at "
                + "FROM alerts WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, event.getSourceId());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                LinkedHashMap<String, String> fields = baseFields(event, resultSet.getString("patient_id"), resultSet.getString("created_at"));
                field(fields, "Severity", resultSet.getString("severity"));
                field(fields, "Status", resultSet.getString("status"));
                field(fields, "Message", resultSet.getString("message"));
                field(fields, "Acknowledged By", resultSet.getString("acknowledged_by"));
                field(fields, "Acknowledged At", resultSet.getString("acknowledged_at"));
                field(fields, "Updated At", resultSet.getString("updated_at"));
                String action = "Review patient status and use Notifications for alert follow-up.";
                return Optional.of(new TimelineEventDetail(event.getEventType(), event.getTitle(), resultSet.getString("patient_id"),
                        resultSet.getString("created_at"), event.getSourceTable(), event.getSourceId(), resultSet.getString("message"),
                        resultSet.getString("severity"), action, fields));
            }
        }
    }

    private Optional<TimelineEventDetail> findMedicalFileDetail(Connection connection, TimelineEvent event) throws SQLException {
        String sql = "SELECT id, file_id, patient_id, original_name, stored_path, file_type, uploaded_by, uploaded_at, extracted_summary, file_size, notes "
                + "FROM medical_files WHERE file_id = ? OR CAST(id AS TEXT) = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, event.getSourceId());
            statement.setString(2, event.getSourceId());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                LinkedHashMap<String, String> fields = baseFields(event, resultSet.getString("patient_id"), resultSet.getString("uploaded_at"));
                field(fields, "File ID", resultSet.getString("file_id"));
                field(fields, "Original Name", resultSet.getString("original_name"));
                field(fields, "Stored Path", resultSet.getString("stored_path"));
                field(fields, "File Type", resultSet.getString("file_type"));
                field(fields, "Uploaded By", resultSet.getString("uploaded_by"));
                field(fields, "Uploaded At", resultSet.getString("uploaded_at"));
                field(fields, "File Size", resultSet.getLong("file_size") + " bytes");
                field(fields, "Extracted Summary", resultSet.getString("extracted_summary"));
                field(fields, "Notes", resultSet.getString("notes"));
                String description = joinDetails("File metadata only; preview/opening is not enabled in this phase.",
                        resultSet.getString("extracted_summary"));
                return Optional.of(new TimelineEventDetail(event.getEventType(), event.getTitle(), resultSet.getString("patient_id"),
                        resultSet.getString("uploaded_at"), event.getSourceTable(), event.getSourceId(), description, "", "", fields));
            }
        }
    }

    private Optional<TimelineEventDetail> findMedicalHistoryDetail(Connection connection, TimelineEvent event) throws SQLException {
        String sql = "SELECT id, patient_id, category, details, created_by, created_at FROM medical_history WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, event.getSourceId());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                LinkedHashMap<String, String> fields = baseFields(event, resultSet.getString("patient_id"), resultSet.getString("created_at"));
                field(fields, "Category", resultSet.getString("category"));
                field(fields, "Details", resultSet.getString("details"));
                field(fields, "Created By", resultSet.getString("created_by"));
                field(fields, "Created At", resultSet.getString("created_at"));
                return Optional.of(new TimelineEventDetail(event.getEventType(), event.getTitle(), resultSet.getString("patient_id"),
                        resultSet.getString("created_at"), event.getSourceTable(), event.getSourceId(), resultSet.getString("details"),
                        "", "", fields));
            }
        }
    }

    private Optional<TimelineEventDetail> findShiftHandoverDetail(Connection connection, TimelineEvent event) throws SQLException {
        String sql = "SELECT id, patient_id, from_user, to_section, note, created_at FROM shift_handover_notes WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, event.getSourceId());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                LinkedHashMap<String, String> fields = baseFields(event, fallback(resultSet.getString("patient_id"), "All/section"), resultSet.getString("created_at"));
                field(fields, "From User", resultSet.getString("from_user"));
                field(fields, "To Section", resultSet.getString("to_section"));
                field(fields, "Note", resultSet.getString("note"));
                field(fields, "Created At", resultSet.getString("created_at"));
                return Optional.of(new TimelineEventDetail(event.getEventType(), event.getTitle(), fallback(resultSet.getString("patient_id"), "All/section"),
                        resultSet.getString("created_at"), event.getSourceTable(), event.getSourceId(), resultSet.getString("note"),
                        "", "", fields));
            }
        }
    }

    private void addVitalEvents(Connection connection, String patientId, List<TimelineEvent> events) throws SQLException {
        String sql = "SELECT id, vital_type, value, unit, recorded_at, source_type, staff_user, device_id "
                + "FROM vital_readings WHERE patient_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, patientId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String vitalType = value(resultSet.getString("vital_type"));
                    String value = value(resultSet.getString("value"));
                    String unit = value(resultSet.getString("unit"));
                    String source = value(resultSet.getString("source_type"));
                    String staff = value(resultSet.getString("staff_user"));
                    String device = value(resultSet.getString("device_id"));
                    events.add(new TimelineEvent(
                            value(resultSet.getString("recorded_at")),
                            "Vitals",
                            vitalType + ": " + value + (unit.isBlank() ? "" : " " + unit),
                            joinDetails("Source: " + fallback(source, "Manual/imported"),
                                    "Staff: " + fallback(staff, "Not recorded"),
                                    device.isBlank() ? "" : "Device: " + device),
                            "",
                            "vital_readings",
                            String.valueOf(resultSet.getLong("id"))
                    ));
                }
            }
        }
    }

    private void addAlertEvents(Connection connection, String patientId, List<TimelineEvent> events) throws SQLException {
        String sql = "SELECT id, severity, message, status, created_at, acknowledged_by, acknowledged_at, updated_at "
                + "FROM alerts WHERE patient_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, patientId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String severity = value(resultSet.getString("severity"));
                    String status = value(resultSet.getString("status"));
                    String acknowledgedBy = value(resultSet.getString("acknowledged_by"));
                    String acknowledgedAt = value(resultSet.getString("acknowledged_at"));
                    events.add(new TimelineEvent(
                            value(resultSet.getString("created_at")),
                            "Alerts",
                            severity + " alert" + (status.isBlank() ? "" : " (" + status + ")"),
                            joinDetails(value(resultSet.getString("message")),
                                    acknowledgedBy.isBlank() ? "" : "Acknowledged by: " + acknowledgedBy,
                                    acknowledgedAt.isBlank() ? "" : "Acknowledged at: " + acknowledgedAt,
                                    "Last updated: " + fallback(resultSet.getString("updated_at"), resultSet.getString("created_at"))),
                            severity,
                            "alerts",
                            String.valueOf(resultSet.getLong("id"))
                    ));
                }
            }
        }
    }

    private void addMedicalFileEvents(Connection connection, String patientId, List<TimelineEvent> events) throws SQLException {
        String sql = "SELECT id, file_id, original_name, stored_path, file_type, uploaded_by, uploaded_at, extracted_summary "
                + "FROM medical_files WHERE patient_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, patientId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String originalName = value(resultSet.getString("original_name"));
                    String fileType = value(resultSet.getString("file_type"));
                    String uploadedBy = value(resultSet.getString("uploaded_by"));
                    String summary = value(resultSet.getString("extracted_summary"));
                    String storedPath = value(resultSet.getString("stored_path"));
                    events.add(new TimelineEvent(
                            value(resultSet.getString("uploaded_at")),
                            "Files",
                            "Uploaded file: " + originalName,
                            joinDetails(fileType.isBlank() ? "" : "Type: " + fileType,
                                    uploadedBy.isBlank() ? "" : "Uploaded by: " + uploadedBy,
                                    summary.isBlank() ? "" : "Summary: " + summary,
                                    storedPath.isBlank() ? "" : "Stored path: " + storedPath),
                            "",
                            "medical_files",
                            fallback(resultSet.getString("file_id"), String.valueOf(resultSet.getLong("id")))
                    ));
                }
            }
        }
    }

    private void addMedicalHistoryEvents(Connection connection, String patientId, List<TimelineEvent> events) throws SQLException {
        String sql = "SELECT id, category, details, created_by, created_at FROM medical_history WHERE patient_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, patientId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String category = value(resultSet.getString("category"));
                    String createdBy = value(resultSet.getString("created_by"));
                    events.add(new TimelineEvent(
                            value(resultSet.getString("created_at")),
                            "Medical History",
                            category.isBlank() ? "Imported medical history" : category,
                            joinDetails(value(resultSet.getString("details")),
                                    createdBy.isBlank() ? "" : "Recorded by: " + createdBy),
                            "",
                            "medical_history",
                            String.valueOf(resultSet.getLong("id"))
                    ));
                }
            }
        }
    }

    private void addShiftHandoverEvents(Connection connection, String patientId, List<TimelineEvent> events) throws SQLException {
        String sql = "SELECT id, from_user, to_section, note, created_at FROM shift_handover_notes "
                + "WHERE patient_id = ? OR COALESCE(patient_id, '') = ''";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, patientId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String fromUser = value(resultSet.getString("from_user"));
                    String toSection = value(resultSet.getString("to_section"));
                    events.add(new TimelineEvent(
                            value(resultSet.getString("created_at")),
                            "Shift Handover",
                            "Imported shift handover note",
                            joinDetails(value(resultSet.getString("note")),
                                    fromUser.isBlank() ? "" : "From: " + fromUser,
                                    toSection.isBlank() ? "" : "To section: " + toSection),
                            "",
                            "shift_handover_notes",
                            String.valueOf(resultSet.getLong("id"))
                    ));
                }
            }
        }
    }

    private boolean matchesType(TimelineEvent event, String normalizedType) {
        return normalizedType.isBlank() || "all events".equals(normalizedType) || event.getEventType().toLowerCase(Locale.ROOT).equals(normalizedType);
    }

    private boolean matchesSearch(TimelineEvent event, String normalizedSearch) {
        if (normalizedSearch.isBlank()) {
            return true;
        }
        return normalize(event.getTitle()).contains(normalizedSearch)
                || normalize(event.getDescription()).contains(normalizedSearch)
                || normalize(event.getSeverity()).contains(normalizedSearch)
                || normalize(event.getSourceTable()).contains(normalizedSearch);
    }

    private void ensureSchema() {
        try {
            SchemaInitializer.initialize();
        } catch (Exception e) {
            System.out.println("SQLite clinical timeline schema check failed: " + e.getMessage());
        }
    }

    private TimelineEventDetail fallbackDetail(TimelineEvent event, String description) {
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        field(fields, "Event Type", event.getEventType());
        field(fields, "Event Time", event.getEventTime());
        field(fields, "Source Table", event.getSourceTable());
        field(fields, "Source ID", event.getSourceId());
        field(fields, "Title", event.getTitle());
        field(fields, "Description", event.getDescription());
        field(fields, "Severity / Risk", event.getSeverity());
        return new TimelineEventDetail(event.getEventType(), event.getTitle(), "", event.getEventTime(),
                event.getSourceTable(), event.getSourceId(), description, event.getSeverity(), "", fields);
    }

    private LinkedHashMap<String, String> baseFields(TimelineEvent event, String patientId, String eventTime) {
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        field(fields, "Event Type", event.getEventType());
        field(fields, "Patient ID", patientId);
        field(fields, "Event Time", eventTime);
        field(fields, "Source Table", event.getSourceTable());
        field(fields, "Source ID", event.getSourceId());
        return fields;
    }

    private void field(Map<String, String> fields, String label, String value) {
        fields.put(label, value == null || value.isBlank() ? "-" : value);
    }

    private String joinDetails(String... parts) {
        ArrayList<String> clean = new ArrayList<>();
        for (String part : parts) {
            if (part != null && !part.isBlank()) {
                clean.add(part);
            }
        }
        return String.join("  |  ", clean);
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
            return LocalDateTime.parse(value, DISPLAY_DATE_TIME);
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDateTime.parse(value.replace(" ", "T"));
            } catch (Exception ignoredAgain) {
                return LocalDateTime.MIN;
            }
        }
    }

    public static class TimelineEvent {
        private final String eventTime;
        private final String eventType;
        private final String title;
        private final String description;
        private final String severity;
        private final String sourceTable;
        private final String sourceId;
        private final LocalDateTime sortTime;

        public TimelineEvent(String eventTime, String eventType, String title, String description,
                             String severity, String sourceTable, String sourceId) {
            this.eventTime = eventTime;
            this.eventType = eventType;
            this.title = title;
            this.description = description;
            this.severity = severity;
            this.sourceTable = sourceTable;
            this.sourceId = sourceId;
            this.sortTime = parseDateTime(eventTime);
        }

        public String getEventTime() {
            return eventTime;
        }

        public String getEventType() {
            return eventType;
        }

        public String getTitle() {
            return title;
        }

        public String getDescription() {
            return description;
        }

        public String getSeverity() {
            return severity;
        }

        public String getSourceTable() {
            return sourceTable;
        }

        public String getSourceId() {
            return sourceId;
        }

        private LocalDateTime getSortTime() {
            return sortTime;
        }
    }

    public static class TimelineEventDetail {
        private final String eventType;
        private final String title;
        private final String patientId;
        private final String eventTime;
        private final String sourceTable;
        private final String sourceId;
        private final String description;
        private final String severityOrRisk;
        private final String recommendedAction;
        private final LinkedHashMap<String, String> fields;

        public TimelineEventDetail(String eventType, String title, String patientId, String eventTime,
                                   String sourceTable, String sourceId, String description, String severityOrRisk,
                                   String recommendedAction, LinkedHashMap<String, String> fields) {
            this.eventType = eventType;
            this.title = title;
            this.patientId = patientId;
            this.eventTime = eventTime;
            this.sourceTable = sourceTable;
            this.sourceId = sourceId;
            this.description = description;
            this.severityOrRisk = severityOrRisk;
            this.recommendedAction = recommendedAction;
            this.fields = fields;
        }

        public String getEventType() {
            return eventType;
        }

        public String getTitle() {
            return title;
        }

        public String getPatientId() {
            return patientId;
        }

        public String getEventTime() {
            return eventTime;
        }

        public String getSourceTable() {
            return sourceTable;
        }

        public String getSourceId() {
            return sourceId;
        }

        public String getDescription() {
            return description;
        }

        public String getSeverityOrRisk() {
            return severityOrRisk;
        }

        public String getRecommendedAction() {
            return recommendedAction;
        }

        public LinkedHashMap<String, String> getFields() {
            return fields;
        }

        public String toSummary() {
            StringBuilder builder = new StringBuilder();
            builder.append(eventType).append(" - ").append(title).append(System.lineSeparator());
            builder.append("Patient ID: ").append(patientId == null || patientId.isBlank() ? "-" : patientId).append(System.lineSeparator());
            builder.append("Event Time: ").append(eventTime == null || eventTime.isBlank() ? "-" : eventTime).append(System.lineSeparator());
            builder.append("Source: ").append(sourceTable).append(" #").append(sourceId).append(System.lineSeparator());
            if (severityOrRisk != null && !severityOrRisk.isBlank()) {
                builder.append("Severity/Risk: ").append(severityOrRisk).append(System.lineSeparator());
            }
            builder.append("Description: ").append(description == null || description.isBlank() ? "-" : description).append(System.lineSeparator());
            if (recommendedAction != null && !recommendedAction.isBlank()) {
                builder.append("Recommended Action: ").append(recommendedAction).append(System.lineSeparator());
            }
            return builder.toString();
        }
    }
}
