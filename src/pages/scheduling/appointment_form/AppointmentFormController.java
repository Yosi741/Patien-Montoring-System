package pages.scheduling.appointment_form;

import app.navigation.AppNavigator;
import app.helpers.DatePickerHelper;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DateCell;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Window;
import pages.notification.NotificationHelper;
import pages.scheduling.SchedulingService;
import pages.scheduling.SqliteAppointmentDao;
import pages.user.User;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * Controls AppointmentFormView.fxml for creating and editing validated patient appointments.
 */
public class AppointmentFormController {

    private static final DateTimeFormatter STORAGE_DATE_TIME = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
    private static final DateTimeFormatter UI_TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);
    private static final String DEFAULT_DURATION = "30 min";

    private final SchedulingService schedulingService = new SchedulingService();
    private User currentUser;
    private SqliteAppointmentDao.AppointmentRecord existingAppointment;
    private boolean saved;
    private boolean lockedPatientContext;
    private boolean suppressUiRefresh;
    private String preservedTitle = "";
    private String preservedLocation = "";
    private String preservedAssignedStaff = "";

    @FXML private Label titleLabel;
    @FXML private TextField patientIdField;
    @FXML private ComboBox<String> typeBox;
    @FXML private ComboBox<String> statusBox;
    @FXML private DatePicker appointmentDatePicker;
    @FXML private ComboBox<String> startTimeBox;
    @FXML private ComboBox<String> durationBox;
    @FXML private TextArea notesArea;
    @FXML private Label statusLabel;

    /**
     * Displays create dialog to the user.
     */
    public static boolean showCreateDialog(Window owner, User currentUser, String patientId) {
        return showDialog(owner, currentUser, patientId, null);
    }

    /**
     * Displays edit dialog to the user.
     */
    public static boolean showEditDialog(Window owner, User currentUser, SqliteAppointmentDao.AppointmentRecord appointment) {
        return showDialog(owner, currentUser, "", appointment);
    }

    /**
     * Displays dialog to the user.
     */
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

    /**
     * Initializes the FXML controls after the JavaFX view has been loaded.
     */
    @FXML
    private void initialize() {
        typeBox.getItems().setAll("VISIT", "FOLLOW_UP", "LAB_TEST", "OTHER", "SURGERY");
        statusBox.getItems().setAll("SCHEDULED", "COMPLETED", "CANCELLED", "MISSED");
        durationBox.getItems().setAll("30 min", "45 min", "60 min");
        typeBox.getSelectionModel().select("VISIT");
        statusBox.getSelectionModel().select("SCHEDULED");
        durationBox.getSelectionModel().select(DEFAULT_DURATION);
        DatePickerHelper.configureDdMmYyyy(appointmentDatePicker);
        installAppointmentDateRules();
        installNineDigitFilter(patientIdField);
        typeBox.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (suppressUiRefresh) {
                return;
            }
            durationBox.getSelectionModel().select(defaultDurationForType(newValue));
            refreshAvailableTimes();
        });
        appointmentDatePicker.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (!suppressUiRefresh) {
                refreshAvailableTimes();
            }
        });
        durationBox.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (!suppressUiRefresh) {
                refreshAvailableTimes();
            }
        });
    }

    /**
     * Prepares the form with the selected appointment record and mode.
     */
    private void prepare(User currentUser, String patientId, SqliteAppointmentDao.AppointmentRecord appointment) {
        this.currentUser = currentUser;
        this.existingAppointment = appointment;
        preservedAssignedStaff = currentUser != null && currentUser.getUsername() != null ? currentUser.getUsername() : "";
        suppressUiRefresh = true;
        if (patientId != null && !patientId.isBlank()) {
            patientIdField.setText(patientId);
            lockedPatientContext = true;
            patientIdField.setEditable(false);
            patientIdField.setFocusTraversable(false);
            patientIdField.getStyleClass().add("locked-context-field");
        }
        if (appointment == null) {
            titleLabel.setText("Create Appointment");
            preservedTitle = autoTitleForType(typeBox == null ? null : typeBox.getValue());
            preservedLocation = "";
            appointmentDatePicker.setValue(defaultWorkingDate(LocalDate.now()));
            durationBox.getSelectionModel().select(defaultDurationForType(typeBox.getValue()));
            suppressUiRefresh = false;
            refreshAvailableTimes();
            return;
        }
        titleLabel.setText("Edit Appointment");
        patientIdField.setText(appointment.getPatientId());
        typeBox.getSelectionModel().select(toUiAppointmentType(appointment.getAppointmentType()));
        statusBox.getSelectionModel().select(appointment.getStatus());
        notesArea.setText(appointment.getNotes());
        preservedTitle = blankTo(appointment.getTitle(), autoTitleForType(appointment.getAppointmentType()));
        preservedLocation = blankTo(appointment.getLocation(), "");
        preservedAssignedStaff = blankTo(appointment.getAssignedStaff(), preservedAssignedStaff);
        applyExistingSchedule(appointment);
        suppressUiRefresh = false;
        refreshAvailableTimes();
    }

    /**
     * Validates and saves save.
     */
    private boolean save() {
        try {
            String patientId = patientIdField.getText() == null ? "" : patientIdField.getText().trim();
            if (patientId.isEmpty()) {
                throw new IllegalArgumentException("Patient ID is required.");
            }
            if (lockedPatientContext && !patientId.matches("\\d{9}")) {
                throw new IllegalArgumentException("Patient ID must contain exactly 9 digits.");
            }
            if (appointmentDatePicker.getValue() == null) {
                throw new IllegalArgumentException("Appointment date is required.");
            }
            if (appointmentDatePicker.getValue().isBefore(LocalDate.now())) {
                throw new IllegalArgumentException("Appointment date cannot be in the past.");
            }
            WorkingHours hours = workingHoursFor(appointmentDatePicker.getValue());
            if (hours.closed()) {
                throw new IllegalArgumentException("Clinic is closed on this day.");
            }
            if (startTimeBox.getValue() == null || startTimeBox.getValue().isBlank()) {
                throw new IllegalArgumentException("Appointment time is required.");
            }
            if (durationBox.getValue() == null || durationBox.getValue().isBlank()) {
                throw new IllegalArgumentException("Duration is required.");
            }
            LocalDateTime start = LocalDateTime.of(appointmentDatePicker.getValue(), parseUiTime(startTimeBox.getValue()));
            LocalDateTime end = start.plusMinutes(selectedDurationMinutes());
            SchedulingService.AppointmentRequest request = new SchedulingService.AppointmentRequest(
                    existingAppointment == null ? 0 : existingAppointment.getId(),
                    patientId,
                    resolvedTitle(),
                    toStorageAppointmentType(typeBox.getValue()),
                    start.format(STORAGE_DATE_TIME),
                    end.format(STORAGE_DATE_TIME),
                    resolvedLocation(),
                    resolvedAssignedStaff(),
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

    /**
     * Applies existing schedule to the current control or record.
     */
    private void applyExistingSchedule(SqliteAppointmentDao.AppointmentRecord appointment) {
        try {
            LocalDateTime start = parseStoredDateTime(appointment.getStartTime());
            LocalDateTime end = parseStoredDateTime(appointment.getEndTime());
            appointmentDatePicker.setValue(start.toLocalDate());
            durationBox.getSelectionModel().select(durationLabelFor(Duration.between(start, end).toMinutes()));
            refreshAvailableTimes(start.toLocalTime().format(UI_TIME), false);
        } catch (Exception e) {
            appointmentDatePicker.setValue(defaultWorkingDate(LocalDate.now()));
            durationBox.getSelectionModel().select(defaultDurationForType(typeBox.getValue()));
            NotificationHelper.showInfo(statusLabel, "Existing appointment time could not be parsed. Defaulted to the next clinic slot.");
        }
    }

    /**
     * Refreshes available times from the current application state.
     */
    private void refreshAvailableTimes() {
        refreshAvailableTimes(startTimeBox == null ? null : startTimeBox.getValue(), true);
    }

    /**
     * Refreshes available times from the current application state.
     */
    private void refreshAvailableTimes(String preferredValue, boolean notifyClosed) {
        if (startTimeBox == null) {
            return;
        }
        startTimeBox.getItems().clear();
        startTimeBox.getSelectionModel().clearSelection();
        LocalDate selectedDate = appointmentDatePicker == null ? null : appointmentDatePicker.getValue();
        if (selectedDate == null) {
            return;
        }
        WorkingHours hours = workingHoursFor(selectedDate);
        if (hours.closed()) {
            if (notifyClosed) {
                NotificationHelper.showInfo(statusLabel, "Clinic is closed on this day.");
            }
            return;
        }
        int durationMinutes = selectedDurationMinutes();
        LocalTime slot = hours.openTime();
        while (!slot.plusMinutes(durationMinutes).isAfter(hours.closeTime())) {
            startTimeBox.getItems().add(slot.format(UI_TIME));
            slot = slot.plusMinutes(30);
        }
        if (preferredValue != null && startTimeBox.getItems().contains(preferredValue)) {
            startTimeBox.getSelectionModel().select(preferredValue);
        } else if (!startTimeBox.getItems().isEmpty()) {
            startTimeBox.getSelectionModel().selectFirst();
        }
    }

    /**
     * Installs nine digit filter on the relevant input control.
     */
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

    /**
     * Installs appointment date rules on the relevant input control.
     */
    private void installAppointmentDateRules() {
        if (appointmentDatePicker == null) {
            return;
        }
        appointmentDatePicker.setDayCellFactory(picker -> new DateCell() {
            /**
             * Updates item.
             */
            @Override
            public void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                boolean pastAppointmentDate = date != null
                        && date.isBefore(LocalDate.now());
                setDisable(empty || pastAppointmentDate);
            }
        });
    }

    /**
     * Resolves resolved title for the current workflow.
     */
    private String resolvedTitle() {
        String type = typeBox == null ? "" : typeBox.getValue();
        String generated = autoTitleForType(type);
        if (existingAppointment == null) {
            return generated;
        }
        return preservedTitle == null || preservedTitle.isBlank() ? generated : preservedTitle.trim();
    }

    /**
     * Resolves resolved location for the current workflow.
     */
    private String resolvedLocation() {
        return existingAppointment == null ? "" : blankTo(preservedLocation, "");
    }

    /**
     * Resolves resolved assigned staff for the current workflow.
     */
    private String resolvedAssignedStaff() {
        if (existingAppointment == null) {
            return blankTo(preservedAssignedStaff, "");
        }
        return blankTo(preservedAssignedStaff, "");
    }

    /**
     * Builds the automatic appointment title for the selected type.
     */
    private String autoTitleForType(String type) {
        String normalized = toStorageAppointmentType(type);
        switch (normalized) {
            case "FOLLOW_UP":
                return "Follow-up Visit";
            case "LAB_TEST":
                return "Lab Test Visit";
            case "SURGERY":
                return "Surgery Visit";
            case "OTHER":
                return "Clinic Visit";
            case "VISIT":
            default:
                return "Clinic Visit";
        }
    }

    /**
     * Normalizes blank to to the workflow fallback value.
     */
    private String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    /**
     * Returns the default duration for type used by this workflow.
     */
    private String defaultDurationForType(String type) {
        String normalized = toStorageAppointmentType(type);
        if ("SURGERY".equals(normalized)) {
            return "60 min";
        }
        return DEFAULT_DURATION;
    }

    /**
     * Converts appointment type to its SQLite storage value.
     */
    private String toStorageAppointmentType(String value) {
        if (value == null || value.isBlank()) {
            return "VISIT";
        }
        String normalized = value.trim().toUpperCase(Locale.ENGLISH);
        return "CHECKUP".equals(normalized) ? "VISIT" : normalized;
    }

    /**
     * Converts appointment type to its user-facing value.
     */
    private String toUiAppointmentType(String value) {
        if ("CHECKUP".equalsIgnoreCase(value)) {
            return "VISIT";
        }
        return value == null || value.isBlank() ? "VISIT" : value.trim().toUpperCase(Locale.ENGLISH);
    }

    /**
     * Selects selected duration minutes without using an invalid index.
     */
    private int selectedDurationMinutes() {
        String value = durationBox == null ? DEFAULT_DURATION : durationBox.getValue();
        if (value == null || value.isBlank()) {
            return 30;
        }
        if (value.startsWith("45")) {
            return 45;
        }
        if (value.startsWith("60")) {
            return 60;
        }
        return 30;
    }

    /**
     * Returns the display or default duration for the selected appointment type.
     */
    private String durationLabelFor(long minutes) {
        if (minutes >= 53) {
            return "60 min";
        }
        if (minutes >= 38) {
            return "45 min";
        }
        return "30 min";
    }

    /**
     * Parses stored date time without exposing format failures to the caller.
     */
    private LocalDateTime parseStoredDateTime(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Appointment time is unavailable.");
        }
        try {
            return LocalDateTime.parse(value.trim(), STORAGE_DATE_TIME);
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDateTime.parse(value.trim(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            } catch (DateTimeParseException ignoredAgain) {
                return LocalDateTime.parse(value.trim().replace(" ", "T"));
            }
        }
    }

    /**
     * Parses ui time without exposing format failures to the caller.
     */
    private LocalTime parseUiTime(String value) {
        return LocalTime.parse(value.trim().toUpperCase(Locale.ENGLISH), UI_TIME);
    }

    /**
     * Returns the default working date used by this workflow.
     */
    private LocalDate defaultWorkingDate(LocalDate candidate) {
        LocalDate safe = candidate == null ? LocalDate.now() : candidate;
        while (workingHoursFor(safe).closed()) {
            safe = safe.plusDays(1);
        }
        return safe;
    }

    /**
     * Returns the clinic working hours for the selected date.
     */
    private WorkingHours workingHoursFor(LocalDate date) {
        if (date == null) {
            return WorkingHours.closedHours();
        }
        DayOfWeek day = date.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY) {
            return WorkingHours.closedHours();
        }
        if (day == DayOfWeek.FRIDAY) {
            return new WorkingHours(LocalTime.of(8, 0), LocalTime.of(13, 0));
        }
        return new WorkingHours(LocalTime.of(8, 0), LocalTime.of(18, 0));
    }

    private record WorkingHours(LocalTime openTime, LocalTime closeTime) {
        /**
         * Creates a closed-hours result for a non-working date.
         */
        private static WorkingHours closedHours() {
            return new WorkingHours(null, null);
        }

        /**
         * Creates a closed-hours result for a non-working date.
         */
        private boolean closed() {
            return openTime == null || closeTime == null;
        }
    }
}
