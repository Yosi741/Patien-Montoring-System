package ui.javafx.pages.notifications;

import javafx.animation.PauseTransition;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.util.Duration;

public final class NotificationHelper {

    private NotificationHelper() {
    }

    public static void showSuccess(Label label, String message) {
        showStatus(label, message, "status-success");
    }

    public static void showError(Label label, String message) {
        showStatus(label, message, "status-error");
    }

    public static void showInfo(Label label, String message) {
        showStatus(label, message, "status-info");
    }

    public static void showTemporaryInfo(Label label, String message, int seconds) {
        showInfo(label, message);
        PauseTransition pause = new PauseTransition(Duration.seconds(Math.max(1, seconds)));
        pause.setOnFinished(event -> showInfo(label, ""));
        pause.play();
    }

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
