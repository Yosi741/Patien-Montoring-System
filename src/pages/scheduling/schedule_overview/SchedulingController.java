package pages.scheduling.schedule_overview;

import pages.scheduling.*;
import pages.scheduling.appointment_form.AppointmentFormController;
import pages.scheduling.reminder_form.ReminderFormController;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import app.AppShell;
import app.FxController;
import app.helpers.DialogHelper;
import pages.notification.NotificationHelper;
import app.helpers.PermissionHelper;
import app.helpers.SelectionHelper;
import users.Session;

public class SchedulingController implements FxController {

    private final SchedulingService schedulingService = new SchedulingService();
    private final ObservableList<SqliteAppointmentDao.AppointmentRow> appointments = FXCollections.observableArrayList();
    private final ObservableList<SqliteReminderDao.ReminderRow> reminders = FXCollections.observableArrayList();
    private AppShell appShell;
    private String patientIdFilter = "";

    @FXML private VBox accessDeniedPane;
    @FXML private VBox contentPane;
    @FXML private Label patientFilterChip;
    @FXML private Button clearPatientFilterButton;
    @FXML private TextField searchField;
    @FXML private ComboBox<String> appointmentTypeFilter;
    @FXML private ComboBox<String> appointmentStatusFilter;
    @FXML private ComboBox<String> reminderTypeFilter;
    @FXML private ComboBox<String> reminderStatusFilter;
    @FXML private Label appointmentsTodayLabel;
    @FXML private Label upcomingSurgeriesLabel;
    @FXML private Label overdueRemindersLabel;
    @FXML private Label patientRemindersTodayLabel;
    @FXML private Label cancelledMissedLabel;
    @FXML private Button newAppointmentButton;
    @FXML private Button editAppointmentButton;
    @FXML private Button completeAppointmentButton;
    @FXML private Button cancelAppointmentButton;
    @FXML private Button newReminderButton;
    @FXML private Button editReminderButton;
    @FXML private Button doneReminderButton;
    @FXML private Button cancelReminderButton;
    @FXML private TableView<SqliteAppointmentDao.AppointmentRow> appointmentTable;
    @FXML private TableColumn<SqliteAppointmentDao.AppointmentRow, Number> appointmentRowNumberColumn;
    @FXML private TableColumn<SqliteAppointmentDao.AppointmentRow, Long> appointmentIdColumn;
    @FXML private TableColumn<SqliteAppointmentDao.AppointmentRow, String> appointmentPatientIdColumn;
    @FXML private TableColumn<SqliteAppointmentDao.AppointmentRow, String> appointmentPatientNameColumn;
    @FXML private TableColumn<SqliteAppointmentDao.AppointmentRow, String> appointmentTitleColumn;
    @FXML private TableColumn<SqliteAppointmentDao.AppointmentRow, String> appointmentTypeColumn;
    @FXML private TableColumn<SqliteAppointmentDao.AppointmentRow, String> appointmentStartColumn;
    @FXML private TableColumn<SqliteAppointmentDao.AppointmentRow, String> appointmentEndColumn;
    @FXML private TableColumn<SqliteAppointmentDao.AppointmentRow, String> appointmentLocationColumn;
    @FXML private TableColumn<SqliteAppointmentDao.AppointmentRow, String> appointmentStaffColumn;
    @FXML private TableColumn<SqliteAppointmentDao.AppointmentRow, String> appointmentStatusColumn;
    @FXML private TableView<SqliteReminderDao.ReminderRow> reminderTable;
    @FXML private TableColumn<SqliteReminderDao.ReminderRow, Number> reminderRowNumberColumn;
    @FXML private TableColumn<SqliteReminderDao.ReminderRow, Long> reminderIdColumn;
    @FXML private TableColumn<SqliteReminderDao.ReminderRow, String> reminderPatientIdColumn;
    @FXML private TableColumn<SqliteReminderDao.ReminderRow, String> reminderPatientNameColumn;
    @FXML private TableColumn<SqliteReminderDao.ReminderRow, String> reminderTypeColumn;
    @FXML private TableColumn<SqliteReminderDao.ReminderRow, String> reminderTitleColumn;
    @FXML private TableColumn<SqliteReminderDao.ReminderRow, String> reminderDueColumn;
    @FXML private TableColumn<SqliteReminderDao.ReminderRow, String> reminderAssignedColumn;
    @FXML private TableColumn<SqliteReminderDao.ReminderRow, String> reminderStatusColumn;
    @FXML private Label reminderDetailTitleLabel;
    @FXML private Label reminderDetailIdLabel;
    @FXML private Label reminderDetailPatientIdLabel;
    @FXML private Label reminderDetailPatientNameLabel;
    @FXML private Label reminderDetailTypeLabel;
    @FXML private Label reminderDetailDueLabel;
    @FXML private Label reminderDetailAssignedLabel;
    @FXML private Label reminderDetailStatusLabel;
    @FXML private Label reminderDetailRepeatRuleLabel;
    @FXML private TextArea reminderDetailNotesArea;
    @FXML private Label statusLabel;

