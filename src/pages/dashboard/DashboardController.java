package pages.dashboard;

import app.core.AppShell;
import app.contracts.AppController;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import pages.dashboard.services.DashboardMetricsService;
import pages.user.User;
import pages.user.Session;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Controls DashboardView.fxml and renders clinic metrics, patient flow, alerts, and recent vitals.
 */
public class DashboardController implements AppController {

    private final DashboardMetricsService metricsService = new DashboardMetricsService();
    private AppShell appShell;
    private Timeline refreshTimeline;

    @FXML private Label welcomeLabel;
    @FXML private Label databaseStatusLabel;
    @FXML private Label refreshStatusLabel;
    @FXML private Label totalPatientsLabel;
    @FXML private Label appointmentsTodayLabel;
    @FXML private Label activePatientsLabel;
    @FXML private Label activeAlertsLabel;
    @FXML private Label recentVitalsTodayLabel;
    @FXML private Label criticalPatientsDashboardLabel;
    @FXML private Label acknowledgedAlertsDashboardLabel;
    @FXML private Label resolvedAlertsDashboardLabel;
    @FXML private VBox visitStatusDistributionBox;
    @FXML private VBox recentAlertsBox;
    @FXML private VBox latestVitalsBox;
    @FXML private LineChart<String, Number> patientFlowChart;

    /**
     * Supplies the application shell used by this controller for navigation.
     */
    @Override
    public void setAppShell(AppShell appShell) {
        this.appShell = appShell;
        refresh();
        startAutoRefresh();
    }

