package pages.patient.patient_detail;

import pages.patient.vitals_entry.*;
import pages.alert.SqliteAlertDao;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import pages.alert.AlertSoundService;
import pages.patient.vitals_entry.VitalThresholdService;
import pages.patient.vitals_entry.VitalTypeCatalog;
import app.core.AppShell;
import app.contracts.AppController;
import pages.notification.NotificationHelper;
import app.helpers.PermissionHelper;
import pages.scheduling.appointment_form.AppointmentFormController;
import pages.patient.medical_files.MedicalFileUploadController;
import pages.patient.Add_Edit_Patient_Dao;
import pages.patient.model.PatientVisit;
import pages.patient.patient_form.PatientFormController;
import pages.patient.services.PatientVisitService;
import pages.patient.services.PatientWriteService;
import pages.user.Session;
import javafx.scene.control.Alert;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Controls PatientDetailView.fxml and presents the patient file, visits, vitals, alerts, and patient actions.
 */
public class PatientDetailController implements AppController {

    private final Add_Edit_Patient_Dao patientDao = new Add_Edit_Patient_Dao();
    private final SqliteVitalReadingDao vitalReadingDao = new SqliteVitalReadingDao();
    private final SqliteAlertDao alertDao = new SqliteAlertDao();
    private final PatientWriteService patientWriteService = new PatientWriteService();
    private final PatientVisitService patientVisitService = new PatientVisitService();
    private final VitalsTrendService vitalsTrendService = new VitalsTrendService();
    private final ObservableList<VitalRecord> vitals = FXCollections.observableArrayList();
    private final ObservableList<PatientVisit> visits = FXCollections.observableArrayList();
    private AppShell appShell;
    private String patientId;
    private boolean dischargedPatient;
    private boolean inactivePatient;

    @FXML private Label nameLabel;
    @FXML private Label patientIdLabel;
    @FXML private Label ageLabel;
    @FXML private Label birthDateLabel;
    @FXML private Label genderLabel;
    @FXML private Label statusLabel;
    @FXML private Label priorityLabel;
    @FXML private Label bloodTypeLabel;
    @FXML private Label allergyLabel;
    @FXML private Label phoneLabel;
    @FXML private Label emailLabel;
    @FXML private Label addressLabel;
    @FXML private Label emergencyContactNameLabel;
    @FXML private Label emergencyContactPhoneLabel;
    @FXML private Label diagnosisLabel;
    @FXML private Label timelineStatusLabel;
    @FXML private Label trendStatusLabel;
    @FXML private Label activeAlertCountLabel;
    @FXML private Label latestAlertSeverityLabel;
    @FXML private Label latestAlertTimeLabel;
    @FXML private Label latestValueLabel;
    @FXML private Label latestMetaLabel;
    @FXML private Label trendStatsLabel;
    @FXML private Label trendCountsLabel;
    @FXML private ComboBox<String> vitalTypeFilter;
    @FXML private ComboBox<String> trendVitalTypeFilter;
    @FXML private ComboBox<String> trendRangeFilter;
    @FXML private TableView<VitalRecord> vitalsTable;
    @FXML private TableView<PatientVisit> pastVisitsTable;
    @FXML private TableColumn<VitalRecord, String> typeColumn;
    @FXML private TableColumn<VitalRecord, String> valueColumn;
    @FXML private TableColumn<VitalRecord, String> unitColumn;
    @FXML private TableColumn<VitalRecord, String> timeColumn;
    @FXML private TableColumn<VitalRecord, String> staffColumn;
    @FXML private TableColumn<PatientVisit, String> visitDateColumn;
    @FXML private TableColumn<PatientVisit, String> dischargeDateColumn;
    @FXML private TableColumn<PatientVisit, String> visitStatusColumn;
    @FXML private TableColumn<PatientVisit, String> visitReportColumn;
    @FXML private LineChart<Number, Number> vitalsTrendChart;
    @FXML private NumberAxis trendXAxis;
    @FXML private NumberAxis trendYAxis;
    @FXML private Button editPatientButton;
    @FXML private Button enterVitalsButton;
    @FXML private Button createAppointmentButton;
    @FXML private Button uploadMedicalFileButton;
    @FXML private Button dischargePatientButton;

