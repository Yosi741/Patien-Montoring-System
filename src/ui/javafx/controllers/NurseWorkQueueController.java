package ui.javafx.controllers;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import services.NurseWorkQueueService;
import services.ReminderEngineService;
import services.SchedulingService;
import ui.javafx.AppShell;
import ui.javafx.FxController;
import ui.javafx.SessionContext;
import ui.javafx.helpers.AuditAction;
import ui.javafx.helpers.AuditWriteHelper;
import ui.javafx.helpers.NotificationHelper;
import ui.javafx.helpers.PermissionHelper;
import users.Session;

public class NurseWorkQueueController implements FxController {

    private final NurseWorkQueueService workQueueService = new NurseWorkQueueService();
    private final SchedulingService schedulingService = new SchedulingService();
    private final ReminderEngineService reminderEngineService = new ReminderEngineService();
    private final ObservableList<NurseWorkQueueService.WorkQueueTask> tasks = FXCollections.observableArrayList();
    private AppShell appShell;
    private Timeline refreshTimeline;

    @FXML private VBox accessDeniedPane;
    @FXML private VBox contentPane;
    @FXML private Label overdueRemindersLabel;
    @FXML private Label upcomingRemindersLabel;
    @FXML private Label totalTasksLabel;
    @FXML private Label criticalAlertsLabel;
    @FXML private Label missingVitalsLabel;
    @FXML private Label notificationLabel;
    @FXML private Label statusLabel;
    @FXML private Button doneButton;
    @FXML private Button missedButton;
    @FXML private Button patientButton;
    @FXML private Button scheduleButton;
    @FXML private Button alertButton;
    @FXML private TableView<NurseWorkQueueService.WorkQueueTask> queueTable;
    @FXML private TableColumn<NurseWorkQueueService.WorkQueueTask, String> statusColumn;
    @FXML private TableColumn<NurseWorkQueueService.WorkQueueTask, String> typeColumn;
    @FXML private TableColumn<NurseWorkQueueService.WorkQueueTask, String> patientIdColumn;
    @FXML private TableColumn<NurseWorkQueueService.WorkQueueTask, String> patientNameColumn;
    @FXML private TableColumn<NurseWorkQueueService.WorkQueueTask, String> sectionColumn;
    @FXML private TableColumn<NurseWorkQueueService.WorkQueueTask, String> roomColumn;
    @FXML private TableColumn<NurseWorkQueueService.WorkQueueTask, String> dueColumn;
    @FXML private TableColumn<NurseWorkQueueService.WorkQueueTask, String> severityColumn;
    @FXML private TableColumn<NurseWorkQueueService.WorkQueueTask, String> titleColumn;

    @Override
    public void setAppShell(AppShell appShell) {
        this.appShell = appShell;
        configureAccess();
        configureTable();
        configureButtons();
        if (isAuthorized()) {
            logOpen();
            refreshQueue();
            startAutoRefresh();
        }
    }

