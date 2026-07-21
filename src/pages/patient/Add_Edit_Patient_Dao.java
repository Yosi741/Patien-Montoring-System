package pages.patient;

import app.database.DatabaseManager;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Provides SQLite CRUD, filtering, detail, and related-record queries for the patients table.
 */
public class Add_Edit_Patient_Dao {



    /**
     * Finds patient list rows in SQLite.
     */
    public List<PatientListRow> findPatientListRows(PatientFilter filter) throws SQLException {
        ArrayList<PatientListRow> rows = new ArrayList<>();
        PatientFilter safeFilter = filter == null ? new PatientFilter() : filter;
        ArrayList<String> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT patient_id, first_name, last_name, birth_date, gender, status, priority, ")
                .append("COALESCE(blood_type, 'Unknown') AS blood_type, ")
                .append("COALESCE(phone, '') AS phone, ")
                .append("COALESCE(email, '') AS email ")
                .append("FROM patients ")
                .append("WHERE 1 = 1 ");

        if (hasText(safeFilter.search)) {
            sql.append("AND (patient_id LIKE ? OR first_name LIKE ? OR last_name LIKE ? OR (first_name || ' ' || last_name) LIKE ? OR phone LIKE ? OR email LIKE ?) ");
            String like = "%" + safeFilter.search.trim() + "%";
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
        }
        if (hasText(safeFilter.displayStatus) && !"All".equalsIgnoreCase(safeFilter.displayStatus)) {
            switch (safeFilter.displayStatus.trim().toUpperCase()) {
                case "ACTIVE":
                    sql.append("AND UPPER(status) NOT IN ('DISCHARGED', 'INACTIVE', 'DEACTIVATED') ")
                            .append("AND UPPER(priority) NOT IN ('CRITICAL', 'EMERGENCY') ");
                    break;
                case "CRITICAL":
                    sql.append("AND UPPER(status) NOT IN ('DISCHARGED', 'INACTIVE', 'DEACTIVATED') ")
                            .append("AND UPPER(priority) IN ('CRITICAL', 'EMERGENCY') ");
                    break;
                case "DISCHARGED":
                    sql.append("AND UPPER(status) = 'DISCHARGED' ");
                    break;
                case "ARCHIVED":
                case "INACTIVE":
                    sql.append("AND UPPER(status) IN ('INACTIVE', 'DEACTIVATED') ");
                    break;
                default:
                    break;
            }
        }

        sql.append("ORDER BY CASE UPPER(priority) ")
                .append("WHEN 'EMERGENCY' THEN 1 ")
                .append("WHEN 'CRITICAL' THEN 2 ")
                .append("WHEN 'HIGH' THEN 3 ")
                .append("WHEN 'WARNING' THEN 3 ")
                .append("WHEN 'NORMAL' THEN 4 ")
                .append("ELSE 6 END, datetime(updated_at) DESC, last_name, first_name, patient_id");
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                statement.setString(i + 1, params.get(i));
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(new PatientListRow(
                            resultSet.getString("patient_id"),
                            resultSet.getString("first_name") + " " + resultSet.getString("last_name"),
                            resultSet.getString("birth_date"),
                            resultSet.getString("gender"),
                            resultSet.getString("status"),
                            resultSet.getString("priority"),
                            resultSet.getString("blood_type"),
                            resultSet.getString("phone"),
                            resultSet.getString("email")
                    ));
                }
            }
        }
        return rows;
    }


    /**
     * Finds detail by ID in SQLite.
     */
    public Optional<PatientDetail> findDetailById(String patientId) throws SQLException {
        String sql = "SELECT patient_id, first_name, last_name, birth_date, gender, status, priority, "
                + "COALESCE(blood_type, 'Unknown') AS blood_type, diagnosis, COALESCE(allergies, 'Unknown') AS allergies, "
                + "COALESCE(phone, '') AS phone, COALESCE(email, '') AS email, COALESCE(address, '') AS address, "
                + "COALESCE(emergency_contact_name, '') AS emergency_contact_name, COALESCE(emergency_contact_phone, '') AS emergency_contact_phone "
                + "FROM patients WHERE patient_id = ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, patientId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(new PatientDetail(
                            resultSet.getString("patient_id"),
                            resultSet.getString("first_name"),
                            resultSet.getString("last_name"),
                            resultSet.getString("birth_date"),
                            resultSet.getString("gender"),
                            resultSet.getString("status"),
                            resultSet.getString("priority"),
                            resultSet.getString("blood_type"),
                            resultSet.getString("diagnosis"),
                            resultSet.getString("allergies"),
                            resultSet.getString("phone"),
                            resultSet.getString("email"),
                            resultSet.getString("address"),
                            resultSet.getString("emergency_contact_name"),
                            resultSet.getString("emergency_contact_phone")
                    ));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Inserts patient into SQLite.
     */
    public void insertPatient(PatientWriteRecord patient) throws SQLException {
        String sql = "INSERT INTO patients(patient_id, first_name, last_name, birth_date, gender, status, priority, blood_type, diagnosis, allergies, phone, email, address, emergency_contact_name, emergency_contact_phone, created_at, updated_at) "
                + "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindWriteRecord(statement, patient);
            statement.executeUpdate();
        }
    }

    /**
     * Updates patient.
     */
    public void updatePatient(PatientWriteRecord patient) throws SQLException {
        String sql = "UPDATE patients SET first_name = ?, last_name = ?, birth_date = ?, gender = ?, "
                + "status = ?, priority = ?, blood_type = ?, diagnosis = ?, allergies = ?, phone = ?, email = ?, address = ?, emergency_contact_name = ?, emergency_contact_phone = ?, "
                + "updated_at = CURRENT_TIMESTAMP WHERE patient_id = ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, patient.getFirstName());
            statement.setString(2, patient.getLastName());
            statement.setString(3, patient.getBirthDate());
            statement.setString(4, patient.getGender());
            statement.setString(5, patient.getStatus());
            statement.setString(6, patient.getPriority());
            statement.setString(7, patient.getBloodType());
            statement.setString(8, patient.getDiagnosis());
            statement.setString(9, patient.getAllergies());
            statement.setString(10, patient.getPhone());
            statement.setString(11, patient.getEmail());
            statement.setString(12, patient.getAddress());
            statement.setString(13, patient.getEmergencyContactName());
            statement.setString(14, patient.getEmergencyContactPhone());
            statement.setString(15, patient.getPatientId());
            statement.executeUpdate();
        }
    }

    /**
     * Marks patient inactive without deleting its stored history.
     */
    public void deactivatePatient(String patientId, String status) throws SQLException {
        String sql = "UPDATE patients SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE patient_id = ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status == null || status.isBlank() ? "DISCHARGED" : status);
            statement.setString(2, patientId);
            statement.executeUpdate();
        }
    }

    /**
     * Counts related records in SQLite.
     */
    public RelatedRecordCounts countRelatedRecords(String patientId) throws SQLException {
        String normalizedPatientId = value(patientId);
        try (Connection connection = DatabaseManager.getConnection()) {
            return new RelatedRecordCounts(
                    countByPatientId(connection, "patient_visits", normalizedPatientId),
                    countByPatientId(connection, "vital_readings", normalizedPatientId),
                    countByPatientId(connection, "appointments", normalizedPatientId),
                    countByPatientId(connection, "medical_files", normalizedPatientId),
                    countByPatientId(connection, "billing_records", normalizedPatientId),
                    countByPatientId(connection, "alerts", normalizedPatientId),
                    countByPatientId(connection, "notifications", normalizedPatientId),
                    countByPatientId(connection, "messages", normalizedPatientId)
            );
        }
    }

    /**
     * Deletes patient and related records after the required checks.
     */
    public boolean deletePatientAndRelatedRecords(String patientId) throws SQLException {
        String normalizedPatientId = value(patientId);
        try (Connection connection = DatabaseManager.getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                deleteByPatientId(connection, "messages", normalizedPatientId);
                deleteByPatientId(connection, "notifications", normalizedPatientId);
                deleteByPatientId(connection, "alerts", normalizedPatientId);
                deleteByPatientId(connection, "billing_records", normalizedPatientId);
                deleteByPatientId(connection, "appointments", normalizedPatientId);
                deleteByPatientId(connection, "medical_files", normalizedPatientId);
                deleteByPatientId(connection, "vital_readings", normalizedPatientId);
                deleteByPatientId(connection, "patient_visits", normalizedPatientId);

                boolean deleted;
                try (PreparedStatement deletePatient = connection.prepareStatement(
                        "DELETE FROM patients WHERE patient_id = ?")) {
                    deletePatient.setString(1, normalizedPatientId);
                    deleted = deletePatient.executeUpdate() > 0;
                }
                connection.commit();
                return deleted;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }
        }
    }



    /**
     * Updates priority if higher.
     */
    public boolean updatePriorityIfHigher(String patientId, String requestedPriority) throws SQLException {
        Optional<PatientDetail> detail = findDetailById(patientId);
        if (detail.isEmpty()) {
            return false;
        }

        PatientDetail patient = detail.get();
        if (isTerminalStatus(patient.getStatus())) {
            return false;
        }

        String normalizedPriority = normalizePriority(requestedPriority);
        if (priorityRank(normalizedPriority) <= priorityRank(patient.getPriority())) {
            return false;
        }

        String sql = "UPDATE patients SET priority = ?, updated_at = CURRENT_TIMESTAMP WHERE patient_id = ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalizedPriority);
            statement.setString(2, patientId);
            return statement.executeUpdate() > 0;
        }
    }

    /**
     * Updates priority if lower.
     */
    public boolean updatePriorityIfLower(String patientId, String requestedPriority) throws SQLException {
        Optional<PatientDetail> detail = findDetailById(patientId);
        if (detail.isEmpty()) {
            return false;
        }

        PatientDetail patient = detail.get();
        if (isTerminalStatus(patient.getStatus())) {
            return false;
        }

        String normalizedPriority = normalizePriority(requestedPriority);
        if (priorityRank(normalizedPriority) >= priorityRank(patient.getPriority())) {
            return false;
        }

        String sql = "UPDATE patients SET priority = ?, updated_at = CURRENT_TIMESTAMP WHERE patient_id = ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalizedPriority);
            statement.setString(2, patientId);
            return statement.executeUpdate() > 0;
        }
    }



    /**
     * Checks whether by patient ID already exists in SQLite.
     */
    public boolean existsByPatientId(String patientId) throws SQLException {
        String sql = "SELECT 1 FROM patients WHERE patient_id = ? LIMIT 1";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, patientId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    /**
     * Binds write record to a prepared SQLite statement.
     */
    private void bindWriteRecord(PreparedStatement statement, PatientWriteRecord patient) throws SQLException {
        statement.setString(1, patient.getPatientId());
        statement.setString(2, patient.getFirstName());
        statement.setString(3, patient.getLastName());
        statement.setString(4, patient.getBirthDate());
        statement.setString(5, patient.getGender());
        statement.setString(6, patient.getStatus());
        statement.setString(7, patient.getPriority());
        statement.setString(8, patient.getBloodType());
        statement.setString(9, patient.getDiagnosis());
        statement.setString(10, patient.getAllergies());
        statement.setString(11, patient.getPhone());
        statement.setString(12, patient.getEmail());
        statement.setString(13, patient.getAddress());
        statement.setString(14, patient.getEmergencyContactName());
        statement.setString(15, patient.getEmergencyContactPhone());
    }

    /**
     * Counts by patient ID in SQLite.
     */
    private int countByPatientId(Connection connection, String tableName, String patientId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM " + tableName + " WHERE patient_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, patientId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt(1) : 0;
            }
        }
    }

    /**
     * Deletes by patient ID after the required checks.
     */
    private void deleteByPatientId(Connection connection, String tableName, String patientId) throws SQLException {
        String sql = "DELETE FROM " + tableName + " WHERE patient_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, patientId);
            statement.executeUpdate();
        }
    }

    /**
     * Returns formatted display text for has text.
     */
    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * Reads value safely from the current SQLite row.
     */
    private String value(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * Determines whether is terminal status for the current record or user.
     */
    private boolean isTerminalStatus(String status) {
        if (status == null) {
            return false;
        }
        String normalized = status.trim().toUpperCase();
        return "DISCHARGED".equals(normalized)
                || "INACTIVE".equals(normalized)
                || "DEACTIVATED".equals(normalized);
    }

    /**
     * Normalizes priority to the stored application format.
     */
    private String normalizePriority(String priority) {
        if (priority == null || priority.isBlank()) {
            return "NORMAL";
        }
        String normalized = priority.trim().toUpperCase();
        if ("WARNING".equals(normalized)) {
            return "HIGH";
        }
        if ("EMERGENCY".equals(normalized)
                || "CRITICAL".equals(normalized)
                || "HIGH".equals(normalized)
                || "NORMAL".equals(normalized)) {
            return normalized;
        }
        return "NORMAL";
    }

    /**
     * Resolves priority rank for the current clinical state.
     */
    private int priorityRank(String priority) {
        String normalized = normalizePriority(priority);
        switch (normalized) {
            case "EMERGENCY":
                return 4;
            case "CRITICAL":
                return 3;
            case "HIGH":
                return 2;
            default:
                return 1;
        }
    }

    public static class PatientListRow {
        private final String patientId;
        private final String name;
        private final String birthDate;
        private final String gender;
        private final String status;
        private final String priority;
        private final String bloodType;
        private final String phone;
        private final String email;

        /**
         * Creates a patient list row from the supplied record values.
         */
        public PatientListRow(String patientId, String name, String birthDate, String gender,
                              String status, String priority, String bloodType, String phone, String email) {
            this.patientId = patientId;
            this.name = name;
            this.birthDate = birthDate;
            this.gender = gender;
            this.status = status;
            this.priority = priority;
            this.bloodType = bloodType == null || bloodType.isBlank() ? "Unknown" : bloodType.trim();
            this.phone = phone == null ? "" : phone.trim();
            this.email = email == null ? "" : email.trim();
        }

        public String getPatientId() { return patientId; }
        public String getName() { return name; }
        public String getBirthDate() { return birthDate; }
        public String getGender() { return gender; }
        public String getStatus() { return status; }
        public String getPriority() { return priority; }
        public String getBloodType() { return bloodType; }
        public String getPhone() { return phone; }
        public String getEmail() { return email; }
    }

    public static class PatientFilter {
        private String search = "";
        private String displayStatus = "All";

        public String getSearch() { return search; }
        public void setSearch(String search) { this.search = search == null ? "" : search; }
        public void setDisplayStatus(String displayStatus) { this.displayStatus = displayStatus == null ? "All" : displayStatus; }

    }

    public static class PatientWriteRecord {
        private final String patientId;
        private final String firstName;
        private final String lastName;
        private final String birthDate;
        private final String gender;
        private final String status;
        private final String priority;
        private final String bloodType;
        private final String diagnosis;
        private final String allergies;
        private final String phone;
        private final String email;
        private final String address;
        private final String emergencyContactName;
        private final String emergencyContactPhone;





        /**
         * Creates a patient write record from the supplied record values.
         */
        public PatientWriteRecord(String patientId, String firstName, String lastName, String birthDate,
                                  String gender, String status,
                                  String priority, String bloodType, String diagnosis, String allergies, String phone, String email, String address,
                                  String emergencyContactName, String emergencyContactPhone) {
            this.patientId = patientId;
            this.firstName = firstName;
            this.lastName = lastName;
            this.birthDate = birthDate;
            this.gender = gender;
            this.status = status;
            this.priority = priority;
            this.bloodType = bloodType == null || bloodType.isBlank() ? "Unknown" : bloodType.trim();
            this.diagnosis = diagnosis;
            this.allergies = allergies == null || allergies.isBlank() ? "Unknown" : allergies.trim();
            this.phone = phone == null ? "" : phone.trim();
            this.email = email == null ? "" : email.trim();
            this.address = address == null ? "" : address.trim();
            this.emergencyContactName = emergencyContactName == null ? "" : emergencyContactName.trim();
            this.emergencyContactPhone = emergencyContactPhone == null ? "" : emergencyContactPhone.trim();
        }

        public String getPatientId() { return patientId; }
        public String getFirstName() { return firstName; }
        public String getLastName() { return lastName; }
        public String getBirthDate() { return birthDate; }
        public String getGender() { return gender; }
        public String getStatus() { return status; }
        public String getPriority() { return priority; }
        public String getBloodType() { return bloodType; }
        public String getDiagnosis() { return diagnosis; }
        public String getAllergies() { return allergies; }
        public String getPhone() { return phone; }
        public String getEmail() { return email; }
        public String getAddress() { return address; }
        public String getEmergencyContactName() { return emergencyContactName; }
        public String getEmergencyContactPhone() { return emergencyContactPhone; }
    }

    public static class PatientDetail {
        private final String patientId;
        private final String firstName;
        private final String lastName;
        private final String birthDate;
        private final String gender;
        private final String status;
        private final String priority;
        private final String bloodType;
        private final String diagnosis;
        private final String allergies;
        private final String phone;
        private final String email;
        private final String address;
        private final String emergencyContactName;
        private final String emergencyContactPhone;




        /**
         * Creates a patient detail from the supplied record values.
         */
        public PatientDetail(String patientId, String firstName, String lastName, String birthDate,
                             String gender , String status,
                             String priority, String bloodType, String diagnosis, String allergies, String phone, String email, String address,
                             String emergencyContactName, String emergencyContactPhone) {
            this.patientId = patientId;
            this.firstName = firstName;
            this.lastName = lastName;
            this.birthDate = birthDate;
            this.gender = gender;
            this.status = status;
            this.priority = priority;
            this.bloodType = bloodType == null || bloodType.isBlank() ? "Unknown" : bloodType;
            this.diagnosis = diagnosis;
            this.allergies = allergies == null || allergies.isBlank() ? "Unknown" : allergies;
            this.phone = phone == null ? "" : phone;
            this.email = email == null ? "" : email;
            this.address = address == null ? "" : address;
            this.emergencyContactName = emergencyContactName == null ? "" : emergencyContactName;
            this.emergencyContactPhone = emergencyContactPhone == null ? "" : emergencyContactPhone;
        }

        public String getPatientId() { return patientId; }
        public String getFirstName() { return firstName; }
        public String getLastName() { return lastName; }
        public String getName() { return firstName + " " + lastName; }
        public String getBirthDate() { return birthDate; }
        public String getGender() { return gender; }
        public String getStatus() { return status; }
        public String getPriority() { return priority; }
        public String getBloodType() { return bloodType; }
        public String getDiagnosis() { return diagnosis == null || diagnosis.isBlank() ? "No diagnosis recorded" : diagnosis; }
        public String getAllergies() { return allergies == null || allergies.isBlank() ? "Unknown" : allergies; }
        public String getPhone() { return phone == null ? "" : phone; }
        public String getEmail() { return email == null ? "" : email; }
        public String getAddress() { return address == null ? "" : address; }
        public String getEmergencyContactName() { return emergencyContactName == null ? "" : emergencyContactName; }
        public String getEmergencyContactPhone() { return emergencyContactPhone == null ? "" : emergencyContactPhone; }

        /**
         * Returns formatted display text for get age text.
         */
        public String getAgeText() {
            try {
                java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy");
                java.time.LocalDate birth = java.time.LocalDate.parse(birthDate, formatter);
                int years = java.time.Period.between(birth, java.time.LocalDate.now()).getYears();
                return years + " years";
            } catch (Exception e) {
                return "Unknown";
            }
        }
    }

    public record RelatedRecordCounts(
            int patientVisits,
            int vitalReadings,
            int appointments,
            int medicalFiles,
            int billingRecords,
            int alerts,
            int notifications,
            int messages
    ) {
        /**
         * Determines whether has any for the current record or user.
         */
        public boolean hasAny() {
            return totalCount() > 0;
        }

        /**
         * Returns the total number of related records that prevent patient deletion.
         */
        public int totalCount() {
            return patientVisits + vitalReadings + appointments + medicalFiles
                    + billingRecords + alerts + notifications + messages;
        }
    }
}
