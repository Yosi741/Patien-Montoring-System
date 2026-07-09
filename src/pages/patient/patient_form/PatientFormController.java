package pages.patient.patient_form;

import pages.patient.dao.SqlitePatientDao;
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
import pages.patient.services.PatientWriteService;
import app.AppNavigator;
import app.helpers.DatePickerHelper;
import app.helpers.DialogHelper;
import pages.notification.NotificationHelper;
import pages.user.User;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class PatientFormController {

    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private final SqlitePatientDao patientDao = new SqlitePatientDao();
    private final PatientWriteService patientWriteService = new PatientWriteService();
    private User currentUser;
    private SqlitePatientDao.PatientDetail existingPatient;
    private boolean returningPatientMode;
    private boolean saved;

    @FXML private Label titleLabel;
    @FXML private Label helpLabel;
    @FXML private TextField patientIdField;
    @FXML private javafx.scene.control.Button checkPatientIdButton;
    @FXML private TextField firstNameField;
    @FXML private TextField lastNameField;
    @FXML private DatePicker birthDatePicker;
    @FXML private ComboBox<String> genderBox;
    @FXML private ComboBox<String> statusBox;
    @FXML private ComboBox<String> priorityBox;
    @FXML private ComboBox<String> bloodTypeBox;
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
            FXMLLoader loader = new FXMLLoader(AppNavigator.resolve("/pages/patient/patient_form/PatientFormView.fxml"));
            Parent root = loader.load();
            PatientFormController controller = loader.getController();
            controller.prepare(currentUser, patient);

            ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setTitle(patient == null ? "Add Patient" : "Edit Patient");
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
            throw new IllegalStateException("Could not open patient form: " + e.getMessage(), e);
        }
    }

    @FXML
    private void initialize() {
        genderBox.getItems().setAll("Female", "Male", "Other", "Unknown");
        statusBox.getItems().setAll("ACTIVE", "DISCHARGED", "DECEASED");
        priorityBox.getItems().setAll("NORMAL", "HIGH", "CRITICAL", "EMERGENCY");
        bloodTypeBox.getItems().setAll("Unknown", "A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-");
        genderBox.getSelectionModel().select("Unknown");
        statusBox.getSelectionModel().select("ACTIVE");
        priorityBox.getSelectionModel().select("NORMAL");
        bloodTypeBox.getSelectionModel().select("Unknown");
        configureInputFilters();
        DatePickerHelper.configureDdMmYyyy(birthDatePicker);
        NotificationHelper.showInfo(statusLabel, "Patient file form. System data is stored in the local clinic database.");
    }

    private void prepare(User currentUser, SqlitePatientDao.PatientDetail patient) {
        this.currentUser = currentUser;
        this.existingPatient = patient;
        this.returningPatientMode = false;
        if (patient == null) {
            titleLabel.setText("Add Patient");
            helpLabel.setText("Create a patient record in the local clinic database.");
            patientIdField.setDisable(false);
            setCheckIdVisible(true);
            return;
        }

        titleLabel.setText("Edit Patient");
        helpLabel.setText("Update this patient record only. System data is stored in the local clinic database.");
        populateFromPatientDetail(patient, false);
        setCheckIdVisible(false);
    }

    private boolean save() {
        try {
            DatePickerHelper.commitEditorText(birthDatePicker);
            SqlitePatientDao.PatientWriteRecord record = buildRecord();
            if (returningPatientMode) {
                patientWriteService.reactivateReturningPatient(currentUser, record);
            } else if (existingPatient == null) {
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
                birthDatePicker.getValue() == null ? "" : birthDatePicker.getValue().format(DISPLAY_DATE),
                genderBox.getValue(),
                resolvedSection(),
                resolvedRoom(),
                statusBox.getValue(),
                priorityBox.getValue(),
                bloodTypeBox.getValue(),
                diagnosisArea.getText(),
                resolvedAssignedDoctor(),
                resolvedAssignedStaff()
        );
    }

    private void configureInputFilters() {
        patientIdField.textProperty().addListener((observable, oldValue, newValue) -> {
            String clean = digitsOnly(newValue);
            if (clean.length() > 9) {
                clean = clean.substring(0, 9);
            }
            if (!clean.equals(newValue)) {
                patientIdField.setText(clean);
            }
        });
        installNameFilter(firstNameField);
        installNameFilter(lastNameField);
    }

    @FXML
    private void checkExistingPatientId() {
        if (existingPatient != null && !returningPatientMode) {
            return;
        }
        String patientId = patientIdField.getText() == null ? "" : patientIdField.getText().trim();
        if (patientId.isBlank()) {
            NotificationHelper.showError(statusLabel, "Patient ID is required.");
            return;
        }
        if (!patientId.matches("\\d{9}")) {
            NotificationHelper.showError(statusLabel, "Patient ID must contain only digits and exactly 9 digits.");
            return;
        }
        try {
            SqlitePatientDao.PatientDetail detail = patientDao.findDetailById(patientId).orElse(null);
            if (detail == null) {
                NotificationHelper.showInfo(statusLabel, "No existing patient file was found for this ID.");
                return;
            }
            boolean confirmed = DialogHelper.confirm(
                    "Existing Patient File",
                    "A patient file already exists for this ID. Do you want to load the existing profile and create a new visit?");
            if (!confirmed) {
                patientIdField.clear();
                patientIdField.requestFocus();
                NotificationHelper.showInfo(statusLabel, "Patient ID cleared. Enter a different ID or continue with a new patient.");
                return;
            }
            existingPatient = detail;
            returningPatientMode = true;
            titleLabel.setText("Returning Patient");
            helpLabel.setText("Existing patient file loaded. Review the profile and save to create a new clinic visit.");
            populateFromPatientDetail(detail, true);
            NotificationHelper.showSuccess(statusLabel, "Existing patient file loaded. Save to create or resume the clinic visit.");
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
        }
    }

    private void installNameFilter(TextField field) {
        field.textProperty().addListener((observable, oldValue, newValue) -> {
            String clean = cleanName(newValue);
            if (!clean.equals(newValue)) {
                field.setText(clean);
            }
        });
    }

    private String digitsOnly(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }

    private String cleanName(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (Character.isLetter(ch) || ch == ' ' || ch == '-' || ch == '\'' || ch == '\u2019') {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    private void populateFromPatientDetail(SqlitePatientDao.PatientDetail patient, boolean returningVisit) {
        patientIdField.setText(patient.getPatientId());
        patientIdField.setDisable(true);
        firstNameField.setText(patient.getFirstName());
        lastNameField.setText(patient.getLastName());
        birthDatePicker.setValue(parseBirthDate(patient.getBirthDate()));
        genderBox.getSelectionModel().select(blankTo(patient.getGender(), "Unknown"));
        statusBox.getSelectionModel().select(returningVisit ? "ACTIVE" : normalizeStatus(patient.getStatus()));
        priorityBox.getSelectionModel().select(normalizePriority(patient.getPriority()));
        bloodTypeBox.getSelectionModel().select(normalizeBloodType(patient.getBloodType()));
        diagnosisArea.setText(returningVisit ? "" : patient.getDiagnosis());
        setCheckIdVisible(!returningVisit);
    }

    private void setCheckIdVisible(boolean visible) {
        if (checkPatientIdButton != null) {
            checkPatientIdButton.setVisible(visible);
            checkPatientIdButton.setManaged(visible);
        }
    }

    private String resolvedSection() {
        return existingPatient == null ? "" : blankTo(existingPatient.getSection(), "");
    }

    private String resolvedRoom() {
        return existingPatient == null ? "" : blankTo(existingPatient.getRoom(), "");
    }

    private String resolvedAssignedDoctor() {
        return existingPatient == null ? "" : blankTo(existingPatient.getAssignedDoctorUsername(), "");
    }

    private String resolvedAssignedStaff() {
        return existingPatient == null ? "" : blankTo(existingPatient.getAssignedStaffUsername(), "");
    }

    private LocalDate parseBirthDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim(), DISPLAY_DATE);
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

    private String normalizeBloodType(String bloodType) {
        if (bloodType == null || bloodType.isBlank()) {
            return "Unknown";
        }
        String normalized = bloodType.trim();
        return "UNKNOWN".equalsIgnoreCase(normalized) ? "Unknown" : normalized.toUpperCase();
    }
}
