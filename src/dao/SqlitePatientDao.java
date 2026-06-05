package dao;

import database.DatabaseManager;
import models.Patient;
import models.VitalSign;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
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
    public List<Patient> findAll() throws SQLException {
        ArrayList<Patient> patients = new ArrayList<>();
        String sql = "SELECT * FROM patients ORDER BY last_name, first_name, patient_id";
        try (Connection connection = DatabaseManager.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                patients.add(mapPatient(resultSet));
            }
        }
        return patients;
    }

    @Override
    public List<Patient> findBySection(String section) throws SQLException {
        ArrayList<Patient> patients = new ArrayList<>();
        String sql = "SELECT * FROM patients WHERE section = ? ORDER BY room, last_name";
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

    @Override
    public List<Patient> findActivePatients() throws SQLException {
        ArrayList<Patient> patients = new ArrayList<>();
        String sql = "SELECT * FROM patients WHERE UPPER(status) NOT IN ('DECEASED', 'DISCHARGED', 'INACTIVE') ORDER BY section, room, last_name";
        try (Connection connection = DatabaseManager.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                patients.add(mapPatient(resultSet));
            }
        }
        return patients;
    }

    public List<PatientListRow> findPatientListRows(String search) throws SQLException {
        PatientFilter filter = new PatientFilter();
        filter.setSearch(search);
        return findPatientListRows(filter);
    }

    public List<PatientListRow> findPatientListRows(PatientFilter filter) throws SQLException {
        ArrayList<PatientListRow> rows = new ArrayList<>();
        PatientFilter safeFilter = filter == null ? new PatientFilter() : filter;
        ArrayList<String> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT patient_id, first_name, last_name, birth_date, gender, section, room, status, priority ")
                .append("FROM patients ")
                .append("WHERE 1 = 1 ");

        if (hasText(safeFilter.search)) {
            sql.append("AND (patient_id LIKE ? OR first_name LIKE ? OR last_name LIKE ? OR (first_name || ' ' || last_name) LIKE ?) ");
            String like = "%" + safeFilter.search.trim() + "%";
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
        if (hasText(safeFilter.status) && !"All".equalsIgnoreCase(safeFilter.status)) {
            if ("ACTIVE".equalsIgnoreCase(safeFilter.status)) {
                sql.append("AND UPPER(status) NOT IN ('DECEASED', 'DISCHARGED', 'INACTIVE') ");
            } else {
                sql.append("AND UPPER(status) = ? ");
                params.add(safeFilter.status.toUpperCase());
            }
        }
        if (safeFilter.criticalEmergencyOnly) {
            sql.append("AND UPPER(priority) IN ('CRITICAL', 'EMERGENCY') ");
        } else if (hasText(safeFilter.priority) && !"All".equalsIgnoreCase(safeFilter.priority)) {
            if ("HIGH".equalsIgnoreCase(safeFilter.priority)) {
                sql.append("AND UPPER(priority) IN ('HIGH', 'WARNING') ");
            } else {
                sql.append("AND UPPER(priority) = ? ");
                params.add(safeFilter.priority.toUpperCase());
            }
        }
        if (safeFilter.recentlyUpdatedOnly) {
            sql.append("AND datetime(updated_at) >= datetime('now', '-7 days') ");
        }

        sql.append("ORDER BY CASE UPPER(priority) ")
                .append("WHEN 'EMERGENCY' THEN 1 ")
                .append("WHEN 'CRITICAL' THEN 2 ")
                .append("WHEN 'HIGH' THEN 3 ")
                .append("WHEN 'WARNING' THEN 3 ")
                .append("WHEN 'NORMAL' THEN 4 ")
                .append("WHEN 'DECEASED' THEN 5 ")
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
                            resultSet.getString("priority")
                    ));
                }
            }
        }
        return rows;
    }

    public List<String> findDistinctSections() throws SQLException {
        return findDistinctValues("section");
    }

    public List<String> findDistinctRooms() throws SQLException {
        return findDistinctValues("room");
    }

    public Optional<PatientDetail> findDetailById(String patientId) throws SQLException {
        String sql = "SELECT patient_id, first_name, last_name, birth_date, gender, section, room, status, priority, diagnosis, "
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
                            resultSet.getString("diagnosis"),
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
        String sql = "INSERT INTO patients(patient_id, first_name, last_name, birth_date, gender, section, room, status, priority, diagnosis, updated_at) "
                + "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP) "
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
            statement.executeUpdate();
        }
    }

    public void insertPatient(PatientWriteRecord patient) throws SQLException {
        String sql = "INSERT INTO patients(patient_id, first_name, last_name, birth_date, gender, section, room, status, priority, diagnosis, "
                + "assigned_doctor_username, assigned_staff_username, created_at, updated_at) "
                + "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindWriteRecord(statement, patient);
            statement.executeUpdate();
        }
    }

    public void updatePatient(PatientWriteRecord patient) throws SQLException {
        String sql = "UPDATE patients SET first_name = ?, last_name = ?, birth_date = ?, gender = ?, section = ?, room = ?, "
                + "status = ?, priority = ?, diagnosis = ?, assigned_doctor_username = ?, assigned_staff_username = ?, "
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
            statement.setString(9, patient.getDiagnosis());
            statement.setString(10, patient.getAssignedDoctorUsername());
            statement.setString(11, patient.getAssignedStaffUsername());
            statement.setString(12, patient.getPatientId());
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

    public void updatePatientRoom(String patientId, String section, String room) throws SQLException {
        String sql = "UPDATE patients SET section = ?, room = ?, updated_at = CURRENT_TIMESTAMP WHERE patient_id = ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value(section));
            statement.setString(2, value(room));
            statement.setString(3, patientId);
            statement.executeUpdate();
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

    public void updatePatientsRoom(String oldSection, String oldRoom, String newSection, String newRoom) throws SQLException {
        String sql = "UPDATE patients SET section = ?, room = ?, updated_at = CURRENT_TIMESTAMP "
                + "WHERE UPPER(COALESCE(section, '')) = ? AND UPPER(COALESCE(room, '')) = ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value(newSection));
            statement.setString(2, value(newRoom));
            statement.setString(3, value(oldSection).toUpperCase());
            statement.setString(4, value(oldRoom).toUpperCase());
            statement.executeUpdate();
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

    @Override
    public void deleteById(String patientId) throws SQLException {
        String sql = "DELETE FROM patients WHERE patient_id = ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, patientId);
            statement.executeUpdate();
        }
    }

    public int count() throws SQLException {
        String sql = "SELECT COUNT(*) FROM patients";
        try (Connection connection = DatabaseManager.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            return resultSet.next() ? resultSet.getInt(1) : 0;
        }
    }

    private List<String> findDistinctValues(String columnName) throws SQLException {
        ArrayList<String> values = new ArrayList<>();
        String sql = "SELECT DISTINCT " + columnName + " FROM patients WHERE " + columnName + " IS NOT NULL AND TRIM(" + columnName + ") <> '' ORDER BY " + columnName;
        try (Connection connection = DatabaseManager.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                values.add(resultSet.getString(1));
            }
        }
        return values;
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
        statement.setString(10, patient.getDiagnosis());
        statement.setString(11, patient.getAssignedDoctorUsername());
        statement.setString(12, patient.getAssignedStaffUsername());
    }

    private LocalDateTime parseSqliteDateTime(String value) {
        if (value == null || value.isBlank()) {
            return LocalDateTime.MIN;
        }
        String normalized = value.trim().replace('T', ' ');
        try {
            return LocalDateTime.parse(normalized, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (DateTimeParseException e) {
            return LocalDateTime.parse(value.trim());
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
        return "DECEASED".equals(normalized)
                || "DISCHARGED".equals(normalized)
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
        return patient;
    }

    private String priorityFor(Patient patient) {
        if (patient.isDeceased()) {
            return "DECEASED";
        }
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

        public PatientListRow(String patientId, String name, String birthDate, String gender,
                              String section, String room, String status, String priority) {
            this.patientId = patientId;
            this.name = name;
            this.birthDate = birthDate;
            this.gender = gender;
            this.section = section;
            this.room = room;
            this.status = status;
            this.priority = priority;
        }

        public String getPatientId() { return patientId; }
        public String getName() { return name; }
        public String getBirthDate() { return birthDate; }
        public String getGender() { return gender; }
        public String getSection() { return section; }
        public String getRoom() { return room; }
        public String getStatus() { return status; }
        public String getPriority() { return priority; }
    }

    public static class PatientFilter {
        private String search = "";
        private String section = "All";
        private String room = "All";
        private String status = "All";
        private String priority = "All";
        private boolean criticalEmergencyOnly;
        private boolean recentlyUpdatedOnly;

        public String getSearch() { return search; }
        public void setSearch(String search) { this.search = search == null ? "" : search; }
        public String getSection() { return section; }
        public void setSection(String section) { this.section = section == null ? "All" : section; }
        public String getRoom() { return room; }
        public void setRoom(String room) { this.room = room == null ? "All" : room; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status == null ? "All" : status; }
        public String getPriority() { return priority; }
        public void setPriority(String priority) { this.priority = priority == null ? "All" : priority; }
        public boolean isCriticalEmergencyOnly() { return criticalEmergencyOnly; }
        public void setCriticalEmergencyOnly(boolean criticalEmergencyOnly) { this.criticalEmergencyOnly = criticalEmergencyOnly; }
        public boolean isRecentlyUpdatedOnly() { return recentlyUpdatedOnly; }
        public void setRecentlyUpdatedOnly(boolean recentlyUpdatedOnly) { this.recentlyUpdatedOnly = recentlyUpdatedOnly; }
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
        private final String diagnosis;
        private final String assignedDoctorUsername;
        private final String assignedStaffUsername;

        public PatientWriteRecord(String patientId, String firstName, String lastName, String birthDate,
                                  String gender, String section, String room, String status,
                                  String priority, String diagnosis) {
            this(patientId, firstName, lastName, birthDate, gender, section, room, status, priority, diagnosis, "", "");
        }

        public PatientWriteRecord(String patientId, String firstName, String lastName, String birthDate,
                                  String gender, String section, String room, String status,
                                  String priority, String diagnosis, String assignedDoctorUsername,
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
            this.diagnosis = diagnosis;
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
        public String getDiagnosis() { return diagnosis; }
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
        private final String diagnosis;
        private final String assignedDoctorUsername;
        private final String assignedStaffUsername;

        public PatientDetail(String patientId, String firstName, String lastName, String birthDate,
                             String gender, String section, String room, String status,
                             String priority, String diagnosis) {
            this(patientId, firstName, lastName, birthDate, gender, section, room, status, priority, diagnosis, "", "");
        }

        public PatientDetail(String patientId, String firstName, String lastName, String birthDate,
                             String gender, String section, String room, String status,
                             String priority, String diagnosis, String assignedDoctorUsername,
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
            this.diagnosis = diagnosis;
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
        public String getDiagnosis() { return diagnosis == null || diagnosis.isBlank() ? "No diagnosis recorded" : diagnosis; }
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
}
