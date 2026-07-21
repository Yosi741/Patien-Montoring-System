package app.helpers;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

import java.util.Optional;

/**
 * Provides shared confirmation and message dialogs for ClinicPulse workflows.
 */
public final class DialogHelper {

    /**
     * Creates a dialog helper from the supplied record values.
     */
    private DialogHelper() {
    }

    /**
     * Displays a info dialog using the shared ClinicPulse styling.
     */
    public static void info(String title, String message) {
        show(Alert.AlertType.INFORMATION, title, message);
    }

    /**
     * Displays a warning dialog using the shared ClinicPulse styling.
     */
    public static void warning(String title, String message) {
        show(Alert.AlertType.WARNING, title, message);
    }

    /**
     * Displays a error dialog using the shared ClinicPulse styling.
     */
    public static void error(String title, String message) {
        show(Alert.AlertType.ERROR, title, message);
    }

    /**
     * Displays a confirmation dialog and returns the user's choice.
     */
    public static boolean confirm(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        DialogThemeHelper.apply(alert);
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    /**
     * Displays show to the user.
     */
    private static void show(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        DialogThemeHelper.apply(alert);
        alert.showAndWait();
    }
}
