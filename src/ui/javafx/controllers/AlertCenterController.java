package ui.javafx.controllers;

import dao.SqliteAlertDao;
import dao.SqliteAuditLogDao;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.util.Duration;
import services.AlertSoundService;
import ui.javafx.AppShell;
import ui.javafx.FxController;
import users.Session;

public class AlertCenterController implements FxController {

    private final SqliteAlertDao alertDao = new SqliteAlertDao();
    private final SqliteAuditLogDao auditLogDao = new SqliteAuditLogDao();
    private final ObservableList<SqliteAlertDao.AlertRow> alerts = FXCollections.observableArrayList();
    private AppShell appShell;
    private Timeline refreshTimeline;
    private Long preselectedAlertId;
    private String patientIdFilter;
    private boolean suppressFilterEvents;

    @FXML private ComboBox<String> severityFilter;
    @FXML private ComboBox<String> statusFilter;
    @FXML private TextField searchField;
    @FXML private HBox patientFilterBox;
    @FXML private Label patientFilterChipLabel;
    @FXML private Label statusLabel;
    @FXML private TableView<SqliteAlertDao.AlertRow> alertTable;
    @FXML private TableColumn<SqliteAlertDao.AlertRow, Long> idColumn;
    @FXML private TableColumn<SqliteAlertDao.AlertRow, String> patientIdColumn;
    @FXML private TableColumn<SqliteAlertDao.AlertRow, String> patientNameColumn;
    @FXML private TableColumn<SqliteAlertDao.AlertRow, String> severityColumn;
    @FXML private TableColumn<SqliteAlertDao.AlertRow, String> messageColumn;
    @FXML private TableColumn<SqliteAlertDao.AlertRow, String> alertStatusColumn;
    @FXML private TableColumn<SqliteAlertDao.AlertRow, String> createdAtColumn;
    @FXML private TableColumn<SqliteAlertDao.AlertRow, String> updatedAtColumn;
    @FXML private TableColumn<SqliteAlertDao.AlertRow, String> acknowledgedByColumn;
    @FXML private TableColumn<SqliteAlertDao.AlertRow, String> acknowledgedAtColumn;
    @FXML private Label detailTitleLabel;
    @FXML private Label detailPatientLabel;
    @FXML private Label detailSeverityLabel;
    @FXML private Label detailStatusLabel;
    @FXML private Label detailCreatedLabel;
    @FXML private Label detailUpdatedLabel;
    @FXML private Label detailAcknowledgedLabel;
    @FXML private TextArea detailMessageArea;
    @FXML private TextArea recommendedActionArea;