    /**
     * Supplies the application shell used by this controller for navigation.
     */
    @Override
    public void setAppShell(AppShell appShell) {
        this.appShell = appShell;
        configureTable();
        configureVisitTable();
        configureWritePermissions();
        vitalTypeFilter.setItems(FXCollections.observableArrayList(VitalTypeCatalog.javaFxFilterTypes()));
        vitalTypeFilter.getSelectionModel().select("All");
        vitalTypeFilter.valueProperty().addListener((observable, oldValue, newValue) -> loadVitals());
        configureTrendControls();
    }

    /**
     * Loads patient for the patient workflow.
     */
    public void loadPatient(String patientId) {
        this.patientId = patientId;
        try {
            Add_Edit_Patient_Dao.PatientDetail detail = patientDao.findDetailById(patientId)
                    .orElseThrow(() -> new IllegalArgumentException("Patient not found in SQLite: " + patientId));
            nameLabel.setText(detail.getName());
            patientIdLabel.setText(detail.getPatientId());
            ageLabel.setText(detail.getAgeText());
            birthDateLabel.setText(detail.getBirthDate());
            genderLabel.setText(detail.getGender());
            statusLabel.setText(detail.getStatus());
            priorityLabel.setText(detail.getPriority());
            bloodTypeLabel.setText(detail.getBloodType());
            allergyLabel.setText(detail.getAllergies());
            phoneLabel.setText(displayValue(detail.getPhone()));
            emailLabel.setText(displayValue(detail.getEmail()));
            addressLabel.setText(displayValue(detail.getAddress()));
            emergencyContactNameLabel.setText(displayValue(detail.getEmergencyContactName()));
            emergencyContactPhoneLabel.setText(displayValue(detail.getEmergencyContactPhone()));
            priorityLabel.getStyleClass().removeAll("priority-normal", "priority-high", "priority-critical", "priority-emergency");
            priorityLabel.getStyleClass().add(priorityStyle(detail.getPriority()));
            diagnosisLabel.setText(detail.getDiagnosis());
            dischargedPatient = "DISCHARGED".equalsIgnoreCase(detail.getStatus());
            inactivePatient = "INACTIVE".equalsIgnoreCase(detail.getStatus())
                    || "DEACTIVATED".equalsIgnoreCase(detail.getStatus());
            configureWritePermissions();
            updateClinicalActionBlocks();
            loadVitals();
            loadVisitHistory();
            loadTrendChart();
            loadAlertSummary();
        } catch (Exception e) {
            nameLabel.setText("Patient unavailable");
            diagnosisLabel.setText(e.getMessage());
        }
    }

    /**
     * Handles the view patient alerts UI action.
     */
    @FXML
    private void viewPatientAlerts() {
        if (patientId == null || patientId.isBlank()) {
            timelineStatusLabel.setText("No patient selected for alert view.");
            return;
        }
        appShell.showNotificationCenter();
    }

    /**
     * Handles the view patient medical files UI action.
     */
    @FXML
    private void viewPatientMedicalFiles() {
        if (patientId == null || patientId.isBlank()) {
            timelineStatusLabel.setText("No patient selected for medical files.");
            return;
        }
        appShell.showMedicalFilesForPatient(patientId);
    }

    /**
     * Handles the upload patient medical file UI action.
     */
    @FXML
    private void uploadPatientMedicalFile() {
        if (!PermissionHelper.canUploadMedicalFile(Session.getCurrentUser())) {
            timelineStatusLabel.setText("Access denied. Admin, Doctor, Nurse, or Secretary role is required.");
            return;
        }
        if (patientId == null || patientId.isBlank()) {
            timelineStatusLabel.setText("No patient selected for file upload.");
            return;
        }
        try {
            boolean saved = MedicalFileUploadController.showDialog(nameLabel.getScene().getWindow(), Session.getCurrentUser(), patientId);
            if (saved) {
                NotificationHelper.showSuccess(timelineStatusLabel, "Medical file uploaded. Open Medical Files to view.");
            }
        } catch (Exception e) {
            NotificationHelper.showError(timelineStatusLabel, e.getMessage());
        }
    }

    /**
     * Handles the view patient scheduling UI action.
     */
    @FXML
    private void viewPatientScheduling() {
        if (patientId == null || patientId.isBlank()) {
            timelineStatusLabel.setText("No patient selected for scheduling view.");
            return;
        }
        appShell.showSchedulingForPatient(patientId);
    }

