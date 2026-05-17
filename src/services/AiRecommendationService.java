package services;

import database.DatabaseManager;
import database.SchemaInitializer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class AiRecommendationService {

    private static final String SOURCE_TITLE = "JavaFX AI Recommendation";
    private static final DateTimeFormatter SQLITE_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter LEGACY_DATE_TIME = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

    public AiRecommendationService() {
        ensureSchema();
    }

    public RecommendationResult generateAndSave(String patientId) throws SQLException {
        RecommendationResult result = analyzePatient(patientId);
        saveRecommendation(result);
        if (result.getRiskScore() >= 80) {
            new NotificationCenterService().notifyHighAiRisk(result.getPatientId(), result.getRiskScore(), "");
        }
        return result;
    }

    public RecommendationResult analyzePatient(String patientId) throws SQLException {
        try (Connection connection = DatabaseManager.getConnection()) {
            PatientContext patient = findPatient(connection, patientId)
                    .orElseThrow(() -> new IllegalArgumentException("Patient not found in SQLite: " + patientId));
            List<VitalReading> vitals = findVitals(connection, patientId);
            List<AlertContext> alerts = findAlerts(connection, patientId);
            int recentTimelineEvents = countRecentTimelineEvents(connection, patientId);
            Optional<AiNoteSummary> latestNote = findLatestAiNote(connection, patientId);
            return scorePatient(patient, vitals, alerts, recentTimelineEvents, latestNote);
        }
    }

    public List<RecommendationBoardRow> loadBoardRows(String sectionFilter, String riskLevelFilter) throws SQLException {
        ArrayList<RecommendationBoardRow> rows = new ArrayList<>();
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT p.patient_id, TRIM(p.first_name || ' ' || p.last_name) AS patient_name, ")
                .append("p.section, p.room, p.status, p.priority, ")
                .append("n.risk_score, n.note, n.source_title, n.created_at ")
                .append("FROM patients p ")
                .append("LEFT JOIN ai_notes n ON n.id = (SELECT id FROM ai_notes WHERE patient_id = p.patient_id ORDER BY datetime(created_at) DESC, id DESC LIMIT 1) ")
                .append("WHERE 1 = 1 ");
        ArrayList<String> params = new ArrayList<>();
        if (sectionFilter != null && !sectionFilter.isBlank() && !"All".equalsIgnoreCase(sectionFilter)) {
            sql.append("AND p.section = ? ");
            params.add(sectionFilter);
        }
        sql.append("ORDER BY COALESCE(n.risk_score, -1) DESC, p.section, p.room, p.last_name, p.first_name");

        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            bind(statement, params);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    int riskScore = resultSet.getString("risk_score") == null ? -1 : resultSet.getInt("risk_score");
                    String riskLevel = riskLevel(riskScore);
                    if (riskLevelFilter != null && !riskLevelFilter.isBlank() && !"All".equalsIgnoreCase(riskLevelFilter)
                            && !riskLevel.equalsIgnoreCase(riskLevelFilter)) {
                        continue;
                    }
                    String note = value(resultSet.getString("note"));
                    rows.add(new RecommendationBoardRow(
                            value(resultSet.getString("patient_id")),
                            fallback(resultSet.getString("patient_name"), "Unknown patient"),
                            fallback(resultSet.getString("section"), "Unassigned"),
                            fallback(resultSet.getString("room"), "-"),
                            fallback(resultSet.getString("status"), "Unknown"),
                            fallback(resultSet.getString("priority"), "NORMAL"),
                            riskScore,
                            riskLevel,
                            note.isBlank() ? "No generated recommendation yet." : note,
                            reasonSummary(note),
                            fallback(resultSet.getString("created_at"), "-")
                    ));
                }
            }
        }
        return rows;
    }

    public Optional<AiNoteSummary> findLatestRecommendation(String patientId) throws SQLException {
        try (Connection connection = DatabaseManager.getConnection()) {
            return findLatestAiNote(connection, patientId);
        }
    }

    public List<String> findSections() throws SQLException {
        ArrayList<String> sections = new ArrayList<>();
        String sql = "SELECT DISTINCT section FROM patients WHERE section IS NOT NULL AND TRIM(section) <> '' ORDER BY section COLLATE NOCASE";
        try (Connection connection = DatabaseManager.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                sections.add(resultSet.getString("section"));
            }
        }
        return sections;
    }

    private RecommendationResult scorePatient(PatientContext patient, List<VitalReading> vitals,
                                               List<AlertContext> alerts, int recentTimelineEvents,
                                               Optional<AiNoteSummary> latestNote) {
        int score = 0;
        LinkedHashSet<String> reasons = new LinkedHashSet<>();
        Map<String, List<VitalReading>> byType = groupVitals(vitals);

        if (vitals.isEmpty()) {
            score += 25;
            reasons.add("No SQLite vital readings are available for this patient.");
        } else {
            LocalDateTime latestVitalTime = latestVitalTime(vitals);
            if (latestVitalTime.equals(LocalDateTime.MIN) || latestVitalTime.isBefore(LocalDateTime.now().minusHours(24))) {
                score += 15;
                reasons.add("No vital reading has been recorded in the last 24 hours.");
            }
        }

        score += heartRateScore(byType, reasons);
        score += oxygenScore(byType, reasons);
        score += feverScore(byType, reasons);
        score += bloodPressureScore(byType, reasons);
        score += alertScore(alerts, reasons);

        if (recentTimelineEvents >= 8) {
            score += 5;
            reasons.add("Patient has several recent clinical timeline events requiring review.");
        }
        latestNote.ifPresent(note -> {
            if (note.getRiskScore() >= 80) {
                reasons.add("Previous AI note already indicated high risk.");
            }
        });

        score = Math.max(0, Math.min(100, score));
        if (reasons.isEmpty()) {
            reasons.add("No major rule-based risk indicators detected from available SQLite data.");
        }

        return new RecommendationResult(
                patient.getPatientId(),
                patient.getPatientName(),
                patient.getSection(),
                patient.getRoom(),
                score,
                riskLevel(score),
                recommendationText(score, reasons),
                new ArrayList<>(reasons),
                now()
        );
    }

    private int heartRateScore(Map<String, List<VitalReading>> byType, Set<String> reasons) {
        List<VitalReading> readings = readingsFor(byType, "heart");
        if (readings.isEmpty()) {
            return 0;
        }
        double latest = readings.get(0).getNumericValue();
        int score = 0;
        if (latest >= 120) {
            score += 20;
            reasons.add("Latest heart rate is critically elevated.");
        } else if (latest >= 100) {
            score += 12;
            reasons.add("Latest heart rate is elevated.");
        }
        if (isRising(readings)) {
            score += 8;
            reasons.add("Heart rate trend is rising across recent readings.");
        }
        return score;
    }

    private int oxygenScore(Map<String, List<VitalReading>> byType, Set<String> reasons) {
        List<VitalReading> readings = readingsFor(byType, "oxygen");
        if (readings.isEmpty()) {
            return 0;
        }
        double latest = readings.get(0).getNumericValue();
        int score = 0;
        if (latest < 90) {
            score += 35;
            reasons.add("Latest oxygen saturation is dangerously low.");
        } else if (latest < 92) {
            score += 25;
            reasons.add("Latest oxygen saturation is low.");
        } else if (latest < 95) {
            score += 10;
            reasons.add("Latest oxygen saturation is borderline low.");
        }
        if (isFalling(readings)) {
            score += 10;
            reasons.add("Oxygen trend is falling across recent readings.");
        }
        return score;
    }

    private int feverScore(Map<String, List<VitalReading>> byType, Set<String> reasons) {
        List<VitalReading> readings = readingsFor(byType, "temperature");
        if (readings.isEmpty()) {
            return 0;
        }
        double latest = readings.get(0).getNumericValue();
        int score = 0;
        if (latest >= 39) {
            score += 25;
            reasons.add("Latest temperature suggests high fever.");
        } else if (latest >= 38) {
            score += 15;
            reasons.add("Latest temperature suggests fever.");
        }
        if (isRising(readings)) {
            score += 5;
            reasons.add("Temperature trend is rising.");
        }
        return score;
    }

    private int bloodPressureScore(Map<String, List<VitalReading>> byType, Set<String> reasons) {
        List<VitalReading> systolic = readingsFor(byType, "systolic");
        List<VitalReading> diastolic = readingsFor(byType, "diastolic");
        double latestSystolic = systolic.isEmpty() ? -1 : systolic.get(0).getNumericValue();
        double latestDiastolic = diastolic.isEmpty() ? -1 : diastolic.get(0).getNumericValue();
        int score = 0;
        if (latestSystolic >= 180 || latestDiastolic >= 120) {
            score += 30;
            reasons.add("Latest blood pressure is in a critical range.");
        } else if (latestSystolic >= 140 || latestDiastolic >= 90) {
            score += 15;
            reasons.add("Latest blood pressure is elevated.");
        }
        if (isRising(systolic) || isRising(diastolic)) {
            score += 8;
            reasons.add("Blood pressure trend is rising.");
        }
        return score;
    }

    private int alertScore(List<AlertContext> alerts, Set<String> reasons) {
        int activeCritical = 0;
        int activeEmergency = 0;
        int recentCritical = 0;
        for (AlertContext alert : alerts) {
            String severity = alert.getSeverity().toUpperCase(Locale.ROOT);
            if ("ACTIVE".equalsIgnoreCase(alert.getStatus())) {
                if (severity.contains("EMERGENCY")) {
                    activeEmergency++;
                } else if (severity.contains("CRITICAL")) {
                    activeCritical++;
                }
            }
            if ((severity.contains("CRITICAL") || severity.contains("EMERGENCY"))
                    && alert.getCreatedAt().isAfter(LocalDateTime.now().minusHours(24))) {
                recentCritical++;
            }
        }
        int score = Math.min(25, activeEmergency * 25 + activeCritical * 15);
        if (activeEmergency > 0) {
            reasons.add("Patient has active emergency alert(s).");
        } else if (activeCritical > 0) {
            reasons.add("Patient has active critical alert(s).");
        }
        if (recentCritical >= 2) {
            score += 15;
            reasons.add("Repeated critical/emergency alerts occurred in the last 24 hours.");
        }
        return score;
    }

    private void saveRecommendation(RecommendationResult result) throws SQLException {
        String sql = "INSERT INTO ai_notes(patient_id, risk_score, note, source_title, created_at) "
                + "SELECT ?, ?, ?, ?, ? "
                + "WHERE NOT EXISTS ("
                + "SELECT 1 FROM ai_notes WHERE patient_id = ? AND source_title = ? AND note = ? "
                + "AND datetime(created_at) >= datetime('now', '-6 hours')"
                + ")";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, result.getPatientId());
            statement.setInt(2, result.getRiskScore());
            statement.setString(3, result.getRecommendation());
            statement.setString(4, SOURCE_TITLE);
            statement.setString(5, result.getCreatedAt());
            statement.setString(6, result.getPatientId());
            statement.setString(7, SOURCE_TITLE);
            statement.setString(8, result.getRecommendation());
            statement.executeUpdate();
        }
    }

    private Optional<PatientContext> findPatient(Connection connection, String patientId) throws SQLException {
        String sql = "SELECT patient_id, TRIM(first_name || ' ' || last_name) AS patient_name, section, room FROM patients WHERE patient_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, patientId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(new PatientContext(
                            resultSet.getString("patient_id"),
                            fallback(resultSet.getString("patient_name"), "Unknown patient"),
                            fallback(resultSet.getString("section"), "Unassigned"),
                            fallback(resultSet.getString("room"), "-")
                    ));
                }
            }
        }
        return Optional.empty();
    }

    private List<VitalReading> findVitals(Connection connection, String patientId) throws SQLException {
        ArrayList<VitalReading> rows = new ArrayList<>();
        String sql = "SELECT vital_type, value, unit, recorded_at FROM vital_readings WHERE patient_id = ? ORDER BY id DESC LIMIT 120";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, patientId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    double numeric = parseNumeric(resultSet.getString("value"));
                    if (!Double.isNaN(numeric)) {
                        rows.add(new VitalReading(
                                value(resultSet.getString("vital_type")),
                                numeric,
                                value(resultSet.getString("unit")),
                                parseDateTime(resultSet.getString("recorded_at"))
                        ));
                    }
                }
            }
        }
        rows.sort(Comparator.comparing(VitalReading::getRecordedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        return rows;
    }

    private List<AlertContext> findAlerts(Connection connection, String patientId) throws SQLException {
        ArrayList<AlertContext> rows = new ArrayList<>();
        String sql = "SELECT severity, status, created_at FROM alerts WHERE patient_id = ? ORDER BY id DESC LIMIT 50";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, patientId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(new AlertContext(
                            value(resultSet.getString("severity")),
                            value(resultSet.getString("status")),
                            parseDateTime(resultSet.getString("created_at"))
                    ));
                }
            }
        }
        return rows;
    }

    private int countRecentTimelineEvents(Connection connection, String patientId) throws SQLException {
        int count = 0;
        count += countRecent(connection, "vital_readings", "patient_id", patientId, "recorded_at");
        count += countRecent(connection, "alerts", "patient_id", patientId, "created_at");
        count += countRecent(connection, "ai_notes", "patient_id", patientId, "created_at");
        count += countRecent(connection, "medical_files", "patient_id", patientId, "uploaded_at");
        count += countRecent(connection, "medical_history", "patient_id", patientId, "created_at");
        count += countRecent(connection, "medication_events", "patient_id", patientId, "given_at");
        return count;
    }

    private int countRecent(Connection connection, String table, String patientColumn, String patientId, String timeColumn) throws SQLException {
        String sql = "SELECT " + timeColumn + " FROM " + table + " WHERE " + patientColumn + " = ?";
        int count = 0;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, patientId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    LocalDateTime time = parseDateTime(resultSet.getString(1));
                    if (!time.equals(LocalDateTime.MIN) && time.isAfter(LocalDateTime.now().minusHours(24))) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private Optional<AiNoteSummary> findLatestAiNote(Connection connection, String patientId) throws SQLException {
        String sql = "SELECT risk_score, note, source_title, created_at FROM ai_notes WHERE patient_id = ? ORDER BY datetime(created_at) DESC, id DESC LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, patientId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(new AiNoteSummary(
                            resultSet.getInt("risk_score"),
                            value(resultSet.getString("note")),
                            value(resultSet.getString("source_title")),
                            value(resultSet.getString("created_at"))
                    ));
                }
            }
        }
        return Optional.empty();
    }

    private Map<String, List<VitalReading>> groupVitals(List<VitalReading> vitals) {
        LinkedHashMap<String, List<VitalReading>> rows = new LinkedHashMap<>();
        for (VitalReading vital : vitals) {
            rows.computeIfAbsent(vital.getVitalType().toLowerCase(Locale.ROOT), key -> new ArrayList<>()).add(vital);
        }
        return rows;
    }

    private List<VitalReading> readingsFor(Map<String, List<VitalReading>> byType, String contains) {
        ArrayList<VitalReading> rows = new ArrayList<>();
        for (Map.Entry<String, List<VitalReading>> entry : byType.entrySet()) {
            if (entry.getKey().contains(contains)) {
                rows.addAll(entry.getValue());
            }
        }
        rows.sort(Comparator.comparing(VitalReading::getRecordedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        return rows;
    }

    private boolean isRising(List<VitalReading> readings) {
        return readings.size() >= 3
                && readings.get(0).getNumericValue() > readings.get(1).getNumericValue()
                && readings.get(1).getNumericValue() > readings.get(2).getNumericValue();
    }

    private boolean isFalling(List<VitalReading> readings) {
        return readings.size() >= 3
                && readings.get(0).getNumericValue() < readings.get(1).getNumericValue()
                && readings.get(1).getNumericValue() < readings.get(2).getNumericValue();
    }

    private LocalDateTime latestVitalTime(List<VitalReading> vitals) {
        LocalDateTime latest = LocalDateTime.MIN;
        for (VitalReading vital : vitals) {
            if (vital.getRecordedAt().isAfter(latest)) {
                latest = vital.getRecordedAt();
            }
        }
        return latest;
    }

    private String recommendationText(int score, Set<String> reasons) {
        String lead;
        if (score >= 80) {
            lead = "High risk: immediate clinical review recommended.";
        } else if (score >= 50) {
            lead = "Moderate risk: review patient trends during current round.";
        } else if (score >= 25) {
            lead = "Low-to-moderate risk: continue monitoring and verify recent vitals.";
        } else {
            lead = "Low risk from available SQLite data: continue routine monitoring.";
        }
        return lead + " Reasons: " + String.join(" ", reasons)
                + " Rule-based decision support only. Not a medical diagnosis.";
    }

    private String reasonSummary(String note) {
        String normalized = value(note);
        int index = normalized.indexOf("Reasons:");
        if (index >= 0) {
            return normalized.substring(index + "Reasons:".length()).trim();
        }
        return normalized.isBlank() ? "-" : normalized;
    }

    private String riskLevel(int score) {
        if (score < 0) {
            return "UNSCORED";
        }
        if (score >= 80) {
            return "CRITICAL";
        }
        if (score >= 50) {
            return "HIGH";
        }
        if (score >= 25) {
            return "MODERATE";
        }
        return "LOW";
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return LocalDateTime.MIN;
        }
        try {
            return LocalDateTime.parse(value, SQLITE_DATE_TIME);
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDateTime.parse(value.replace(" ", "T"));
            } catch (DateTimeParseException ignoredAgain) {
                try {
                    return LocalDateTime.parse(value, LEGACY_DATE_TIME);
                } catch (DateTimeParseException ignoredThird) {
                    return LocalDateTime.MIN;
                }
            }
        }
    }

    private double parseNumeric(String value) {
        if (value == null) {
            return Double.NaN;
        }
        String cleaned = value.replaceAll("[^0-9.\\-]", " ").trim();
        if (cleaned.isBlank()) {
            return Double.NaN;
        }
        try {
            return Double.parseDouble(cleaned.split("\\s+")[0]);
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    private String now() {
        return LocalDateTime.now().format(SQLITE_DATE_TIME);
    }

    private void bind(PreparedStatement statement, ArrayList<String> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            statement.setString(i + 1, params.get(i));
        }
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
            System.out.println("SQLite AI recommendation schema check failed: " + e.getMessage());
        }
    }

    private static class PatientContext {
        private final String patientId;
        private final String patientName;
        private final String section;
        private final String room;

        private PatientContext(String patientId, String patientName, String section, String room) {
            this.patientId = patientId;
            this.patientName = patientName;
            this.section = section;
            this.room = room;
        }

        public String getPatientId() { return patientId; }
        public String getPatientName() { return patientName; }
        public String getSection() { return section; }
        public String getRoom() { return room; }
    }

    private static class VitalReading {
        private final String vitalType;
        private final double numericValue;
        private final String unit;
        private final LocalDateTime recordedAt;

        private VitalReading(String vitalType, double numericValue, String unit, LocalDateTime recordedAt) {
            this.vitalType = vitalType;
            this.numericValue = numericValue;
            this.unit = unit;
            this.recordedAt = recordedAt;
        }

        public String getVitalType() { return vitalType; }
        public double getNumericValue() { return numericValue; }
        public String getUnit() { return unit; }
        public LocalDateTime getRecordedAt() { return recordedAt; }
    }

    private static class AlertContext {
        private final String severity;
        private final String status;
        private final LocalDateTime createdAt;

        private AlertContext(String severity, String status, LocalDateTime createdAt) {
            this.severity = severity;
            this.status = status;
            this.createdAt = createdAt;
        }

        public String getSeverity() { return severity; }
        public String getStatus() { return status; }
        public LocalDateTime getCreatedAt() { return createdAt; }
    }

    public static class RecommendationResult {
        private final String patientId;
        private final String patientName;
        private final String section;
        private final String room;
        private final int riskScore;
        private final String riskLevel;
        private final String recommendation;
        private final List<String> reasons;
        private final String createdAt;

        public RecommendationResult(String patientId, String patientName, String section, String room,
                                    int riskScore, String riskLevel, String recommendation,
                                    List<String> reasons, String createdAt) {
            this.patientId = patientId;
            this.patientName = patientName;
            this.section = section;
            this.room = room;
            this.riskScore = riskScore;
            this.riskLevel = riskLevel;
            this.recommendation = recommendation;
            this.reasons = reasons;
            this.createdAt = createdAt;
        }

        public String getPatientId() { return patientId; }
        public String getPatientName() { return patientName; }
        public String getSection() { return section; }
        public String getRoom() { return room; }
        public int getRiskScore() { return riskScore; }
        public String getRiskLevel() { return riskLevel; }
        public String getRecommendation() { return recommendation; }
        public List<String> getReasons() { return reasons; }
        public String getCreatedAt() { return createdAt; }
    }

    public static class AiNoteSummary {
        private final int riskScore;
        private final String note;
        private final String sourceTitle;
        private final String createdAt;

        public AiNoteSummary(int riskScore, String note, String sourceTitle, String createdAt) {
            this.riskScore = riskScore;
            this.note = note;
            this.sourceTitle = sourceTitle;
            this.createdAt = createdAt;
        }

        public int getRiskScore() { return riskScore; }
        public String getNote() { return note; }
        public String getSourceTitle() { return sourceTitle; }
        public String getCreatedAt() { return createdAt; }
        public String getRiskLevel() {
            if (riskScore >= 80) return "CRITICAL";
            if (riskScore >= 50) return "HIGH";
            if (riskScore >= 25) return "MODERATE";
            return "LOW";
        }
    }

    public static class RecommendationBoardRow {
        private final String patientId;
        private final String patientName;
        private final String section;
        private final String room;
        private final String status;
        private final String priority;
        private final int riskScore;
        private final String riskLevel;
        private final String latestRecommendation;
        private final String reasonIndicators;
        private final String createdAt;

        public RecommendationBoardRow(String patientId, String patientName, String section, String room,
                                      String status, String priority, int riskScore, String riskLevel,
                                      String latestRecommendation, String reasonIndicators, String createdAt) {
            this.patientId = patientId;
            this.patientName = patientName;
            this.section = section;
            this.room = room;
            this.status = status;
            this.priority = priority;
            this.riskScore = riskScore;
            this.riskLevel = riskLevel;
            this.latestRecommendation = latestRecommendation;
            this.reasonIndicators = reasonIndicators;
            this.createdAt = createdAt;
        }

        public String getPatientId() { return patientId; }
        public String getPatientName() { return patientName; }
        public String getSection() { return section; }
        public String getRoom() { return room; }
        public String getStatus() { return status; }
        public String getPriority() { return priority; }
        public int getRiskScore() { return riskScore; }
        public String getRiskScoreText() { return riskScore < 0 ? "Not generated" : String.valueOf(riskScore); }
        public String getRiskLevel() { return riskLevel; }
        public String getLatestRecommendation() { return latestRecommendation; }
        public String getReasonIndicators() { return reasonIndicators; }
        public String getCreatedAt() { return createdAt; }
    }
}
