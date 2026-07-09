package pages.alert;

import pages.patient.Patient;
import pages.notification.NotificationCenterService;
import pages.patient.VitalSign;
import users.Session;

import javax.sound.sampled.*;
import javafx.application.Platform;
import javafx.scene.control.Alert;

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
    }

    public static synchronized void stopAlarm() {
        AlertPersistenceService.markStopped(activePatientId, Session.getUsername());
        stopSoundOnly();
        state = AlarmState.STOPPED;
        alertShownForActiveAlarm = false;
    }

    public static synchronized void resolveAlarm() {
        AlertPersistenceService.markResolved(activePatientId, Session.getUsername());
        stopSoundOnly();
        state = AlarmState.RESOLVED;
        activePatientId = "";
        alertShownForActiveAlarm = false;
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
            AudioInputStream audioStream = AlertSoundResolver.openAudioStream(AlarmService.class, "Alarm sound");
            if (audioStream == null) {
                return;
            }
            currentClip = AudioSystem.getClip();
            currentClip.open(audioStream);
            audioStream.close();
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