    /**
     * Handles the create patient appointment UI action.
     */
    @FXML
    private void createPatientAppointment() {
        if (!PermissionHelper.canCreateAppointment(Session.getCurrentUser())) {
            timelineStatusLabel.setText("Access denied. Only Admin or Secretary users can create appointments.");
            return;
        }
        if (patientId == null || patientId.isBlank()) {
            timelineStatusLabel.setText("No patient selected for appointment scheduling.");
            return;
        }
        if (blockIfInactive()) {
            return;
        }
        try {
            boolean saved = AppointmentFormController.showCreateDialog(nameLabel.getScene().getWindow(), Session.getCurrentUser(), patientId);
            if (saved) {
                NotificationHelper.showSuccess(timelineStatusLabel, "Appointment saved. Open Scheduling to view.");
            }
        } catch (Exception e) {
            NotificationHelper.showError(timelineStatusLabel, e.getMessage());
        }
    }

    /**
     * Handles the discharge patient UI action.
     */
    @FXML
    private void dischargePatient() {
        if (!PermissionHelper.canDeactivatePatient(Session.getCurrentUser())) {
            timelineStatusLabel.setText("Access denied. Admin or Doctor role is required.");
            return;
        }
        if (patientId == null || patientId.isBlank()) {
            timelineStatusLabel.setText("No patient selected for discharge.");
            return;
        }
        if (dischargedPatient) {
            NotificationHelper.showInfo(timelineStatusLabel, "This patient is already discharged.");
            return;
        }
        String dischargeSummary = promptVisitReport(
                nameLabel.getScene() == null ? null : nameLabel.getScene().getWindow(),
                "Discharge Patient",
                "Visit report / discharge summary");
        if (dischargeSummary == null) {
            return;
        }
        try {
            patientWriteService.dischargePatient(Session.getCurrentUser(), patientId, dischargeSummary);
            loadPatient(patientId);
            NotificationHelper.showSuccess(timelineStatusLabel, "Patient discharged and visit summary saved.");
        } catch (Exception e) {
            NotificationHelper.showError(timelineStatusLabel, e.getMessage());
        }
    }

    /**
     * Handles the edit patient UI action.
     */
    @FXML
    private void editPatient() {
        if (!PermissionHelper.canUpdatePatient(Session.getCurrentUser())) {
            timelineStatusLabel.setText("Access denied. Admin or Doctor role is required.");
            return;
        }
        if (patientId == null || patientId.isBlank()) {
            timelineStatusLabel.setText("No patient selected for editing.");
            return;
        }
        try {
            Add_Edit_Patient_Dao.PatientDetail detail = patientDao.findDetailById(patientId)
                    .orElseThrow(() -> new IllegalArgumentException("Patient not found in SQLite: " + patientId));
            boolean saved = PatientFormController.showEditDialog(nameLabel.getScene().getWindow(), Session.getCurrentUser(), detail);
            if (saved) {
                loadPatient(patientId);
                NotificationHelper.showSuccess(timelineStatusLabel, "Patient updated. System data updated.");
            }
        } catch (Exception e) {
            NotificationHelper.showError(timelineStatusLabel, e.getMessage());
        }
    }

    /**
     * Handles the enter vitals UI action.
     */
    @FXML
    private void enterVitals() {
        if (!PermissionHelper.canEnterVitals(Session.getCurrentUser())) {
            timelineStatusLabel.setText("Access denied. Admin, Doctor, or Nurse role is required.");
            return;
        }
        if (patientId == null || patientId.isBlank()) {
            timelineStatusLabel.setText("No patient selected for vitals entry.");
            return;
        }
        if (blockIfInactive()) {
            return;
        }
        try {
            VitalsWriteService.VitalsWriteResult result = VitalsEntryController.showDialog(
                    nameLabel.getScene().getWindow(),
                    Session.getCurrentUser(),
                    patientId
            );
            if (result != null) {
                loadPatient(patientId);
                loadVitals();
                loadTrendChart();
                loadAlertSummary();
                if (appShell != null) {
                    appShell.refreshNotificationCount();
                }
                showVitalAlertPopupIfNeeded(result);
                NotificationHelper.showSuccess(timelineStatusLabel,
                        "Saved " + result.getVitalType() + " " + result.getValue() + " " + result.getUnit()
                                + " as " + result.getStatus() + ". Vitals and alerts are refreshed.");
            }
        } catch (Exception e) {
            NotificationHelper.showError(timelineStatusLabel, e.getMessage());
        }
    }


