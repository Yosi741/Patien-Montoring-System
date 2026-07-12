package pages.patient;

import pages.user.User;

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
    private String allergies;
    private String phone;
    private String email;
    private String address;
    private String emergencyContactName;
    private String emergencyContactPhone;
    private String familyHistory;
    private User doctor;
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
        this.allergies = "";
        this.phone = "";
        this.email = "";
        this.address = "";
        this.emergencyContactName = "";
        this.emergencyContactPhone = "";
        this.familyHistory = "";
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

    public String getAllergies() {
        return allergies;
    }

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }

    public String getAddress() {
        return address;
    }

    public String getEmergencyContactName() {
        return emergencyContactName;
    }

    public String getEmergencyContactPhone() {
        return emergencyContactPhone;
    }

    public String getFamilyHistory() {
        return familyHistory;
    }

    public User getDoctor() {
        return doctor;
    }

    public void setDoctor(User doctor) {
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

    public void setAllergies(String allergies) {
        this.allergies = allergies;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public void setEmergencyContactName(String emergencyContactName) {
        this.emergencyContactName = emergencyContactName;
    }

    public void setEmergencyContactPhone(String emergencyContactPhone) {
        this.emergencyContactPhone = emergencyContactPhone;
    }

    public void setFamilyHistory(String familyHistory) {
        this.familyHistory = familyHistory;
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
