package app.helpers;

import javafx.scene.control.Dialog;
import app.navigation.AppNavigator;
import app.core.AppShell;

/**
 * Applies the active ClinicPulse stylesheet and window behavior to JavaFX dialogs.
 */
public final class DialogThemeHelper {


    /**
     * Applies apply to the current control or record.
     */
    public static void apply(Dialog<?> dialog) {
        if (dialog == null || dialog.getDialogPane() == null) {
            return;
        }
        dialog.getDialogPane().getStyleClass().add("dialog-root");
        dialog.getDialogPane().getStylesheets().clear();
        dialog.getDialogPane().getStylesheets().add(AppNavigator.resolve(AppShell.getActiveThemePath()).toExternalForm());
    }
}
