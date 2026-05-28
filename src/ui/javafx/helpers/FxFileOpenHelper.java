package ui.javafx.helpers;

import javafx.application.HostServices;

import java.nio.file.Files;
import java.nio.file.Path;

public final class FxFileOpenHelper {

    private static HostServices hostServices;

    private FxFileOpenHelper() {
    }

    public static void registerHostServices(HostServices services) {
        hostServices = services;
    }

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
