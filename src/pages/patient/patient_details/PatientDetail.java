package pages.patient.patient_details;

/**
 * Represents the full patient file details shown by Patient File and related workflows.
 */
public class PatientDetail {
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
     * Creates a patient detail object from SQLite patient columns.
     */
    public PatientDetail(String patientId, String firstName, String lastName, String birthDate,
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
        this.bloodType = bloodType == null || bloodType.isBlank() ? "Unknown" : bloodType;
        this.diagnosis = diagnosis;
        this.allergies = allergies == null || allergies.isBlank() ? "Unknown" : allergies;
        this.phone = phone == null ? "" : phone;
        this.email = email == null ? "" : email;
        this.address = address == null ? "" : address;
        this.emergencyContactName = emergencyContactName == null ? "" : emergencyContactName;
        this.emergencyContactPhone = emergencyContactPhone == null ? "" : emergencyContactPhone;
    }

    /** Returns the real 9-digit patient ID. */
    public String getPatientId() { return patientId; }

    /** Returns the patient first name. */
    public String getFirstName() { return firstName; }

    /** Returns the patient last name. */
    public String getLastName() { return lastName; }

    /** Returns the patient display name. */
    public String getName() { return firstName + " " + lastName; }

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
    public String getDiagnosis() { return diagnosis == null || diagnosis.isBlank() ? "No diagnosis recorded" : diagnosis; }

    /** Returns the allergy summary. */
    public String getAllergies() { return allergies == null || allergies.isBlank() ? "Unknown" : allergies; }

    /** Returns the patient phone number. */
    public String getPhone() { return phone == null ? "" : phone; }

    /** Returns the patient email address. */
    public String getEmail() { return email == null ? "" : email; }

    /** Returns the patient address. */
    public String getAddress() { return address == null ? "" : address; }

    /** Returns the emergency contact name. */
    public String getEmergencyContactName() { return emergencyContactName == null ? "" : emergencyContactName; }

    /** Returns the emergency contact phone. */
    public String getEmergencyContactPhone() { return emergencyContactPhone == null ? "" : emergencyContactPhone; }

    /**
     * Returns a display age calculated from the stored patient birth date.
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



