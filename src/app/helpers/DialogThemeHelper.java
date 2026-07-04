package app.helpers;

import javafx.scene.control.Dialog;
import app.AppNavigator;
import app.AppShell;

public final class DialogThemeHelper {

    private DialogThemeHelper() {
    }

    public static void apply(Dialog<?> dialog) {
        if (dialog == null || dialog.getDialogPane() == null) {
            return;
        }
        dialog.getDialogPane().getStyleClass().add("dialog-root");
        dialog.getDialogPane().getStylesheets().clear();
        dialog.getDialogPane().getStylesheets().add(AppNavigator.resolve(AppShell.PRESENTATION_THEME).toExternalForm());
    }
}
