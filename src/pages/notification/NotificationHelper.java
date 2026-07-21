package pages.notification;

import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;

/**
 * Displays consistent inline success, error, and informational status messages in JavaFX pages.
 */
public final class NotificationHelper {

    /**
     * Creates a notification helper from the supplied record values.
     */
    private NotificationHelper() {
    }

    /**
     * Displays success to the user.
     */
    public static void showSuccess(Label label, String message) {
        showStatus(label, message, "status-success");
    }

    /**
     * Displays error to the user.
     */
    public static void showError(Label label, String message) {
        showStatus(label, message, "status-error");
    }

    /**
     * Displays info to the user.
     */
    public static void showInfo(Label label, String message) {
        showStatus(label, message, "status-info");
    }


    /**
     * Displays status to the user.
     */
    private static void showStatus(Label label, String message, String styleClass) {
        if (label == null) {
            return;
        }
        label.setText(message == null ? "" : message);
        label.setWrapText(true);
        label.setTooltip(message == null || message.isBlank() ? null : new Tooltip(message));
        label.getStyleClass().removeAll("status-success", "status-error", "status-info");
        label.getStyleClass().add(styleClass);
    }
}