    @Override
    public void setAppShell(AppShell appShell) {
        this.appShell = appShell;
        configureAccess();
        configureFilters();
        configureTables();
        configureWriteButtons();
        if (isAuthorized()) {
            loadScheduling();
        }
    }

    public void openForPatient(String patientId) {
        patientIdFilter = patientId == null ? "" : patientId;
        updatePatientFilterChip();
        if (isAuthorized()) {
            loadScheduling();
        }
    }

    @FXML
    private void loadScheduling() {
        if (!isAuthorized()) {
            NotificationHelper.showError(statusLabel, "Access denied.");
            return;
        }
        try {
            SchedulingService.SchedulingOverview overview = schedulingService.loadOverview(
                    searchField.getText(),
                    appointmentTypeFilter.getValue(),
                    appointmentStatusFilter.getValue(),
                    reminderTypeFilter.getValue(),
                    reminderStatusFilter.getValue(),
                    patientIdFilter);
            appointmentsTodayLabel.setText(String.valueOf(overview.getAppointmentsToday()));
            upcomingSurgeriesLabel.setText(String.valueOf(overview.getUpcomingSurgeries()));
            overdueRemindersLabel.setText(String.valueOf(overview.getOverdueReminders()));
            patientRemindersTodayLabel.setText(String.valueOf(overview.getPatientRemindersToday()));
            cancelledMissedLabel.setText(String.valueOf(overview.getCancelledMissedItems()));
            SelectionHelper.runWhenTablesStable(() -> {
                SelectionHelper.safeReplaceItems(appointmentTable, appointments, overview.getAppointments());
                SelectionHelper.safeReplaceItems(reminderTable, reminders, overview.getReminders());
                showReminderDetail(null);
                NotificationHelper.showInfo(statusLabel, "Scheduling refreshed from the local database. Appointments: "
                        + appointments.size() + ", reminders: " + reminders.size());
            }, appointmentTable, reminderTable);
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, "Could not load scheduling data: " + e.getMessage());
        }
    }

    @FXML
    private void createAppointment() {
        if (!PermissionHelper.canManageAppointment(Session.getCurrentUser())) {
            NotificationHelper.showError(statusLabel, "Access denied. Admin or Doctor role is required.");
            return;
        }
        try {
            if (AppointmentFormController.showCreateDialog(appointmentTable.getScene().getWindow(), Session.getCurrentUser(), patientIdFilter)) {
                loadScheduling();
                NotificationHelper.showSuccess(statusLabel, "Appointment saved.");
            }
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
        }
    }

    @FXML
    private void editAppointment() {
        if (!PermissionHelper.canManageAppointment(Session.getCurrentUser())) {
            NotificationHelper.showError(statusLabel, "Access denied. Admin or Doctor role is required.");
            return;
        }
        SqliteAppointmentDao.AppointmentRow selected = selectedAppointment();
        if (selected == null) {
            return;
        }
        try {
            if (AppointmentFormController.showEditDialog(appointmentTable.getScene().getWindow(), Session.getCurrentUser(), selected)) {
                loadScheduling();
                NotificationHelper.showSuccess(statusLabel, "Appointment updated.");
            }
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
        }
    }

    @FXML
    private void completeAppointment() {
        SqliteAppointmentDao.AppointmentRow selected = selectedAppointment();
        if (selected == null) {
            return;
        }
        try {
            schedulingService.markAppointmentCompleted(Session.getCurrentUser(), selected.getId());
            loadScheduling();
            NotificationHelper.showSuccess(statusLabel, "Appointment marked completed.");
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
        }
    }

    @FXML
    private void cancelAppointment() {
        SqliteAppointmentDao.AppointmentRow selected = selectedAppointment();
        if (selected == null) {
            return;
        }
        if (!DialogHelper.confirm("Cancel Appointment", "Cancel appointment " + selected.getTitle() + "?")) {
            return;
        }
        try {
            schedulingService.cancelAppointment(Session.getCurrentUser(), selected.getId());
            loadScheduling();
            NotificationHelper.showSuccess(statusLabel, "Appointment cancelled.");
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
        }
    }

    @FXML
    private void createReminder() {
        if (!PermissionHelper.canManageReminder(Session.getCurrentUser())) {
            NotificationHelper.showError(statusLabel, "Access denied. Admin, Doctor, or Nurse role is required.");
            return;
        }
        try {
            if (ReminderFormController.showCreateDialog(reminderTable.getScene().getWindow(), Session.getCurrentUser(), patientIdFilter)) {
                loadScheduling();
                NotificationHelper.showSuccess(statusLabel, "Checkup reminder saved.");
            }
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
        }
    }

    @FXML
    private void editReminder() {
        if (!PermissionHelper.canManageReminder(Session.getCurrentUser())) {
            NotificationHelper.showError(statusLabel, "Access denied. Admin, Doctor, or Nurse role is required.");
            return;
        }
        SqliteReminderDao.ReminderRow selected = selectedReminder();
        if (selected == null) {
            return;
        }
        try {
            if (ReminderFormController.showEditDialog(reminderTable.getScene().getWindow(), Session.getCurrentUser(), selected)) {
                loadScheduling();
                NotificationHelper.showSuccess(statusLabel, "Checkup reminder updated.");
            }
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
        }
    }

    @FXML
    private void markReminderDone() {
        SqliteReminderDao.ReminderRow selected = selectedReminder();
        if (selected == null) {
            return;
        }
        try {
            schedulingService.markReminderDone(Session.getCurrentUser(), selected.getId());
            loadScheduling();
            NotificationHelper.showSuccess(statusLabel, "Checkup reminder marked done.");
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
        }
    }

    @FXML
    private void cancelReminder() {
        SqliteReminderDao.ReminderRow selected = selectedReminder();
        if (selected == null) {
            return;
        }
        if (!DialogHelper.confirm("Cancel Checkup", "Cancel checkup reminder " + selected.getTitle() + "?")) {
            return;
        }
        try {
            schedulingService.cancelReminder(Session.getCurrentUser(), selected.getId());
            loadScheduling();
            NotificationHelper.showSuccess(statusLabel, "Checkup reminder cancelled.");
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
        }
    }

    @FXML
    private void clearFilters() {
        searchField.clear();
        appointmentTypeFilter.getSelectionModel().select("All");
        appointmentStatusFilter.getSelectionModel().select("All");
        reminderTypeFilter.getSelectionModel().select("All");
        reminderStatusFilter.getSelectionModel().select("All");
        loadScheduling();
    }

    @FXML
    private void clearPatientFilter() {
        patientIdFilter = "";
        updatePatientFilterChip();
        loadScheduling();
    }

    @FXML
    private void showDashboard() {
        appShell.showDashboard(Session.getCurrentUser());
    }

    private void configureAccess() {
        boolean authorized = isAuthorized();
        accessDeniedPane.setVisible(!authorized);
        accessDeniedPane.setManaged(!authorized);
        contentPane.setVisible(authorized);
        contentPane.setManaged(authorized);
    }

    private void configureFilters() {
        appointmentTypeFilter.setItems(FXCollections.observableArrayList("All", "CHECKUP", "FOLLOW_UP", "LAB_TEST", "OTHER", "SURGERY"));
        appointmentStatusFilter.setItems(FXCollections.observableArrayList("All", "SCHEDULED", "COMPLETED", "CANCELLED", "MISSED"));
        reminderTypeFilter.setItems(FXCollections.observableArrayList("All", "APPOINTMENT", "CHECKUP", "FOLLOW_UP", "CUSTOM"));
        reminderStatusFilter.setItems(FXCollections.observableArrayList("All", "PENDING", "OVERDUE", "DONE", "MISSED", "CANCELLED"));
        appointmentTypeFilter.getSelectionModel().select("All");
        appointmentStatusFilter.getSelectionModel().select("All");
        reminderTypeFilter.getSelectionModel().select("All");
        reminderStatusFilter.getSelectionModel().select("All");
        searchField.textProperty().addListener((observable, oldValue, newValue) -> loadScheduling());
        appointmentTypeFilter.valueProperty().addListener((observable, oldValue, newValue) -> loadScheduling());
        appointmentStatusFilter.valueProperty().addListener((observable, oldValue, newValue) -> loadScheduling());
        reminderTypeFilter.valueProperty().addListener((observable, oldValue, newValue) -> loadScheduling());
        reminderStatusFilter.valueProperty().addListener((observable, oldValue, newValue) -> loadScheduling());
    }

    private void configureTables() {
        if (appointmentRowNumberColumn != null) {
            appointmentRowNumberColumn.setCellValueFactory(cell -> {
                int index = appointmentTable.getItems() == null ? -1 : appointmentTable.getItems().indexOf(cell.getValue());
                Number rowNumber = index >= 0 ? index + 1 : null;
                return new ReadOnlyObjectWrapper<>(rowNumber);
            });
        }
        appointmentIdColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        appointmentPatientIdColumn.setCellValueFactory(new PropertyValueFactory<>("patientId"));
        appointmentPatientNameColumn.setCellValueFactory(new PropertyValueFactory<>("patientName"));
        appointmentTitleColumn.setCellValueFactory(new PropertyValueFactory<>("title"));
        appointmentTypeColumn.setCellValueFactory(new PropertyValueFactory<>("appointmentType"));
        appointmentStartColumn.setCellValueFactory(new PropertyValueFactory<>("startTime"));
        appointmentEndColumn.setCellValueFactory(new PropertyValueFactory<>("endTime"));
        appointmentLocationColumn.setCellValueFactory(new PropertyValueFactory<>("location"));
        appointmentStaffColumn.setCellValueFactory(new PropertyValueFactory<>("assignedStaff"));
        appointmentStatusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));

        if (reminderRowNumberColumn != null) {
            reminderRowNumberColumn.setCellValueFactory(cell -> {
                int index = reminderTable.getItems() == null ? -1 : reminderTable.getItems().indexOf(cell.getValue());
                Number rowNumber = index >= 0 ? index + 1 : null;
                return new ReadOnlyObjectWrapper<>(rowNumber);
            });
        }
        reminderIdColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        reminderPatientIdColumn.setCellValueFactory(new PropertyValueFactory<>("patientId"));
        reminderPatientNameColumn.setCellValueFactory(new PropertyValueFactory<>("patientName"));
        reminderTypeColumn.setCellValueFactory(new PropertyValueFactory<>("reminderType"));
        reminderTitleColumn.setCellValueFactory(new PropertyValueFactory<>("title"));
        reminderDueColumn.setCellValueFactory(new PropertyValueFactory<>("dueTime"));
        reminderAssignedColumn.setCellValueFactory(new PropertyValueFactory<>("assignedTo"));
        reminderStatusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        reminderTable.getSelectionModel().selectedItemProperty()
                .addListener((observable, oldValue, newValue) -> showReminderDetail(newValue));
        showReminderDetail(null);
    }

    private void configureWriteButtons() {
        boolean canManageAppointments = PermissionHelper.canManageAppointment(Session.getCurrentUser());
        boolean canManageReminders = PermissionHelper.canManageReminder(Session.getCurrentUser());
        boolean canCompleteReminders = PermissionHelper.canCompleteReminder(Session.getCurrentUser());
        newAppointmentButton.setVisible(canManageAppointments);
        newAppointmentButton.setManaged(canManageAppointments);
        editAppointmentButton.setVisible(canManageAppointments);
        editAppointmentButton.setManaged(canManageAppointments);
        completeAppointmentButton.setVisible(canManageAppointments);
        completeAppointmentButton.setManaged(canManageAppointments);
        cancelAppointmentButton.setVisible(canManageAppointments);
        cancelAppointmentButton.setManaged(canManageAppointments);
        newReminderButton.setVisible(canManageReminders);
        newReminderButton.setManaged(canManageReminders);
        editReminderButton.setVisible(canManageReminders);
        editReminderButton.setManaged(canManageReminders);
        cancelReminderButton.setVisible(canManageReminders);
        cancelReminderButton.setManaged(canManageReminders);
        doneReminderButton.setVisible(canCompleteReminders);
        doneReminderButton.setManaged(canCompleteReminders);
        updatePatientFilterChip();
    }

    private void updatePatientFilterChip() {
        boolean filtered = patientIdFilter != null && !patientIdFilter.isBlank();
        patientFilterChip.setVisible(filtered);
        patientFilterChip.setManaged(filtered);
        clearPatientFilterButton.setVisible(filtered);
        clearPatientFilterButton.setManaged(filtered);
        patientFilterChip.setText(filtered ? "Patient ID = " + patientIdFilter : "");
    }

    private SqliteAppointmentDao.AppointmentRow selectedAppointment() {
        SqliteAppointmentDao.AppointmentRow selected = appointmentTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            NotificationHelper.showError(statusLabel, "Select an appointment first.");
            return null;
        }
        return selected;
    }

    private SqliteReminderDao.ReminderRow selectedReminder() {
        SqliteReminderDao.ReminderRow selected = reminderTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            NotificationHelper.showError(statusLabel, "Select a reminder first.");
            return null;
        }
        return selected;
    }

    private boolean isAuthorized() {
        return PermissionHelper.canViewScheduling(Session.getCurrentUser());
    }

    private void showReminderDetail(SqliteReminderDao.ReminderRow reminder) {
        if (reminder == null) {
            setLabel(reminderDetailTitleLabel, "Select a checkup");
            setLabel(reminderDetailIdLabel, "-");
            setLabel(reminderDetailPatientIdLabel, "-");
            setLabel(reminderDetailPatientNameLabel, "-");
            setLabel(reminderDetailTypeLabel, "-");
            setLabel(reminderDetailDueLabel, "-");
            setLabel(reminderDetailAssignedLabel, "-");
            setLabel(reminderDetailStatusLabel, "-");
            setLabel(reminderDetailRepeatRuleLabel, "-");
            setTextArea(reminderDetailNotesArea, "-");
            return;
        }
        setLabel(reminderDetailTitleLabel, nullTo(reminder.getTitle(), "Checkup"));
        setLabel(reminderDetailIdLabel, String.valueOf(reminder.getId()));
        setLabel(reminderDetailPatientIdLabel, nullTo(reminder.getPatientId(), "-"));
        setLabel(reminderDetailPatientNameLabel, nullTo(reminder.getPatientName(), "-"));
        setLabel(reminderDetailTypeLabel, nullTo(reminder.getReminderType(), "-"));
        setLabel(reminderDetailDueLabel, nullTo(reminder.getDueTime(), "-"));
        setLabel(reminderDetailAssignedLabel, nullTo(reminder.getAssignedTo(), "-"));
        setLabel(reminderDetailStatusLabel, nullTo(reminder.getStatus(), "-"));
        setLabel(reminderDetailRepeatRuleLabel, nullTo(reminder.getRepeatRule(), "-"));
        setTextArea(reminderDetailNotesArea, nullTo(reminder.getNotes(), "-"));
    }

    private String nullTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private void setLabel(Label label, String value) {
        if (label != null) {
            label.setText(value);
        }
    }

    private void setTextArea(TextArea textArea, String value) {
        if (textArea != null) {
            textArea.setText(value);
        }
    }
}
