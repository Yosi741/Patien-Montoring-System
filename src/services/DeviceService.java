package services;

import database.DeviceStorage;
import database.NotificationStorage;
import devices.MedicalDeviceAdapter;
import devices.SimulatedBluetoothDeviceAdapter;
import logs.AuditLog;
import models.MedicalDevice;
import models.Patient;
import models.VitalSign;
import users.Session;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DeviceService {

    private static final Map<String, Boolean> runningByPatient = new HashMap<>();
    private static final Map<String, MedicalDevice> connectedDevices = new HashMap<>();
    private static final Map<String, MedicalDeviceAdapter> adapters = new HashMap<>();

    public static MedicalDevice connectSimulationMonitor(Patient patient) {
        if (hasConnectedDevice(patient.getPatientId())) {
            return connectedDevices.get(patient.getPatientId());
        }
        SimulatedBluetoothDeviceAdapter adapter = new SimulatedBluetoothDeviceAdapter();
        return connectDevice(patient, adapter, true);
    }

    public static MedicalDevice connectDevice(Patient patient, MedicalDeviceAdapter adapter, boolean startReadingLoop) {
        if (hasConnectedDevice(patient.getPatientId())) {
            return connectedDevices.get(patient.getPatientId());
        }

        if (!adapter.connect()) {
            throw new IllegalStateException("Device connection failed. Real Bluetooth integration is not configured on this computer.");
        }

        String deviceId = "DEV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        MedicalDevice device = new MedicalDevice(
                deviceId,
                adapter.getDeviceName(),
                adapter.getDeviceType(),
                adapter.getSerialNumber(),
                "Connected",
                now(),
                patient.getPatientId()
        );

        connectedDevices.put(patient.getPatientId(), device);
        adapters.put(patient.getPatientId(), adapter);
        runningByPatient.put(patient.getPatientId(), true);
        DeviceStorage.upsertDevice(device);
        AuditLog.addLog(Session.getUsername(), "Connected device " + device.getDeviceId() + " for patient: " + patient.getName());

        if (startReadingLoop) {
            startReadingLoop(patient, device, adapter);
        }

        return device;
    }

    public static void disconnectPatientDevice(Patient patient) {
        String patientId = patient.getPatientId();
        runningByPatient.put(patientId, false);

        MedicalDeviceAdapter adapter = adapters.remove(patientId);
        if (adapter != null) {
            adapter.disconnect();
        }

        MedicalDevice device = connectedDevices.remove(patientId);
        if (device != null) {
            device.setConnectionStatus("Disconnected");
            device.setLastConnectionTime(now());
            DeviceStorage.upsertDevice(device);
            AuditLog.addLog(Session.getUsername(), "Disconnected device " + device.getDeviceId() + " for patient: " + patient.getName());
            NotificationStorage.addNotification("ALL", "WARNING", "Device disconnected for patient " + patient.getName() + ": " + device.getDeviceName());
        }
    }

    public static boolean hasConnectedDevice(String patientId) {
        return connectedDevices.containsKey(patientId)
                && connectedDevices.get(patientId).getConnectionStatus().equals("Connected");
    }

    public static boolean hasConnectedEcg(String patientId) {
        return hasConnectedDevice(patientId)
                && connectedDevices.get(patientId).getDeviceType().equalsIgnoreCase("ECG");
    }

    public static MedicalDevice getConnectedDevice(String patientId) {
        return connectedDevices.get(patientId);
    }

    private static void startReadingLoop(Patient patient, MedicalDevice device, MedicalDeviceAdapter adapter) {
        Thread monitorThread = new Thread(() -> {
            while (runningByPatient.getOrDefault(patient.getPatientId(), false)) {
                try {
                    VitalSign vitalSign = adapter.readVitals();
                    if (vitalSign != null) {
                        VitalService.recordDeviceVitals(patient, vitalSign, device);
                    }
                    Thread.sleep(5000);
                } catch (Exception e) {
                    System.out.println("Device monitor error: " + e.getMessage());
                }
            }
        });

        monitorThread.setDaemon(true);
        monitorThread.start();
    }

    private static String now() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"));
    }
}
