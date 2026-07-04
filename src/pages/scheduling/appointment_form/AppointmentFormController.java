package pages.scheduling.appointment_form;

import pages.scheduling.*;
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
import app.AppNavigator;
import pages.notification.NotificationHelper;
import pages.user.User;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class AppointmentFormController {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

    private final SchedulingService schedulingService = new SchedulingService();
    private User currentUser;
    private SqliteAppointmentDao.AppointmentRecord existingAppointment;
    private boolean saved;
    private boolean lockedPatientContext;

    @FXML private Label titleLabel;
    @FXML private TextField patientIdField;
    @FXML private TextField appointmentTitleField;
    @FXML private ComboBox<String> typeBox;
    @FXML private TextField startTimeField;
    @FXML private TextField endTimeField;
    @FXML private TextField locationField;
    @FXML private TextField assignedStaffField;
    @FXML private ComboBox<String> statusBox;
    @FXML private TextArea notesArea;
    @FXML private Label statusLabel;

    public static boolean showCreateDialog(Window owner, User currentUser, String patientId) {
        return showDialog(owner, currentUser, patientId, null);
    }

    public static boolean showEditDialog(Window owner, User currentUser, SqliteAppointmentDao.AppointmentRecord appointment) {
        return showDialog(owner, currentUser, "", appointment);
    }

    private static boolean showDialog(Window owner, User currentUser, String patientId, SqliteAppointmentDao.AppointmentRecord appointment) {
        try {
            FXMLLoader loader = new FXMLLoader(AppNavigator.resolve("/pages/scheduling/appointment_form/AppointmentFormView.fxml"));
            Parent root = loader.load();
            AppointmentFormController controller = loader.getController();
            controller.prepare(currentUser, patientId, appointment);

            ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setTitle(appointment == null ? "Create Appointment" : "Edit Appointment");
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
            throw new IllegalStateException("Could not open appointment form: " + e.getMessage(), e);
        }
    }

    @FXML
    private void initialize() {
        typeBox.getItems().setAll("CHECKUP", "SURGERY", "FOLLOW_UP", "LAB_TEST", "MEDICATION_REVIEW", "OTHER");
        statusBox.getItems().setAll("SCHEDULED", "COMPLETED", "CANCELLED", "MISSED");
        typeBox.getSelectionModel().select("CHECKUP");
        statusBox.getSelectionModel().select("SCHEDULED");
        LocalDateTime now = LocalDateTime.now().withSecond(0).withNano(0);
        startTimeField.setText(now.format(DATE_TIME));
        endTimeField.setText(now.plusHours(1).format(DATE_TIME));
        installNineDigitFilter(patientIdField);
    }

    private void prepare(User currentUser, String patientId, SqliteAppointmentDao.AppointmentRecord appointment) {
        this.currentUser = currentUser;
        this.existingAppointment = appointment;
        if (currentUser != null && currentUser.getUsername() != null) {
            assignedStaffField.setText(currentUser.getUsername());
        }
        if (patientId != null && !patientId.isBlank()) {
            patientIdField.setText(patientId);
            lockedPatientContext = true;
            patientIdField.setEditable(false);
            patientIdField.setFocusTraversable(false);
            patientIdField.getStyleClass().add("locked-context-field");
        }
        if (appointment == null) {
            titleLabel.setText("Create Appointment");
            NotificationHelper.showInfo(statusLabel, "Local database scheduling. External calendar integration is future work.");
            return;
        }
        titleLabel.setText("Edit Appointment");
        patientIdField.setText(appointment.getPatientId());
        appointmentTitleField.setText(appointment.getTitle());
        typeBox.getSelectionModel().select(appointment.getAppointmentType());
        startTimeField.setText(appointment.getStartTime());
        endTimeField.setText(appointment.getEndTime());
        locationField.setText(appointment.getLocation());
        assignedStaffField.setText(appointment.getAssignedStaff());
        statusBox.getSelectionModel().select(appointment.getStatus());
        notesArea.setText(appointment.getNotes());
    }

    private boolean save() {
        try {
            if (lockedPatientContext && !patientIdField.getText().trim().matches("\\d{9}")) {
                throw new IllegalArgumentException("Patient ID must contain exactly 9 digits.");
            }
            SchedulingService.AppointmentRequest request = new SchedulingService.AppointmentRequest(
                    existingAppointment == null ? 0 : existingAppointment.getId(),
                    patientIdField.getText(),
                    appointmentTitleField.getText(),
                    typeBox.getValue(),
                    startTimeField.getText(),
                    endTimeField.getText(),
                    locationField.getText(),
                    assignedStaffField.getText(),
                    statusBox.getValue(),
                    notesArea.getText()
            );
            if (existingAppointment == null) {
                schedulingService.createAppointment(currentUser, request);
            } else {
                schedulingService.updateAppointment(currentUser, request);
            }
            saved = true;
            return true;
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
            return false;
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
}