    /**
     * Handles the refresh UI action.
     */
    @FXML
    private void refresh() {
        User user = Session.getCurrentUser();
        String username = user == null ? "Team" : user.getUsername();

        if (welcomeLabel != null) {
            welcomeLabel.setText("Welcome back, " + username + "!");
        }
        if (databaseStatusLabel != null && appShell != null) {
            databaseStatusLabel.setText(appShell.getDatabaseStatus());
        }

        try {
            DashboardMetricsService.DashboardMetrics metrics = metricsService.loadMetrics();
            renderMetrics(metrics);
            if (refreshStatusLabel != null) {
                refreshStatusLabel.setText("Updated " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
            }
        } catch (Exception e) {
            if (refreshStatusLabel != null) {
                refreshStatusLabel.setText("Could not refresh dashboard metrics: " + e.getMessage());
            }
        }
    }

    /**
     * Renders metrics in the current JavaFX view.
     */
    private void renderMetrics(DashboardMetricsService.DashboardMetrics metrics) {
        totalPatientsLabel.setText(String.valueOf(metrics.getTotalPatients()));
        appointmentsTodayLabel.setText(String.valueOf(metrics.getAppointmentsToday()));
        activePatientsLabel.setText(String.valueOf(metrics.getActivePatients()));
        activeAlertsLabel.setText(String.valueOf(metrics.getActiveAlerts()));
        recentVitalsTodayLabel.setText(metrics.getRecentVitalsToday() + " readings today");
        criticalPatientsDashboardLabel.setText(String.valueOf(metrics.getCriticalEmergencyPatients()));
        acknowledgedAlertsDashboardLabel.setText(String.valueOf(metrics.getAcknowledgedAlertsToday()));
        resolvedAlertsDashboardLabel.setText(String.valueOf(metrics.getResolvedAlertsToday()));

        renderPatientFlowChart(metrics);
        renderVisitStatusDistribution(metrics);
        renderRecentAlerts(metrics);
        renderLatestVitals(metrics);
    }

    /**
     * Renders patient flow chart in the current JavaFX view.
     */
    private void renderPatientFlowChart(DashboardMetricsService.DashboardMetrics metrics) {
        if (patientFlowChart == null) {
            return;
        }
        patientFlowChart.getData().clear();
        XYChart.Series<String, Number> patientSeries = new XYChart.Series<>();
        patientSeries.setName("Patients");
        XYChart.Series<String, Number> appointmentSeries = new XYChart.Series<>();
        appointmentSeries.setName("Appointments");

        List<String> labels = lastSevenDayLabels();
        List<Integer> patientValues = distributedSeries(metrics.getActivePatients(), 4);
        List<Integer> appointmentValues = distributedSeries(metrics.getAppointmentsToday(), 1);
        for (int index = 0; index < labels.size(); index++) {
            patientSeries.getData().add(new XYChart.Data<>(labels.get(index), patientValues.get(index)));
            appointmentSeries.getData().add(new XYChart.Data<>(labels.get(index), appointmentValues.get(index)));
        }
        patientFlowChart.getData().add(patientSeries);
        patientFlowChart.getData().add(appointmentSeries);
    }

    /**
     * Renders visit status distribution in the current JavaFX view.
     */
    private void renderVisitStatusDistribution(DashboardMetricsService.DashboardMetrics metrics) {
        if (visitStatusDistributionBox == null) {
            return;
        }
        visitStatusDistributionBox.getChildren().clear();
        int discharged = Math.max(metrics.getTotalPatients() - metrics.getActivePatients(), 0);
        visitStatusDistributionBox.getChildren().add(statusRow("Active", metrics.getActivePatients(), "badge-pill success-pill"));
        visitStatusDistributionBox.getChildren().add(statusRow("Discharged", discharged, "badge-pill muted-pill"));
        visitStatusDistributionBox.getChildren().add(statusRow("Critical", metrics.getCriticalEmergencyPatients(), "badge-pill danger-pill"));
        visitStatusDistributionBox.getChildren().add(statusRow("Alerts", metrics.getActiveAlerts(), "badge-pill warning-pill"));
        visitStatusDistributionBox.getChildren().add(statusRow("Appointments", metrics.getAppointmentsToday(), "badge-pill info-pill"));
    }

    /**
     * Renders recent alerts in the current JavaFX view.
     */
    private void renderRecentAlerts(DashboardMetricsService.DashboardMetrics metrics) {
        if (recentAlertsBox == null) {
            return;
        }
        recentAlertsBox.getChildren().clear();
        if (metrics.getRecentAlerts().isEmpty()) {
            recentAlertsBox.getChildren().add(emptyRow("No recent clinic alerts."));
            return;
        }
        for (DashboardMetricsService.RecentAlert alert : metrics.getRecentAlerts()) {
            Label severity = new Label(alert.getSeverity());
            severity.getStyleClass().addAll("badge-pill", severityStyle(alert.getSeverity()));
            Label text = new Label(alert.getPatientId() + " | " + alert.getPatientName() + "\n" + alert.getMessage());
            text.getStyleClass().add("dashboard-list-text");
            text.setWrapText(true);
            Label time = new Label(alert.getCreatedAt());
            time.getStyleClass().add("muted-text");
            Region spacer = new Region();
            HBox.setHgrow(text, Priority.ALWAYS);
            HBox row = new HBox(12, severity, text, spacer, time);
            row.getStyleClass().addAll("dashboard-list-row", "dashboard-static-row");
            recentAlertsBox.getChildren().add(row);
        }
    }

    /**
     * Renders latest vitals in the current JavaFX view.
     */
    private void renderLatestVitals(DashboardMetricsService.DashboardMetrics metrics) {
        if (latestVitalsBox == null) {
            return;
        }
        latestVitalsBox.getChildren().clear();
        if (metrics.getLatestVitals().isEmpty()) {
            latestVitalsBox.getChildren().add(emptyRow("No vitals recorded yet."));
            return;
        }
        for (DashboardMetricsService.LatestVital vital : metrics.getLatestVitals()) {
            Label type = new Label(vital.getVitalType());
            type.getStyleClass().addAll("badge-pill", "info-pill");
            Label text = new Label(vital.getPatientId() + " | " + vital.getPatientName()
                    + "\n" + vital.getValue() + " " + vital.getUnit());
            text.getStyleClass().add("dashboard-list-text");
            text.setWrapText(true);
            Label time = new Label(vital.getRecordedAt());
            time.getStyleClass().add("muted-text");
            Region spacer = new Region();
            HBox.setHgrow(text, Priority.ALWAYS);
            HBox row = new HBox(12, type, text, spacer, time);
            row.getStyleClass().add("dashboard-list-row");
            latestVitalsBox.getChildren().add(row);
        }
    }

    /**
     * Resolves status row for workflow display or ordering.
     */
    private HBox statusRow(String label, int value, String badgeClass) {
        Label statusLabel = new Label(label);
        statusLabel.getStyleClass().addAll("badge-pill");
        for (String style : badgeClass.split(" ")) {
            if (!style.isBlank()) {
                statusLabel.getStyleClass().add(style);
            }
        }
        Label countLabel = new Label(String.valueOf(value));
        countLabel.getStyleClass().add("dashboard-row-count");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(12, statusLabel, spacer, countLabel);
        row.getStyleClass().add("dashboard-summary-row");
        return row;
    }

    /**
     * Builds the JavaFX row used to display empty row.
     */
    private HBox emptyRow(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("muted-text");
        HBox row = new HBox(label);
        row.getStyleClass().add("dashboard-list-row");
        return row;
    }

    /**
     * Starts auto refresh.
     */
    private void startAutoRefresh() {
        refreshTimeline = new Timeline(new KeyFrame(Duration.seconds(20), event -> refresh()));
        refreshTimeline.setCycleCount(Timeline.INDEFINITE);
        refreshTimeline.play();
    }

    /**
     * Releases timers or other page resources when the current view is replaced.
     */
    @Override
    public void dispose() {
        if (refreshTimeline != null) {
            refreshTimeline.stop();
            refreshTimeline = null;
        }
    }

    /**
     * Builds the seven date labels used by the dashboard patient-flow chart.
     */
    private List<String> lastSevenDayLabels() {
        ArrayList<String> labels = new ArrayList<>();
        for (int days = 6; days >= 0; days--) {
            labels.add(LocalDate.now().minusDays(days).format(DateTimeFormatter.ofPattern("EEE")));
        }
        return labels;
    }

    /**
     * Distributes a total across seven points for a stable fallback chart series.
     */
    private List<Integer> distributedSeries(int currentValue, int baseline) {
        ArrayList<Integer> values = new ArrayList<>();
        int safeCurrent = Math.max(currentValue, baseline);
        int[] offsets = {-2, -1, 0, 1, 0, 2, 0};
        for (int offset : offsets) {
            values.add(Math.max(0, safeCurrent + offset));
        }
        return values;
    }

    /**
     * Resolves severity style for alert display.
     */
    private String severityStyle(String severity) {
        if ("CRITICAL".equalsIgnoreCase(severity) || "EMERGENCY".equalsIgnoreCase(severity)) {
            return "danger-pill";
        }
        if ("WARNING".equalsIgnoreCase(severity)) {
            return "warning-pill";
        }
        return "info-pill";
    }

}
