package ui.javafx.controllers;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import services.DashboardMetricsService;
import ui.javafx.AppShell;
import ui.javafx.FxController;
import users.Session;
import users.User;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

public class DashboardController implements FxController {

    private final DashboardMetricsService metricsService = new DashboardMetricsService();
    private AppShell appShell;
    private Timeline refreshTimeline;

    @FXML private Label welcomeLabel;
    @FXML private Label roleLabel;
    @FXML private Label databaseStatusLabel;
    @FXML private Label migrationStatusLabel;
    @FXML private Label refreshStatusLabel;
    @FXML private Label totalPatientsLabel;
    @FXML private Label activePatientsLabel;
    @FXML private Label deceasedPatientsLabel;
    @FXML private Label newbornRecordsLabel;
    @FXML private Label birthsTodayLabel;
    @FXML private Label deathsThisMonthLabel;
    @FXML private Label pendingBirthCertificatesLabel;
    @FXML private Label pendingDeathCertificatesLabel;
    @FXML private Label criticalPatientsLabel;
    @FXML private Label activeAlertsLabel;
    @FXML private Label acknowledgedTodayLabel;
    @FXML private Label resolvedTodayLabel;
    @FXML private Label medicalFilesLabel;
    @FXML private Label aiNotesLabel;
    @FXML private Label recentVitalsTodayLabel;
    @FXML private Label appointmentsTodayLabel;
    @FXML private Label pendingRemindersLabel;
    @FXML private Label overdueRemindersDashboardLabel;
    @FXML private Label upcomingRemindersDashboardLabel;
    @FXML private Label nurseQueueTasksLabel;
    @FXML private VBox prioritySummaryBox;
    @FXML private VBox activeAlertSeverityBox;
    @FXML private VBox recentAlertsBox;
    @FXML private VBox latestVitalsBox;

    @Override
    public void setAppShell(AppShell appShell) {
        this.appShell = appShell;
        refresh();
        startAutoRefresh();
    }

