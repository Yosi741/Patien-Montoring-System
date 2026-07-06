package pages.patient;

import pages.patient.VitalSign;
import users.Doctor;

import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;

public class Patient {

    private String patientId;
    private String firstName;
    private String lastName;
    private String birthDate;
    private String gender;
    private String room;
    private String section;
    private String status;
    private String diagnosis;
    private String medicalHistory;
    private String currentMedications;
    private String pastMedications;
    private String allergies;
    private String familyHistory;
    private String deathDateTime;
    private String deathCause;
    private String deathClinicalSummary;
    private String deathNotes;
    private String pronouncingDoctorName;
    private String pronouncingDoctorId;
    private String deathCertificatePath;
    private String deathCertificateNumber;

    private Doctor doctor;
    private VitalSign vitalSign;

    public Patient(String patientId, String firstName, String lastName,
                   String birthDate, String gender, String room) {

        this.patientId = patientId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.birthDate = birthDate;
        this.gender = gender;
        this.room = room;
        this.section = "Unassigned";
        this.status = "Active";
        this.diagnosis = "";
        this.medicalHistory = "";
        this.currentMedications = "";
        this.pastMedications = "";
        this.allergies = "";
        this.familyHistory = "";
        this.deathDateTime = "";
        this.deathCause = "";
        this.deathClinicalSummary = "";
        this.deathNotes = "";
        this.pronouncingDoctorName = "";
        this.pronouncingDoctorId = "";
        this.deathCertificatePath = "";
        this.deathCertificateNumber = "";
    }

    public String getPatientId() {
        return patientId;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getName() {
        return firstName + " " + lastName;
    }

    public String getBirthDate() {
        return birthDate;
    }

    public int getAge() {
        DateTimeFormatter formatter =
                DateTimeFormatter.ofPattern("dd-MM-yyyy");

        LocalDate birth =
                LocalDate.parse(birthDate, formatter);

        return Period.between(birth, LocalDate.now()).getYears();
    }

    public String getGender() {
        return gender;
    }

    public String getRoom() {
        return room;
    }

    public String getSection() {
        return section;
    }

    public String getStatus() {
        return status;
    }

    public String getDiagnosis() {
        return diagnosis;
    }

    public String getMedicalHistory() {
        return medicalHistory;
    }

    public String getCurrentMedications() {
        return currentMedications;
    }

    public String getPastMedications() {
        return pastMedications;
    }

    public String getAllergies() {
        return allergies;
    }

    public String getFamilyHistory() {
        return familyHistory;
    }

    public String getDeathDateTime() {
        return deathDateTime;
    }

    public String getDeathCause() {
        return deathCause;
    }

    public String getDeathClinicalSummary() {
        return deathClinicalSummary;
    }

    public String getDeathNotes() {
        return deathNotes;
    }

    public String getPronouncingDoctorName() {
        return pronouncingDoctorName;
    }

    public String getPronouncingDoctorId() {
        return pronouncingDoctorId;
    }

    public String getDeathCertificatePath() {
        return deathCertificatePath;
    }

    public String getDeathCertificateNumber() {
        return deathCertificateNumber;
    }

    public boolean isDeceased() {
        return status != null && status.equalsIgnoreCase("DECEASED");
    }

    public Doctor getDoctor() {
        return doctor;
    }

    public void setDoctor(Doctor doctor) {
        this.doctor = doctor;
    }

    public VitalSign getVitalSign() {
        return vitalSign;
    }

    public void setVitalSign(VitalSign vitalSign) {
        this.vitalSign = vitalSign;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public void setBirthDate(String birthDate) {
        this.birthDate = birthDate;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public void setRoom(String room) {
        this.room = room;
    }

    public void setSection(String section) {
        this.section = section;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setDiagnosis(String diagnosis) {
        this.diagnosis = diagnosis;
    }

    public void setMedicalHistory(String medicalHistory) {
        this.medicalHistory = medicalHistory;
    }

    public void setCurrentMedications(String currentMedications) {
        this.currentMedications = currentMedications;
    }

    public void setPastMedications(String pastMedications) {
        this.pastMedications = pastMedications;
    }

    public void setAllergies(String allergies) {
        this.allergies = allergies;
    }

    public void setFamilyHistory(String familyHistory) {
        this.familyHistory = familyHistory;
    }

    public void setDeathDateTime(String deathDateTime) {
        this.deathDateTime = deathDateTime;
    }

    public void setDeathCause(String deathCause) {
        this.deathCause = deathCause;
    }

    public void setDeathClinicalSummary(String deathClinicalSummary) {
        this.deathClinicalSummary = deathClinicalSummary;
    }

    public void setDeathNotes(String deathNotes) {
        this.deathNotes = deathNotes;
    }

    public void setPronouncingDoctorName(String pronouncingDoctorName) {
        this.pronouncingDoctorName = pronouncingDoctorName;
    }

    public void setPronouncingDoctorId(String pronouncingDoctorId) {
        this.pronouncingDoctorId = pronouncingDoctorId;
    }

    public void setDeathCertificatePath(String deathCertificatePath) {
        this.deathCertificatePath = deathCertificatePath;
    }

    public void setDeathCertificateNumber(String deathCertificateNumber) {
        this.deathCertificateNumber = deathCertificateNumber;
    }

    public void displayPatientInfo() {
        System.out.println("Patient ID: " + patientId);
        System.out.println("Name: " + getName());
        System.out.println("Birth Date: " + birthDate);
        System.out.println("Age: " + getAge());
        System.out.println("Gender: " + gender);
        System.out.println("Room: " + room);
    }
}
