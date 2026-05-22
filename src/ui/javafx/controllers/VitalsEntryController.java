package ui.javafx.controllers;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Window;
import services.VitalTypeCatalog;
import services.VitalsWriteService;
import ui.javafx.AppNavigator;
import ui.javafx.SessionContext;
import ui.javafx.helpers.NotificationHelper;
import users.User;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class VitalsEntryController {

    private static final DateTimeFormatter LEGACY_DATE_TIME = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

    private final VitalsWriteService vitalsWriteService = new VitalsWriteService();
    private User currentUser;
    private String patientId;
    private VitalsWriteService.VitalsWriteResult result;

    @FXML private Label patientIdLabel;
    @FXML private ComboBox<String> vitalTypeBox;
    @FXML private TextField valueField;
    @FXML private Label secondValueLabel;
    @FXML private TextField secondValueField;
    @FXML private TextField unitField;
    @FXML private TextField recordedAtField;
    @FXML private TextField sourceTypeField;
    @FXML private TextField staffUserField;
    @FXML private Label statusLabel;

    public static VitalsWriteService.VitalsWriteResult showDialog(Window owner, User currentUser, String patientId) {
        try {
            FXMLLoader loader = new FXMLLoader(AppNavigator.resolve("/ui/javafx/views/VitalsEntryView.fxml"));
            Parent root = loader.load();
            VitalsEntryController controller = loader.getController();
            controller.prepare(currentUser, patientId);

            ButtonType saveButtonType = new ButtonType("Save Vitals", ButtonBar.ButtonData.OK_DONE);
            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setTitle("Enter Vitals");
            dialog.initOwner(owner);
            dialog.getDialogPane().setContent(root);
            dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, saveButtonType);
            dialog.getDialogPane().lookupButton(saveButtonType).addEventFilter(ActionEvent.ACTION, event -> {
                if (!controller.save()) {
                    event.consume();
                }
            });
            dialog.showAndWait();
            return controller.result;
        } catch (Exception e) {
            throw new IllegalStateException("Could not open vitals entry form: " + e.getMessage(), e);
        }
    }

    @FXML
    private void initialize() {
        vitalTypeBox.getItems().setAll(VitalTypeCatalog.javaFxEntryTypes());
        vitalTypeBox.getSelectionModel().select(VitalTypeCatalog.HEART_RATE);
        vitalTypeBox.valueProperty().addListener((observable, oldValue, newValue) -> updateTypeFields());
        sourceTypeField.setText("Manual");
        sourceTypeField.setEditable(false);
        staffUserField.setText(SessionContext.username());
        staffUserField.setEditable(false);
        recordedAtField.setText(LocalDateTime.now().format(LEGACY_DATE_TIME));
        updateTypeFields();
        NotificationHelper.showInfo(statusLabel, "Abnormal JavaFX vitals create SQLite alerts, notifications, and a local JavaFX alarm sound.");
    }

    private void prepare(User currentUser, String patientId) {
        this.currentUser = currentUser;
        this.patientId = patientId;
        patientIdLabel.setText(patientId == null || patientId.isBlank() ? "No patient selected" : patientId);
        staffUserField.setText(currentUser == null ? SessionContext.username() : currentUser.getUsername());
    }

    private boolean save() {
        try {
            result = vitalsWriteService.enterVitalReading(currentUser, new VitalsWriteService.VitalsEntryRequest(
                    patientId,
                    vitalTypeBox.getValue(),
                    valueField.getText(),
                    secondValueField.getText(),
                    unitField.getText(),
                    recordedAtField.getText()
            ));
            return true;
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
            return false;
        }
    }

    private void updateTypeFields() {
        String type = vitalTypeBox.getValue();
        boolean bloodPressure = VitalTypeCatalog.BLOOD_PRESSURE.equals(VitalTypeCatalog.normalize(type));
        secondValueLabel.setVisible(bloodPressure);
        secondValueLabel.setManaged(bloodPressure);
        secondValueField.setVisible(bloodPressure);
        secondValueField.setManaged(bloodPressure);
        secondValueField.setPromptText(bloodPressure ? "Diastolic" : "");
        valueField.setPromptText(bloodPressure ? "Systolic" : "Value");
        unitField.setText(unitFor(type));
        unitField.setEditable(false);
    }

    private String unitFor(String type) {
        return VitalTypeCatalog.expectedUnit(type);
    }
}
