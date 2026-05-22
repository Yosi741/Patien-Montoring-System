package services;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import java.io.File;

public final class AlertSoundService {

    private static final Object LOCK = new Object();
    private static Clip currentClip;

    private AlertSoundService() {
    }

    public static void playAlertSound() {
        synchronized (LOCK) {
            if (currentClip != null && currentClip.isRunning()) {
                return;
            }

            try {
                File soundFile = new File("resources/sounds/alarm.wav");
                if (!soundFile.exists()) {
                    System.out.println("JavaFX alert sound file not found: " + soundFile.getPath());
                    return;
                }

                AudioInputStream audioStream = AudioSystem.getAudioInputStream(soundFile);
                currentClip = AudioSystem.getClip();
                currentClip.open(audioStream);
                currentClip.loop(Clip.LOOP_CONTINUOUSLY);
                currentClip.start();
            } catch (Exception e) {
                System.out.println("JavaFX alert sound error: " + e.getMessage());
            }
        }
    }

    public static void stopAlertSound() {
        synchronized (LOCK) {
            try {
                if (currentClip != null) {
                    currentClip.stop();
                    currentClip.close();
                    currentClip = null;
                }
            } catch (Exception e) {
                System.out.println("JavaFX stop alert sound error: " + e.getMessage());
            }
        }
    }
}
