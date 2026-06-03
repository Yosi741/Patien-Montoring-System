package dao;

import database.DatabaseManager;
import database.SchemaInitializer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SqliteMedicationDao implements MedicationDao {

    public SqliteMedicationDao() {
        ensureSchema();
    }

    @Override
    public long saveMedication(String patientId, String name, String dose, String route, String frequency, boolean active) throws SQLException {
        Long existingId = findMedicationId(patientId, name, dose, route, frequency, active);
        if (existingId != null) {
            return existingId;
        }

        String sql = "INSERT INTO medications(patient_id, name, dose, route, frequency, active) VALUES(?, ?, ?, ?, ?, ?)";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, patientId);
            statement.setString(2, name);
            statement.setString(3, value(dose));
            statement.setString(4, value(route));
            statement.setString(5, value(frequency));
            statement.setInt(6, active ? 1 : 0);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        Long insertedId = findMedicationId(patientId, name, dose, route, frequency, active);
        return insertedId == null ? -1L : insertedId;
    }

    @Override
    public boolean saveMedicationEvent(long medicationId, String patientId, String givenBy, String givenAt, String notes) throws SQLException {
        String sql = "INSERT INTO medication_events(medication_id, patient_id, given_by, given_at, notes) "
                + "SELECT ?, ?, ?, ?, ? "
                + "WHERE NOT EXISTS ("
                + "SELECT 1 FROM medication_events "
                + "WHERE patient_id = ? AND COALESCE(medication_id, -1) = ? AND given_at = ? AND COALESCE(notes, '') = ?"
                + ")";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, medicationId);
            statement.setString(2, patientId);
            statement.setString(3, value(givenBy));
            statement.setString(4, value(givenAt));
            statement.setString(5, value(notes));
            statement.setString(6, patientId);
            statement.setLong(7, medicationId);
            statement.setString(8, value(givenAt));
            statement.setString(9, value(notes));
            return statement.executeUpdate() > 0;
        }
    }

    public long insertMedication(MedicationRecord medication) throws SQLException {
        String sql = "INSERT INTO medications(patient_id, catalog_medication_id, name, dose, dose_amount, dose_unit, route, frequency, active) "
                + "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bindMedication(statement, medication);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        return -1L;
    }

    public void updateMedication(MedicationRecord medication) throws SQLException {
        String sql = "UPDATE medications SET catalog_medication_id = ?, name = ?, dose = ?, dose_amount = ?, dose_unit = ?, "
                + "route = ?, frequency = ?, active = ? WHERE id = ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            setNullableLong(statement, 1, medication.getCatalogMedicationId());
            statement.setString(2, value(medication.getName()));
            statement.setString(3, value(medication.getDose()));
            setNullableDouble(statement, 4, medication.getDoseAmount());
            statement.setString(5, value(medication.getDoseUnit()));
            statement.setString(6, value(medication.getRoute()));
            statement.setString(7, value(medication.getFrequency()));
            statement.setInt(8, medication.isActive() ? 1 : 0);
            statement.setLong(9, medication.getId());
            statement.executeUpdate();
        }
    }

    public void discontinueMedication(long medicationId) throws SQLException {
        String sql = "UPDATE medications SET active = 0 WHERE id = ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, medicationId);
            statement.executeUpdate();
        }
    }

    public boolean insertMedicationEvent(long medicationId, String patientId, String givenBy, String givenAt, String notes) throws SQLException {
        String sql = "INSERT INTO medication_events(medication_id, patient_id, given_by, given_at, notes) VALUES(?, ?, ?, ?, ?)";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, medicationId);
            statement.setString(2, patientId);
            statement.setString(3, value(givenBy));
            statement.setString(4, value(givenAt));
            statement.setString(5, value(notes));
            return statement.executeUpdate() > 0;
        }
    }

    public Optional<MedicationRecord> findMedicationById(long medicationId) throws SQLException {
        String sql = medicationSelect() + " WHERE id = ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, medicationId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapMedication(resultSet));
                }
            }
        }
        return Optional.empty();
    }

    public List<MedicationRecord> findActiveMedicationsForPatient(String patientId) throws SQLException {
        ArrayList<MedicationRecord> medications = new ArrayList<>();
        String sql = medicationSelect()
                + "WHERE patient_id = ? AND active = 1 ORDER BY name COLLATE NOCASE, dose COLLATE NOCASE";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, patientId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    medications.add(mapMedication(resultSet));
                }
            }
        }
        return medications;
    }

    public boolean hasDuplicateActiveMedication(String patientId, String name, String dose, long excludeMedicationId) throws SQLException {
        String sql = "SELECT 1 FROM medications WHERE patient_id = ? AND UPPER(name) = ? AND UPPER(COALESCE(dose, '')) = ? "
                + "AND active = 1 AND id <> ? LIMIT 1";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, patientId);
            statement.setString(2, value(name).toUpperCase());
            statement.setString(3, value(dose).toUpperCase());
            statement.setLong(4, excludeMedicationId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    @Override
    public int countMedications() throws SQLException {
        return count("medications");
    }

    @Override
    public int countMedicationEvents() throws SQLException {
        return count("medication_events");
    }

    private Long findMedicationId(String patientId, String name, String dose, String route, String frequency, boolean active) throws SQLException {
        String sql = "SELECT id FROM medications "
                + "WHERE patient_id = ? AND name = ? AND COALESCE(dose, '') = ? AND COALESCE(route, '') = ? "
                + "AND COALESCE(frequency, '') = ? AND active = ? LIMIT 1";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, patientId);
            statement.setString(2, name);
            statement.setString(3, value(dose));
            statement.setString(4, value(route));
            statement.setString(5, value(frequency));
            statement.setInt(6, active ? 1 : 0);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getLong("id") : null;
            }
        }
    }

    private void bindMedication(PreparedStatement statement, MedicationRecord medication) throws SQLException {
        statement.setString(1, medication.getPatientId());
        setNullableLong(statement, 2, medication.getCatalogMedicationId());
        statement.setString(3, value(medication.getName()));
        statement.setString(4, value(medication.getDose()));
        setNullableDouble(statement, 5, medication.getDoseAmount());
        statement.setString(6, value(medication.getDoseUnit()));
        statement.setString(7, value(medication.getRoute()));
        statement.setString(8, value(medication.getFrequency()));
        statement.setInt(9, medication.isActive() ? 1 : 0);
    }

    private MedicationRecord mapMedication(ResultSet resultSet) throws SQLException {
        Double doseAmount = nullableDouble(resultSet, "dose_amount");
        String doseUnit = resultSet.getString("dose_unit");
        String dose = resultSet.getString("dose");
        if ((dose == null || dose.isBlank()) && doseAmount != null) {
            dose = formatDose(doseAmount, doseUnit);
        }
        return new MedicationRecord(
                resultSet.getLong("id"),
                resultSet.getString("patient_id"),
                nullableLong(resultSet, "catalog_medication_id"),
                resultSet.getString("name"),
                dose,
                doseAmount,
                doseUnit,
                resultSet.getString("route"),
                resultSet.getString("frequency"),
                resultSet.getInt("active") == 1
        );
    }

    private String medicationSelect() {
        return "SELECT id, patient_id, catalog_medication_id, name, dose, dose_amount, dose_unit, route, frequency, active FROM medications ";
    }

    private int count(String tableName) throws SQLException {
        try (Connection connection = DatabaseManager.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
            return resultSet.next() ? resultSet.getInt(1) : 0;
        }
    }

    private void ensureSchema() {
        try {
            SchemaInitializer.initialize();
        } catch (Exception e) {
            System.out.println("SQLite medication schema check failed: " + e.getMessage());
        }
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private void setNullableLong(PreparedStatement statement, int index, Long value) throws SQLException {
        if (value == null || value <= 0) {
            statement.setNull(index, java.sql.Types.INTEGER);
        } else {
            statement.setLong(index, value);
        }
    }

    private void setNullableDouble(PreparedStatement statement, int index, Double value) throws SQLException {
        if (value == null) {
            statement.setNull(index, java.sql.Types.REAL);
        } else {
            statement.setDouble(index, value);
        }
    }

    private Long nullableLong(ResultSet resultSet, String column) throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private Double nullableDouble(ResultSet resultSet, String column) throws SQLException {
        double value = resultSet.getDouble(column);
        return resultSet.wasNull() ? null : value;
    }

    private String formatDose(Double amount, String unit) {
        if (amount == null) {
            return "";
        }
        String amountText = amount == Math.rint(amount) ? String.valueOf(amount.longValue()) : String.valueOf(amount);
        return unit == null || unit.isBlank() ? amountText : amountText + " " + unit;
    }

    public static class MedicationRecord {
        private final long id;
        private final String patientId;
        private final Long catalogMedicationId;
        private final String name;
        private final String dose;
        private final Double doseAmount;
        private final String doseUnit;
        private final String route;
        private final String frequency;
        private final boolean active;

        public MedicationRecord(long id, String patientId, String name, String dose, String route, String frequency, boolean active) {
            this(id, patientId, null, name, dose, parseDoseAmount(dose), parseDoseUnit(dose), route, frequency, active);
        }

        public MedicationRecord(long id, String patientId, Long catalogMedicationId, String name, String dose,
                                Double doseAmount, String doseUnit, String route, String frequency, boolean active) {
            this.id = id;
            this.patientId = patientId;
            this.catalogMedicationId = catalogMedicationId;
            this.name = name;
            this.dose = dose;
            this.doseAmount = doseAmount;
            this.doseUnit = doseUnit;
            this.route = route;
            this.frequency = frequency;
            this.active = active;
        }

        public long getId() { return id; }
        public String getPatientId() { return patientId; }
        public Long getCatalogMedicationId() { return catalogMedicationId; }
        public String getName() { return name; }
        public String getDose() { return dose; }
        public Double getDoseAmount() { return doseAmount; }
        public String getDoseUnit() { return doseUnit; }
        public String getRoute() { return route; }
        public String getFrequency() { return frequency; }
        public boolean isActive() { return active; }
        public int setDose(String dose) { return this.dose.compareTo(dose); }

        @Override
        public String toString() {
            return name + " " + dose + " (" + route + ", " + frequency + ")";
        }

        private static Double parseDoseAmount(String dose) {
            if (dose == null || dose.isBlank()) {
                return null;
            }
            String[] parts = dose.trim().split("\\s+");
            try {
                return Double.parseDouble(parts[0]);
            } catch (Exception e) {
                return null;
            }
        }

        private static String parseDoseUnit(String dose) {
            if (dose == null || dose.isBlank()) {
                return "";
            }
            String trimmed = dose.trim();
            int firstSpace = trimmed.indexOf(' ');
            return firstSpace < 0 ? "" : trimmed.substring(firstSpace + 1).trim();
        }
    }
}
