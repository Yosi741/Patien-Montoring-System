package ui.javafx.pages.medications;

import app.DatabaseManager;
import app.SchemaInitializer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SqliteMedicationCatalogDao {

    public SqliteMedicationCatalogDao() {
        ensureSchema();
    }

    public long insertCatalogItem(MedicationCatalogRecord record) throws SQLException {
        String sql = "INSERT INTO medication_catalog(name, form_type, default_route, default_frequency, default_unit, "
                + "allowed_units, allowed_routes, min_single_dose, max_single_dose, max_daily_dose, "
                + "min_interval_minutes, min_interval_hours, requires_doctor_override, danger_notes, notes, active, updated_at) "
                + "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bindCatalog(statement, record);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : -1L;
            }
        }
    }

    public void updateCatalogItem(MedicationCatalogRecord record) throws SQLException {
        String sql = "UPDATE medication_catalog SET name = ?, form_type = ?, default_route = ?, default_frequency = ?, "
                + "default_unit = ?, allowed_units = ?, allowed_routes = ?, min_single_dose = ?, max_single_dose = ?, "
                + "max_daily_dose = ?, min_interval_minutes = ?, min_interval_hours = ?, requires_doctor_override = ?, "
                + "danger_notes = ?, notes = ?, active = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindCatalog(statement, record);
            statement.setLong(17, record.getId());
            statement.executeUpdate();
        }
    }

    public Optional<MedicationCatalogRecord> findCatalogItemById(long id) throws SQLException {
        String sql = catalogSelect() + " WHERE id = ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapCatalog(resultSet)) : Optional.empty();
            }
        }
    }

    public Optional<MedicationCatalogRecord> findCatalogItemByName(String name) throws SQLException {
        String sql = catalogSelect() + " WHERE UPPER(name) = ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value(name).toUpperCase());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapCatalog(resultSet)) : Optional.empty();
            }
        }
    }

    public List<MedicationCatalogRecord> searchCatalog(String search, boolean activeOnly) throws SQLException {
        ArrayList<MedicationCatalogRecord> rows = new ArrayList<>();
        StringBuilder sql = new StringBuilder(catalogSelect()).append(" WHERE 1 = 1 ");
        ArrayList<String> params = new ArrayList<>();
        if (activeOnly) {
            sql.append("AND active = 1 ");
        }
        if (search != null && !search.isBlank()) {
            sql.append("AND (name LIKE ? OR form_type LIKE ? OR notes LIKE ? OR danger_notes LIKE ?) ");
            String like = "%" + search.trim() + "%";
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
        }
        sql.append("ORDER BY name COLLATE NOCASE");
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                statement.setString(i + 1, params.get(i));
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(mapCatalog(resultSet));
                }
            }
        }
        return rows;
    }

    public boolean catalogNameExists(String name, long excludeId) throws SQLException {
        String sql = "SELECT 1 FROM medication_catalog WHERE UPPER(name) = ? AND id <> ? LIMIT 1";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value(name).toUpperCase());
            statement.setLong(2, excludeId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    public long insertInteraction(MedicationInteractionRecord record) throws SQLException {
        MedicationPair pair = MedicationPair.of(record.getMedicationA(), record.getMedicationB());
        String sql = "INSERT INTO medication_interactions(medication_a_id, medication_b_id, medication_a, medication_b, "
                + "severity, min_wait_minutes, notes, message, active, updated_at) "
                + "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP) "
                + "ON CONFLICT(medication_a, medication_b) DO UPDATE SET "
                + "medication_a_id = excluded.medication_a_id, medication_b_id = excluded.medication_b_id, "
                + "severity = excluded.severity, min_wait_minutes = excluded.min_wait_minutes, "
                + "notes = excluded.notes, message = excluded.message, active = excluded.active, updated_at = CURRENT_TIMESTAMP";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            setNullableLong(statement, 1, orderedMedicationId(record, pair.first));
            setNullableLong(statement, 2, orderedMedicationId(record, pair.second));
            statement.setString(3, pair.first);
            statement.setString(4, pair.second);
            statement.setString(5, value(record.getSeverity()).toUpperCase());
            statement.setInt(6, Math.max(0, record.getMinWaitMinutes()));
            statement.setString(7, value(record.getNotes()));
            statement.setString(8, value(record.getMessage()));
            statement.setInt(9, record.isActive() ? 1 : 0);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : -1L;
            }
        }
    }

    public void updateInteraction(MedicationInteractionRecord record) throws SQLException {
        MedicationPair pair = MedicationPair.of(record.getMedicationA(), record.getMedicationB());
        String sql = "UPDATE medication_interactions SET medication_a_id = ?, medication_b_id = ?, medication_a = ?, medication_b = ?, severity = ?, "
                + "min_wait_minutes = ?, notes = ?, message = ?, active = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            setNullableLong(statement, 1, orderedMedicationId(record, pair.first));
            setNullableLong(statement, 2, orderedMedicationId(record, pair.second));
            statement.setString(3, pair.first);
            statement.setString(4, pair.second);
            statement.setString(5, value(record.getSeverity()).toUpperCase());
            statement.setInt(6, Math.max(0, record.getMinWaitMinutes()));
            statement.setString(7, value(record.getNotes()));
            statement.setString(8, value(record.getMessage()));
            statement.setInt(9, record.isActive() ? 1 : 0);
            statement.setLong(10, record.getId());
            statement.executeUpdate();
        }
    }

    public List<MedicationInteractionRecord> findInteractionsForMedication(long catalogMedicationId) throws SQLException {
        ArrayList<MedicationInteractionRecord> rows = new ArrayList<>();
        String sql = interactionSelect() + " WHERE active = 1 AND (medication_a_id = ? OR medication_b_id = ?) "
                + "ORDER BY severity DESC, medication_a COLLATE NOCASE, medication_b COLLATE NOCASE";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, catalogMedicationId);
            statement.setLong(2, catalogMedicationId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(mapInteraction(resultSet));
                }
            }
        }
        return rows;
    }

    public Optional<MedicationInteractionRecord> findActiveInteractionBetween(long medicationAId, long medicationBId) throws SQLException {
        String sql = interactionSelect() + " WHERE active = 1 AND ((medication_a_id = ? AND medication_b_id = ?) "
                + "OR (medication_a_id = ? AND medication_b_id = ?)) LIMIT 1";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, medicationAId);
            statement.setLong(2, medicationBId);
            statement.setLong(3, medicationBId);
            statement.setLong(4, medicationAId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapInteraction(resultSet)) : Optional.empty();
            }
        }
    }

    public List<MedicationInteractionRecord> listActiveInteractions() throws SQLException {
        ArrayList<MedicationInteractionRecord> rows = new ArrayList<>();
        String sql = interactionSelect() + " WHERE active = 1 ORDER BY medication_a COLLATE NOCASE, medication_b COLLATE NOCASE";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                rows.add(mapInteraction(resultSet));
            }
        }
        return rows;
    }

    public void deactivateInteraction(long interactionId) throws SQLException {
        String sql = "UPDATE medication_interactions SET active = 0, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, interactionId);
            statement.executeUpdate();
        }
    }

    public List<MedicationInteractionRecord> findInteractionsForMedication(String medicationName) throws SQLException {
        ArrayList<MedicationInteractionRecord> rows = new ArrayList<>();
        String normalized = normalizeName(medicationName);
        String sql = interactionSelect() + " WHERE active = 1 AND (UPPER(medication_a) = ? OR UPPER(medication_b) = ?) "
                + "ORDER BY severity DESC, medication_a COLLATE NOCASE, medication_b COLLATE NOCASE";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalized.toUpperCase());
            statement.setString(2, normalized.toUpperCase());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(mapInteraction(resultSet));
                }
            }
        }
        return rows;
    }

    public Optional<MedicationInteractionRecord> findInteraction(String medicationA, String medicationB) throws SQLException {
        MedicationPair pair = MedicationPair.of(medicationA, medicationB);
        String sql = interactionSelect() + " WHERE UPPER(medication_a) = ? AND UPPER(medication_b) = ? LIMIT 1";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, pair.first.toUpperCase());
            statement.setString(2, pair.second.toUpperCase());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapInteraction(resultSet)) : Optional.empty();
            }
        }
    }

    public List<MedicationInteractionRecord> searchInteractions(String search, boolean activeOnly) throws SQLException {
        ArrayList<MedicationInteractionRecord> rows = new ArrayList<>();
        StringBuilder sql = new StringBuilder(interactionSelect()).append(" WHERE 1 = 1 ");
        ArrayList<String> params = new ArrayList<>();
        if (activeOnly) {
            sql.append("AND active = 1 ");
        }
        if (search != null && !search.isBlank()) {
            sql.append("AND (medication_a LIKE ? OR medication_b LIKE ? OR message LIKE ?) ");
            String like = "%" + search.trim() + "%";
            params.add(like);
            params.add(like);
            params.add(like);
        }
        sql.append("ORDER BY medication_a COLLATE NOCASE, medication_b COLLATE NOCASE");
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                statement.setString(i + 1, params.get(i));
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(mapInteraction(resultSet));
                }
            }
        }
        return rows;
    }

    private String catalogSelect() {
        return "SELECT id, name, form_type, default_route, default_frequency, default_unit, allowed_units, "
                + "allowed_routes, min_single_dose, max_single_dose, max_daily_dose, min_interval_minutes, "
                + "min_interval_hours, requires_doctor_override, danger_notes, notes, active, created_at, updated_at "
                + "FROM medication_catalog";
    }

    private String interactionSelect() {
        return "SELECT id, medication_a_id, medication_b_id, medication_a, medication_b, severity, min_wait_minutes, notes, message, active, created_at, updated_at "
                + "FROM medication_interactions";
    }

    private void bindCatalog(PreparedStatement statement, MedicationCatalogRecord record) throws SQLException {
        statement.setString(1, value(record.getName()));
        statement.setString(2, value(record.getFormType()).isBlank() ? "OTHER" : value(record.getFormType()).toUpperCase());
        statement.setString(3, value(record.getDefaultRoute()));
        statement.setString(4, value(record.getDefaultFrequency()));
        statement.setString(5, value(record.getDefaultUnit()));
        statement.setString(6, value(record.getAllowedUnits()));
        statement.setString(7, value(record.getAllowedRoutes()));
        setNullableDouble(statement, 8, record.getMinSingleDose());
        setNullableDouble(statement, 9, record.getMaxSingleDose());
        setNullableDouble(statement, 10, record.getMaxDailyDose());
        setNullableDouble(statement, 11, record.getMinIntervalMinutes());
        setNullableDouble(statement, 12, record.getMinIntervalHours());
        statement.setInt(13, record.isRequiresDoctorOverride() ? 1 : 0);
        statement.setString(14, value(record.getDangerNotes()));
        statement.setString(15, value(record.getNotes()));
        statement.setInt(16, record.isActive() ? 1 : 0);
    }

    private MedicationCatalogRecord mapCatalog(ResultSet resultSet) throws SQLException {
        return new MedicationCatalogRecord(
                resultSet.getLong("id"),
                resultSet.getString("name"),
                resultSet.getString("form_type"),
                resultSet.getString("default_route"),
                resultSet.getString("default_frequency"),
                resultSet.getString("default_unit"),
                resultSet.getString("allowed_units"),
                resultSet.getString("allowed_routes"),
                nullableDouble(resultSet, "min_single_dose"),
                nullableDouble(resultSet, "max_single_dose"),
                nullableDouble(resultSet, "max_daily_dose"),
                nullableDouble(resultSet, "min_interval_minutes"),
                nullableDouble(resultSet, "min_interval_hours"),
                resultSet.getInt("requires_doctor_override") == 1,
                resultSet.getString("danger_notes"),
                resultSet.getString("notes"),
                resultSet.getInt("active") == 1,
                resultSet.getString("created_at"),
                resultSet.getString("updated_at")
        );
    }

    private MedicationInteractionRecord mapInteraction(ResultSet resultSet) throws SQLException {
        return new MedicationInteractionRecord(
                resultSet.getLong("id"),
                nullableLong(resultSet, "medication_a_id"),
                nullableLong(resultSet, "medication_b_id"),
                resultSet.getString("medication_a"),
                resultSet.getString("medication_b"),
                resultSet.getString("severity"),
                resultSet.getInt("min_wait_minutes"),
                resultSet.getString("notes"),
                resultSet.getString("message"),
                resultSet.getInt("active") == 1,
                resultSet.getString("created_at"),
                resultSet.getString("updated_at")
        );
    }

    private void setNullableDouble(PreparedStatement statement, int index, Double value) throws SQLException {
        if (value == null) {
            statement.setNull(index, java.sql.Types.REAL);
        } else {
            statement.setDouble(index, value);
        }
    }

    private void setNullableLong(PreparedStatement statement, int index, Long value) throws SQLException {
        if (value == null || value <= 0) {
            statement.setNull(index, java.sql.Types.INTEGER);
        } else {
            statement.setLong(index, value);
        }
    }

    private Double nullableDouble(ResultSet resultSet, String column) throws SQLException {
        double value = resultSet.getDouble(column);
        return resultSet.wasNull() ? null : value;
    }

    private Long nullableLong(ResultSet resultSet, String column) throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private Long orderedMedicationId(MedicationInteractionRecord record, String orderedName) {
        if (orderedName == null) {
            return null;
        }
        if (orderedName.equals(record.getMedicationA())) {
            return record.getMedicationAId();
        }
        if (orderedName.equals(record.getMedicationB())) {
            return record.getMedicationBId();
        }
        if (orderedName.equalsIgnoreCase(record.getMedicationA())) {
            return record.getMedicationAId();
        }
        if (orderedName.equalsIgnoreCase(record.getMedicationB())) {
            return record.getMedicationBId();
        }
        return null;
    }

    private String normalizeName(String value) {
        return value == null ? "" : value.trim();
    }

    private String value(String value) {
        return value == null ? "" : value.trim();
    }

    private void ensureSchema() {
        try {
            SchemaInitializer.initialize();
        } catch (Exception e) {
            System.out.println("SQLite medication catalog schema check failed: " + e.getMessage());
        }
    }

    private static class MedicationPair {
        private final String first;
        private final String second;

        private MedicationPair(String first, String second) {
            this.first = first;
            this.second = second;
        }

        private static MedicationPair of(String medicationA, String medicationB) {
            String first = medicationA == null ? "" : medicationA.trim();
            String second = medicationB == null ? "" : medicationB.trim();
            if (first.compareToIgnoreCase(second) <= 0) {
                return new MedicationPair(first, second);
            }
            return new MedicationPair(second, first);
        }
    }

    public static class MedicationCatalogRecord {
        private final long id;
        private final String name;
        private final String formType;
        private final String defaultRoute;
        private final String defaultFrequency;
        private final String defaultUnit;
        private final String allowedUnits;
        private final String allowedRoutes;
        private final Double minSingleDose;
        private final Double maxSingleDose;
        private final Double maxDailyDose;
        private final Double minIntervalMinutes;
        private final Double minIntervalHours;
        private final boolean requiresDoctorOverride;
        private final String dangerNotes;
        private final String notes;
        private final boolean active;
        private final String createdAt;
        private final String updatedAt;

        public MedicationCatalogRecord(long id, String name, String formType, String defaultRoute, String defaultFrequency,
                                       String defaultUnit, String allowedUnits, String allowedRoutes, Double minSingleDose,
                                       Double maxSingleDose, Double maxDailyDose, Double minIntervalMinutes,
                                       Double minIntervalHours, boolean requiresDoctorOverride, String dangerNotes,
                                       String notes, boolean active, String createdAt, String updatedAt) {
            this.id = id;
            this.name = name;
            this.formType = formType;
            this.defaultRoute = defaultRoute;
            this.defaultFrequency = defaultFrequency;
            this.defaultUnit = defaultUnit;
            this.allowedUnits = allowedUnits;
            this.allowedRoutes = allowedRoutes;
            this.minSingleDose = minSingleDose;
            this.maxSingleDose = maxSingleDose;
            this.maxDailyDose = maxDailyDose;
            this.minIntervalMinutes = minIntervalMinutes;
            this.minIntervalHours = minIntervalHours;
            this.requiresDoctorOverride = requiresDoctorOverride;
            this.dangerNotes = dangerNotes;
            this.notes = notes;
            this.active = active;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
        }

        public long getId() { return id; }
        public String getName() { return name; }
        public String getFormType() { return formType; }
        public String getDefaultRoute() { return defaultRoute; }
        public String getDefaultFrequency() { return defaultFrequency; }
        public String getDefaultUnit() { return defaultUnit; }
        public String getAllowedUnits() { return allowedUnits; }
        public String getAllowedRoutes() { return allowedRoutes; }
        public Double getMinSingleDose() { return minSingleDose; }
        public Double getMaxSingleDose() { return maxSingleDose; }
        public Double getMaxDailyDose() { return maxDailyDose; }
        public Double getMinIntervalMinutes() { return minIntervalMinutes; }
        public Double getMinIntervalHours() { return minIntervalHours; }
        public boolean isRequiresDoctorOverride() { return requiresDoctorOverride; }
        public String getDangerNotes() { return dangerNotes; }
        public String getNotes() { return notes; }
        public boolean isActive() { return active; }
        public String getCreatedAt() { return createdAt; }
        public String getUpdatedAt() { return updatedAt; }

        @Override
        public String toString() {
            return name == null ? "" : name;
        }
    }

    public static class MedicationInteractionRecord {
        private final long id;
        private final Long medicationAId;
        private final Long medicationBId;
        private final String medicationA;
        private final String medicationB;
        private final String severity;
        private final int minWaitMinutes;
        private final String notes;
        private final String message;
        private final boolean active;
        private final String createdAt;
        private final String updatedAt;

        public MedicationInteractionRecord(long id, String medicationA, String medicationB, String severity,
                                           String message, boolean active, String createdAt, String updatedAt) {
            this(id, null, null, medicationA, medicationB, severity, 0, message, message, active, createdAt, updatedAt);
        }

        public MedicationInteractionRecord(long id, Long medicationAId, Long medicationBId, String medicationA, String medicationB,
                                           String severity, int minWaitMinutes, String notes, String message,
                                           boolean active, String createdAt, String updatedAt) {
            this.id = id;
            this.medicationAId = medicationAId;
            this.medicationBId = medicationBId;
            this.medicationA = medicationA;
            this.medicationB = medicationB;
            this.severity = severity;
            this.minWaitMinutes = minWaitMinutes;
            this.notes = notes;
            this.message = message;
            this.active = active;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
        }

        public long getId() { return id; }
        public Long getMedicationAId() { return medicationAId; }
        public Long getMedicationBId() { return medicationBId; }
        public String getMedicationA() { return medicationA; }
        public String getMedicationB() { return medicationB; }
        public String getSeverity() { return severity; }
        public int getMinWaitMinutes() { return minWaitMinutes; }
        public String getNotes() { return notes; }
        public String getMessage() { return message; }
        public boolean isActive() { return active; }
        public String getCreatedAt() { return createdAt; }
        public String getUpdatedAt() { return updatedAt; }
    }
}
