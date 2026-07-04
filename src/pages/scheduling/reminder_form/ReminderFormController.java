package pages.scheduling.reminder_form;

import pages.scheduling.*;
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
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import app.AppNavigator;
import pages.notification.NotificationHelper;
import pages.user.User;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class ReminderFormController {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

    private final SchedulingService schedulingService = new SchedulingService();
    private User currentUser;
    private SqliteReminderDao.ReminderRecord existingReminder;
    private boolean saved;
    private boolean lockedPatientContext;
    private boolean orderCheckupMode;

    @FXML private Label titleLabel;
    @FXML private TextField patientIdField;
    @FXML private TextField medicationIdField;
    @FXML private VBox medicationIdBox;
    @FXML private VBox reminderTypeContainer;
    @FXML private ComboBox<String> reminderTypeBox;
    @FXML private TextField reminderTitleField;
    @FXML private TextField dueTimeField;
    @FXML private TextField repeatRuleField;
    @FXML private TextField assignedToField;
    @FXML private ComboBox<String> statusBox;
    @FXML private TextArea notesArea;
    @FXML private Label statusLabel;
    @FXML private VBox checkupSelectionBox;
    @FXML private CheckBox heartRateCheckBox;
    @FXML private CheckBox bloodPressureCheckBox;
    @FXML private CheckBox temperatureCheckBox;
    @FXML private CheckBox oxygenSaturationCheckBox;
    @FXML private CheckBox weightCheckBox;
    @FXML private CheckBox respiratoryRateCheckBox;
    @FXML private CheckBox cbcCheckBox;
    @FXML private CheckBox rbcCheckBox;
    @FXML private CheckBox wbcCheckBox;
    @FXML private CheckBox hemoglobinCheckBox;
    @FXML private CheckBox plateletsCheckBox;
    @FXML private CheckBox crpCheckBox;
    @FXML private CheckBox ironFerritinCheckBox;
    @FXML private CheckBox vitaminB12CheckBox;
    @FXML private CheckBox vitaminDCheckBox;
    @FXML private CheckBox xrayCheckBox;
    @FXML private CheckBox ctScanCheckBox;
    @FXML private CheckBox mriCheckBox;
    @FXML private CheckBox ultrasoundCheckBox;
    @FXML private CheckBox doctorReviewCheckBox;
    @FXML private CheckBox nurseFollowUpCheckBox;
    @FXML private CheckBox medicationReviewCheckBox;
    @FXML private CheckBox painAssessmentCheckBox;

    public static boolean showCreateDialog(Window owner, User currentUser, String patientId, Long medicationId, String medicationName) {
        return showDialog(owner, currentUser, patientId, medicationId, medicationName, null, false);
    }

    public static boolean showOrderCheckupDialog(Window owner, User currentUser, String patientId) {
        return showDialog(owner, currentUser, patientId, null, "", null, true);
    }

    public static boolean showEditDialog(Window owner, User currentUser, SqliteReminderDao.ReminderRecord reminder) {
        return showDialog(owner, currentUser, "", null, "", reminder, false);
    }

    private static boolean showDialog(Window owner, User currentUser, String patientId, Long medicationId,
                                      String medicationName, SqliteReminderDao.ReminderRecord reminder,
                                      boolean orderCheckupMode) {
        try {
            FXMLLoader loader = new FXMLLoader(AppNavigator.resolve("/pages/scheduling/reminder_form/ReminderFormView.fxml"));
            Parent root = loader.load();
            ReminderFormController controller = loader.getController();
            controller.prepare(currentUser, patientId, medicationId, medicationName, reminder, orderCheckupMode);

            ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setTitle(orderCheckupMode ? "Order Checkup" : reminder == null ? "Create Reminder" : "Edit Reminder");
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
            throw new IllegalStateException("Could not open reminder form: " + e.getMessage(), e);
        }
    }

    @FXML
    private void initialize() {
        reminderTypeBox.getItems().setAll("MEDICATION", "APPOINTMENT", "CHECKUP", "CUSTOM");
        statusBox.getItems().setAll("PENDING", "OVERDUE", "DONE", "MISSED", "CANCELLED");
        reminderTypeBox.getSelectionModel().select("CUSTOM");
        statusBox.getSelectionModel().select("PENDING");
        dueTimeField.setText(LocalDateTime.now().plusHours(4).withSecond(0).withNano(0).format(DATE_TIME));
        reminderTypeBox.valueProperty().addListener((observable, oldValue, newValue) -> updateTypeVisibility());
        installNineDigitFilter(patientIdField);
        updateTypeVisibility();
        NotificationHelper.showInfo(statusLabel, "Local database reminder. External calendar integration is future work.");
    }

    private void prepare(User currentUser, String patientId, Long medicationId, String medicationName,
                         SqliteReminderDao.ReminderRecord reminder, boolean orderCheckupMode) {
        this.currentUser = currentUser;
        this.existingReminder = reminder;
        this.orderCheckupMode = orderCheckupMode;
        if (currentUser != null && currentUser.getUsername() != null) {
            assignedToField.setText(currentUser.getUsername());
        }
        if (patientId != null && !patientId.isBlank()) {
            patientIdField.setText(patientId);
            lockedPatientContext = true;
            patientIdField.setEditable(false);
            patientIdField.setFocusTraversable(false);
            patientIdField.getStyleClass().add("locked-context-field");
        }
        if (medicationId != null && medicationId > 0) {
            medicationIdField.setText(String.valueOf(medicationId));
            reminderTypeBox.getSelectionModel().select("MEDICATION");
            reminderTitleField.setText("Medication reminder: " + (medicationName == null || medicationName.isBlank() ? medicationId : medicationName));
        }
        if (orderCheckupMode) {
            titleLabel.setText("Order Checkup");
            reminderTypeBox.getSelectionModel().select("CHECKUP");
            reminderTitleField.setText("Checkup");
            if (reminderTypeContainer != null) {
                reminderTypeContainer.setVisible(false);
                reminderTypeContainer.setManaged(false);
            }
            NotificationHelper.showInfo(statusLabel, "Select one or more requested checkups/tests for this patient.");
            updateTypeVisibility();
        }
        if (reminder == null) {
            if (!orderCheckupMode) {
                titleLabel.setText("Create Reminder");
            }
            return;
        }
        titleLabel.setText("Edit Reminder");
        patientIdField.setText(reminder.getPatientId());
        medicationIdField.setText(reminder.getMedicationId() == null ? "" : String.valueOf(reminder.getMedicationId()));
        reminderTypeBox.getSelectionModel().select(reminder.getReminderType());
        reminderTitleField.setText(reminder.getTitle());
        dueTimeField.setText(reminder.getDueTime());
        repeatRuleField.setText(reminder.getRepeatRule());
        assignedToField.setText(reminder.getAssignedTo());
        statusBox.getSelectionModel().select(reminder.getStatus());
        notesArea.setText(reminder.getNotes());
        updateTypeVisibility();
    }

    private boolean save() {
        try {
            if (lockedPatientContext && !patientIdField.getText().trim().matches("\\d{9}")) {
                throw new IllegalArgumentException("Patient ID must contain exactly 9 digits.");
            }
            applyCheckupSummaryIfNeeded();
            SchedulingService.ReminderRequest request = new SchedulingService.ReminderRequest(
                    existingReminder == null ? 0 : existingReminder.getId(),
                    patientIdField.getText(),
                    parseMedicationId(),
                    reminderTypeBox.getValue(),
                    reminderTitleField.getText(),
                    dueTimeField.getText(),
                    repeatRuleField.getText(),
                    statusBox.getValue(),
                    assignedToField.getText(),
                    notesArea.getText()
            );
            if (existingReminder == null) {
                schedulingService.createReminder(currentUser, request);
            } else {
                schedulingService.updateReminder(currentUser, request);
            }
            saved = true;
            return true;
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
            return false;
        }
    }

    private Long parseMedicationId() {
        if (!"MEDICATION".equalsIgnoreCase(reminderTypeBox.getValue())) {
            return null;
        }
        String value = medicationIdField.getText();
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Medication ID must be a whole number when provided.");
        }
    }

    private void installNineDigitFilter(TextField field) {
        field.textProperty().addListener((observable, oldValue, newValue) -> {
            String clean = newValue == null ? "" : newValue.replaceAll("\\D", "");
            if (clean.length() > 9) {
                clean = clean.substring(0, 9);
            }
            if (!clean.equals(newValue)) {
                field.setText(clean);
            }
        });
    }

    private void updateTypeVisibility() {
        String type = reminderTypeBox == null ? "" : reminderTypeBox.getValue();
        boolean medication = "MEDICATION".equalsIgnoreCase(type);
        boolean checkup = "CHECKUP".equalsIgnoreCase(type);
        if (medicationIdBox != null) {
            medicationIdBox.setVisible(medication);
            medicationIdBox.setManaged(medication);
        }
        if (!medication && medicationIdField != null) {
            medicationIdField.clear();
        }
        if (checkupSelectionBox != null) {
            checkupSelectionBox.setVisible(checkup);
            checkupSelectionBox.setManaged(checkup);
        }
    }

    private void applyCheckupSummaryIfNeeded() {
        if (!"CHECKUP".equalsIgnoreCase(reminderTypeBox.getValue())) {
            return;
        }
        List<String> selected = selectedCheckups();
        if (orderCheckupMode && selected.isEmpty()) {
            throw new IllegalArgumentException("Select at least one checkup or test.");
        }
        if (selected.isEmpty()) {
            return;
        }
        String fullList = String.join(", ", selected);
        String title = "Checkup: " + fullList;
        if (title.length() > 110) {
            title = "Checkup: " + String.join(", ", selected.subList(0, Math.min(4, selected.size()))) + " +" + Math.max(0, selected.size() - 4) + " more";
        }
        reminderTitleField.setText(title);
        String notes = notesArea.getText() == null ? "" : notesArea.getText().trim();
        String selectedText = "Requested checkups/tests: " + fullList;
        if (!notes.contains(selectedText)) {
            notesArea.setText(notes.isBlank() ? selectedText : notes + "\n\n" + selectedText);
        }
    }

    private List<String> selectedCheckups() {
        ArrayList<String> selected = new ArrayList<>();
        addIfSelected(selected, heartRateCheckBox, "Heart Rate");
        addIfSelected(selected, bloodPressureCheckBox, "Blood Pressure");
        addIfSelected(selected, temperatureCheckBox, "Temperature");
        addIfSelected(selected, oxygenSaturationCheckBox, "Oxygen Saturation");
        addIfSelected(selected, weightCheckBox, "Weight");
        addIfSelected(selected, respiratoryRateCheckBox, "Respiratory Rate");
        addIfSelected(selected, cbcCheckBox, "Complete Blood Count / CBC");
        addIfSelected(selected, rbcCheckBox, "Red Blood Cell Count / RBC");
        addIfSelected(selected, wbcCheckBox, "White Blood Cell Count / WBC");
        addIfSelected(selected, hemoglobinCheckBox, "Hemoglobin");
        addIfSelected(selected, plateletsCheckBox, "Platelets");
        addIfSelected(selected, crpCheckBox, "CRP");
        addIfSelected(selected, ironFerritinCheckBox, "Iron / Ferritin");
        addIfSelected(selected, vitaminB12CheckBox, "Vitamin B12");
        addIfSelected(selected, vitaminDCheckBox, "Vitamin D");
        addIfSelected(selected, xrayCheckBox, "X-Ray");
        addIfSelected(selected, ctScanCheckBox, "CT Scan");
        addIfSelected(selected, mriCheckBox, "MRI");
        addIfSelected(selected, ultrasoundCheckBox, "Ultrasound");
        addIfSelected(selected, doctorReviewCheckBox, "Doctor Review");
        addIfSelected(selected, nurseFollowUpCheckBox, "Nurse Follow-up");
        addIfSelected(selected, medicationReviewCheckBox, "Medication Review");
        addIfSelected(selected, painAssessmentCheckBox, "Pain Assessment");
        return selected;
    }

    private void addIfSelected(List<String> selected, CheckBox checkBox, String label) {
        if (checkBox != null && checkBox.isSelected()) {
            selected.add(label);
        }
    }
}