    @FXML
    private void refresh() {
        User user = Session.getCurrentUser();
        String username = user == null ? "Unknown" : user.getUsername();
        String role = user == null ? "Unknown" : user.getRole();
        String section = user == null ? "Unknown" : user.getSection();

        welcomeLabel.setText("Welcome, " + username);
        roleLabel.setText(role + " | Section: " + section);
        databaseStatusLabel.setText(appShell.getDatabaseStatus());
        migrationStatusLabel.setText(appShell.getMigrationStatus());

        try {
            DashboardMetricsService.DashboardMetrics metrics = metricsService.loadMetrics();
            renderMetrics(metrics);
            refreshStatusLabel.setText("Dashboard refreshed from SQLite at "
                    + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        } catch (Exception e) {
            refreshStatusLabel.setText("Could not refresh dashboard metrics: " + e.getMessage());
        }
    }

    @FXML
    private void toggleTheme() {
        appShell.toggleTheme();
    }

    @FXML
    private void openPatientList() {
        appShell.showPatientList();
    }

    @FXML
    private void openAlertCenter() {
        appShell.showAlertCenter();
    }

    @FXML
    private void openWorkQueue() {
        appShell.showNurseWorkQueue();
    }

    @FXML
    private void logout() {
        if (refreshTimeline != null) {
            refreshTimeline.stop();
        }
        appShell.logout();
    }

    private void renderMetrics(DashboardMetricsService.DashboardMetrics metrics) {
        totalPatientsLabel.setText(String.valueOf(metrics.getTotalPatients()));
        activePatientsLabel.setText(String.valueOf(metrics.getActivePatients()));
        deceasedPatientsLabel.setText(String.valueOf(metrics.getDeceasedPatients()));
        newbornRecordsLabel.setText(String.valueOf(metrics.getNewbornRecords()));
        birthsTodayLabel.setText(String.valueOf(metrics.getBirthsToday()));
        deathsThisMonthLabel.setText(String.valueOf(metrics.getDeathsThisMonth()));
        pendingBirthCertificatesLabel.setText(String.valueOf(metrics.getPendingBirthCertificates()));
        pendingDeathCertificatesLabel.setText(String.valueOf(metrics.getPendingDeathCertificates()));
        criticalPatientsLabel.setText(String.valueOf(metrics.getCriticalEmergencyPatients()));
        activeAlertsLabel.setText(String.valueOf(metrics.getActiveAlerts()));
        acknowledgedTodayLabel.setText(String.valueOf(metrics.getAcknowledgedAlertsToday()));
        resolvedTodayLabel.setText(String.valueOf(metrics.getResolvedAlertsToday()));
        medicalFilesLabel.setText(String.valueOf(metrics.getImportedMedicalFiles()));
        aiNotesLabel.setText(String.valueOf(metrics.getAiNotes()));
        recentVitalsTodayLabel.setText(String.valueOf(metrics.getRecentVitalsToday()));
        appointmentsTodayLabel.setText(String.valueOf(metrics.getAppointmentsToday()));
        pendingRemindersLabel.setText(String.valueOf(metrics.getPendingReminders()));
        overdueRemindersDashboardLabel.setText(String.valueOf(metrics.getOverdueReminders()));
        upcomingRemindersDashboardLabel.setText(String.valueOf(metrics.getUpcomingRemindersToday()));
        nurseQueueTasksLabel.setText(String.valueOf(metrics.getNurseQueueTasks()));

        prioritySummaryBox.getChildren().setAll();
        for (Map.Entry<String, Integer> entry : metrics.getPriorityCounts().entrySet()) {
            prioritySummaryBox.getChildren().add(summaryRow(entry.getKey(), entry.getValue(), priorityStyle(entry.getKey())));
        }

        activeAlertSeverityBox.getChildren().setAll();
        for (Map.Entry<String, Integer> entry : metrics.getActiveAlertSeverityCounts().entrySet()) {
            activeAlertSeverityBox.getChildren().add(summaryRow(entry.getKey(), entry.getValue(), severityStyle(entry.getKey())));
        }

        recentAlertsBox.getChildren().setAll();
        if (metrics.getRecentAlerts().isEmpty()) {
            recentAlertsBox.getChildren().add(emptyRow("No SQLite alerts found."));
        } else {
            for (DashboardMetricsService.RecentAlert alert : metrics.getRecentAlerts()) {
                recentAlertsBox.getChildren().add(alertRow(alert));
            }
        }

        latestVitalsBox.getChildren().setAll();
        if (metrics.getLatestVitals().isEmpty()) {
            latestVitalsBox.getChildren().add(emptyRow("No SQLite vital readings found."));
        } else {
            for (DashboardMetricsService.LatestVital vital : metrics.getLatestVitals()) {
                latestVitalsBox.getChildren().add(vitalRow(vital));
            }
        }
    }

    private HBox summaryRow(String name, int value, String styleClass) {
        Label badge = new Label(name);
        badge.getStyleClass().addAll("dashboard-badge", styleClass);
        Label count = new Label(String.valueOf(value));
        count.getStyleClass().add("dashboard-row-count");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(12, badge, spacer, count);
        row.getStyleClass().add("dashboard-summary-row");
        return row;
    }

    private HBox alertRow(DashboardMetricsService.RecentAlert alert) {
        Label severity = new Label(alert.getSeverity());
        severity.getStyleClass().addAll("dashboard-badge", severityStyle(alert.getSeverity()));
        Label text = new Label(alert.getPatientId() + " | " + alert.getPatientName() + " | " + alert.getStatus()
                + "\n" + alert.getMessage());
        text.getStyleClass().add("dashboard-list-text");
        text.setWrapText(true);
        Label time = new Label(alert.getCreatedAt());
        time.getStyleClass().add("timeline-time");
        Region spacer = new Region();
        HBox.setHgrow(text, Priority.ALWAYS);
        HBox row = new HBox(10, severity, text, spacer, time);
        row.getStyleClass().add("dashboard-list-row");
        row.setOnMouseClicked(event -> appShell.showAlertCenterForAlert(alert.getId()));
        return row;
    }

    private HBox vitalRow(DashboardMetricsService.LatestVital vital) {
        Label type = new Label(vital.getVitalType());
        type.getStyleClass().addAll("dashboard-badge", "timeline-type-vital");
        Label text = new Label(vital.getPatientId() + " | " + vital.getPatientName()
                + "\n" + vital.getValue() + " " + vital.getUnit());
        text.getStyleClass().add("dashboard-list-text");
        text.setWrapText(true);
        Label time = new Label(vital.getRecordedAt());
        time.getStyleClass().add("timeline-time");
        Region spacer = new Region();
        HBox.setHgrow(text, Priority.ALWAYS);
        HBox row = new HBox(10, type, text, spacer, time);
        row.getStyleClass().add("dashboard-list-row");
        return row;
    }

    private HBox emptyRow(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("muted-text");
        HBox row = new HBox(label);
        row.getStyleClass().add("dashboard-list-row");
        return row;
    }

    private void startAutoRefresh() {
        refreshTimeline = new Timeline(new KeyFrame(Duration.seconds(20), event -> refresh()));
        refreshTimeline.setCycleCount(Timeline.INDEFINITE);
        refreshTimeline.play();
    }

    private String priorityStyle(String priority) {
        if ("EMERGENCY".equalsIgnoreCase(priority)) {
            return "priority-emergency";
        }
        if ("CRITICAL".equalsIgnoreCase(priority)) {
            return "priority-critical";
        }
        if ("HIGH".equalsIgnoreCase(priority)) {
            return "priority-high";
        }
        return "priority-normal";
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
}
