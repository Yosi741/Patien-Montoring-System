package users;

public class Doctor {

    private String doctorId;
    private String name;
    private String specialty;

    public Doctor(String doctorId, String name, String specialty) {

        this.doctorId = doctorId;
        this.name = name;
        this.specialty = specialty;

    }

    public String getDoctorId() {
        return doctorId;
    }

    public void setDoctorId(String doctorId) {
        this.doctorId = doctorId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSpecialty() {
        return specialty;
    }

    public void setSpecialty(String specialty) {
        this.specialty = specialty;
    }

    public void displayDoctorInfo() {

        System.out.println("users.Doctor ID: " + doctorId);
        System.out.println("Name: " + name);
        System.out.println("Specialty: " + specialty);

    }

}