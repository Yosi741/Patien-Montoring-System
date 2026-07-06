package pages.alert;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;

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
