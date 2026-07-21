package pages.patient.patient_registration;

/**
 * Carries Add/Edit Patient form data from the JavaFX dialog into validation and persistence.
 */
public class PatientRegistrationData {
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
     * Creates the data object used to create, update, or reactivate a patient record.
     */
    public PatientRegistrationData(String patientId, String firstName, String lastName, String birthDate,
                                   String gender, String status, String priority, String bloodType,
                                   String diagnosis, String allergies, String phone, String email, String address,
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

    /** Returns the real 9-digit patient ID. */
    public String getPatientId() { return patientId; }

    /** Returns the patient first name. */
    public String getFirstName() { return firstName; }

    /** Returns the patient last name. */
    public String getLastName() { return lastName; }

    /** Returns the patient birth date. */
    public String getBirthDate() { return birthDate; }

    /** Returns the patient gender. */
    public String getGender() { return gender; }

    /** Returns the patient status. */
    public String getStatus() { return status; }

    /** Returns the patient priority. */
    public String getPriority() { return priority; }

    /** Returns the patient blood type. */
    public String getBloodType() { return bloodType; }

    /** Returns the visit reason or diagnosis. */
    public String getDiagnosis() { return diagnosis; }

    /** Returns the allergy summary. */
    public String getAllergies() { return allergies; }

    /** Returns the patient phone number. */
    public String getPhone() { return phone; }

    /** Returns the patient email address. */
    public String getEmail() { return email; }

    /** Returns the patient address. */
    public String getAddress() { return address; }

    /** Returns the emergency contact name. */
    public String getEmergencyContactName() { return emergencyContactName; }

    /** Returns the emergency contact phone. */
    public String getEmergencyContactPhone() { return emergencyContactPhone; }
}
