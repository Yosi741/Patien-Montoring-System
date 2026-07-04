package pages.patient;

public class MotherInfo {

    private String motherId;
    private String firstName;
    private String lastName;
    private String contactInfo;
    private String notes;

    public MotherInfo(String motherId, String firstName, String lastName, String contactInfo, String notes) {
        this.motherId = motherId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.contactInfo = contactInfo;
        this.notes = notes;
    }

    public String getMotherId() {
        return motherId;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getContactInfo() {
        return contactInfo;
    }

    public String getNotes() {
        return notes;
    }
}