    /**
     * Handles the load vitals UI action.
     */
    @FXML
    private void loadVitals() {
        if (patientId == null || patientId.isBlank()) {
            return;
        }
        try {
            vitals.setAll(vitalReadingDao.findByPatientIdAndType(patientId, vitalTypeFilter.getValue()));
            vitalsTable.setItems(vitals);
            timelineStatusLabel.setText("Vital readings loaded from the local database: " + vitals.size());
        } catch (Exception e) {
            timelineStatusLabel.setText("Could not load vitals: " + e.getMessage());
        }
    }

    /**
     * Handles the load trend chart UI action.
     */
    @FXML
    private void loadTrendChart() {
        if (patientId == null || patientId.isBlank()) {
            return;
        }
        try {
            VitalsTrendService.TrendResult result = vitalsTrendService.loadTrend(
                    patientId,
                    trendVitalTypeFilter.getValue(),
                    trendRangeFilter.getValue());
            renderTrend(result);
        } catch (Exception e) {
            trendStatusLabel.setText("Could not load vital trend: " + e.getMessage());
            vitalsTrendChart.getData().clear();
        }
    }

    /**
     * Loads alert summary for the patient workflow.
     */
    private void loadAlertSummary() {
        if (patientId == null || patientId.isBlank()) {
            return;
        }
        try {
            int activeCount = alertDao.countActiveForPatient(patientId);
            activeAlertCountLabel.setText(String.valueOf(activeCount));
            alertDao.findLatestByPatientId(patientId).ifPresentOrElse(alert -> {
                latestAlertSeverityLabel.setText(alert.getSeverity() + " | " + alert.getStatus());
                latestAlertTimeLabel.setText(alert.getCreatedAt());
                latestAlertSeverityLabel.getStyleClass().removeAll("severity-warning", "severity-critical", "severity-emergency");
                latestAlertSeverityLabel.getStyleClass().add(severityStyle(alert.getSeverity()));
            }, () -> {
                latestAlertSeverityLabel.setText("No alerts recorded");
                latestAlertTimeLabel.setText("-");
                latestAlertSeverityLabel.getStyleClass().removeAll("severity-warning", "severity-critical", "severity-emergency");
            });
        } catch (Exception e) {
            activeAlertCountLabel.setText("-");
            latestAlertSeverityLabel.setText("Could not load alerts");
            latestAlertTimeLabel.setText(e.getMessage());
        }
    }

    /**
     * Configures table.
     */
    private void configureTable() {
        vitalsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        typeColumn.setCellValueFactory(new PropertyValueFactory<>("vitalType"));
        valueColumn.setCellValueFactory(new PropertyValueFactory<>("value"));
        unitColumn.setCellValueFactory(new PropertyValueFactory<>("unit"));
        timeColumn.setCellValueFactory(new PropertyValueFactory<>("dateTime"));
        staffColumn.setCellValueFactory(new PropertyValueFactory<>("staffName"));
    }

    /**
     * Configures visit table.
     */
    private void configureVisitTable() {
        if (pastVisitsTable == null) {
            return;
        }
        visitDateColumn.setCellValueFactory(new PropertyValueFactory<>("visitDate"));
        dischargeDateColumn.setCellValueFactory(new PropertyValueFactory<>("dischargeDate"));
        visitStatusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        visitReportColumn.setCellValueFactory(new PropertyValueFactory<>("report"));
        pastVisitsTable.setItems(visits);
        pastVisitsTable.setPlaceholder(new Label("No previous visits yet."));
    }

