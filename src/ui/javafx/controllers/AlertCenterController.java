package ui.javafx.controllers;

import dao.SqliteAlertDao;
import dao.SqliteAuditLogDao;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.Button;
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
import ui.javafx.helpers.SelectionHelper;
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
    @FXML private Label activeCountLabel;
    @FXML private Label criticalCountLabel;
    @FXML private Label warningCountLabel;
    @FXML private Label completedCountLabel;
    @FXML private TableView<SqliteAlertDao.AlertRow> alertTable;
    @FXML private TableColumn<SqliteAlertDao.AlertRow, String> severityColumn;
    @FXML private TableColumn<SqliteAlertDao.AlertRow, String> patientColumn;
    @FXML private TableColumn<SqliteAlertDao.AlertRow, String> messageColumn;
    @FXML private TableColumn<SqliteAlertDao.AlertRow, String> alertStatusColumn;
    @FXML private TableColumn<SqliteAlertDao.AlertRow, String> createdAtColumn;
    @FXML private Label detailTitleLabel;
    @FXML private Label detailPatientLabel;
    @FXML private Label detailSeverityLabel;
    @FXML private Label detailStatusLabel;
    @FXML private Label detailCreatedLabel;
    @FXML private Label detailUpdatedLabel;
    @FXML private Label detailAcknowledgedLabel;
    @FXML private TextArea detailMessageArea;
    @FXML private TextArea recommendedActionArea;
    @FXML private Button acknowledgeButton;
    @FXML private Button acknowledgeDetailButton;
    @FXML private Button resolveButton;
    @FXML private Button openPatientFileButton;

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
            SelectionHelper.safeClearSelection(alertTable);
            alerts.setAll(alertDao.findAlertRows(severityFilter.getValue(), statusFilter.getValue(), searchField.getText(), patientIdFilter));
            alertTable.setItems(alerts);
            renderPatientFilterChip();
            renderSummaryCounters();
            statusLabel.setText("Alerts loaded: " + alerts.size()
                    + (patientIdFilter == null || patientIdFilter.isBlank() ? "" : " for patient " + patientIdFilter));
            if (selectedId != null && selectAlertById(selectedId)) {
                preselectedAlertId = null;
                return;
            }
            if (!alerts.isEmpty()) {
                SelectionHelper.safeSelectFirst(alertTable);
                showDetail(alerts.get(0));
            } else {
                SelectionHelper.safeClearSelection(alertTable);
                clearDetail();
            }
            updateActionButtons();
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
    private void resolveSelected() {
        SqliteAlertDao.AlertRow selected = alertTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            statusLabel.setText("Select an alert first.");
            return;
        }
        try {
            alertDao.resolve(selected.getId(), Session.getUsername());
            AlertSoundService.stopAlertSound();
            logAudit("JavaFX ALERT resolve alert #" + selected.getId() + " for patient " + selected.getPatientId());
            loadAlerts();
            alertDao.findAlertRowById(selected.getId()).ifPresent(alert -> {
                showDetail(alert);
                selectAlertById(alert.getId());
            });
            statusLabel.setText("SQLite alert resolved. JavaFX alert sound stopped if it was active.");
        } catch (Exception e) {
            statusLabel.setText("Could not resolve alert: " + e.getMessage());
        }
    }

    @FXML
    private void openSelectedPatientFile() {
        SqliteAlertDao.AlertRow selected = alertTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            statusLabel.setText("Select an alert first.");
            return;
        }
        if (selected.getPatientId() == null || selected.getPatientId().isBlank()) {
            statusLabel.setText("This alert has no patient ID to open.");
            return;
        }
        appShell.showPatientDetail(selected.getPatientId());
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
        severityColumn.setCellValueFactory(new PropertyValueFactory<>("severity"));
        patientColumn.setCellValueFactory(cell -> new SimpleStringProperty(formatPatient(cell.getValue())));
        messageColumn.setCellValueFactory(new PropertyValueFactory<>("message"));
        alertStatusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        createdAtColumn.setCellValueFactory(new PropertyValueFactory<>("createdAt"));

        severityColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String severity, boolean empty) {
                super.updateItem(severity, empty);
                if (empty || severity == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                setText(null);
                setGraphic(badge(severity, severityStyle(severity), "alert-badge-compact"));
            }
        });

        alertStatusColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                setText(null);
                setGraphic(badge(status, statusStyle(status), "alert-badge-compact"));
            }
        });

        messageColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String message, boolean empty) {
                super.updateItem(message, empty);
                setGraphic(null);
                if (empty || message == null) {
                    setText(null);
                    return;
                }
                setText(message);
                setWrapText(false);
                setTextOverrun(javafx.scene.control.OverrunStyle.ELLIPSIS);
            }
        });

        alertTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                showDetail(newValue);
            } else {
                clearDetail();
            }
            updateActionButtons();
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
        detailPatientLabel.setText(formatPatient(alert));
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
        updateActionButtons();
    }

    private boolean selectAlertById(long alertId) {
        for (int i = 0; i < alerts.size(); i++) {
            if (alerts.get(i).getId() == alertId) {
                if (!SelectionHelper.safeSelectIndex(alertTable, i)) {
                    return false;
                }
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
        updateActionButtons();
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

    private void renderSummaryCounters() {
        int active = 0;
        int critical = 0;
        int warning = 0;
        int completed = 0;
        for (SqliteAlertDao.AlertRow alert : alerts) {
            if ("ACTIVE".equalsIgnoreCase(alert.getStatus())) {
                active++;
            }
            if ("CRITICAL".equalsIgnoreCase(alert.getSeverity()) || "EMERGENCY".equalsIgnoreCase(alert.getSeverity())) {
                critical++;
            }
            if ("WARNING".equalsIgnoreCase(alert.getSeverity())) {
                warning++;
            }
            if ("ACKNOWLEDGED".equalsIgnoreCase(alert.getStatus()) || "RESOLVED".equalsIgnoreCase(alert.getStatus())) {
                completed++;
            }
        }
        activeCountLabel.setText(String.valueOf(active));
        criticalCountLabel.setText(String.valueOf(critical));
        warningCountLabel.setText(String.valueOf(warning));
        completedCountLabel.setText(String.valueOf(completed));
    }

    private void updateActionButtons() {
        SqliteAlertDao.AlertRow selected = alertTable == null ? null : alertTable.getSelectionModel().getSelectedItem();
        boolean hasSelection = selected != null;
        boolean active = hasSelection && "ACTIVE".equalsIgnoreCase(selected.getStatus());
        setDisabled(acknowledgeButton, !active);
        setDisabled(acknowledgeDetailButton, !active);
        setDisabled(resolveButton, !active);
        setDisabled(openPatientFileButton, !hasSelection || selected.getPatientId() == null || selected.getPatientId().isBlank());
    }

    private void setDisabled(Button button, boolean disabled) {
        if (button != null) {
            button.setDisable(disabled);
        }
    }

    private Label badge(String text, String style, String extraStyle) {
        Label label = new Label(text);
        label.getStyleClass().addAll(style, extraStyle);
        return label;
    }

    private String formatPatient(SqliteAlertDao.AlertRow alert) {
        String patientId = alert.getPatientId() == null || alert.getPatientId().isBlank() ? "No patient ID" : alert.getPatientId();
        return patientId + " | " + alert.getPatientName();
    }

    private void startAutoRefresh() {
        if (refreshTimeline != null) {
            refreshTimeline.stop();
        }
        refreshTimeline = new Timeline(new KeyFrame(Duration.seconds(15), event -> loadAlerts()));
        refreshTimeline.setCycleCount(Timeline.INDEFINITE);
        refreshTimeline.play();
    }

    @Override
    public void dispose() {
        if (refreshTimeline != null) {
            refreshTimeline.stop();
            refreshTimeline = null;
        }
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
