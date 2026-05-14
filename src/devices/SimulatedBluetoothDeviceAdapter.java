package devices;

import models.VitalSign;

import java.util.Random;

public class SimulatedBluetoothDeviceAdapter implements MedicalDeviceAdapter {

    private Random random = new Random();
    private boolean connected = false;

    @Override
    public boolean connect() {
        connected = true;
        return true;
    }

    @Override
    public void disconnect() {
        connected = false;
    }

    @Override
    public VitalSign readVitals() {
        if (!connected) {
            return null;
        }

        double temperature = 36 + (random.nextDouble() * 4);
        int heartRate = 60 + random.nextInt(80);
        int systolic = 100 + random.nextInt(70);
        int diastolic = 60 + random.nextInt(35);
        int oxygen = 85 + random.nextInt(15);

        return new VitalSign(temperature, heartRate, systolic, diastolic, oxygen);
    }

    @Override
    public String getDeviceName() {
        return "Simulated Bluetooth ICU Monitor";
    }

    @Override
    public String getDeviceType() {
        return "ECG";
    }

    @Override
    public String getSerialNumber() {
        return "SIM-BT-ECG-001";
    }
}
