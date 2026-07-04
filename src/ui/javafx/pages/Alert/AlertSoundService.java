package ui.javafx.pages.Alert;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import java.io.File;
import java.net.URL;

public final class AlertSoundService {

    private static final String CLASSPATH_SOUND = "/sound/alarm.wav";
    private static final String CANONICAL_SOUND_PATH = "src/sound/alarm.wav";
    private static final String LEGACY_SOUND_PATH = "resources/sounds/alarm.wav";

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
                AudioInputStream audioStream = openAudioStream();
                if (audioStream == null) {
                    System.out.println("JavaFX alert sound file not found in classpath or filesystem fallbacks.");
                    return;
                }

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

    private static AudioInputStream openAudioStream() {
        try {
            URL soundUrl = AlertSoundService.class.getResource(CLASSPATH_SOUND);
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
            System.out.println("JavaFX alert sound load error: " + e.getMessage());
        }
        return null;
    }
}
