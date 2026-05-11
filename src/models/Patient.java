package models;

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

    private Doctor doctor;
    private VitalSign vitalSign;
    private Medication medication;

    public Patient(String patientId, String firstName, String lastName,
                   String birthDate, String gender, String room) {

        this.patientId = patientId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.birthDate = birthDate;
        this.gender = gender;
        this.room = room;
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

    public Medication getMedication() {
        return medication;
    }

    public void setMedication(Medication medication) {
        this.medication = medication;
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

    public void displayPatientInfo() {
        System.out.println("Patient ID: " + patientId);
        System.out.println("Name: " + getName());
        System.out.println("Birth Date: " + birthDate);
        System.out.println("Age: " + getAge());
        System.out.println("Gender: " + gender);
        System.out.println("Room: " + room);
    }
}