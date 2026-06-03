package ui.javafx.controllers;

import dao.SqliteMedicationDao;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Window;
import services.MedicationWriteService;
import ui.javafx.AppNavigator;
import ui.javafx.helpers.NotificationHelper;
import users.User;

public class MedicationFormController {

    private final MedicationWriteService medicationWriteService = new MedicationWriteService();
    private User currentUser;
    private String patientId;
    private SqliteMedicationDao.MedicationRecord existingMedication;
    private boolean saved;

    @FXML private Label titleLabel;
    @FXML private Label patientIdLabel;
    @FXML private TextField nameField;
    @FXML private TextField doseField;
    @FXML private ComboBox<String> routeField;
    @FXML private ComboBox<String> frequencyField;
    @FXML private CheckBox activeCheckBox;
    @FXML private Label statusLabel;

    public static boolean showCreateDialog(Window owner, User currentUser, String patientId) {
        return showDialog(owner, currentUser, patientId, null);
    }

    public static boolean showEditDialog(Window owner, User currentUser, SqliteMedicationDao.MedicationRecord medication) {
        return showDialog(owner, currentUser, medication == null ? "" : medication.getPatientId(), medication);
    }

    private static boolean showDialog(Window owner, User currentUser, String patientId, SqliteMedicationDao.MedicationRecord medication) {
        try {
            FXMLLoader loader = new FXMLLoader(AppNavigator.resolve("/ui/javafx/views/MedicationFormView.fxml"));
            Parent root = loader.load();
            MedicationFormController controller = loader.getController();
            controller.prepare(currentUser, patientId, medication);

            ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setTitle(medication == null ? "Add Medication" : "Edit Medication");
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
            throw new IllegalStateException("Could not open medication form: " + e.getMessage(), e);
        }
    }

    @FXML
    private void initialize() {
        routeField.getItems().setAll("Oral", "IV", "IM", "SC", "Inhalation", "Topical", "Other");
        frequencyField.getItems().setAll("Once daily", "Twice daily", "Three times daily", "Every 6 hours",
                "Every 8 hours", "Every 12 hours", "Weekly", "As needed", "Other");
        routeField.getSelectionModel().select("Oral");
        frequencyField.getSelectionModel().select("Once daily");
        activeCheckBox.setSelected(true);
        NotificationHelper.showInfo(statusLabel, "Medication form. System data is stored in the local database.");
    }

    private void prepare(User currentUser, String patientId, SqliteMedicationDao.MedicationRecord medication) {
        this.currentUser = currentUser;
        this.patientId = patientId == null ? "" : patientId;
        this.existingMedication = medication;
        patientIdLabel.setText(this.patientId);
        if (medication == null) {
            titleLabel.setText("Add Medication");
            return;
        }

        titleLabel.setText("Edit Medication");
        nameField.setText(medication.getName());
        doseField.setText(medication.getDose());
        selectOrFallback(routeField, medication.getRoute());
        selectOrFallback(frequencyField, medication.getFrequency());
        activeCheckBox.setSelected(medication.isActive());
    }

    private boolean save() {
        try {
            MedicationWriteService.MedicationRequest request = new MedicationWriteService.MedicationRequest(
                    existingMedication == null ? 0 : existingMedication.getId(),
                    patientId,
                    nameField.getText(),
                    doseField.getText(),
                    routeField.getValue(),
                    frequencyField.getValue(),
                    activeCheckBox.isSelected()
            );
            if (existingMedication == null) {
                medicationWriteService.addMedication(currentUser, request);
            } else {
                medicationWriteService.updateMedication(currentUser, request);
            }
            saved = true;
            return true;
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
            return false;
        }
    }

    private void selectOrFallback(ComboBox<String> comboBox, String value) {
        if (value != null && comboBox.getItems().contains(value)) {
            comboBox.getSelectionModel().select(value);
        } else if (!comboBox.getItems().isEmpty()) {
            comboBox.getSelectionModel().selectFirst();
        }
    }
}
