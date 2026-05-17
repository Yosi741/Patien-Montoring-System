package ui.javafx.controllers;

import dao.SqliteDeviceDao;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Window;
import services.DeviceWriteService;
import ui.javafx.AppNavigator;
import ui.javafx.helpers.NotificationHelper;
import users.User;

public class DeviceAssignmentController {

    private final DeviceWriteService deviceWriteService = new DeviceWriteService();
    private User currentUser;
    private SqliteDeviceDao.DeviceRecord device;
    private boolean saved;

    @FXML private Label deviceLabel;
    @FXML private TextField patientIdField;
    @FXML private Label statusLabel;

    public static boolean showDialog(Window owner, User currentUser, SqliteDeviceDao.DeviceRecord device, String patientId) {
        try {
            FXMLLoader loader = new FXMLLoader(AppNavigator.resolve("/ui/javafx/views/DeviceAssignmentView.fxml"));
            Parent root = loader.load();
            DeviceAssignmentController controller = loader.getController();
            controller.prepare(currentUser, device, patientId);

            ButtonType assignButtonType = new ButtonType("Assign", ButtonBar.ButtonData.OK_DONE);
            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setTitle("Assign Device");
            dialog.initOwner(owner);
            dialog.getDialogPane().setContent(root);
            dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, assignButtonType);
            dialog.getDialogPane().lookupButton(assignButtonType).addEventFilter(ActionEvent.ACTION, event -> {
                if (!controller.assign()) {
                    event.consume();
                }
            });
            dialog.showAndWait();
            return controller.saved;
        } catch (Exception e) {
            throw new IllegalStateException("Could not open device assignment form: " + e.getMessage(), e);
        }
    }

    private void prepare(User currentUser, SqliteDeviceDao.DeviceRecord device, String patientId) {
        this.currentUser = currentUser;
        this.device = device;
        deviceLabel.setText(device == null ? "No device selected" : device.getDeviceId() + " | " + device.getName());
        patientIdField.setText(patientId == null ? "" : patientId);
        NotificationHelper.showInfo(statusLabel, "Only AVAILABLE devices can be assigned.");
    }

    private boolean assign() {
        try {
            if (device == null) {
                throw new IllegalArgumentException("Select a device first.");
            }
            deviceWriteService.assignDeviceToPatient(currentUser, device.getDeviceId(), patientIdField.getText());
            saved = true;
            return true;
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
            return false;
        }
    }
}
