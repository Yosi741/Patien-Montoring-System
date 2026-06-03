package services;

import alerts.CriticalAlertManager;
import dao.SqliteAuditLogDao;
import dao.SqliteVitalReadingDao;
import models.MedicalDevice;
import models.Patient;
import models.VitalRecord;
import models.VitalSign;
import users.Session;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public class VitalService {

    public static String validateVitals(double temperature, int heartRate, int systolic, int diastolic, int oxygen) {
        if (temperature < 30 || temperature > 45) {
            return "Temperature must be between 30 and 45 C.";
        }
        if (heartRate < 20 || heartRate > 250) {
            return "Heart rate must be between 20 and 250 bpm.";
        }
        if (systolic < 50 || systolic > 260) {
            return "Systolic pressure must be between 50 and 260 mmHg.";
        }
        if (diastolic < 30 || diastolic > 160) {
            return "Diastolic pressure must be between 30 and 160 mmHg.";
        }
        if (diastolic >= systolic) {
            return "Diastolic pressure must be lower than systolic pressure.";
        }
        if (oxygen < 50 || oxygen > 100) {
            return "Oxygen saturation must be between 50% and 100%.";
        }
        return null;
    }

    public static void recordManualVitals(Patient patient, VitalSign vitalSign) {
        patient.setVitalSign(vitalSign);
        saveVitalSet(patient, vitalSign, "Manual", Session.getUsername(), "", "", "", "");
        CriticalAlertManager.checkPatient(patient);
        logAudit(Session.getUsername(), "Added manual vital signs for: " + patient.getName());
    }

    public static void recordDeviceVitals(Patient patient, VitalSign vitalSign, MedicalDevice device) {
        patient.setVitalSign(vitalSign);
        saveVitalSet(patient, vitalSign, "Device", "", device.getDeviceId(), device.getSerialNumber(),
                device.getDeviceName(), device.getDeviceType());
        CriticalAlertManager.checkPatient(patient);
        logAudit("Device:" + device.getDeviceId(), "Added device vital signs for: " + patient.getName());
    }

    private static void saveVitalSet(Patient patient, VitalSign vitalSign, String sourceType, String staffName,
                                     String deviceId, String deviceSerial, String deviceName, String deviceType) {
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"));
        addRecord(patient, "Temperature", String.format("%.1f", vitalSign.getTemperature()), "C", now, sourceType, staffName, deviceId, deviceSerial, deviceName, deviceType);
        addRecord(patient, "Heart Rate", String.valueOf(vitalSign.getHeartRate()), "bpm", now, sourceType, staffName, deviceId, deviceSerial, deviceName, deviceType);
        addRecord(patient, "Systolic Pressure", String.valueOf(vitalSign.getSystolicPressure()), "mmHg", now, sourceType, staffName, deviceId, deviceSerial, deviceName, deviceType);
        addRecord(patient, "Diastolic Pressure", String.valueOf(vitalSign.getDiastolicPressure()), "mmHg", now, sourceType, staffName, deviceId, deviceSerial, deviceName, deviceType);
        addRecord(patient, "Oxygen Saturation", String.valueOf(vitalSign.getOxygenLevel()), "%", now, sourceType, staffName, deviceId, deviceSerial, deviceName, deviceType);
    }

    private static void addRecord(Patient patient, String vitalType, String value, String unit, String now,
                                  String sourceType, String staffName, String deviceId, String deviceSerial,
                                  String deviceName, String deviceType) {
        try {
            new SqliteVitalReadingDao().insertVitalReading(new VitalRecord(
                    UUID.randomUUID().toString(),
                    patient.getPatientId(),
                    vitalType,
                    value,
                    unit,
                    now,
                    sourceType,
                    staffName,
                    deviceId,
                    deviceSerial,
                    deviceName,
                    deviceType
            ));
        } catch (Exception e) {
            System.out.println("SQLite vital runtime save failed: " + e.getMessage());
        }
    }

    private static void logAudit(String username, String action) {
        try {
            new SqliteAuditLogDao().log(username, action);
        } catch (Exception e) {
            System.out.println("SQLite vital audit skipped: " + e.getMessage());
        }
    }
}
