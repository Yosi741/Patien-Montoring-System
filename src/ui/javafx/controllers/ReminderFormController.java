package ui.javafx.controllers;

import dao.SqliteReminderDao;
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
import services.SchedulingService;
import ui.javafx.AppNavigator;
import ui.javafx.helpers.NotificationHelper;
import users.User;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ReminderFormController {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

    private final SchedulingService schedulingService = new SchedulingService();
    private User currentUser;
    private SqliteReminderDao.ReminderRecord existingReminder;
    private boolean saved;

    @FXML private Label titleLabel;
    @FXML private TextField patientIdField;
    @FXML private TextField medicationIdField;
    @FXML private ComboBox<String> reminderTypeBox;
    @FXML private TextField reminderTitleField;
    @FXML private TextField dueTimeField;
    @FXML private TextField repeatRuleField;
    @FXML private TextField assignedToField;
    @FXML private ComboBox<String> statusBox;
    @FXML private TextArea notesArea;
    @FXML private Label statusLabel;

    public static boolean showCreateDialog(Window owner, User currentUser, String patientId, Long medicationId, String medicationName) {
        return showDialog(owner, currentUser, patientId, medicationId, medicationName, null);
    }

    public static boolean showEditDialog(Window owner, User currentUser, SqliteReminderDao.ReminderRecord reminder) {
        return showDialog(owner, currentUser, "", null, "", reminder);
    }

    private static boolean showDialog(Window owner, User currentUser, String patientId, Long medicationId,
                                      String medicationName, SqliteReminderDao.ReminderRecord reminder) {
        try {
            FXMLLoader loader = new FXMLLoader(AppNavigator.resolve("/ui/javafx/views/ReminderFormView.fxml"));
            Parent root = loader.load();
            ReminderFormController controller = loader.getController();
            controller.prepare(currentUser, patientId, medicationId, medicationName, reminder);

            ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setTitle(reminder == null ? "Create Reminder" : "Edit Reminder");
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
        NotificationHelper.showInfo(statusLabel, "SQLite-only reminder. External calendar integration is future work.");
    }

    private void prepare(User currentUser, String patientId, Long medicationId, String medicationName,
                         SqliteReminderDao.ReminderRecord reminder) {
        this.currentUser = currentUser;
        this.existingReminder = reminder;
        if (currentUser != null && currentUser.getUsername() != null) {
            assignedToField.setText(currentUser.getUsername());
        }
        if (patientId != null && !patientId.isBlank()) {
            patientIdField.setText(patientId);
        }
        if (medicationId != null && medicationId > 0) {
            medicationIdField.setText(String.valueOf(medicationId));
            reminderTypeBox.getSelectionModel().select("MEDICATION");
            reminderTitleField.setText("Medication reminder: " + (medicationName == null || medicationName.isBlank() ? medicationId : medicationName));
        }
        if (reminder == null) {
            titleLabel.setText("Create Reminder");
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
    }

    private boolean save() {
        try {
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
}
