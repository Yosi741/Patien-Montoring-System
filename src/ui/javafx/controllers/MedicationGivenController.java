package ui.javafx.controllers;

import dao.SqliteMedicationDao;
import javafx.collections.FXCollections;
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
import services.MedicationWriteService;
import ui.javafx.AppNavigator;
import ui.javafx.SessionContext;
import ui.javafx.helpers.NotificationHelper;
import users.User;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class MedicationGivenController {

    private static final DateTimeFormatter LEGACY_DATE_TIME = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

    private final MedicationWriteService medicationWriteService = new MedicationWriteService();
    private User currentUser;
    private String patientId;
    private boolean saved;

    @FXML private Label patientIdLabel;
    @FXML private ComboBox<SqliteMedicationDao.MedicationRecord> medicationBox;
    @FXML private TextField givenByField;
    @FXML private TextField givenAtField;
    @FXML private ComboBox<String> statusBox;
    @FXML private TextArea notesArea;
    @FXML private Label statusLabel;

    public static boolean showDialog(Window owner, User currentUser, String patientId, SqliteMedicationDao.MedicationRecord selectedMedication) {
        try {
            FXMLLoader loader = new FXMLLoader(AppNavigator.resolve("/ui/javafx/views/MedicationGivenView.fxml"));
            Parent root = loader.load();
            MedicationGivenController controller = loader.getController();
            controller.prepare(currentUser, patientId, selectedMedication);

            ButtonType saveButtonType = new ButtonType("Record", ButtonBar.ButtonData.OK_DONE);
            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setTitle("Record Medication Given");
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
            throw new IllegalStateException("Could not open medication administration form: " + e.getMessage(), e);
        }
    }

    @FXML
    private void initialize() {
        statusBox.setItems(FXCollections.observableArrayList("GIVEN", "MISSED", "DELAYED"));
        statusBox.getSelectionModel().select("GIVEN");
        givenByField.setText(SessionContext.username());
        givenByField.setEditable(false);
        givenAtField.setText(LocalDateTime.now().format(LEGACY_DATE_TIME));
        NotificationHelper.showInfo(statusLabel, "Local database medication event. System data is stored in the local database.");
    }

    private void prepare(User currentUser, String patientId, SqliteMedicationDao.MedicationRecord selectedMedication) throws Exception {
        this.currentUser = currentUser;
        this.patientId = patientId == null ? "" : patientId;
        patientIdLabel.setText(this.patientId);
        givenByField.setText(currentUser == null ? SessionContext.username() : currentUser.getUsername());

        List<SqliteMedicationDao.MedicationRecord> activeMedications = medicationWriteService.findActiveMedicationsForPatient(this.patientId);
        medicationBox.setItems(FXCollections.observableArrayList(activeMedications));
        if (selectedMedication != null && selectedMedication.isActive()) {
            medicationBox.getSelectionModel().select(selectedMedication);
        } else if (!activeMedications.isEmpty()) {
            medicationBox.getSelectionModel().selectFirst();
        }
        if (activeMedications.isEmpty()) {
            NotificationHelper.showError(statusLabel, "No active medications found for this patient.");
        }
    }

    private boolean save() {
        try {
            SqliteMedicationDao.MedicationRecord medication = medicationBox.getValue();
            if (medication == null) {
                throw new IllegalArgumentException("Select an active medication first.");
            }
            medicationWriteService.recordMedicationGiven(currentUser, new MedicationWriteService.MedicationEventRequest(
                    medication.getId(),
                    givenAtField.getText(),
                    statusBox.getValue(),
                    notesArea.getText()
            ));
            saved = true;
            return true;
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
            return false;
        }
    }
}
