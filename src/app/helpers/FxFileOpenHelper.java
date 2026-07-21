package app.helpers;

import javafx.application.HostServices;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Opens approved local files through the JavaFX host environment with user-friendly error handling.
 */
public final class FxFileOpenHelper {

    private static HostServices hostServices;

    /**
     * Creates a fx file open helper from the supplied record values.
     */
    private FxFileOpenHelper() {
    }

    /**
     * Registers host services for later application use.
     */
    public static void registerHostServices(HostServices services) {
        hostServices = services;
    }

    /**
     * Opens open for the selected record.
     */
    public static String open(Path path) {
        if (path == null) {
            throw new IllegalArgumentException("Path is required.");
        }
        Path safePath = path.toAbsolutePath().normalize();
        if (!Files.exists(safePath)) {
            throw new IllegalArgumentException("File does not exist: " + safePath);
        }
        if (hostServices == null) {
            throw new UnsupportedOperationException("JavaFX file opening is not ready. Safe path: " + safePath);
        }
        hostServices.showDocument(safePath.toUri().toString());
        return "Opened local file through JavaFX: " + safePath;
    }
}
