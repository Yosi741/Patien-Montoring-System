package pages.alert;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;

/**
 * Coordinates JavaFX alarm playback for active critical alerts and safely stops prior audio.
 */
public final class AlertSoundService {

    private static final Object LOCK = new Object();
    private static Clip currentClip;


    /**
     * Plays alert sound through the JavaFX media system.
     */
    public static void playAlertSound() {
        synchronized (LOCK) {
            if (currentClip != null && currentClip.isRunning()) {
                return;
            }

            try (AudioInputStream audioStream =
                         AlertSoundResolver.openAudioStream(AlertSoundService.class, "JavaFX alert sound")) {
                if (audioStream == null) {
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

    /**
     * Stops alert sound and releases its resources.
     */
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