    @FXML
    private void refreshQueue() {
        if (!isAuthorized()) {
            NotificationHelper.showError(statusLabel, "Access denied.");
            return;
        }
        try {
            NurseWorkQueueService.WorkQueueOverview overview = workQueueService.loadQueue(SessionContext.username());
            overdueRemindersLabel.setText(String.valueOf(overview.getOverdueReminders()));
            upcomingRemindersLabel.setText(String.valueOf(overview.getUpcomingReminders()));
            totalTasksLabel.setText(String.valueOf(overview.getTotalTasks()));
            criticalAlertsLabel.setText(String.valueOf(overview.getCriticalAlerts()));
            missingVitalsLabel.setText(String.valueOf(overview.getMissingVitals()));
            tasks.setAll(overview.getTasks());
            queueTable.setItems(tasks);
            renderReminderNotifications();
            NotificationHelper.showInfo(statusLabel, "Work Queue refreshed from SQLite: " + tasks.size() + " tasks.");
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, "Could not load Work Queue: " + e.getMessage());
        }
    }

    @FXML
    private void markSelectedDone() {
        NurseWorkQueueService.WorkQueueTask selected = selectedTask();
        if (selected == null) {
            return;
        }
        if (!selected.isReminderTask()) {
            NotificationHelper.showError(statusLabel, "Only reminder tasks can be marked done here.");
            return;
        }
        try {
            schedulingService.markReminderDone(Session.getCurrentUser(), selected.getSourceId());
            refreshQueue();
            NotificationHelper.showSuccess(statusLabel, "Reminder marked done in SQLite.");
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
        }
    }

    @FXML
    private void markSelectedMissed() {
        NurseWorkQueueService.WorkQueueTask selected = selectedTask();
        if (selected == null) {
            return;
        }
        if (!selected.isReminderTask()) {
            NotificationHelper.showError(statusLabel, "Only reminder tasks can be marked missed here.");
            return;
        }
        try {
            schedulingService.markReminderMissed(Session.getCurrentUser(), selected.getSourceId());
            refreshQueue();
            NotificationHelper.showSuccess(statusLabel, "Reminder marked missed in SQLite.");
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
        }
    }

    @FXML
    private void openPatientDetail() {
        NurseWorkQueueService.WorkQueueTask selected = selectedTask();
        if (selected != null && selected.getPatientId() != null && !selected.getPatientId().isBlank()) {
            appShell.showPatientDetail(selected.getPatientId());
        }
    }

    @FXML
    private void openScheduling() {
        NurseWorkQueueService.WorkQueueTask selected = selectedTask();
        if (selected != null && selected.getPatientId() != null && !selected.getPatientId().isBlank()) {
            appShell.showSchedulingForPatient(selected.getPatientId());
        }
    }

    @FXML
    private void openAlertCenter() {
        NurseWorkQueueService.WorkQueueTask selected = selectedTask();
        if (selected == null) {
            return;
        }
        if (selected.isAlertTask() && selected.getSourceId() > 0) {
            appShell.showAlertCenterForAlert(selected.getSourceId());
        } else if (selected.getPatientId() != null && !selected.getPatientId().isBlank()) {
            appShell.showAlertCenterForPatient(selected.getPatientId());
        }
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

    private void configureTable() {
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("actionStatus"));
        typeColumn.setCellValueFactory(new PropertyValueFactory<>("taskType"));
        patientIdColumn.setCellValueFactory(new PropertyValueFactory<>("patientId"));
        patientNameColumn.setCellValueFactory(new PropertyValueFactory<>("patientName"));
        sectionColumn.setCellValueFactory(new PropertyValueFactory<>("section"));
        roomColumn.setCellValueFactory(new PropertyValueFactory<>("room"));
        dueColumn.setCellValueFactory(new PropertyValueFactory<>("dueTime"));
        severityColumn.setCellValueFactory(new PropertyValueFactory<>("severity"));
        titleColumn.setCellValueFactory(new PropertyValueFactory<>("title"));
        queueTable.setRowFactory(table -> new TableRow<>() {
            @Override
            protected void updateItem(NurseWorkQueueService.WorkQueueTask item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("work-queue-overdue-row", "work-queue-alert-row");
                if (empty || item == null) {
                    return;
                }
                if ("OVERDUE".equalsIgnoreCase(item.getActionStatus()) || "EMERGENCY".equalsIgnoreCase(item.getSeverity())) {
                    getStyleClass().add("work-queue-overdue-row");
                } else if (item.isAlertTask()) {
                    getStyleClass().add("work-queue-alert-row");
                }
            }
        });
    }

    private void configureButtons() {
        boolean canMark = PermissionHelper.canCompleteReminder(Session.getCurrentUser());
        doneButton.setVisible(canMark);
        doneButton.setManaged(canMark);
        missedButton.setVisible(canMark);
        missedButton.setManaged(canMark);
        queueTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> updateActionButtons(newValue));
        updateActionButtons(null);
    }

    private void updateActionButtons(NurseWorkQueueService.WorkQueueTask task) {
        boolean hasTask = task != null;
        boolean reminder = hasTask && task.isReminderTask();
        boolean alert = hasTask && task.isAlertTask();
        doneButton.setDisable(!reminder);
        missedButton.setDisable(!reminder);
        patientButton.setDisable(!hasTask || task.getPatientId() == null || task.getPatientId().isBlank());
        scheduleButton.setDisable(!hasTask || task.getPatientId() == null || task.getPatientId().isBlank());
        alertButton.setDisable(!hasTask || (!alert && (task.getPatientId() == null || task.getPatientId().isBlank())));
    }

    private void renderReminderNotifications() {
        try {
            var notifications = reminderEngineService.loadNotifications(SessionContext.username());
            if (notifications.isEmpty()) {
                NotificationHelper.showInfo(notificationLabel, "No new local reminder notifications.");
                return;
            }
            ReminderEngineService.ReminderNotification first = notifications.get(0);
            String prefix = "OVERDUE".equalsIgnoreCase(first.getStatus()) ? "Overdue" : "Upcoming";
            NotificationHelper.showError(notificationLabel, prefix + " " + first.getReminderType()
                    + " reminder for patient " + first.getPatientId() + ": " + first.getTitle()
                    + " (" + first.getDueTime() + ")");
        } catch (Exception e) {
            NotificationHelper.showError(notificationLabel, "Reminder notifications unavailable: " + e.getMessage());
        }
    }

    private void startAutoRefresh() {
        refreshTimeline = new Timeline(new KeyFrame(Duration.seconds(30), event -> refreshQueue()));
        refreshTimeline.setCycleCount(Timeline.INDEFINITE);
        refreshTimeline.play();
    }

    private NurseWorkQueueService.WorkQueueTask selectedTask() {
        NurseWorkQueueService.WorkQueueTask selected = queueTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            NotificationHelper.showError(statusLabel, "Select a Work Queue task first.");
            return null;
        }
        return selected;
    }

    private void logOpen() {
        try {
            AuditWriteHelper.write(SessionContext.username(), AuditAction.OPEN_WORK_QUEUE, "JavaFX Nurse Work Queue opened");
        } catch (Exception e) {
            System.out.println("SQLite Work Queue audit skipped: " + e.getMessage());
        }
    }

    private boolean isAuthorized() {
        return PermissionHelper.canViewWorkQueue(Session.getCurrentUser());
    }
}
