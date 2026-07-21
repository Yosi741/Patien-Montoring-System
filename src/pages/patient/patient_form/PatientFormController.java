package pages.patient.patient_form;

import pages.patient.Add_Edit_Patient_Dao;
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
import javafx.scene.layout.VBox;
import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import javafx.stage.Window;
import pages.patient.services.PatientWriteService;
import app.navigation.AppNavigator;
import app.helpers.DatePickerHelper;
import app.helpers.DialogHelper;
import pages.notification.NotificationHelper;
import pages.user.User;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Controls PatientFormView.fxml for Add Patient, Edit Patient, and returning-patient validation workflows.
 */
public class PatientFormController {

    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final String ALLERGY_STATUS_NONE = "No allergies";
    private static final String ALLERGY_STATUS_HAS = "Has allergies";
    private static final String ALLERGY_STATUS_UNKNOWN = "Unknown";

    private final Add_Edit_Patient_Dao patientDao = new Add_Edit_Patient_Dao();
    private final PatientWriteService patientWriteService = new PatientWriteService();
    private User currentUser;
    private Add_Edit_Patient_Dao.PatientDetail existingPatient;
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
    @FXML private ComboBox<String> allergyStatusBox;
    @FXML private VBox allergyDetailsContainer;
    @FXML private TextArea allergyDetailsArea;
    @FXML private TextField phoneField;
    @FXML private TextField emailField;
    @FXML private TextArea addressArea;
    @FXML private TextField emergencyContactNameField;
    @FXML private TextField emergencyContactPhoneField;
    @FXML private TextArea diagnosisArea;
    @FXML private Label statusLabel;

    /**
     * Displays create dialog to the user.
     */
    public static boolean showCreateDialog(Window owner, User currentUser) {
        return showDialog(owner, currentUser, null);
    }

    /**
     * Displays edit dialog to the user.
     */
    public static boolean showEditDialog(Window owner, User currentUser, Add_Edit_Patient_Dao.PatientDetail patient) {
        return showDialog(owner, currentUser, patient);
    }

