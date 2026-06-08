package ui.javafx.controllers;

import dao.SqlitePatientDao;
import dao.SqliteVitalReadingDao;
import dao.SqliteAlertDao;
import dao.SqliteNewbornRecordDao;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import models.VitalRecord;
import dao.SqliteAuditLogDao;
import services.AlertSoundService;
import services.VitalThresholdService;
import services.VitalTypeCatalog;
import services.VitalsTrendService;
import ui.javafx.AppShell;
import ui.javafx.FxController;
import ui.javafx.SessionContext;
import ui.javafx.helpers.NotificationHelper;
import ui.javafx.helpers.PermissionHelper;
import users.Session;
import javafx.scene.control.Alert;
import javafx.scene.layout.VBox;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class PatientDetailController implements FxController {

    private final SqlitePatientDao patientDao = new SqlitePatientDao();
    private final SqliteVitalReadingDao vitalReadingDao = new SqliteVitalReadingDao();
    private final SqliteAlertDao alertDao = new SqliteAlertDao();
    private final SqliteNewbornRecordDao newbornDao = new SqliteNewbornRecordDao();
    private final SqliteAuditLogDao auditLogDao = new SqliteAuditLogDao();
    private final VitalsTrendService vitalsTrendService = new VitalsTrendService();
    private final ObservableList<VitalRecord> vitals = FXCollections.observableArrayList();
    private AppShell appShell;
    private String patientId;
    private boolean deceasedPatient;

    @FXML private Label nameLabel;
    @FXML private Label patientIdLabel;
    @FXML private Label ageLabel;
    @FXML private Label birthDateLabel;
    @FXML private Label genderLabel;
    @FXML private Label sectionLabel;
    @FXML private Label roomLabel;
    @FXML private Label statusLabel;
    @FXML private Label priorityLabel;
    @FXML private Label bloodTypeLabel;
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
    @FXML private Label babiesCountLabel;
    @FXML private Label babiesListLabel;
    @FXML private ComboBox<String> vitalTypeFilter;
    @FXML private ComboBox<String> trendVitalTypeFilter;
    @FXML private ComboBox<String> trendRangeFilter;
    @FXML private TableView<VitalRecord> vitalsTable;
    @FXML private TableColumn<VitalRecord, String> typeColumn;
    @FXML private TableColumn<VitalRecord, String> valueColumn;
    @FXML private TableColumn<VitalRecord, String> unitColumn;
    @FXML private TableColumn<VitalRecord, String> timeColumn;
    @FXML private TableColumn<VitalRecord, String> sourceColumn;
    @FXML private TableColumn<VitalRecord, String> staffColumn;
    @FXML private TableColumn<VitalRecord, String> deviceColumn;
    @FXML private LineChart<Number, Number> vitalsTrendChart;
    @FXML private NumberAxis trendXAxis;
    @FXML private NumberAxis trendYAxis;
    @FXML private Button editPatientButton;
    @FXML private Button enterVitalsButton;
    @FXML private Button addMedicationButton;
    @FXML private Button clinicalAddMedicationButton;
    @FXML private Button recordMedicationButton;
    @FXML private Button createAppointmentButton;
    @FXML private Button createReminderButton;
    @FXML private Button uploadMedicalFileButton;
    @FXML private Button movePatientButton;
    @FXML private Button markDeceasedButton;
    @FXML private Button viewNewbornsButton;
    @FXML private Button viewBabiesButton;
    @FXML private VBox babiesCard;

    @Override
    public void setAppShell(AppShell appShell) {
        this.appShell = appShell;
        configureTable();
        configureWritePermissions();
        vitalTypeFilter.setItems(FXCollections.observableArrayList(VitalTypeCatalog.javaFxFilterTypes()));
        vitalTypeFilter.getSelectionModel().select("All");
        vitalTypeFilter.valueProperty().addListener((observable, oldValue, newValue) -> loadVitals());
        configureTrendControls();
    }

    public void loadPatient(String patientId) {
        this.patientId = patientId;
        try {
            SqlitePatientDao.PatientDetail detail = patientDao.findDetailById(patientId)
                    .orElseThrow(() -> new IllegalArgumentException("Patient not found in SQLite: " + patientId));
            nameLabel.setText(detail.getName());
            patientIdLabel.setText(detail.getPatientId());
            ageLabel.setText(detail.getAgeText());
            birthDateLabel.setText(detail.getBirthDate());
            genderLabel.setText(detail.getGender());
            sectionLabel.setText(detail.getSection());
            roomLabel.setText(detail.getRoom());
            statusLabel.setText(detail.getStatus());
            priorityLabel.setText(detail.getPriority());
            bloodTypeLabel.setText(detail.getBloodType());
            priorityLabel.getStyleClass().removeAll("priority-normal", "priority-high", "priority-critical", "priority-emergency");
            priorityLabel.getStyleClass().add(priorityStyle(detail.getPriority()));
            diagnosisLabel.setText(detail.getDiagnosis());
            deceasedPatient = "DECEASED".equalsIgnoreCase(detail.getStatus());
            updateDeceasedButton(detail.getStatus());
            updateClinicalActionBlocks();
            loadVitals();
            loadTrendChart();
            loadAlertSummary();
            loadLinkedNewborns();
        } catch (Exception e) {
            nameLabel.setText("Patient unavailable");
            diagnosisLabel.setText(e.getMessage());
        }
    }

    @FXML
    private void backToPatients() {
        appShell.showPatientList();
    }

    @FXML
    private void openClinicalTimeline() {
        if (patientId == null || patientId.isBlank()) {
            timelineStatusLabel.setText("No patient selected for clinical timeline.");
            return;
        }
        appShell.showClinicalTimeline(patientId);
    }

    @FXML
    private void viewPatientAlerts() {
        if (patientId == null || patientId.isBlank()) {
            timelineStatusLabel.setText("No patient selected for alert view.");
            return;
        }
        appShell.showAlertCenterForPatient(patientId);
    }

    @FXML
    private void viewPatientMedications() {
        if (patientId == null || patientId.isBlank()) {
            timelineStatusLabel.setText("No patient selected for medication view.");
            return;
        }
        appShell.showMedicationOverviewForPatient(patientId);
    }

    @FXML
    private void viewPatientNewborns() {
        if (!PermissionHelper.canViewNewbornRecords(Session.getCurrentUser())) {
            timelineStatusLabel.setText("Access denied. Admin, Doctor, or Nurse role is required.");
            return;
        }
        if (patientId == null || patientId.isBlank()) {
            timelineStatusLabel.setText("No patient selected for newborn lookup.");
            return;
        }
        logAudit(ui.javafx.helpers.AuditAction.OPEN_BABIES_FROM_MOTHER + " patient_id=" + patientId);
        appShell.showNewbornRecordsForMother(patientId);
    }

    @FXML
    private void viewPatientMedicalFiles() {
        if (patientId == null || patientId.isBlank()) {
            timelineStatusLabel.setText("No patient selected for medical files.");
            return;
        }
        appShell.showMedicalFilesForPatient(patientId);
    }

    @FXML
    private void uploadPatientMedicalFile() {
        if (!PermissionHelper.canUploadMedicalFile(Session.getCurrentUser())) {
            timelineStatusLabel.setText("Access denied. Admin, Doctor, or Nurse role is required.");
            return;
        }
        if (patientId == null || patientId.isBlank()) {
            timelineStatusLabel.setText("No patient selected for file upload.");
            return;
        }
        try {
            boolean saved = MedicalFileUploadController.showDialog(nameLabel.getScene().getWindow(), Session.getCurrentUser(), patientId);
            if (saved) {
                NotificationHelper.showSuccess(timelineStatusLabel, "Medical file uploaded. Open Medical Files or Clinical Timeline to view.");
            }
        } catch (Exception e) {
            NotificationHelper.showError(timelineStatusLabel, e.getMessage());
        }
    }

    @FXML
    private void viewPatientScheduling() {
        if (patientId == null || patientId.isBlank()) {
            timelineStatusLabel.setText("No patient selected for scheduling view.");
            return;
        }
        appShell.showSchedulingForPatient(patientId);
    }

    @FXML
    private void createPatientAppointment() {
        if (!PermissionHelper.canManageAppointment(Session.getCurrentUser())) {
            timelineStatusLabel.setText("Access denied. Admin or Doctor role is required.");
            return;
        }
        if (patientId == null || patientId.isBlank()) {
            timelineStatusLabel.setText("No patient selected for appointment scheduling.");
            return;
        }
        if (blockIfDeceased("create appointment")) {
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

    @FXML
    private void createPatientReminder() {
        if (!PermissionHelper.canManageReminder(Session.getCurrentUser())) {
            timelineStatusLabel.setText("Access denied. Admin, Doctor, or Nurse role is required.");
            return;
        }
        if (patientId == null || patientId.isBlank()) {
            timelineStatusLabel.setText("No patient selected for reminder scheduling.");
            return;
        }
        if (blockIfDeceased("create reminder/checkup order")) {
            return;
        }
        try {
            boolean saved = ReminderFormController.showOrderCheckupDialog(nameLabel.getScene().getWindow(), Session.getCurrentUser(), patientId);
            if (saved) {
                NotificationHelper.showSuccess(timelineStatusLabel, "Checkup order saved. Open Scheduling or Nurse Work Queue to view.");
            }
        } catch (Exception e) {
            NotificationHelper.showError(timelineStatusLabel, e.getMessage());
        }
    }

    @FXML
    private void movePatientRoom() {
        if (!PermissionHelper.canAssignPatientRoom(Session.getCurrentUser())) {
            timelineStatusLabel.setText("Access denied. Admin, Doctor, or Nurse role is required.");
            return;
        }
        if (patientId == null || patientId.isBlank()) {
            timelineStatusLabel.setText("No patient selected for room move.");
            return;
        }
        if (blockIfDeceased("move room")) {
            return;
        }
        try {
            boolean saved = RoomAssignmentController.showMovePatientDialog(nameLabel.getScene().getWindow(), Session.getCurrentUser(), patientId);
            if (saved) {
                loadPatient(patientId);
                NotificationHelper.showSuccess(timelineStatusLabel, "Patient room updated. System data updated.");
            }
        } catch (Exception e) {
            NotificationHelper.showError(timelineStatusLabel, e.getMessage());
        }
    }

    @FXML
    private void markPatientDeceased() {
        if (!PermissionHelper.canMarkPatientDeceased(Session.getCurrentUser())) {
            timelineStatusLabel.setText("Access denied. Admin or Doctor role is required.");
            return;
        }
        if (patientId == null || patientId.isBlank()) {
            timelineStatusLabel.setText("No patient selected for deceased workflow.");
            return;
        }
        try {
            boolean saved = DeathRecordFormController.showMarkDialog(nameLabel.getScene().getWindow(), Session.getCurrentUser(), patientId);
            if (saved) {
                loadPatient(patientId);
                NotificationHelper.showSuccess(timelineStatusLabel, "Patient marked DECEASED and death record created.");
            }
        } catch (Exception e) {
            NotificationHelper.showError(timelineStatusLabel, e.getMessage());
        }
    }

    @FXML
    private void addPatientMedication() {
        if (!PermissionHelper.canAddMedication(Session.getCurrentUser())) {
            timelineStatusLabel.setText("Access denied. Admin or Doctor role is required.");
            return;
        }
        if (patientId == null || patientId.isBlank()) {
            timelineStatusLabel.setText("No patient selected for medication entry.");
            return;
        }
        if (blockIfDeceased("add medication")) {
            return;
        }
        try {
            boolean saved = MedicationFormController.showCreateDialog(
                    nameLabel.getScene().getWindow(),
                    Session.getCurrentUser(),
                    patientId);
            if (saved) {
                NotificationHelper.showSuccess(timelineStatusLabel, "Medication saved in SQLite. Open Medications to view.");
            }
        } catch (Exception e) {
            NotificationHelper.showError(timelineStatusLabel, e.getMessage());
        }
    }

    @FXML
    private void recordPatientMedicationGiven() {
        if (!PermissionHelper.canGiveMedication(Session.getCurrentUser())) {
            timelineStatusLabel.setText("Access denied. Admin, Doctor, or Nurse role is required.");
            return;
        }
        if (patientId == null || patientId.isBlank()) {
            timelineStatusLabel.setText("No patient selected for medication administration.");
            return;
        }
        if (blockIfDeceased("record medication")) {
            return;
        }
        try {
            boolean saved = MedicationGivenController.showDialog(
                    nameLabel.getScene().getWindow(),
                    Session.getCurrentUser(),
                    patientId,
                    null);
            if (saved) {
                NotificationHelper.showSuccess(timelineStatusLabel, "Medication administration recorded.");
            }
        } catch (Exception e) {
            NotificationHelper.showError(timelineStatusLabel, e.getMessage());
        }
    }

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
            SqlitePatientDao.PatientDetail detail = patientDao.findDetailById(patientId)
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
        if (blockIfDeceased("enter vitals")) {
            return;
        }
        try {
            services.VitalsWriteService.VitalsWriteResult result = VitalsEntryController.showDialog(
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
                                + " as " + result.getStatus() + ". Timeline and alerts are refreshed.");
            }
        } catch (Exception e) {
            NotificationHelper.showError(timelineStatusLabel, e.getMessage());
        }
    }


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

    private void configureTable() {
        typeColumn.setCellValueFactory(new PropertyValueFactory<>("vitalType"));
        valueColumn.setCellValueFactory(new PropertyValueFactory<>("value"));
        unitColumn.setCellValueFactory(new PropertyValueFactory<>("unit"));
        timeColumn.setCellValueFactory(new PropertyValueFactory<>("dateTime"));
        sourceColumn.setCellValueFactory(new PropertyValueFactory<>("sourceType"));
        staffColumn.setCellValueFactory(new PropertyValueFactory<>("staffName"));
        deviceColumn.setCellValueFactory(new PropertyValueFactory<>("deviceId"));
    }

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

    private void configureWritePermissions() {
        boolean canEdit = PermissionHelper.canUpdatePatient(Session.getCurrentUser());
        setButtonVisible(editPatientButton, canEdit);
        boolean canEnterVitals = PermissionHelper.canEnterVitals(Session.getCurrentUser());
        setButtonVisible(enterVitalsButton, canEnterVitals);
        boolean canAddMedication = PermissionHelper.canAddMedication(Session.getCurrentUser());
        setButtonVisible(addMedicationButton, canAddMedication);
        setButtonVisible(clinicalAddMedicationButton, canAddMedication);
        boolean canGiveMedication = PermissionHelper.canGiveMedication(Session.getCurrentUser());
        setButtonVisible(recordMedicationButton, canGiveMedication);
        boolean canAppointments = PermissionHelper.canManageAppointment(Session.getCurrentUser());
        setButtonVisible(createAppointmentButton, canAppointments);
        boolean canReminders = PermissionHelper.canManageReminder(Session.getCurrentUser());
        setButtonVisible(createReminderButton, canReminders);
        boolean canUploadFiles = PermissionHelper.canUploadMedicalFile(Session.getCurrentUser());
        setButtonVisible(uploadMedicalFileButton, canUploadFiles);
        boolean canMoveRoom = PermissionHelper.canAssignPatientRoom(Session.getCurrentUser());
        setButtonVisible(movePatientButton, canMoveRoom);
        setButtonVisible(markDeceasedButton, PermissionHelper.canMarkPatientDeceased(Session.getCurrentUser()));
        setButtonVisible(viewNewbornsButton, PermissionHelper.canViewNewbornRecords(Session.getCurrentUser()));
    }

    private void updateDeceasedButton(String status) {
        boolean visible = PermissionHelper.canMarkPatientDeceased(Session.getCurrentUser())
                && !"DECEASED".equalsIgnoreCase(status);
        setButtonVisible(markDeceasedButton, visible);
    }

    private void updateClinicalActionBlocks() {
        if (!deceasedPatient) {
            return;
        }
        setButtonVisible(enterVitalsButton, false);
        setButtonVisible(addMedicationButton, false);
        setButtonVisible(clinicalAddMedicationButton, false);
        setButtonVisible(recordMedicationButton, false);
        setButtonVisible(createAppointmentButton, false);
        setButtonVisible(createReminderButton, false);
        setButtonVisible(movePatientButton, false);
    }

    private void setButtonVisible(Button button, boolean visible) {
        if (button != null) {
            button.setVisible(visible);
            button.setManaged(visible);
        }
    }

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
                + " | Staff: " + fallback(latest.getStaffUser(), "Not recorded")
                + (latest.getDeviceId().isBlank() ? "" : " | Device: " + latest.getDeviceId()));
        trendStatsLabel.setText(String.format("Min: %.1f | Max: %.1f | Avg: %.1f",
                result.getMin(), result.getMax(), result.getAverage()));
        trendCountsLabel.setText("Normal: " + result.getNormalCount()
                + " | Warning: " + result.getWarningCount()
                + " | Critical: " + result.getCriticalCount());
        trendStatusLabel.setText("Read-only local database trend loaded: " + result.getReadings().size() + " readings.");
    }

    private void loadLinkedNewborns() {
        if (patientId == null || patientId.isBlank()) {
            return;
        }
        try {
            List<SqliteNewbornRecordDao.NewbornRecord> babies = newbornDao.findByMother(patientId);
            babiesCountLabel.setText(babies.size() + (babies.size() == 1 ? " linked newborn" : " linked newborns"));
            if (babies.isEmpty()) {
                babiesListLabel.setText("No babies linked to this patient yet");
                return;
            }
            babiesListLabel.setText(babies.stream()
                    .map(baby -> baby.getNewbornId() + " - " + baby.getBabyName() + " (" + baby.getBirthTime() + ")")
                    .collect(Collectors.joining("\n")));
        } catch (Exception e) {
            babiesCountLabel.setText("Newborn links unavailable");
            babiesListLabel.setText(e.getMessage());
        }
    }

    private boolean blockIfDeceased(String action) {
        if (!deceasedPatient) {
            return false;
        }
        logAudit(ui.javafx.helpers.AuditAction.BLOCK_DECEASED_CLINICAL_ACTION + " patient_id=" + patientId + ", action=" + action);
        NotificationHelper.showError(timelineStatusLabel, "Clinical actions are blocked for deceased patients.");
        return true;
    }

    private void logAudit(String action) {
        try {
            auditLogDao.log(SessionContext.username(), action);
        } catch (Exception e) {
            System.out.println("SQLite patient detail audit skipped: " + e.getMessage());
        }
    }

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

    private String trendStyle(VitalThresholdService.VitalStatus status) {
        if (status == VitalThresholdService.VitalStatus.CRITICAL) {
            return "trend-critical";
        }
        if (status == VitalThresholdService.VitalStatus.WARNING) {
            return "trend-warning";
        }
        return "trend-normal";
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

    private void showVitalAlertPopupIfNeeded(services.VitalsWriteService.VitalsWriteResult result) {
        if (result.getStatus() == VitalThresholdService.VitalStatus.NORMAL) {
            return;
        }

        Alert.AlertType alertType = result.getStatus() == VitalThresholdService.VitalStatus.WARNING
                ? Alert.AlertType.WARNING
                : Alert.AlertType.ERROR;

        Alert alert = new Alert(alertType);
        alert.setTitle(result.getStatus() + " Vital Alert");
        ui.javafx.helpers.DialogThemeHelper.apply(alert);
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

    private String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