    @Override
    public void setAppShell(AppShell appShell) {
        this.appShell = appShell;
        configureFilters();
        configureTable();
        seedDemoAlerts();
        loadAlerts();
        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!suppressFilterEvents) {
                loadAlerts();
            }
        });
        startAutoRefresh();
    }

    @FXML
    private void loadAlerts() {
        try {
            Long selectedId = preselectedAlertId;
            SqliteAlertDao.AlertRow currentSelection = alertTable.getSelectionModel().getSelectedItem();
            if (selectedId == null && currentSelection != null) {
                selectedId = currentSelection.getId();
            }
            alerts.setAll(alertDao.findAlertRows(severityFilter.getValue(), statusFilter.getValue(), searchField.getText(), patientIdFilter));
            alertTable.setItems(alerts);
            renderPatientFilterChip();
            statusLabel.setText("SQLite alerts loaded: " + alerts.size()
                    + (patientIdFilter == null || patientIdFilter.isBlank() ? "" : " for patient " + patientIdFilter));
            if (selectedId != null && selectAlertById(selectedId)) {
                preselectedAlertId = null;
                return;
            }
            if (!alerts.isEmpty()) {
                alertTable.getSelectionModel().selectFirst();
                showDetail(alerts.get(0));
            } else {
                clearDetail();
            }
        } catch (Exception e) {
            statusLabel.setText("Could not load alerts: " + e.getMessage());
        }
    }

    @FXML
    private void acknowledgeSelected() {
        SqliteAlertDao.AlertRow selected = alertTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            statusLabel.setText("Select an alert first.");
            return;
        }
        try {
            alertDao.acknowledge(selected.getId(), Session.getUsername());
            AlertSoundService.stopAlertSound();
            logAudit("JavaFX ALERT acknowledge alert #" + selected.getId() + " for patient " + selected.getPatientId());
            loadAlerts();
            alertDao.findAlertRowById(selected.getId()).ifPresent(alert -> {
                showDetail(alert);
                selectAlertById(alert.getId());
            });
            statusLabel.setText("SQLite alert acknowledged. JavaFX alert sound stopped if it was active.");
        } catch (Exception e) {
            statusLabel.setText("Could not acknowledge alert: " + e.getMessage());
        }
    }

    @FXML
    private void showDashboard() {
        appShell.showDashboard(Session.getCurrentUser());
    }

    @FXML
    private void clearPatientFilter() {
        patientIdFilter = null;
        preselectedAlertId = null;
        loadAlerts();
    }

    public void openWithAlert(long alertId) {
        this.preselectedAlertId = alertId;
        this.patientIdFilter = null;
        loadAlerts();
    }

    public void openForPatient(String patientId) {
        this.patientIdFilter = patientId;
        this.preselectedAlertId = null;
        suppressFilterEvents = true;
        severityFilter.getSelectionModel().select("All");
        statusFilter.getSelectionModel().select("All");
        searchField.clear();
        suppressFilterEvents = false;
        loadAlerts();
    }

    private void configureFilters() {
        severityFilter.setItems(FXCollections.observableArrayList("All", "WARNING", "CRITICAL", "EMERGENCY"));
        statusFilter.setItems(FXCollections.observableArrayList("All", "ACTIVE", "ACKNOWLEDGED", "RESOLVED"));
        severityFilter.getSelectionModel().select("All");
        statusFilter.getSelectionModel().select("All");
        severityFilter.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (!suppressFilterEvents) {
                loadAlerts();
            }
        });
        statusFilter.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (!suppressFilterEvents) {
                loadAlerts();
            }
        });
    }

    private void configureTable() {
        idColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        patientIdColumn.setCellValueFactory(new PropertyValueFactory<>("patientId"));
        patientNameColumn.setCellValueFactory(new PropertyValueFactory<>("patientName"));
        severityColumn.setCellValueFactory(new PropertyValueFactory<>("severity"));
        messageColumn.setCellValueFactory(new PropertyValueFactory<>("message"));
        alertStatusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        createdAtColumn.setCellValueFactory(new PropertyValueFactory<>("createdAt"));
        updatedAtColumn.setCellValueFactory(new PropertyValueFactory<>("updatedAt"));
        acknowledgedByColumn.setCellValueFactory(new PropertyValueFactory<>("acknowledgedBy"));
        acknowledgedAtColumn.setCellValueFactory(new PropertyValueFactory<>("acknowledgedAt"));

        severityColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String severity, boolean empty) {
                super.updateItem(severity, empty);
                getStyleClass().removeAll("severity-warning", "severity-critical", "severity-emergency");
                if (empty || severity == null) {
                    setText(null);
                    return;
                }
                setText(severity);
                getStyleClass().add(severityStyle(severity));
            }
        });

        alertStatusColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                getStyleClass().removeAll("alert-active", "alert-acknowledged", "alert-resolved");
                if (empty || status == null) {
                    setText(null);
                    return;
                }
                setText(status);
                getStyleClass().add(statusStyle(status));
            }
        });

        alertTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                showDetail(newValue);
            }
        });

        alertTable.setRowFactory(table -> new TableRow<>() {
            @Override
            protected void updateItem(SqliteAlertDao.AlertRow row, boolean empty) {
                super.updateItem(row, empty);
                getStyleClass().removeAll("active-alert-row");
                if (!empty && row != null && "ACTIVE".equalsIgnoreCase(row.getStatus())) {
                    getStyleClass().add("active-alert-row");
                }
            }
        });
    }

    private void showDetail(SqliteAlertDao.AlertRow alert) {
        detailTitleLabel.setText("Alert #" + alert.getId());
        detailPatientLabel.setText(alert.getPatientId() + " | " + alert.getPatientName());
        detailSeverityLabel.setText(alert.getSeverity());
        detailStatusLabel.setText(alert.getStatus());
        detailCreatedLabel.setText(alert.getCreatedAt());
        detailUpdatedLabel.setText(alert.getUpdatedAt());
        detailAcknowledgedLabel.setText(alert.getAcknowledgedBy() + " at " + alert.getAcknowledgedAt());
        detailMessageArea.setText(alert.getMessage());
        recommendedActionArea.setText(recommendedAction(alert));

        detailSeverityLabel.getStyleClass().removeAll("severity-warning", "severity-critical", "severity-emergency");
        detailSeverityLabel.getStyleClass().add(severityStyle(alert.getSeverity()));
        detailStatusLabel.getStyleClass().removeAll("alert-active", "alert-acknowledged", "alert-resolved");
        detailStatusLabel.getStyleClass().add(statusStyle(alert.getStatus()));
    }

    private boolean selectAlertById(long alertId) {
        for (int i = 0; i < alerts.size(); i++) {
            if (alerts.get(i).getId() == alertId) {
                alertTable.getSelectionModel().select(i);
                alertTable.scrollTo(i);
                showDetail(alerts.get(i));
                return true;
            }
        }
        return false;
    }

    private void clearDetail() {
        detailTitleLabel.setText("Alert Detail");
        detailPatientLabel.setText("-");
        detailSeverityLabel.setText("-");
        detailStatusLabel.setText("-");
        detailCreatedLabel.setText("-");
        detailUpdatedLabel.setText("-");
        detailAcknowledgedLabel.setText("-");
        detailMessageArea.clear();
        recommendedActionArea.clear();
    }

    private void renderPatientFilterChip() {
        boolean hasPatientFilter = patientIdFilter != null && !patientIdFilter.isBlank();
        patientFilterBox.setVisible(hasPatientFilter);
        patientFilterBox.setManaged(hasPatientFilter);
        patientFilterChipLabel.setText(hasPatientFilter ? "Patient ID = " + patientIdFilter : "");
    }

    private void seedDemoAlerts() {
        try {
            alertDao.seedDemoAlertsIfEmpty();
        } catch (Exception e) {
            statusLabel.setText("Demo alert seeding skipped: " + e.getMessage());
        }
    }

    private void startAutoRefresh() {
        refreshTimeline = new Timeline(new KeyFrame(Duration.seconds(15), event -> loadAlerts()));
        refreshTimeline.setCycleCount(Timeline.INDEFINITE);
        refreshTimeline.play();
    }

    private String recommendedAction(SqliteAlertDao.AlertRow alert) {
        if ("EMERGENCY".equalsIgnoreCase(alert.getSeverity())) {
            return "Recommended action placeholder: call urgent clinical review, verify current vitals, and prepare escalation workflow.";
        }
        if ("CRITICAL".equalsIgnoreCase(alert.getSeverity())) {
            return "Recommended action placeholder: bedside assessment, repeat vitals, check device connection, and notify responsible doctor.";
        }
        return "Recommended action placeholder: review trend, repeat measurement if needed, and monitor during next round.";
    }

    private String severityStyle(String severity) {
        if ("EMERGENCY".equalsIgnoreCase(severity)) {
            return "severity-emergency";
        }
        if ("CRITICAL".equalsIgnoreCase(severity)) {
            return "severity-critical";
        }
        return "severity-warning";
    }

    private String statusStyle(String status) {
        if ("ACKNOWLEDGED".equalsIgnoreCase(status)) {
            return "alert-acknowledged";
        }
        if ("RESOLVED".equalsIgnoreCase(status)) {
            return "alert-resolved";
        }
        return "alert-active";
    }

    private void logAudit(String action) {
        try {
            auditLogDao.log(Session.getUsername(), action);
        } catch (Exception e) {
            System.out.println("SQLite alert audit skipped: " + e.getMessage());
        }
    }
}
