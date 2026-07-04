package ui.javafx.pages.certificates;

import ui.javafx.pages.deceased.SqliteDeceasedRecordDao;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Window;
import ui.javafx.pages.deceased.DeceasedPatientService;
import app.AppNavigator;
import ui.javafx.pages.notifications.NotificationHelper;
import pages.user.User;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class DeathRecordFormController {

    private static final DateTimeFormatter SQLITE_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final DeceasedPatientService deceasedPatientService = new DeceasedPatientService();
    private User currentUser;
    private String patientId;
    private SqliteDeceasedRecordDao.DeathRecord existingRecord;
    private boolean saved;

    @FXML private Label titleLabel;
    @FXML private TextField patientIdField;
    @FXML private TextField deathTimeField;
    @FXML private TextField pronouncedByField;
    @FXML private TextField causeField;
    @FXML private TextArea notesArea;
    @FXML private Label statusLabel;

    public static boolean showMarkDialog(Window owner, User currentUser, String patientId) {
        return showDialog(owner, currentUser, patientId, null);
    }

    public static boolean showEditDialog(Window owner, User currentUser, SqliteDeceasedRecordDao.DeathRecord record) {
        return showDialog(owner, currentUser, record == null ? "" : record.getPatientId(), record);
    }

    private static boolean showDialog(Window owner, User currentUser, String patientId, SqliteDeceasedRecordDao.DeathRecord record) {
        try {
            FXMLLoader loader = new FXMLLoader(AppNavigator.resolve("/ui/javafx/pages/certificates/DeathRecordFormView.fxml"));
            Parent root = loader.load();
            DeathRecordFormController controller = loader.getController();
            controller.prepare(currentUser, patientId, record);

            ButtonType saveButtonType = new ButtonType(record == null ? "Mark Deceased" : "Save", ButtonBar.ButtonData.OK_DONE);
            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setTitle(record == null ? "Mark Patient Deceased" : "Update Death Record");
            app.helpers.DialogThemeHelper.apply(dialog);
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
            throw new IllegalStateException("Could not open death record form: " + e.getMessage(), e);
        }
    }

    @FXML
    private void initialize() {
        deathTimeField.setText(LocalDateTime.now().format(SQLITE_DATE_TIME));
        NotificationHelper.showInfo(statusLabel, "Death record workflow. System data is stored in the local database.");
    }

    private void prepare(User currentUser, String patientId, SqliteDeceasedRecordDao.DeathRecord record) {
        this.currentUser = currentUser;
        this.patientId = patientId == null ? "" : patientId;
        this.existingRecord = record;
        patientIdField.setText(this.patientId);
        patientIdField.setDisable(true);
        if (record == null) {
            titleLabel.setText("Mark Patient Deceased");
            pronouncedByField.setText(currentUser == null ? "" : currentUser.getUsername());
            causeField.setText("Unknown/Pending");
            return;
        }
        titleLabel.setText("Update Death Record");
        deathTimeField.setText(record.getDeathTime());
        pronouncedByField.setText(record.getPronouncedBy());
        causeField.setText(record.getCauseOfDeath());
        notesArea.setText(record.getNotes());
    }

    private boolean save() {
        try {
            DeceasedPatientService.DeathRecordRequest request = new DeceasedPatientService.DeathRecordRequest(
                    patientIdField.getText(),
                    deathTimeField.getText(),
                    pronouncedByField.getText(),
                    causeField.getText(),
                    notesArea.getText()
            );
            if (existingRecord == null) {
                deceasedPatientService.markPatientDeceased(currentUser, request);
            } else {
                deceasedPatientService.updateDeathRecord(currentUser, existingRecord.getId(), request);
            }
            saved = true;
            return true;
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
            return false;
        }
    }
}
