package pages.patient.patient_registration;

import app.database.DatabaseManager;
import pages.patient.patient_details.PatientDetail;
import pages.patient.patient_details.PatientDetailsRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Persists Add/Edit Patient changes and checks patient IDs for registration workflows.
 */
public class PatientRegistrationRepository {
    private final PatientDetailsRepository patientDetailsRepository;

    /**
     * Creates the repository with the default patient details dependency.
     */
    public PatientRegistrationRepository() {
        this(new PatientDetailsRepository());
    }

    /**
     * Creates the repository with a supplied patient details dependency.
     */
    public PatientRegistrationRepository(PatientDetailsRepository patientDetailsRepository) {
        this.patientDetailsRepository = patientDetailsRepository;
    }

    /**
     * Creates a new patient record in SQLite.
     */
    public void createPatient(PatientRegistrationData patient) throws SQLException {
        String sql = "INSERT INTO patients(patient_id, first_name, last_name, birth_date, gender, status, priority, blood_type, diagnosis, allergies, phone, email, address, emergency_contact_name, emergency_contact_phone, created_at, updated_at) "
                + "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindPatientRegistrationData(statement, patient);
            statement.executeUpdate();
        }
    }

    /**
     * Updates an existing patient record in SQLite.
     */
    public void updateExistingPatient(PatientRegistrationData patient) throws SQLException {
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
     * Returns true when a patient ID already exists in SQLite.
     */
    public boolean patientIdExists(String patientId) throws SQLException {
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
     * Finds an existing patient by ID for returning-patient registration.
     */
    public Optional<PatientDetail> findExistingPatientById(String patientId) throws SQLException {
        return patientDetailsRepository.findPatientDetailsById(patientId);
    }

    /**
     * Binds patient registration values into an insert statement.
     */
    private void bindPatientRegistrationData(PreparedStatement statement, PatientRegistrationData patient) throws SQLException {
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
}



