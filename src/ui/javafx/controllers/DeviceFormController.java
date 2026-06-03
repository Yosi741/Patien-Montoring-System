package ui.javafx.controllers;

import dao.SqliteDeviceDao;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Window;
import services.DeviceWriteService;
import ui.javafx.AppNavigator;
import ui.javafx.helpers.NotificationHelper;
import users.User;

public class DeviceFormController {

    private final DeviceWriteService deviceWriteService = new DeviceWriteService();
    private User currentUser;
    private SqliteDeviceDao.DeviceRecord existingDevice;
    private boolean saved;

    @FXML private Label titleLabel;
    @FXML private TextField deviceIdField;
    @FXML private TextField nameField;
    @FXML private ComboBox<String> typeBox;
    @FXML private TextField serialField;
    @FXML private ComboBox<String> statusBox;
    @FXML private TextArea notesArea;
    @FXML private Label statusLabel;

    public static boolean showCreateDialog(Window owner, User currentUser) {
        return showDialog(owner, currentUser, null);
    }

    public static boolean showEditDialog(Window owner, User currentUser, SqliteDeviceDao.DeviceRecord device) {
        return showDialog(owner, currentUser, device);
    }

    private static boolean showDialog(Window owner, User currentUser, SqliteDeviceDao.DeviceRecord device) {
        try {
            FXMLLoader loader = new FXMLLoader(AppNavigator.resolve("/ui/javafx/views/DeviceFormView.fxml"));
            Parent root = loader.load();
            DeviceFormController controller = loader.getController();
            controller.prepare(currentUser, device);

            ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setTitle(device == null ? "Register Device" : "Edit Device");
            dialog.initOwner(owner);
            dialog.getDialogPane().setContent(root);
            dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, saveButtonType);
            dialog.getDialogPane().lookupButton(saveButtonType).addEventFilter(ActionEvent.ACTION, event -> {
                if (!controller.save()) {
                    event.consume();
                }
            });
            dialog.showAndWait();
            return controller.saved;
        } catch (Exception e) {
            throw new IllegalStateException("Could not open device form: " + e.getMessage(), e);
        }
    }

    @FXML
    private void initialize() {
        typeBox.getItems().setAll("Watch", "Blood Pressure Monitor", "Oximeter", "Thermometer", "Glucose Meter", "Other");
        statusBox.getItems().setAll("AVAILABLE", "ASSIGNED", "MAINTENANCE", "INACTIVE");
        typeBox.getSelectionModel().select("Other");
        statusBox.getSelectionModel().select("AVAILABLE");
        NotificationHelper.showInfo(statusLabel, "Registration only. Real device integration is future work.");
    }

    private void prepare(User currentUser, SqliteDeviceDao.DeviceRecord device) {
        this.currentUser = currentUser;
        this.existingDevice = device;
        if (device == null) {
            titleLabel.setText("Register Device");
            return;
        }
        titleLabel.setText("Edit Device");
        deviceIdField.setText(device.getDeviceId());
        deviceIdField.setDisable(true);
        nameField.setText(device.getName());
        typeBox.getSelectionModel().select(device.getType());
        serialField.setText(device.getSerial());
        statusBox.getSelectionModel().select(device.getStatus());
        notesArea.setText(device.getNotes());
    }

    private boolean save() {
        try {
            DeviceWriteService.DeviceRequest request = new DeviceWriteService.DeviceRequest(
                    deviceIdField.getText(),
                    nameField.getText(),
                    typeBox.getValue(),
                    serialField.getText(),
                    statusBox.getValue(),
                    existingDevice == null ? "" : existingDevice.getPatientId(),
                    notesArea.getText()
            );
            if (existingDevice == null) {
                deviceWriteService.registerDevice(currentUser, request);
            } else {
                deviceWriteService.updateDevice(currentUser, request);
            }
            saved = true;
            return true;
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
            return false;
        }
    }
}
