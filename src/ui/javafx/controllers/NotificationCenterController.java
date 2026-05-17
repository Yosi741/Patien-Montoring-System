package ui.javafx.controllers;

import dao.SqliteNotificationDao;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import services.NotificationCenterService;
import ui.javafx.AppShell;
import ui.javafx.FxController;
import ui.javafx.helpers.NotificationHelper;
import ui.javafx.helpers.PermissionHelper;
import users.Session;

public class NotificationCenterController implements FxController {

    private final NotificationCenterService notificationService = new NotificationCenterService();
    private final ObservableList<SqliteNotificationDao.NotificationRow> rows = FXCollections.observableArrayList();
    private AppShell appShell;
    private Timeline refreshTimeline;

    @FXML private VBox accessDeniedPane;
    @FXML private VBox contentPane;
    @FXML private ComboBox<String> severityFilter;
    @FXML private ComboBox<String> statusFilter;
    @FXML private ComboBox<String> dateRangeFilter;
    @FXML private TextField patientSearchField;
    @FXML private TableView<SqliteNotificationDao.NotificationRow> notificationTable;
    @FXML private TableColumn<SqliteNotificationDao.NotificationRow, Long> idColumn;
    @FXML private TableColumn<SqliteNotificationDao.NotificationRow, String> severityColumn;
    @FXML private TableColumn<SqliteNotificationDao.NotificationRow, String> titleColumn;
    @FXML private TableColumn<SqliteNotificationDao.NotificationRow, String> statusColumn;
    @FXML private TableColumn<SqliteNotificationDao.NotificationRow, String> patientColumn;
    @FXML private TableColumn<SqliteNotificationDao.NotificationRow, String> sourceColumn;
    @FXML private TableColumn<SqliteNotificationDao.NotificationRow, String> createdColumn;
    @FXML private Label detailTitleLabel;
    @FXML private Label detailMetaLabel;
    @FXML private TextArea detailMessageArea;
    @FXML private Label statusLabel;

    @Override
    public void setAppShell(AppShell appShell) {
        this.appShell = appShell;
        configureAccess();
        configureFilters();
        configureTable();
        loadNotifications();
        startAutoRefresh();
    }

