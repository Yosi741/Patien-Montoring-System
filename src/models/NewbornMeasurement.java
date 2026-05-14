package models;

public class NewbornMeasurement {
    private String babyId;
    private String timestamp;
    private String type;
    private String value;
    private String notes;

    public NewbornMeasurement(String babyId, String timestamp, String type, String value, String notes) {
        this.babyId = babyId;
        this.timestamp = timestamp;
        this.type = type;
        this.value = value;
        this.notes = notes;
    }

    public String getBabyId() { return babyId; }
    public String getTimestamp() { return timestamp; }
    public String getType() { return type; }
    public String getValue() { return value; }
    public String getNotes() { return notes; }
}
