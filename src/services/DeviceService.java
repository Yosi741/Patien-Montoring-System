package services;

import dao.SqliteAuditLogDao;
import dao.SqliteDeviceDao;
import dao.SqliteNotificationDao;
import devices.MedicalDeviceAdapter;
import devices.SimulatedBluetoothDeviceAdapter;
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
        saveDevice(device);
        logAudit(Session.getUsername(), "Connected device " + device.getDeviceId() + " for patient: " + patient.getName());

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
            saveDevice(device);
            logAudit(Session.getUsername(), "Disconnected device " + device.getDeviceId() + " for patient: " + patient.getName());
            notifyDeviceDisconnected(patient, device);
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

    private static void saveDevice(MedicalDevice device) {
        try {
            SqliteDeviceDao dao = new SqliteDeviceDao();
            SqliteDeviceDao.DeviceRecord record = new SqliteDeviceDao.DeviceRecord(
                    device.getDeviceId(),
                    device.getDeviceName(),
                    device.getDeviceType(),
                    device.getSerialNumber(),
                    normalizeStatus(device.getConnectionStatus()),
                    device.getPatientId(),
                    "",
                    "Runtime device state",
                    device.getLastConnectionTime()
            );
            if (dao.existsByDeviceId(device.getDeviceId())) {
                dao.updateDevice(record);
            } else {
                dao.insertDevice(record);
            }
        } catch (Exception e) {
            System.out.println("SQLite device runtime state skipped: " + e.getMessage());
        }
    }

    private static String normalizeStatus(String status) {
        if ("Connected".equalsIgnoreCase(status)) {
            return "ASSIGNED";
        }
        if ("Disconnected".equalsIgnoreCase(status)) {
            return "AVAILABLE";
        }
        return status == null || status.isBlank() ? "AVAILABLE" : status.toUpperCase();
    }

    private static void notifyDeviceDisconnected(Patient patient, MedicalDevice device) {
        try {
            new SqliteNotificationDao().insert(new SqliteNotificationDao.NotificationWriteRecord(
                    "",
                    "NURSE",
                    patient.getSection(),
                    patient.getPatientId(),
                    "WARNING",
                    "Device disconnected",
                    "Device disconnected for patient " + patient.getName() + ": " + device.getDeviceName(),
                    "DEVICE",
                    device.getDeviceId()
            ));
        } catch (Exception e) {
            System.out.println("SQLite device notification skipped: " + e.getMessage());
        }
    }

    private static void logAudit(String username, String action) {
        try {
            new SqliteAuditLogDao().log(username, action);
        } catch (Exception e) {
            System.out.println("SQLite device audit skipped: " + e.getMessage());
        }
    }
}
