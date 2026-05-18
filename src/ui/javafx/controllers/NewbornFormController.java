package ui.javafx.controllers;

import dao.SqliteNewbornRecordDao;
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
import services.NewbornService;
import ui.javafx.AppNavigator;
import ui.javafx.helpers.NotificationHelper;
import users.User;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class NewbornFormController {

    private static final DateTimeFormatter SQLITE_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final NewbornService newbornService = new NewbornService();
    private User currentUser;
    private SqliteNewbornRecordDao.NewbornRecord existingRecord;
    private String motherPatientId;
    private boolean saved;

    @FXML private Label titleLabel;
    @FXML private TextField newbornIdField;
    @FXML private TextField babyNameField;
    @FXML private ComboBox<String> genderBox;
    @FXML private TextField birthTimeField;
    @FXML private TextField birthWeightField;
    @FXML private TextField birthLengthField;
    @FXML private TextField motherPatientIdField;
    @FXML private TextField motherNameField;
    @FXML private TextField fatherNameField;
    @FXML private ComboBox<String> deliveryTypeBox;
    @FXML private TextField roomField;
    @FXML private TextField sectionField;
    @FXML private TextField doctorField;
    @FXML private TextArea notesArea;
    @FXML private Label statusLabel;

    public static boolean showCreateDialog(Window owner, User currentUser, String motherPatientId) {
        return showDialog(owner, currentUser, motherPatientId, null);
    }

    public static boolean showEditDialog(Window owner, User currentUser, SqliteNewbornRecordDao.NewbornRecord record) {
        return showDialog(owner, currentUser, record == null ? "" : record.getMotherPatientId(), record);
    }

    private static boolean showDialog(Window owner, User currentUser, String motherPatientId, SqliteNewbornRecordDao.NewbornRecord record) {
        try {
            FXMLLoader loader = new FXMLLoader(AppNavigator.resolve("/ui/javafx/views/NewbornFormView.fxml"));
            Parent root = loader.load();
            NewbornFormController controller = loader.getController();
            controller.prepare(currentUser, motherPatientId, record);

            ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setTitle(record == null ? "Create Newborn Record" : "Update Newborn Record");
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
            throw new IllegalStateException("Could not open newborn form: " + e.getMessage(), e);
        }
    }

    @FXML
    private void initialize() {
        genderBox.getItems().setAll("MALE", "FEMALE", "UNKNOWN");
        genderBox.getSelectionModel().select("UNKNOWN");
        deliveryTypeBox.getItems().setAll("NATURAL", "C_SECTION", "ASSISTED", "UNKNOWN");
        deliveryTypeBox.getSelectionModel().select("UNKNOWN");
        birthTimeField.setText(LocalDateTime.now().format(SQLITE_DATE_TIME));
        NotificationHelper.showInfo(statusLabel, "SQLite-only newborn workflow. Legacy text files are unchanged.");
    }

    private void prepare(User currentUser, String motherPatientId, SqliteNewbornRecordDao.NewbornRecord record) {
        this.currentUser = currentUser;
        this.existingRecord = record;
        this.motherPatientId = motherPatientId == null ? "" : motherPatientId;
        motherPatientIdField.setText(this.motherPatientId);
        if (record == null) {
            titleLabel.setText("Create Newborn Record");
            doctorField.setText(currentUser == null ? "" : currentUser.getUsername());
            return;
        }
        titleLabel.setText("Update Newborn Record");
        newbornIdField.setText(record.getNewbornId());
        newbornIdField.setDisable(true);
        babyNameField.setText(record.getBabyName());
        genderBox.getSelectionModel().select(record.getGender());
        birthTimeField.setText(record.getBirthTime());
        birthWeightField.setText(String.valueOf(record.getBirthWeight()));
        birthLengthField.setText(record.getBirthLength() == null ? "" : String.valueOf(record.getBirthLength()));
        motherPatientIdField.setText(record.getMotherPatientId());
        motherNameField.setText(record.getMotherName());
        fatherNameField.setText(record.getFatherName());
        deliveryTypeBox.getSelectionModel().select(record.getDeliveryType());
        roomField.setText(record.getRoom());
        sectionField.setText(record.getSection());
        doctorField.setText(record.getDoctorOrMidwife());
        notesArea.setText(record.getNotes());
    }

    private boolean save() {
        try {
            NewbornService.NewbornRecordRequest request = new NewbornService.NewbornRecordRequest(
                    newbornIdField.getText(),
                    babyNameField.getText(),
                    genderBox.getValue(),
                    birthTimeField.getText(),
                    birthWeightField.getText(),
                    birthLengthField.getText(),
                    motherPatientIdField.getText(),
                    motherNameField.getText(),
                    fatherNameField.getText(),
                    deliveryTypeBox.getValue(),
                    roomField.getText(),
                    sectionField.getText(),
                    doctorField.getText(),
                    notesArea.getText()
            );
            if (existingRecord == null) {
                newbornService.createNewbornRecord(currentUser, request);
            } else {
                newbornService.updateNewbornRecord(currentUser, request);
            }
            saved = true;
            return true;
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
            return false;
        }
    }
}
