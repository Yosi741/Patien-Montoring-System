package ui.javafx.pages.Alert;

import ui.javafx.pages.audit_logs.SqliteAuditLogDao;
import pages.patient.Patient;
import ui.javafx.pages.notifications.NotificationCenterService;
import pages.patient.VitalSign;
import users.Session;

import javax.sound.sampled.*;
import javafx.application.Platform;
import javafx.scene.control.Alert;

import java.io.File;
import java.net.URL;

public class AlarmService {

    private static final String CLASSPATH_SOUND = "/sound/alarm.wav";
    private static final String CANONICAL_SOUND_PATH = "src/sound/alarm.wav";
    private static final String LEGACY_SOUND_PATH = "resources/sounds/alarm.wav";

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
        if (patient == null) {
            return;
        }
        if (!isCritical(patient.getVitalSign())) {
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
            logAudit("System", "Alarm ACTIVE for patient: " + patient.getName());
            AlertPersistenceService.persistCriticalPatientAlert(patient);
            new NotificationCenterService().notifyCriticalAlert(patient.getPatientId(), "CRITICAL",
                    "Critical alert active for patient " + patient.getName() + " in " + patient.getSection() + " room " + patient.getRoom(),
                    patient.getPatientId());
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
        logAudit("System", "Alarm ACKNOWLEDGED");
    }

    public static synchronized void stopAlarm() {
        AlertPersistenceService.markStopped(activePatientId, Session.getUsername());
        stopSoundOnly();
        state = AlarmState.STOPPED;
        alertShownForActiveAlarm = false;
        logAudit("System", "Alarm STOPPED");
    }

    public static synchronized void resolveAlarm() {
        AlertPersistenceService.markResolved(activePatientId, Session.getUsername());
        stopSoundOnly();
        state = AlarmState.RESOLVED;
        activePatientId = "";
        alertShownForActiveAlarm = false;
        logAudit("System", "Alarm RESOLVED");
    }

    public static synchronized AlarmState getState() {
        return state;
    }

    private static boolean isCritical(VitalSign vitalSign) {
        if (vitalSign == null) {
            return false;
        }
        return vitalSign.getTemperature() >= 39.0
                || vitalSign.getHeartRate() >= 130
                || vitalSign.getSystolicPressure() >= 180
                || vitalSign.getDiastolicPressure() >= 120
                || vitalSign.getOxygenLevel() <= 88;
    }

    private static synchronized void startAlarm() {
        if (currentClip != null && currentClip.isRunning()) {
            return;
        }

        try {
            AudioInputStream audioStream = openAudioStream();
            if (audioStream == null) {
                System.out.println("Alarm sound file not found in classpath or filesystem fallbacks.");
                return;
            }
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

    private static AudioInputStream openAudioStream() {
        try {
            URL soundUrl = AlarmService.class.getResource(CLASSPATH_SOUND);
            if (soundUrl != null) {
                return AudioSystem.getAudioInputStream(soundUrl);
            }

            File canonicalSoundFile = new File(CANONICAL_SOUND_PATH);
            if (canonicalSoundFile.exists()) {
                return AudioSystem.getAudioInputStream(canonicalSoundFile);
            }

            File legacySoundFile = new File(LEGACY_SOUND_PATH);
            if (legacySoundFile.exists()) {
                return AudioSystem.getAudioInputStream(legacySoundFile);
            }
        } catch (Exception e) {
            System.out.println("Alarm sound load error: " + e.getMessage());
        }
        return null;
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

    private static void logAudit(String username, String action) {
        try {
            new SqliteAuditLogDao().log(username, action);
        } catch (Exception e) {
            System.out.println("SQLite alarm audit skipped: " + e.getMessage());
        }
    }
}
