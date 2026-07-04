package ui.javafx.pages.patients.vitals_entry;

public class VitalRecord {

    private String recordId;
    private String patientId;
    private String vitalType;
    private String value;
    private String unit;
    private String dateTime;
    private String sourceType;
    private String staffName;
    private String deviceId;
    private String deviceSerial;
    private String deviceName;
    private String deviceType;

    public VitalRecord(String recordId, String patientId, String vitalType, String value, String unit,
                       String dateTime, String sourceType, String staffName, String deviceId,
                       String deviceSerial, String deviceName, String deviceType) {
        this.recordId = recordId;
        this.patientId = patientId;
        this.vitalType = vitalType;
        this.value = value;
        this.unit = unit;
        this.dateTime = dateTime;
        this.sourceType = sourceType;
        this.staffName = staffName;
        this.deviceId = deviceId;
        this.deviceSerial = deviceSerial;
        this.deviceName = deviceName;
        this.deviceType = deviceType;
    }

    public String getRecordId() { return recordId; }
    public String getPatientId() { return patientId; }
    public String getVitalType() { return vitalType; }
    public String getValue() { return value; }
    public String getUnit() { return unit; }
    public String getDateTime() { return dateTime; }
    public String getSourceType() { return sourceType; }
    public String getStaffName() { return staffName; }
    public String getDeviceId() { return deviceId; }
    public String getDeviceSerial() { return deviceSerial; }
    public String getDeviceName() { return deviceName; }
    public String getDeviceType() { return deviceType; }
}
