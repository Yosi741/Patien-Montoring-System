package devices;

import models.VitalSign;

public interface MedicalDeviceAdapter {
    boolean connect();
    void disconnect();
    VitalSign readVitals();
    String getDeviceName();
    String getDeviceType();
    String getSerialNumber();
}
