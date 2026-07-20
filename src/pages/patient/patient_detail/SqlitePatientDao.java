package pages.patient.patient_detail;

import app.database.DatabaseManager;
import pages.patient.patient_board.PatientDao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SqlitePatientDao implements PatientDao {

    @Override
    public Optional<Patient> findById(String patientId) throws SQLException {
        String sql = "SELECT * FROM patients WHERE patient_id = ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, patientId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapPatient(resultSet));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public List<Patient> findBySection(String section) throws SQLException {
        ArrayList<Patient> patients = new ArrayList<>();
        String sql = "SELECT * FROM patients WHERE section = ? ORDER BY last_name, first_name, patient_id";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, section);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    patients.add(mapPatient(resultSet));
                }
            }
        }
        return patients;
    }

    public List<PatientListRow> findPatientListRows(PatientFilter filter) throws SQLException {
        ArrayList<PatientListRow> rows = new ArrayList<>();
        PatientFilter safeFilter = filter == null ? new PatientFilter() : filter;
        ArrayList<String> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT patient_id, first_name, last_name, birth_date, gender, section, room, status, priority, ")
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
        if (hasText(safeFilter.section) && !"All".equalsIgnoreCase(safeFilter.section)) {
            sql.append("AND section = ? ");
            params.add(safeFilter.section);
        }
        if (hasText(safeFilter.room) && !"All".equalsIgnoreCase(safeFilter.room)) {
            sql.append("AND room = ? ");
            params.add(safeFilter.room);
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
        } else if (hasText(safeFilter.status) && !"All".equalsIgnoreCase(safeFilter.status)) {
            if ("ACTIVE".equalsIgnoreCase(safeFilter.status)) {
                sql.append("AND UPPER(status) NOT IN ('DISCHARGED', 'INACTIVE') ");
            } else {
                sql.append("AND UPPER(status) = ? ");
                params.add(safeFilter.status.toUpperCase());
            }
        }
        if (hasText(safeFilter.priority) && !"All".equalsIgnoreCase(safeFilter.priority)) {
            if ("HIGH".equalsIgnoreCase(safeFilter.priority)) {
                sql.append("AND UPPER(priority) IN ('HIGH', 'WARNING') ");
            } else {
                sql.append("AND UPPER(priority) = ? ");
                params.add(safeFilter.priority.toUpperCase());
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
                            resultSet.getString("section"),
                            resultSet.getString("room"),
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


    public Optional<PatientDetail> findDetailById(String patientId) throws SQLException {
        String sql = "SELECT patient_id, first_name, last_name, birth_date, gender, section, room, status, priority, "
                + "COALESCE(blood_type, 'Unknown') AS blood_type, diagnosis, COALESCE(allergies, 'Unknown') AS allergies, "
                + "COALESCE(phone, '') AS phone, COALESCE(email, '') AS email, COALESCE(address, '') AS address, "
                + "COALESCE(emergency_contact_name, '') AS emergency_contact_name, COALESCE(emergency_contact_phone, '') AS emergency_contact_phone, "
                + "COALESCE(assigned_doctor_username, '') AS assigned_doctor_username, "
                + "COALESCE(assigned_staff_username, '') AS assigned_staff_username "
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
                            resultSet.getString("section"),
                            resultSet.getString("room"),
                            resultSet.getString("status"),
                            resultSet.getString("priority"),
                            resultSet.getString("blood_type"),
                            resultSet.getString("diagnosis"),
                            resultSet.getString("allergies"),
                            resultSet.getString("phone"),
                            resultSet.getString("email"),
                            resultSet.getString("address"),
                            resultSet.getString("emergency_contact_name"),
                            resultSet.getString("emergency_contact_phone"),
                            resultSet.getString("assigned_doctor_username"),
                            resultSet.getString("assigned_staff_username")
                    ));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public void save(Patient patient) throws SQLException {
        String sql = "INSERT INTO patients(patient_id, first_name, last_name, birth_date, gender, section, room, status, priority, diagnosis, allergies, phone, email, address, emergency_contact_name, emergency_contact_phone, updated_at) "
                + "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP) "
                + "ON CONFLICT(patient_id) DO UPDATE SET "
                + "first_name = excluded.first_name, "
                + "last_name = excluded.last_name, "
                + "birth_date = excluded.birth_date, "
                + "gender = excluded.gender, "
                + "section = excluded.section, "
                + "room = excluded.room, "
                + "status = excluded.status, "
                + "priority = excluded.priority, "
                + "diagnosis = excluded.diagnosis, "
                + "allergies = excluded.allergies, "
                + "phone = excluded.phone, "
                + "email = excluded.email, "
                + "address = excluded.address, "
                + "emergency_contact_name = excluded.emergency_contact_name, "
                + "emergency_contact_phone = excluded.emergency_contact_phone, "
                + "updated_at = CURRENT_TIMESTAMP";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, patient.getPatientId());
            statement.setString(2, patient.getFirstName());
            statement.setString(3, patient.getLastName());
            statement.setString(4, patient.getBirthDate());
            statement.setString(5, patient.getGender());
            statement.setString(6, patient.getSection());
            statement.setString(7, patient.getRoom());
            statement.setString(8, patient.getStatus());
            statement.setString(9, priorityFor(patient));
            statement.setString(10, patient.getDiagnosis());
            statement.setString(11, value(patient.getAllergies()).isBlank() ? "Unknown" : patient.getAllergies().trim());
            statement.setString(12, value(patient.getPhone()));
            statement.setString(13, value(patient.getEmail()));
            statement.setString(14, value(patient.getAddress()));
            statement.setString(15, value(patient.getEmergencyContactName()));
            statement.setString(16, value(patient.getEmergencyContactPhone()));
            statement.executeUpdate();
        }
    }

    public void insertPatient(PatientWriteRecord patient) throws SQLException {
        String sql = "INSERT INTO patients(patient_id, first_name, last_name, birth_date, gender, section, room, status, priority, blood_type, diagnosis, allergies, phone, email, address, emergency_contact_name, emergency_contact_phone, "
                + "assigned_doctor_username, assigned_staff_username, created_at, updated_at) "
                + "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindWriteRecord(statement, patient);
            statement.executeUpdate();
        }
    }

    public void updatePatient(PatientWriteRecord patient) throws SQLException {
        String sql = "UPDATE patients SET first_name = ?, last_name = ?, birth_date = ?, gender = ?, section = ?, room = ?, "
                + "status = ?, priority = ?, blood_type = ?, diagnosis = ?, allergies = ?, phone = ?, email = ?, address = ?, emergency_contact_name = ?, emergency_contact_phone = ?, assigned_doctor_username = ?, assigned_staff_username = ?, "
                + "updated_at = CURRENT_TIMESTAMP WHERE patient_id = ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, patient.getFirstName());
            statement.setString(2, patient.getLastName());
            statement.setString(3, patient.getBirthDate());
            statement.setString(4, patient.getGender());
            statement.setString(5, patient.getSection());
            statement.setString(6, patient.getRoom());
            statement.setString(7, patient.getStatus());
            statement.setString(8, patient.getPriority());
            statement.setString(9, patient.getBloodType());
            statement.setString(10, patient.getDiagnosis());
            statement.setString(11, patient.getAllergies());
            statement.setString(12, patient.getPhone());
            statement.setString(13, patient.getEmail());
            statement.setString(14, patient.getAddress());
            statement.setString(15, patient.getEmergencyContactName());
            statement.setString(16, patient.getEmergencyContactPhone());
            statement.setString(17, patient.getAssignedDoctorUsername());
            statement.setString(18, patient.getAssignedStaffUsername());
            statement.setString(19, patient.getPatientId());
            statement.executeUpdate();
        }
    }

    public void deactivatePatient(String patientId, String status) throws SQLException {
        String sql = "UPDATE patients SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE patient_id = ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status == null || status.isBlank() ? "DISCHARGED" : status);
            statement.setString(2, patientId);
            statement.executeUpdate();
        }
    }

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

    public Optional<String> findUpdatedAt(String patientId) throws SQLException {
        String sql = "SELECT updated_at FROM patients WHERE patient_id = ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, patientId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.ofNullable(resultSet.getString("updated_at"));
                }
            }
        }
        return Optional.empty();
    }


    public int count() throws SQLException {
        String sql = "SELECT COUNT(*) FROM patients";
        try (Connection connection = DatabaseManager.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            return resultSet.next() ? resultSet.getInt(1) : 0;
        }
    }

    private void bindWriteRecord(PreparedStatement statement, PatientWriteRecord patient) throws SQLException {
        statement.setString(1, patient.getPatientId());
        statement.setString(2, patient.getFirstName());
        statement.setString(3, patient.getLastName());
        statement.setString(4, patient.getBirthDate());
        statement.setString(5, patient.getGender());
        statement.setString(6, patient.getSection());
        statement.setString(7, patient.getRoom());
        statement.setString(8, patient.getStatus());
        statement.setString(9, patient.getPriority());
        statement.setString(10, patient.getBloodType());
        statement.setString(11, patient.getDiagnosis());
        statement.setString(12, patient.getAllergies());
        statement.setString(13, patient.getPhone());
        statement.setString(14, patient.getEmail());
        statement.setString(15, patient.getAddress());
        statement.setString(16, patient.getEmergencyContactName());
        statement.setString(17, patient.getEmergencyContactPhone());
        statement.setString(18, patient.getAssignedDoctorUsername());
        statement.setString(19, patient.getAssignedStaffUsername());
    }

    private int countByPatientId(Connection connection, String tableName, String patientId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM " + tableName + " WHERE patient_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, patientId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt(1) : 0;
            }
        }
    }

    private void deleteByPatientId(Connection connection, String tableName, String patientId) throws SQLException {
        String sql = "DELETE FROM " + tableName + " WHERE patient_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, patientId);
            statement.executeUpdate();
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String value(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isTerminalStatus(String status) {
        if (status == null) {
            return false;
        }
        String normalized = status.trim().toUpperCase();
        return "DISCHARGED".equals(normalized)
                || "INACTIVE".equals(normalized)
                || "DEACTIVATED".equals(normalized);
    }

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

    private Patient mapPatient(ResultSet resultSet) throws SQLException {
        Patient patient = new Patient(
                resultSet.getString("patient_id"),
                resultSet.getString("first_name"),
                resultSet.getString("last_name"),
                resultSet.getString("birth_date"),
                resultSet.getString("gender"),
                resultSet.getString("room")
        );
        patient.setSection(resultSet.getString("section"));
        patient.setStatus(resultSet.getString("status"));
        patient.setDiagnosis(resultSet.getString("diagnosis"));
        patient.setAllergies(resultSet.getString("allergies"));
        patient.setPhone(resultSet.getString("phone"));
        patient.setEmail(resultSet.getString("email"));
        patient.setAddress(resultSet.getString("address"));
        patient.setEmergencyContactName(resultSet.getString("emergency_contact_name"));
        patient.setEmergencyContactPhone(resultSet.getString("emergency_contact_phone"));
        return patient;
    }

    private String priorityFor(Patient patient) {
        return "NORMAL";
    }

    public static class PatientListRow {
        private final String patientId;
        private final String name;
        private final String birthDate;
        private final String gender;
        private final String section;
        private final String room;
        private final String status;
        private final String priority;
        private final String bloodType;
        private final String phone;
        private final String email;

        public PatientListRow(String patientId, String name, String birthDate, String gender,
                              String section, String room, String status, String priority, String bloodType, String phone, String email) {
            this.patientId = patientId;
            this.name = name;
            this.birthDate = birthDate;
            this.gender = gender;
            this.section = section;
            this.room = room;
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
        public String getSection() { return section; }
        public String getRoom() { return room; }
        public String getStatus() { return status; }
        public String getPriority() { return priority; }
        public String getBloodType() { return bloodType; }
        public String getPhone() { return phone; }
        public String getEmail() { return email; }
    }

    public static class PatientFilter {
        private String search = "";
        private String section = "All";
        private String room = "All";
        private String status = "All";
        private String displayStatus = "All";
        private String priority = "All";

        public String getSearch() { return search; }
        public void setSearch(String search) { this.search = search == null ? "" : search; }
        public String getSection() { return section; }
        public void setSection(String section) { this.section = section == null ? "All" : section; }
        public String getRoom() { return room; }
        public void setRoom(String room) { this.room = room == null ? "All" : room; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status == null ? "All" : status; }
        public void setDisplayStatus(String displayStatus) { this.displayStatus = displayStatus == null ? "All" : displayStatus; }
        public String getPriority() { return priority; }
        public void setPriority(String priority) { this.priority = priority == null ? "All" : priority; }

    }

    public static class PatientWriteRecord {
        private final String patientId;
        private final String firstName;
        private final String lastName;
        private final String birthDate;
        private final String gender;
        private final String section;
        private final String room;
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
        private final String assignedDoctorUsername;
        private final String assignedStaffUsername;





        public PatientWriteRecord(String patientId, String firstName, String lastName, String birthDate,
                                  String gender, String section, String room, String status,
                                  String priority, String bloodType, String diagnosis, String allergies, String phone, String email, String address,
                                  String emergencyContactName, String emergencyContactPhone, String assignedDoctorUsername,
                                  String assignedStaffUsername) {
            this.patientId = patientId;
            this.firstName = firstName;
            this.lastName = lastName;
            this.birthDate = birthDate;
            this.gender = gender;
            this.section = section;
            this.room = room;
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
            this.assignedDoctorUsername = assignedDoctorUsername == null ? "" : assignedDoctorUsername.trim();
            this.assignedStaffUsername = assignedStaffUsername == null ? "" : assignedStaffUsername.trim();
        }

        public String getPatientId() { return patientId; }
        public String getFirstName() { return firstName; }
        public String getLastName() { return lastName; }
        public String getBirthDate() { return birthDate; }
        public String getGender() { return gender; }
        public String getSection() { return section; }
        public String getRoom() { return room; }
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
        public String getAssignedDoctorUsername() { return assignedDoctorUsername; }
        public String getAssignedStaffUsername() { return assignedStaffUsername; }
    }

    public static class PatientDetail {
        private final String patientId;
        private final String firstName;
        private final String lastName;
        private final String birthDate;
        private final String gender;
        private final String section;
        private final String room;
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
        private final String assignedDoctorUsername;
        private final String assignedStaffUsername;




        public PatientDetail(String patientId, String firstName, String lastName, String birthDate,
                             String gender, String section, String room, String status,
                             String priority, String bloodType, String diagnosis, String allergies, String phone, String email, String address,
                             String emergencyContactName, String emergencyContactPhone, String assignedDoctorUsername,
                             String assignedStaffUsername) {
            this.patientId = patientId;
            this.firstName = firstName;
            this.lastName = lastName;
            this.birthDate = birthDate;
            this.gender = gender;
            this.section = section;
            this.room = room;
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
            this.assignedDoctorUsername = assignedDoctorUsername == null ? "" : assignedDoctorUsername;
            this.assignedStaffUsername = assignedStaffUsername == null ? "" : assignedStaffUsername;
        }

        public String getPatientId() { return patientId; }
        public String getFirstName() { return firstName; }
        public String getLastName() { return lastName; }
        public String getName() { return firstName + " " + lastName; }
        public String getBirthDate() { return birthDate; }
        public String getGender() { return gender; }
        public String getSection() { return section; }
        public String getRoom() { return room; }
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
        public String getAssignedDoctorUsername() { return assignedDoctorUsername; }
        public String getAssignedStaffUsername() { return assignedStaffUsername; }

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
        public boolean hasAny() {
            return totalCount() > 0;
        }

        public int totalCount() {
            return patientVisits + vitalReadings + appointments + medicalFiles
                    + billingRecords + alerts + notifications + messages;
        }
    }
}