    /**
     * Configures trend controls.
     */
    private void configureTrendControls() {
        trendVitalTypeFilter.setItems(FXCollections.observableArrayList(
                VitalTypeCatalog.javaFxEntryTypes()));
        trendRangeFilter.setItems(FXCollections.observableArrayList("Last 24 hours", "Last 7 days", "Last 30 days", "All"));
        trendVitalTypeFilter.getSelectionModel().select(VitalTypeCatalog.HEART_RATE);
        trendRangeFilter.getSelectionModel().select("Last 7 days");
        trendVitalTypeFilter.valueProperty().addListener((observable, oldValue, newValue) -> loadTrendChart());
        trendRangeFilter.valueProperty().addListener((observable, oldValue, newValue) -> loadTrendChart());
        vitalsTrendChart.setAnimated(false);
        vitalsTrendChart.setCreateSymbols(true);
        trendXAxis.setLabel("Reading order");
        trendYAxis.setLabel("Value");
        trendXAxis.setForceZeroInRange(false);
        trendYAxis.setForceZeroInRange(false);
    }

    /**
     * Configures write permissions.
     */
    private void configureWritePermissions() {
        boolean canEdit = PermissionHelper.canUpdatePatient(Session.getCurrentUser());
        setButtonVisible(editPatientButton, canEdit);
        boolean canEnterVitals = PermissionHelper.canEnterVitals(Session.getCurrentUser());
        setButtonVisible(enterVitalsButton, canEnterVitals);
        boolean canAppointments = PermissionHelper.canCreateAppointment(Session.getCurrentUser());
        setButtonVisible(createAppointmentButton, canAppointments);
        boolean canUploadFiles = PermissionHelper.canUploadMedicalFile(Session.getCurrentUser());
        setButtonVisible(uploadMedicalFileButton, canUploadFiles);
        boolean canDischarge = PermissionHelper.canDeactivatePatient(Session.getCurrentUser());
        setButtonVisible(dischargePatientButton, canDischarge);
    }

    /**
     * Updates clinical action blocks.
     */
    private void updateClinicalActionBlocks() {
        if (!inactivePatient && !dischargedPatient) {
            return;
        }
        setButtonVisible(enterVitalsButton, false);
        setButtonVisible(createAppointmentButton, false);
        setButtonVisible(dischargePatientButton, false);
    }

    /**
     * Updates button visible for the current object.
     */
    private void setButtonVisible(Button button, boolean visible) {
        if (button != null) {
            button.setVisible(visible);
            button.setManaged(visible);
        }
    }

    /**
     * Renders trend in the current JavaFX view.
     */
    private void renderTrend(VitalsTrendService.TrendResult result) {
        vitalsTrendChart.getData().clear();
        latestValueLabel.getStyleClass().removeAll("trend-normal", "trend-warning", "trend-critical");

        if (result.isEmpty()) {
            trendStatusLabel.setText("No vital readings found for " + result.getVitalFilter() + " in " + result.getRangeFilter() + ".");
            latestValueLabel.setText("-");
            latestMetaLabel.setText("No latest reading available.");
            trendStatsLabel.setText("Min: - | Max: - | Avg: -");
            trendCountsLabel.setText("Normal: 0 | Warning: 0 | Critical: 0");
            return;
        }

        Map<String, XYChart.Series<Number, Number>> seriesByType = new LinkedHashMap<>();
        int index = 1;
        for (VitalsTrendService.TrendReading reading : result.getReadings()) {
            XYChart.Series<Number, Number> series = seriesByType.computeIfAbsent(reading.getVitalType(), key -> {
                XYChart.Series<Number, Number> created = new XYChart.Series<>();
                created.setName(key);
                return created;
            });
            series.getData().add(new XYChart.Data<>(index++, reading.getNumericValue()));
        }
        vitalsTrendChart.getData().setAll(seriesByType.values());

        VitalsTrendService.TrendReading latest = result.getLatest();
        latestValueLabel.setText(latest.getRawValue() + " " + latest.getUnit() + " - " + latest.getStatus());
        latestValueLabel.getStyleClass().add(trendStyle(latest.getStatus()));
        latestMetaLabel.setText(latest.getRecordedAt()
                + " | Source: " + fallback(latest.getSourceType(), "Manual")
                + " | Staff: " + fallback(latest.getStaffUser(), "Not recorded"));
        trendStatsLabel.setText(String.format("Min: %.1f | Max: %.1f | Avg: %.1f",
                result.getMin(), result.getMax(), result.getAverage()));
        trendCountsLabel.setText("Normal: " + result.getNormalCount()
                + " | Warning: " + result.getWarningCount()
                + " | Critical: " + result.getCriticalCount());
        trendStatusLabel.setText("Read-only local database trend loaded: " + result.getReadings().size() + " readings.");
    }