    @FXML
    private void loadNotifications() {
        if (!PermissionHelper.canViewNotifications(Session.getCurrentUser())) {
            return;
        }
        try {
            rows.setAll(notificationService.findForCurrentUser(
                    Session.getCurrentUser(),
                    severityFilter.getValue(),
                    statusFilter.getValue(),
                    patientSearchField.getText(),
                    dateRangeFilter.getValue()
            ));
            notificationTable.setItems(rows);
            NotificationHelper.showInfo(statusLabel, "Notifications loaded: " + rows.size());
            if (!rows.isEmpty() && notificationTable.getSelectionModel().isEmpty()) {
                notificationTable.getSelectionModel().selectFirst();
                showDetail(rows.get(0));
            } else if (rows.isEmpty()) {
                clearDetail();
            }
            appShell.refreshNotificationCount();
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, "Could not load notifications: " + e.getMessage());
        }
    }

    @FXML
    private void markRead() {
        SqliteNotificationDao.NotificationRow row = selectedRow();
        if (row == null) {
            return;
        }
        try {
            notificationService.markRead(Session.getCurrentUser(), row.getId());
            loadNotifications();
            NotificationHelper.showSuccess(statusLabel, "Notification marked read.");
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, "Could not mark read: " + e.getMessage());
        }
    }

    @FXML
    private void dismissNotification() {
        SqliteNotificationDao.NotificationRow row = selectedRow();
        if (row == null) {
            return;
        }
        try {
            notificationService.dismiss(Session.getCurrentUser(), row.getId());
            loadNotifications();
            NotificationHelper.showSuccess(statusLabel, "Notification dismissed.");
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, "Could not dismiss: " + e.getMessage());
        }
    }

    @FXML
    private void openLinkedItem() {
        SqliteNotificationDao.NotificationRow row = selectedRow();
        if (row == null) {
            return;
        }
        if (row.getPatientId() != null && !row.getPatientId().isBlank()) {
            if ("ALERT".equalsIgnoreCase(row.getSourceType()) && row.getSourceId() != null && row.getSourceId().matches("\\d+")) {
                appShell.showAlertCenterForAlert(Long.parseLong(row.getSourceId()));
            } else if ("REMINDER".equalsIgnoreCase(row.getSourceType()) || "SCHEDULING".equalsIgnoreCase(row.getSourceType())) {
                appShell.showSchedulingForPatient(row.getPatientId());
            } else {
                appShell.showPatientDetail(row.getPatientId());
            }
        }
    }

    @FXML
    private void showDashboard() {
        appShell.showDashboard(Session.getCurrentUser());
    }

    private void configureAccess() {
        boolean allowed = PermissionHelper.canViewNotifications(Session.getCurrentUser());
        accessDeniedPane.setVisible(!allowed);
        accessDeniedPane.setManaged(!allowed);
        contentPane.setVisible(allowed);
        contentPane.setManaged(allowed);
    }

    private void configureFilters() {
        severityFilter.setItems(FXCollections.observableArrayList("All", "INFO", "WARNING", "CRITICAL"));
        statusFilter.setItems(FXCollections.observableArrayList("All", "UNREAD", "READ", "DISMISSED"));
        dateRangeFilter.setItems(FXCollections.observableArrayList("All", "Today", "Last 7 days", "Last 30 days"));
        severityFilter.getSelectionModel().select("All");
        statusFilter.getSelectionModel().select("All");
        dateRangeFilter.getSelectionModel().select("All");
        severityFilter.valueProperty().addListener((obs, old, value) -> loadNotifications());
        statusFilter.valueProperty().addListener((obs, old, value) -> loadNotifications());
        dateRangeFilter.valueProperty().addListener((obs, old, value) -> loadNotifications());
        patientSearchField.textProperty().addListener((obs, old, value) -> loadNotifications());
    }

    private void configureTable() {
        idColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        severityColumn.setCellValueFactory(new PropertyValueFactory<>("severity"));
        titleColumn.setCellValueFactory(new PropertyValueFactory<>("title"));
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        patientColumn.setCellValueFactory(new PropertyValueFactory<>("patientId"));
        sourceColumn.setCellValueFactory(new PropertyValueFactory<>("sourceType"));
        createdColumn.setCellValueFactory(new PropertyValueFactory<>("createdAt"));
        notificationTable.getSelectionModel().selectedItemProperty().addListener((obs, old, row) -> showDetail(row));
    }

    private void showDetail(SqliteNotificationDao.NotificationRow row) {
        if (row == null) {
            clearDetail();
            return;
        }
        detailTitleLabel.setText(row.getTitle());
        detailMetaLabel.setText(row.getSeverity() + " | " + row.getStatus() + " | " + row.getTargetSummary()
                + " | Patient " + nullTo(row.getPatientId(), "-") + " | " + row.getCreatedAt());
        detailMessageArea.setText(row.getMessage());
    }

    private void clearDetail() {
        detailTitleLabel.setText("Select a notification");
        detailMetaLabel.setText("-");
        detailMessageArea.clear();
    }

    private SqliteNotificationDao.NotificationRow selectedRow() {
        SqliteNotificationDao.NotificationRow row = notificationTable.getSelectionModel().getSelectedItem();
        if (row == null) {
            NotificationHelper.showError(statusLabel, "Select a notification first.");
        }
        return row;
    }

    private void startAutoRefresh() {
        refreshTimeline = new Timeline(new KeyFrame(Duration.seconds(25), event -> loadNotifications()));
        refreshTimeline.setCycleCount(Timeline.INDEFINITE);
        refreshTimeline.play();
    }

    private String nullTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