    /**
     * Displays dialog to the user.
     */
    private static boolean showDialog(Window owner, User currentUser, Add_Edit_Patient_Dao.PatientDetail patient) {
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
            dialog.setResizable(true);
            configureDialogSize(dialog);
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

    /**
     * Keeps the dialog chrome on screen while the long form body scrolls.
     */
    private static void configureDialogSize(Dialog<?> dialog) {
        Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
        double preferredHeight = Math.min(650, bounds.getHeight() * 0.72);

        dialog.getDialogPane().setPrefWidth(760);
        dialog.getDialogPane().setPrefHeight(Math.max(420, preferredHeight));
        dialog.getDialogPane().setMinHeight(420);
    }

    /**
     * Initializes the FXML controls after the JavaFX view has been loaded.
     */
    @FXML
    private void initialize() {
        genderBox.getItems().setAll("Female", "Male", "Other", "Unknown");
        statusBox.getItems().setAll("ACTIVE", "DISCHARGED");
        priorityBox.getItems().setAll("NORMAL", "HIGH", "CRITICAL", "EMERGENCY");
        bloodTypeBox.getItems().setAll("Unknown", "A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-");
        allergyStatusBox.getItems().setAll(ALLERGY_STATUS_NONE, ALLERGY_STATUS_HAS, ALLERGY_STATUS_UNKNOWN);
        genderBox.getSelectionModel().select("Unknown");
        statusBox.getSelectionModel().select("ACTIVE");
        priorityBox.getSelectionModel().select("NORMAL");
        bloodTypeBox.getSelectionModel().select("Unknown");
        allergyStatusBox.getSelectionModel().select(ALLERGY_STATUS_UNKNOWN);
        allergyStatusBox.valueProperty().addListener((observable, oldValue, newValue) -> updateAllergyDetailsVisibility(true));
        updateAllergyDetailsVisibility(false);
        configureInputFilters();
        DatePickerHelper.configureDdMmYyyy(birthDatePicker);
        NotificationHelper.showInfo(statusLabel, "Patient file form. System data is stored in the local clinic database.");
    }

    /**
     * Prepares the form with the selected patient record and mode.
     */
    private void prepare(User currentUser, Add_Edit_Patient_Dao.PatientDetail patient) {
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

    /**
     * Validates and saves save.
     */
    private boolean save() {
        try {
            DatePickerHelper.commitEditorText(birthDatePicker);
            Add_Edit_Patient_Dao.PatientWriteRecord record = buildRecord();
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

    /**
     * Builds record used by the patient view.
     */
    private Add_Edit_Patient_Dao.PatientWriteRecord buildRecord() {
        return new Add_Edit_Patient_Dao.PatientWriteRecord(
                patientIdField.getText(),
                firstNameField.getText(),
                lastNameField.getText(),
                birthDatePicker.getValue() == null ? "" : birthDatePicker.getValue().format(DISPLAY_DATE),
                genderBox.getValue(),
                statusBox.getValue(),
                priorityBox.getValue(),
                bloodTypeBox.getValue(),
                diagnosisArea.getText(),
                resolveAllergies(),
                phoneField.getText(),
                emailField.getText(),
                addressArea.getText(),
                emergencyContactNameField.getText(),
                emergencyContactPhoneField.getText()
        );
    }

    /**
     * Configures input filters.
     */
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
        installNameFilter(emergencyContactNameField);
    }

    /**
     * Handles the check existing patient ID UI action.
     */
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
            Add_Edit_Patient_Dao.PatientDetail detail = patientDao.findDetailById(patientId).orElse(null);
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

    /**
     * Installs name filter on the relevant input control.
     */
    private void installNameFilter(TextField field) {
        field.textProperty().addListener((observable, oldValue, newValue) -> {
            String clean = cleanName(newValue);
            if (!clean.equals(newValue)) {
                field.setText(clean);
            }
        });
    }

    /**
     * Filters a text field so it accepts only the permitted number of digits.
     */
    private String digitsOnly(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }

    /**
     * Trims and normalizes name before storage or comparison.
     */
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

    /**
     * Populates from patient detail from the selected record.
     */
    private void populateFromPatientDetail(Add_Edit_Patient_Dao.PatientDetail patient, boolean returningVisit) {
        patientIdField.setText(patient.getPatientId());
        patientIdField.setDisable(true);
        firstNameField.setText(patient.getFirstName());
        lastNameField.setText(patient.getLastName());
        birthDatePicker.setValue(parseBirthDate(patient.getBirthDate()));
        genderBox.getSelectionModel().select(blankTo(patient.getGender(), "Unknown"));
        statusBox.getSelectionModel().select(returningVisit ? "ACTIVE" : normalizeStatus(patient.getStatus()));
        priorityBox.getSelectionModel().select(normalizePriority(patient.getPriority()));
        bloodTypeBox.getSelectionModel().select(normalizeBloodType(patient.getBloodType()));
        populateAllergyFields(patient.getAllergies());
        phoneField.setText(patient.getPhone());
        emailField.setText(patient.getEmail());
        addressArea.setText(patient.getAddress());
        emergencyContactNameField.setText(patient.getEmergencyContactName());
        emergencyContactPhoneField.setText(patient.getEmergencyContactPhone());
        diagnosisArea.setText(returningVisit ? "" : patient.getDiagnosis());
        setCheckIdVisible(!returningVisit);
    }

    /**
     * Updates check ID visible for the current object.
     */
    private void setCheckIdVisible(boolean visible) {
        if (checkPatientIdButton != null) {
            checkPatientIdButton.setVisible(visible);
            checkPatientIdButton.setManaged(visible);
        }
    }

    /**
     * Populates allergy fields from the selected record.
     */
    private void populateAllergyFields(String allergies) {
        String normalized = allergies == null ? "" : allergies.trim();
        if (normalized.isBlank() || ALLERGY_STATUS_UNKNOWN.equalsIgnoreCase(normalized)) {
            allergyStatusBox.getSelectionModel().select(ALLERGY_STATUS_UNKNOWN);
            allergyDetailsArea.clear();
        } else if (ALLERGY_STATUS_NONE.equalsIgnoreCase(normalized)) {
            allergyStatusBox.getSelectionModel().select(ALLERGY_STATUS_NONE);
            allergyDetailsArea.clear();
        } else {
            allergyStatusBox.getSelectionModel().select(ALLERGY_STATUS_HAS);
            allergyDetailsArea.setText(normalized);
        }
        updateAllergyDetailsVisibility(false);
    }

    /**
     * Updates allergy details visibility.
     */
    private void updateAllergyDetailsVisibility(boolean clearWhenHidden) {
        boolean hasAllergies = ALLERGY_STATUS_HAS.equals(allergyStatusBox.getValue());
        allergyDetailsContainer.setManaged(hasAllergies);
        allergyDetailsContainer.setVisible(hasAllergies);
        allergyDetailsArea.setDisable(!hasAllergies);
        if (!hasAllergies && clearWhenHidden) {
            allergyDetailsArea.clear();
        }
    }

    /**
     * Resolves allergies for the current workflow.
     */
    private String resolveAllergies() {
        String allergyStatus = blankTo(allergyStatusBox.getValue(), ALLERGY_STATUS_UNKNOWN);
        if (ALLERGY_STATUS_HAS.equals(allergyStatus)) {
            String details = allergyDetailsArea.getText() == null ? "" : allergyDetailsArea.getText().trim();
            if (details.isBlank()) {
                throw new IllegalArgumentException("Allergy details are required when allergies are selected.");
            }
            return details;
        }
        return ALLERGY_STATUS_NONE.equals(allergyStatus) ? ALLERGY_STATUS_NONE : ALLERGY_STATUS_UNKNOWN;
    }

    /**
     * Parses birth date without exposing format failures to the caller.
     */
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

    /**
     * Normalizes blank to to the workflow fallback value.
     */
    private String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    /**
     * Normalizes status to the stored application format.
     */
    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return "ACTIVE";
        }
        return "Active".equalsIgnoreCase(status) ? "ACTIVE" : status.toUpperCase();
    }

    /**
     * Normalizes priority to the stored application format.
     */
    private String normalizePriority(String priority) {
        if (priority == null || priority.isBlank()) {
            return "NORMAL";
        }
        return "WARNING".equalsIgnoreCase(priority) ? "HIGH" : priority.toUpperCase();
    }

    /**
     * Normalizes blood type to the stored application format.
     */
    private String normalizeBloodType(String bloodType) {
        if (bloodType == null || bloodType.isBlank()) {
            return "Unknown";
        }
        String normalized = bloodType.trim();
        return "UNKNOWN".equalsIgnoreCase(normalized) ? "Unknown" : normalized.toUpperCase();
    }
}
