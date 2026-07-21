package pages.patient.patient_directory;

/**
 * Represents one visible row in the Patient Management table.
 */
public class PatientListRow {
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
     * Creates a patient directory row from values loaded from SQLite.
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

    /** Returns the real 9-digit patient ID. */
    public String getPatientId() { return patientId; }

    /** Returns the patient display name. */
    public String getName() { return name; }

    /** Returns the patient birth date as stored for display. */
    public String getBirthDate() { return birthDate; }

    /** Returns the patient gender. */
    public String getGender() { return gender; }

    /** Returns the patient status. */
    public String getStatus() { return status; }

    /** Returns the patient priority. */
    public String getPriority() { return priority; }

    /** Returns the patient blood type. */
    public String getBloodType() { return bloodType; }

    /** Returns the patient phone number. */
    public String getPhone() { return phone; }

    /** Returns the patient email address. */
    public String getEmail() { return email; }
}

