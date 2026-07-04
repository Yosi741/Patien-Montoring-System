package pages.newborn;

public class NewbornRecord {

    private String babyId;
    private String babyName;
    private String motherId;
    private String motherFirstName;
    private String motherLastName;
    private String fatherFirstName;
    private String fatherLastName;
    private String birthDateTime;
    private String gender;
    private double birthWeightKg;
    private String deliveryType;
    private String section;
    private String room;
    private String hospitalStatus;
    private boolean premature;
    private String vitals;
    private String notes;
    private String certificatePath;

    public NewbornRecord(String babyId, String babyName, String motherId, String motherFirstName,
                         String motherLastName, String fatherFirstName, String fatherLastName,
                         String birthDateTime, String gender, double birthWeightKg, String deliveryType,
                         String section, String room, String hospitalStatus, boolean premature,
                         String vitals, String notes, String certificatePath) {
        this.babyId = babyId;
        this.babyName = babyName;
        this.motherId = motherId;
        this.motherFirstName = motherFirstName;
        this.motherLastName = motherLastName;
        this.fatherFirstName = fatherFirstName;
        this.fatherLastName = fatherLastName;
        this.birthDateTime = birthDateTime;
        this.gender = gender;
        this.birthWeightKg = birthWeightKg;
        this.deliveryType = deliveryType;
        this.section = section;
        this.room = room;
        this.hospitalStatus = hospitalStatus;
        this.premature = premature;
        this.vitals = vitals;
        this.notes = notes;
        this.certificatePath = certificatePath;
    }

    public String getBabyId() { return babyId; }
    public String getBabyName() { return babyName; }
    public String getMotherId() { return motherId; }
    public String getMotherFirstName() { return motherFirstName; }
    public String getMotherLastName() { return motherLastName; }
    public String getFatherFirstName() { return fatherFirstName; }
    public String getFatherLastName() { return fatherLastName; }
    public String getBirthDateTime() { return birthDateTime; }
    public String getGender() { return gender; }
    public double getBirthWeightKg() { return birthWeightKg; }
    public String getDeliveryType() { return deliveryType; }
    public String getSection() { return section; }
    public String getRoom() { return room; }
    public String getHospitalStatus() { return hospitalStatus; }
    public boolean isPremature() { return premature; }
    public String getVitals() { return vitals; }
    public String getNotes() { return notes; }
    public String getCertificatePath() { return certificatePath; }
}
