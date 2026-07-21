package pages.patient.patient_vitals;

/**
 * Represents one recorded vital value with its unit, timestamp, and recording staff member.
 */
public class VitalRecord {

    private String recordId;
    private String patientId;
    private String vitalType;
    private String value;
    private String unit;
    private String dateTime;
    private String sourceType;
    private String staffName;

    /**
     * Creates a vital record from the supplied record values.
     */
    public VitalRecord(String recordId, String patientId, String vitalType, String value, String unit,
                       String dateTime, String sourceType, String staffName) {
        this.recordId = recordId;
        this.patientId = patientId;
        this.vitalType = vitalType;
        this.value = value;
        this.unit = unit;
        this.dateTime = dateTime;
        this.sourceType = sourceType;
        this.staffName = staffName;
    }

    public String getPatientId() { return patientId; }
    public String getVitalType() { return vitalType; }
    public String getValue() { return value; }
    public String getUnit() { return unit; }
    public String getDateTime() { return dateTime; }
    public String getSourceType() { return sourceType; }
    public String getStaffName() { return staffName; }
}

