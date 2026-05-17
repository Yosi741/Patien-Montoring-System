package ui.javafx.controllers;

import dao.SqlitePatientDao;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Window;
import services.PatientWriteService;
import ui.javafx.AppNavigator;
import ui.javafx.helpers.NotificationHelper;
import users.User;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class PatientFormController {

    private static final DateTimeFormatter LEGACY_DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private final PatientWriteService patientWriteService = new PatientWriteService();
    private User currentUser;
    private SqlitePatientDao.PatientDetail existingPatient;
    private boolean saved;

    @FXML private Label titleLabel;
    @FXML private Label helpLabel;
    @FXML private TextField patientIdField;
    @FXML private TextField firstNameField;
    @FXML private TextField lastNameField;
    @FXML private DatePicker birthDatePicker;
    @FXML private ComboBox<String> genderBox;
    @FXML private TextField sectionField;
    @FXML private TextField roomField;
    @FXML private ComboBox<String> statusBox;
    @FXML private ComboBox<String> priorityBox;
    @FXML private TextArea diagnosisArea;
    @FXML private Label statusLabel;

    public static boolean showCreateDialog(Window owner, User currentUser) {
        return showDialog(owner, currentUser, null);
    }

    public static boolean showEditDialog(Window owner, User currentUser, SqlitePatientDao.PatientDetail patient) {
        return showDialog(owner, currentUser, patient);
    }

    private static boolean showDialog(Window owner, User currentUser, SqlitePatientDao.PatientDetail patient) {
        try {
            FXMLLoader loader = new FXMLLoader(AppNavigator.resolve("/ui/javafx/views/PatientFormView.fxml"));
            Parent root = loader.load();
            PatientFormController controller = loader.getController();
            controller.prepare(currentUser, patient);

            ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setTitle(patient == null ? "Add Patient" : "Edit Patient");
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
            throw new IllegalStateException("Could not open patient form: " + e.getMessage(), e);
        }
    }

    @FXML
    private void initialize() {
        genderBox.getItems().setAll("Female", "Male", "Other", "Unknown");
        statusBox.getItems().setAll("ACTIVE", "DISCHARGED", "DECEASED");
        priorityBox.getItems().setAll("NORMAL", "HIGH", "CRITICAL", "EMERGENCY");
        genderBox.getSelectionModel().select("Unknown");
        statusBox.getSelectionModel().select("ACTIVE");
        priorityBox.getSelectionModel().select("NORMAL");
        NotificationHelper.showInfo(statusLabel, "SQLite-only patient write. Legacy text files are not changed.");
    }

    private void prepare(User currentUser, SqlitePatientDao.PatientDetail patient) {
        this.currentUser = currentUser;
        this.existingPatient = patient;
        if (patient == null) {
            titleLabel.setText("Add Patient");
            helpLabel.setText("Create a SQLite-only patient record for the JavaFX preview.");
            return;
        }

        titleLabel.setText("Edit Patient");
        helpLabel.setText("Update this SQLite patient record only. Legacy text-file storage is unchanged.");
        patientIdField.setText(patient.getPatientId());
        patientIdField.setDisable(true);
        firstNameField.setText(patient.getFirstName());
        lastNameField.setText(patient.getLastName());
        birthDatePicker.setValue(parseBirthDate(patient.getBirthDate()));
        genderBox.getSelectionModel().select(blankTo(patient.getGender(), "Unknown"));
        sectionField.setText(patient.getSection());
        roomField.setText(patient.getRoom());
        statusBox.getSelectionModel().select(normalizeStatus(patient.getStatus()));
        priorityBox.getSelectionModel().select(normalizePriority(patient.getPriority()));
        diagnosisArea.setText(patient.getDiagnosis());
    }

    private boolean save() {
        try {
            SqlitePatientDao.PatientWriteRecord record = buildRecord();
            if (existingPatient == null) {
                patientWriteService.createPatient(currentUser, record);
            } else {
                patientWriteService.updatePatient(currentUser, record);
            }
            saved = true;
            return true;
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
            return false;
        }
    }

    private SqlitePatientDao.PatientWriteRecord buildRecord() {
        return new SqlitePatientDao.PatientWriteRecord(
                patientIdField.getText(),
                firstNameField.getText(),
                lastNameField.getText(),
                birthDatePicker.getValue() == null ? "" : birthDatePicker.getValue().format(LEGACY_DATE),
                genderBox.getValue(),
                sectionField.getText(),
                roomField.getText(),
                statusBox.getValue(),
                priorityBox.getValue(),
                diagnosisArea.getText()
        );
    }

    private LocalDate parseBirthDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim(), LEGACY_DATE);
        } catch (DateTimeParseException e) {
            try {
                return LocalDate.parse(value.trim());
            } catch (DateTimeParseException ignored) {
                return null;
            }
        }
    }

    private String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return "ACTIVE";
        }
        return "Active".equalsIgnoreCase(status) ? "ACTIVE" : status.toUpperCase();
    }

    private String normalizePriority(String priority) {
        if (priority == null || priority.isBlank()) {
            return "NORMAL";
        }
        return "WARNING".equalsIgnoreCase(priority) ? "HIGH" : priority.toUpperCase();
    }
}
