package devices;

import models.VitalSign;

public class BluetoothMedicalDeviceAdapter implements MedicalDeviceAdapter {

    private String deviceName;
    private String deviceType;
    private String serialNumber;

    public BluetoothMedicalDeviceAdapter(String deviceName, String deviceType, String serialNumber) {
        this.deviceName = deviceName;
        this.deviceType = deviceType;
        this.serialNumber = serialNumber;
    }

    @Override
    public boolean connect() {
        System.out.println("Bluetooth adapter interface ready for real device integration: " + deviceName);
        return false;
    }

    @Override
    public void disconnect() {
        System.out.println("Bluetooth device disconnected: " + deviceName);
    }

    @Override
    public VitalSign readVitals() {
        return null;
    }

    @Override
    public String getDeviceName() {
        return deviceName;
    }

    @Override
    public String getDeviceType() {
        return deviceType;
    }

    @Override
    public String getSerialNumber() {
        return serialNumber;
    }
}
