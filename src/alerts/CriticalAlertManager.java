package alerts;

import ai.AIAnalysis;
import models.Patient;

import javax.sound.sampled.*;
import javax.swing.*;
import java.io.File;

public class CriticalAlertManager {

    private static Clip currentClip;
    private static boolean alertOpen = false;

    public static void checkPatient(Patient patient) {

        String risk = AIAnalysis.analyzeRisk(patient.getVitalSign());

        if (risk.equals("Critical")) {

            if (alertOpen) {
                return;
            }

            alertOpen = true;

            playAlarm();

            JOptionPane.showMessageDialog(
                    null,
                    "CRITICAL ALERT!\n\nPatient: "
                            + patient.getName()
                            + "\nRoom: "
                            + patient.getRoom()
                            + "\nImmediate medical attention required.",
                    "Critical Patient Alert",
                    JOptionPane.ERROR_MESSAGE
            );

            stopAlarm();

            alertOpen = false;
        }
    }

    private static void playAlarm() {
        try {
            stopAlarm();

            File soundFile = new File("resources/sounds/alarm.wav");

            AudioInputStream audioStream =
                    AudioSystem.getAudioInputStream(soundFile);

            currentClip = AudioSystem.getClip();
            currentClip.open(audioStream);
            currentClip.loop(Clip.LOOP_CONTINUOUSLY);
            currentClip.start();

        } catch (Exception e) {
            System.out.println("Alarm sound error: " + e.getMessage());
        }
    }

    public static void stopAlarm() {
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
}