    /**
     * Blocks clinical actions when the selected patient is inactive.
     */
    private boolean blockIfInactive() {
        if (!inactivePatient && !dischargedPatient) {
            return false;
        }
        NotificationHelper.showError(timelineStatusLabel, "Clinical actions are blocked for inactive patient records.");
        return true;
    }

    /**
     * Loads visit history for the patient workflow.
     */
    private void loadVisitHistory() {
        if (patientId == null || patientId.isBlank() || pastVisitsTable == null) {
            return;
        }
        try {
            visits.setAll(patientVisitService.getVisitHistory(patientId));
        } catch (Exception e) {
            timelineStatusLabel.setText("Could not load visit history: " + e.getMessage());
        }
    }

    /**
     * Resolves priority style for the current clinical state.
     */
    private String priorityStyle(String priority) {
        if (priority == null) {
            return "priority-normal";
        }
        switch (priority.toUpperCase()) {
            case "EMERGENCY":
                return "priority-emergency";
            case "CRITICAL":
                return "priority-critical";
            case "HIGH":
            case "WARNING":
                return "priority-high";
            default:
                return "priority-normal";
        }
    }

    /**
     * Returns the CSS style class for trend style.
     */
    private String trendStyle(VitalThresholdService.VitalStatus status) {
        if (status == VitalThresholdService.VitalStatus.CRITICAL) {
            return "trend-critical";
        }
        if (status == VitalThresholdService.VitalStatus.WARNING) {
            return "trend-warning";
        }
        return "trend-normal";
    }

    /**
     * Resolves severity style for alert display.
     */
    private String severityStyle(String severity) {
        if ("EMERGENCY".equalsIgnoreCase(severity)) {
            return "severity-emergency";
        }
        if ("CRITICAL".equalsIgnoreCase(severity)) {
            return "severity-critical";
        }
        return "severity-warning";
    }

    /**
     * Displays vital alert popup if needed to the user.
     */
    private void showVitalAlertPopupIfNeeded(VitalsWriteService.VitalsWriteResult result) {
        if (result.getStatus() == VitalThresholdService.VitalStatus.NORMAL) {
            return;
        }

        Alert.AlertType alertType = result.getStatus() == VitalThresholdService.VitalStatus.WARNING
                ? Alert.AlertType.WARNING
                : Alert.AlertType.ERROR;

        Alert alert = new Alert(alertType);
        alert.setTitle(result.getStatus() + " Vital Alert");
        app.helpers.DialogThemeHelper.apply(alert);
        alert.setHeaderText(result.getStatus() + " vital reading detected");
        alert.setContentText(
                "Patient: " + nameLabel.getText()
                        + "\nPatient ID: " + patientId
                        + "\nVital: " + result.getVitalType()
                        + "\nValue: " + result.getValue() + " " + result.getUnit()
                        + "\n\nImmediate staff review is required."
        );

        AlertSoundService.playAlertSound();
        try {
            alert.showAndWait();
        } finally {
            AlertSoundService.stopAlertSound();
        }
    }

    /**
     * Returns fallback display text when the preferred value is blank.
     */
    private String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    /**
     * Formats value for display in the JavaFX UI.
     */
    private String displayValue(String value) {
        return value == null || value.isBlank() ? "\u2014" : value.trim();
    }

    /**
     * Prompts for the visit report used when completing a patient visit.
     */
    private String promptVisitReport(Window owner, String title, String prompt) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle(title);
        app.helpers.DialogThemeHelper.apply(dialog);
        if (owner != null) {
            dialog.initOwner(owner);
        }
        TextArea reportArea = new TextArea();
        reportArea.setWrapText(true);
        reportArea.setPromptText("Enter a short visit summary");
        reportArea.setPrefRowCount(6);
        VBox content = new VBox(10.0, new Label(prompt), reportArea);
        dialog.getDialogPane().setContent(content);
        ButtonType saveButton = new ButtonType("Save Summary", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, saveButton);
        dialog.setResultConverter(buttonType -> buttonType == saveButton ? reportArea.getText() : null);
        return dialog.showAndWait().orElse(null);
    }
}
