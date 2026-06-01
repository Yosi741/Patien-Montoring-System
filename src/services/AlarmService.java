package services;

import ai_Prototype.AIAnalysis;
import database.NotificationStorage;
import logs.AuditLog;
import models.Patient;
import users.Session;

import javax.sound.sampled.*;
import javafx.application.Platform;
import javafx.scene.control.Alert;

import java.io.File;

public class AlarmService {

    public enum AlarmState {
        ACTIVE,
        ACKNOWLEDGED,
        STOPPED,
        RESOLVED
    }

    private static Clip currentClip;
    private static AlarmState state = AlarmState.STOPPED;
    private static boolean alertDialogOpen = false;
    private static boolean alertShownForActiveAlarm = false;
    private static String activePatientId = "";

    public static synchronized void checkPatient(Patient patient) {
        String risk = AIAnalysis.analyzeRisk(patient.getVitalSign());

        if (!risk.equals("Critical")) {
            if (activePatientId.equals(patient.getPatientId())) {
                resolveAlarm();
            }
            return;
        }

        if (state == AlarmState.ACKNOWLEDGED && activePatientId.equals(patient.getPatientId())) {
            return;
        }

        if (state != AlarmState.ACTIVE || !activePatientId.equals(patient.getPatientId())) {
            activePatientId = patient.getPatientId();
            state = AlarmState.ACTIVE;
            alertShownForActiveAlarm = false;
            startAlarm();
            AuditLog.addLog("System", "Alarm ACTIVE for patient: " + patient.getName());
            NotificationStorage.addNotification("ALL", "CRITICAL", "Critical alert active for patient " + patient.getName() + " in " + patient.getSection() + " room " + patient.getRoom());
            AlertPersistenceService.persistCriticalPatientAlert(patient);
        }

        if (!alertDialogOpen && !alertShownForActiveAlarm) {
            Platform.runLater(() -> showCriticalDialog(patient));
        }
    }

    public static synchronized void acknowledgeAlarm() {
        AlertPersistenceService.markAcknowledged(activePatientId, Session.getUsername());
        stopSoundOnly();
        state = AlarmState.ACKNOWLEDGED;
        alertShownForActiveAlarm = true;
        AuditLog.addLog("System", "Alarm ACKNOWLEDGED");
    }

    public static synchronized void stopAlarm() {
        AlertPersistenceService.markStopped(activePatientId, Session.getUsername());
        stopSoundOnly();
        state = AlarmState.STOPPED;
        alertShownForActiveAlarm = false;
        AuditLog.addLog("System", "Alarm STOPPED");
    }

    public static synchronized void resolveAlarm() {
        AlertPersistenceService.markResolved(activePatientId, Session.getUsername());
        stopSoundOnly();
        state = AlarmState.RESOLVED;
        activePatientId = "";
        alertShownForActiveAlarm = false;
        AuditLog.addLog("System", "Alarm RESOLVED");
    }

    public static synchronized AlarmState getState() {
        return state;
    }

    private static synchronized void startAlarm() {
        if (currentClip != null && currentClip.isRunning()) {
            return;
        }

        try {
            File soundFile = new File("resources/sounds/alarm.wav");
            AudioInputStream audioStream = AudioSystem.getAudioInputStream(soundFile);
            currentClip = AudioSystem.getClip();
            currentClip.open(audioStream);
            currentClip.loop(Clip.LOOP_CONTINUOUSLY);
            currentClip.start();
        } catch (Exception e) {
            System.out.println("Alarm sound error: " + e.getMessage());
        }
    }

    private static synchronized void stopSoundOnly() {
        try {
            if (currentClip != null) {
                currentClip.stop();
                currentClip.close();
                currentClip = null;
            }
        } catch (Exception e) {
            System.out.println("Stop alarm error: " + e.getMessage());
        }
    }

    private static void showCriticalDialog(Patient patient) {
        alertDialogOpen = true;
        alertShownForActiveAlarm = true;

        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Critical Patient Alert");
        alert.setHeaderText("⚠ CRITICAL ALERT");
        alert.setContentText(
            "Patient: " + patient.getName()
            + "\nRoom: " + patient.getRoom()
            + "\nSection: " + patient.getSection()
            + "\nRisk Level: CRITICAL"
            + "\n\nImmediate medical attention required."
            + "\n\nActions:"
            + "\n- Review current vitals immediately."
            + "\n- Check device ID/history if device error is possible."
            + "\n- Press OK to acknowledge and silence the alarm."
        );

        alert.setOnHidden(event -> {
            alertDialogOpen = false;
            acknowledgeAlarm();
        });

        alert.showAndWait();
    }
}
