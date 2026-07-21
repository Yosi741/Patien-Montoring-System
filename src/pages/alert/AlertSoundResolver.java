package pages.alert;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.File;
import java.net.URL;

/**
 * Locates the packaged alarm WAV resource and safe filesystem fallbacks for alert playback.
 */
final class AlertSoundResolver {

    private static final String CLASSPATH_SOUND = "/sound/alarm.wav";
    private static final String CANONICAL_SOURCE_SOUND = "src/sound/alarm.wav";
    private static final String RUNTIME_OUTPUT_SOUND = "out/production/untitledSmartPatientMonitoringSystem/sound/alarm.wav";
    private static final String LEGACY_SOUND = "resources/sounds/alarm.wav";
    private static final String EXTRA_FALLBACK_SOUND = "sound/alarm.wav";

    /**
     * Creates a alert sound resolver from the supplied record values.
     */
    private AlertSoundResolver() {
    }

    /**
     * Opens audio stream for the selected record.
     */
    static AudioInputStream openAudioStream(Class<?> owner, String logPrefix) {
        URL classpathUrl = owner.getResource(CLASSPATH_SOUND);
        if (classpathUrl != null) {
            try {
                return AudioSystem.getAudioInputStream(classpathUrl);
            } catch (Exception e) {
                System.out.println(logPrefix + " classpath load failed: " + classpathUrl + " - " + e.getMessage());
            }
        }

        AudioInputStream stream = tryFile(logPrefix, CANONICAL_SOURCE_SOUND);
        if (stream != null) {
            return stream;
        }

        stream = tryFile(logPrefix, RUNTIME_OUTPUT_SOUND);
        if (stream != null) {
            return stream;
        }

        stream = tryFile(logPrefix, LEGACY_SOUND);
        if (stream != null) {
            return stream;
        }

        stream = tryFile(logPrefix, EXTRA_FALLBACK_SOUND);
        if (stream != null) {
            return stream;
        }

        logMissing(logPrefix, classpathUrl);
        return null;
    }

    /**
     * Attempts file and returns the usable result when available.
     */
    private static AudioInputStream tryFile(String logPrefix, String candidate) {
        File file = new File(candidate);
        if (!file.exists()) {
            return null;
        }
        try {
            return AudioSystem.getAudioInputStream(file);
        } catch (Exception e) {
            System.out.println(logPrefix + " file load failed: " + file.getAbsolutePath() + " - " + e.getMessage());
            return null;
        }
    }

    /**
     * Logs missing for resource diagnostics.
     */
    private static void logMissing(String logPrefix, URL classpathUrl) {
        System.out.println(logPrefix + " file not found in classpath or filesystem fallbacks.");
        System.out.println(logPrefix + " user.dir=" + System.getProperty("user.dir"));
        System.out.println(logPrefix + " classpath " + CLASSPATH_SOUND + " -> " + (classpathUrl == null ? "missing" : classpathUrl));
        logAttempt(logPrefix, CANONICAL_SOURCE_SOUND);
        logAttempt(logPrefix, RUNTIME_OUTPUT_SOUND);
        logAttempt(logPrefix, LEGACY_SOUND);
        logAttempt(logPrefix, EXTRA_FALLBACK_SOUND);
    }

    /**
     * Logs attempt for resource diagnostics.
     */
    private static void logAttempt(String logPrefix, String candidate) {
        File file = new File(candidate);
        System.out.println(logPrefix + " attempt " + candidate + " -> " + file.getAbsolutePath() + " exists=" + file.exists());
    }
}
