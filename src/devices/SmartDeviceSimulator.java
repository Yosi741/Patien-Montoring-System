package devices;

import models.Patient;
import services.DeviceService;

public class SmartDeviceSimulator {

    private static Patient activePatient;

    public static void startMonitoring(Patient patient) {
        activePatient = patient;
        DeviceService.connectSimulationMonitor(patient);
    }

    public static void stopMonitoring() {
        if (activePatient != null) {
            DeviceService.disconnectPatientDevice(activePatient);
            activePatient = null;
        }
    }
}
