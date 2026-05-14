package models;

public class MedicalDevice {

    private String deviceId;
    private String deviceName;
    private String deviceType;
    private String serialNumber;
    private String connectionStatus;
    private String lastConnectionTime;
    private String patientId;

    public MedicalDevice(String deviceId, String deviceName, String deviceType, String serialNumber,
                         String connectionStatus, String lastConnectionTime, String patientId) {
        this.deviceId = deviceId;
        this.deviceName = deviceName;
        this.deviceType = deviceType;
        this.serialNumber = serialNumber;
        this.connectionStatus = connectionStatus;
        this.lastConnectionTime = lastConnectionTime;
        this.patientId = patientId;
    }

    public String getDeviceId() { return deviceId; }
    public String getDeviceName() { return deviceName; }
    public String getDeviceType() { return deviceType; }
    public String getSerialNumber() { return serialNumber; }
    public String getConnectionStatus() { return connectionStatus; }
    public String getLastConnectionTime() { return lastConnectionTime; }
    public String getPatientId() { return patientId; }

    public void setConnectionStatus(String connectionStatus) {
        this.connectionStatus = connectionStatus;
    }

    public void setLastConnectionTime(String lastConnectionTime) {
        this.lastConnectionTime = lastConnectionTime;
    }

    public void setPatientId(String patientId) {
        this.patientId = patientId;
    }
}
