package ui.javafx.controllers;

import Data_Access_Object.SqliteAuditLogDao;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import Data_Access_Object.SqliteMedicationDao;
import ui.javafx.services.MedicationOverviewService;
import ui.javafx.services.MedicationWriteService;
import ui.javafx.AppShell;
import ui.javafx.FxController;
import ui.javafx.SessionContext;
import ui.javafx.helpers.DialogHelper;
import ui.javafx.helpers.NotificationHelper;
import ui.javafx.helpers.PermissionHelper;
import ui.javafx.helpers.SelectionHelper;
import users.Session;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

public class MedicationOverviewController implements FxController {

    private final MedicationOverviewService medicationService = new MedicationOverviewService();
    private final MedicationWriteService medicationWriteService = new MedicationWriteService();
    private final SqliteAuditLogDao auditLogDao = new SqliteAuditLogDao();
    private final ObservableList<MedicationOverviewService.MedicationRow> medicationRows = FXCollections.observableArrayList();
    private final ObservableList<MedicationOverviewService.MedicationEventRow> eventRows = FXCollections.observableArrayList();
    private AppShell appShell;
    private String patientIdFilter = "";

    @FXML private VBox accessDeniedPane;
    @FXML private VBox medicationContentPane;
    @FXML private Label scopeLabel;
    @FXML private Label patientFilterChip;
    @FXML private Button clearPatientFilterButton;
    @FXML private Button addMedicationButton;
    @FXML private Button registerCatalogButton;
    @FXML private Button editMedicationButton;
    @FXML private Button discontinueMedicationButton;
    @FXML private Button recordGivenButton;
    @FXML private Button createReminderButton;
    @FXML private TextField searchField;
    @FXML private ComboBox<String> activeFilter;
    @FXML private ComboBox<String> routeFilter;
    @FXML private ComboBox<String> eventDateRangeFilter;
    @FXML private Label activeMedicationsLabel;
    @FXML private Label eventsTodayLabel;
    @FXML private Label missedOverdueLabel;
    @FXML private Label patientsWithMedicationsLabel;
    @FXML private Label latestEventTimeLabel;
    @FXML private TableView<MedicationOverviewService.MedicationRow> medicationTable;
    @FXML private TableColumn<MedicationOverviewService.MedicationRow, Number> medRowNumberColumn;
    @FXML private TableColumn<MedicationOverviewService.MedicationRow, String> medPatientIdColumn;
    @FXML private TableColumn<MedicationOverviewService.MedicationRow, String> medPatientNameColumn;
    @FXML private TableColumn<MedicationOverviewService.MedicationRow, String> medNameColumn;
    @FXML private TableColumn<MedicationOverviewService.MedicationRow, String> doseColumn;
    @FXML private TableColumn<MedicationOverviewService.MedicationRow, String> routeColumn;
    @FXML private TableColumn<MedicationOverviewService.MedicationRow, String> frequencyColumn;
    @FXML private TableColumn<MedicationOverviewService.MedicationRow, String> activeColumn;
    @FXML private TableView<MedicationOverviewService.MedicationEventRow> eventTable;
    @FXML private TableColumn<MedicationOverviewService.MedicationEventRow, Number> eventRowNumberColumn;
    @FXML private TableColumn<MedicationOverviewService.MedicationEventRow, String> eventPatientIdColumn;
    @FXML private TableColumn<MedicationOverviewService.MedicationEventRow, String> eventMedicationColumn;
    @FXML private TableColumn<MedicationOverviewService.MedicationEventRow, String> givenByColumn;
    @FXML private TableColumn<MedicationOverviewService.MedicationEventRow, String> givenAtColumn;
    @FXML private TableColumn<MedicationOverviewService.MedicationEventRow, String> notesColumn;
    @FXML private Label statusLabel;

    @Override
    public void setAppShell(AppShell appShell) {
        this.appShell = appShell;
        configureAccess();
        configureFilters();
        configureTables();
        configureWriteButtons();
        if (isAuthorized()) {
            logAudit("JavaFX MEDICATION opened overview");
            loadOverview();
        }
    }

    public void openForPatient(String patientId) {
        patientIdFilter = patientId == null ? "" : patientId;
        updatePatientFilterChip();
        if (isAuthorized()) {
            logAudit("JavaFX MEDICATION opened patient-filtered overview for " + patientIdFilter);
            loadOverview();
        }
    }

    @FXML
    private void loadOverview() {
        if (!isAuthorized()) {
            statusLabel.setText("Access denied.");
            return;
        }

        try {
            MedicationOverviewService.MedicationFilter filter = new MedicationOverviewService.MedicationFilter(
                    searchField.getText(),
                    activeFilter.getValue(),
                    routeFilter.getValue(),
                    eventDateRangeFilter.getValue(),
                    patientIdFilter
            );
            MedicationOverviewService.MedicationOverview overview = medicationService.loadOverview(filter);
            renderOverview(overview);
            statusLabel.setText("Medication overview refreshed from the local database at "
                    + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        } catch (Exception e) {
            statusLabel.setText("Could not load medication overview: " + e.getMessage());
        }
    }

    @FXML
    private void clearFilters() {
        searchField.clear();
        activeFilter.getSelectionModel().select("All");
        routeFilter.getSelectionModel().select("All");
        eventDateRangeFilter.getSelectionModel().select("Last 30 days");
        loadOverview();
    }

    @FXML
    private void clearPatientFilter() {
        patientIdFilter = "";
        updatePatientFilterChip();
        loadOverview();
    }

    @FXML
    private void showDashboard() {
        appShell.showDashboard(Session.getCurrentUser());
    }

    @FXML
    private void addMedication() {
        if (!PermissionHelper.canAddMedication(Session.getCurrentUser())) {
            NotificationHelper.showError(statusLabel, "Access denied. Admin or Doctor role is required.");
            return;
        }
        String patientId = targetPatientId();
        if (patientId.isBlank()) {
            NotificationHelper.showError(statusLabel, "Select a medication row or open this screen from Patient Detail first.");
            return;
        }
        try {
            boolean saved = MedicationFormController.showCreateDialog(medicationTable.getScene().getWindow(), Session.getCurrentUser(), patientId);
            if (saved) {
                loadOverview();
                NotificationHelper.showSuccess(statusLabel, "Medication saved. System data updated.");
            }
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
        }
    }

    @FXML
    private void registerCatalogMedication() {
        if (!PermissionHelper.canManageMedicationCatalog(Session.getCurrentUser())) {
            NotificationHelper.showError(statusLabel, "Access denied. Admin or Doctor role is required.");
            return;
        }
        try {
            boolean changed = MedicationCatalogController.showDialog(
                    medicationTable.getScene().getWindow(),
                    Session.getCurrentUser());
            if (changed) {
                NotificationHelper.showSuccess(statusLabel, "Medication catalog updated.");
            }
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
        }
    }

    @FXML
    private void editSelectedMedication() {
        if (!PermissionHelper.canAddMedication(Session.getCurrentUser())) {
            NotificationHelper.showError(statusLabel, "Access denied. Admin or Doctor role is required.");
            return;
        }
        MedicationOverviewService.MedicationRow selected = selectedMedicationRow();
        if (selected == null) {
            return;
        }
        try {
            boolean saved = MedicationFormController.showEditDialog(
                    medicationTable.getScene().getWindow(),
                    Session.getCurrentUser(),
                    toMedicationRecord(selected));
            if (saved) {
                loadOverview();
                NotificationHelper.showSuccess(statusLabel, "Medication updated.");
            }
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
        }
    }

    @FXML
    private void discontinueSelectedMedication() {
        if (!PermissionHelper.canAddMedication(Session.getCurrentUser())) {
            NotificationHelper.showError(statusLabel, "Access denied. Admin or Doctor role is required.");
            return;
        }
        MedicationOverviewService.MedicationRow selected = selectedMedicationRow();
        if (selected == null) {
            return;
        }
        boolean confirmed = DialogHelper.confirm(
                "Discontinue Medication",
                "Discontinue " + selected.getMedicationName() + " for patient " + selected.getPatientId()
                        + "? This updates SQLite only.");
        if (!confirmed) {
            return;
        }
        try {
            medicationWriteService.discontinueMedication(Session.getCurrentUser(), selected.getId());
            loadOverview();
            NotificationHelper.showSuccess(statusLabel, "Medication discontinued.");
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
        }
    }

    @FXML
    private void recordMedicationGiven() {
        if (!PermissionHelper.canGiveMedication(Session.getCurrentUser())) {
            NotificationHelper.showError(statusLabel, "Access denied. Admin, Doctor, or Nurse role is required.");
            return;
        }
        MedicationOverviewService.MedicationRow selected = medicationTable.getSelectionModel().getSelectedItem();
        String patientId = selected == null ? targetPatientId() : selected.getPatientId();
        if (patientId == null || patientId.isBlank()) {
            NotificationHelper.showError(statusLabel, "Select a medication row or open this screen from Patient Detail first.");
            return;
        }
        if (selected != null && !selected.isActive()) {
            NotificationHelper.showError(statusLabel, "Cannot record administration for inactive/discontinued medication.");
            return;
        }
        SqliteMedicationDao.MedicationRecord selectedMedication = selected == null ? null : toMedicationRecord(selected);
        try {
            boolean saved = MedicationGivenController.showDialog(
                    medicationTable.getScene().getWindow(),
                    Session.getCurrentUser(),
                    patientId,
                    selectedMedication);
            if (saved) {
                loadOverview();
                NotificationHelper.showSuccess(statusLabel, "Medication administration recorded.");
            }
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
        }
    }

    @FXML
    private void createMedicationReminder() {
        if (!PermissionHelper.canManageReminder(Session.getCurrentUser())) {
            NotificationHelper.showError(statusLabel, "Access denied. Admin, Doctor, or Nurse role is required.");
            return;
        }
        MedicationOverviewService.MedicationRow selected = selectedMedicationRow();
        if (selected == null) {
            return;
        }
        if (!selected.isActive()) {
            NotificationHelper.showError(statusLabel, "Medication reminder must reference an active medication.");
            return;
        }
        try {
            boolean saved = ReminderFormController.showCreateDialog(
                    medicationTable.getScene().getWindow(),
                    Session.getCurrentUser(),
                    selected.getPatientId(),
                    selected.getId(),
                    selected.getMedicationName());
            if (saved) {
                loadOverview();
                NotificationHelper.showSuccess(statusLabel, "Medication reminder saved.");
            }
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
        }
    }

    private void configureAccess() {
        boolean authorized = isAuthorized();
        accessDeniedPane.setVisible(!authorized);
        accessDeniedPane.setManaged(!authorized);
        medicationContentPane.setVisible(authorized);
        medicationContentPane.setManaged(authorized);
        if (isAdmin()) {
            scopeLabel.setText("Admin view: all local database medication rows and administration events.");
        } else if (isClinical()) {
            scopeLabel.setText("Clinical read-only medication overview for JavaFX application.");
        }
        updatePatientFilterChip();
    }

    private void configureWriteButtons() {
        boolean canManage = PermissionHelper.canAddMedication(Session.getCurrentUser());
        boolean canGive = PermissionHelper.canGiveMedication(Session.getCurrentUser());
        boolean canManageCatalog = PermissionHelper.canManageMedicationCatalog(Session.getCurrentUser());
        registerCatalogButton.setVisible(canManageCatalog);
        registerCatalogButton.setManaged(canManageCatalog);
        addMedicationButton.setVisible(canManage);
        addMedicationButton.setManaged(canManage);
        editMedicationButton.setVisible(canManage);
        editMedicationButton.setManaged(canManage);
        discontinueMedicationButton.setVisible(canManage);
        discontinueMedicationButton.setManaged(canManage);
        recordGivenButton.setVisible(canGive);
        recordGivenButton.setManaged(canGive);
        boolean canManageReminders = PermissionHelper.canManageReminder(Session.getCurrentUser());
        createReminderButton.setVisible(canManageReminders);
        createReminderButton.setManaged(canManageReminders);
    }

    private void configureFilters() {
        activeFilter.setItems(FXCollections.observableArrayList("All", "Active", "Inactive"));
        eventDateRangeFilter.setItems(FXCollections.observableArrayList("Today", "Last 7 days", "Last 30 days", "All"));
        activeFilter.getSelectionModel().select("All");
        eventDateRangeFilter.getSelectionModel().select("Last 30 days");

        ArrayList<String> routes = new ArrayList<>();
        routes.add("All");
        try {
            routes.addAll(medicationService.findRoutes());
        } catch (Exception e) {
            statusLabel.setText("Route filters unavailable: " + e.getMessage());
        }
        routeFilter.setItems(FXCollections.observableArrayList(routes));
        routeFilter.getSelectionModel().select("All");

        searchField.textProperty().addListener((observable, oldValue, newValue) -> loadOverview());
        activeFilter.valueProperty().addListener((observable, oldValue, newValue) -> loadOverview());
        routeFilter.valueProperty().addListener((observable, oldValue, newValue) -> loadOverview());
        eventDateRangeFilter.valueProperty().addListener((observable, oldValue, newValue) -> loadOverview());
    }

    private void configureTables() {
        if (medRowNumberColumn != null) {
            medRowNumberColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(
                    medicationTable.getItems().indexOf(cell.getValue()) + 1));
        }
        medPatientIdColumn.setCellValueFactory(new PropertyValueFactory<>("patientId"));
        medPatientNameColumn.setCellValueFactory(new PropertyValueFactory<>("patientName"));
        medNameColumn.setCellValueFactory(new PropertyValueFactory<>("medicationName"));
        doseColumn.setCellValueFactory(new PropertyValueFactory<>("dose"));
        routeColumn.setCellValueFactory(new PropertyValueFactory<>("route"));
        frequencyColumn.setCellValueFactory(new PropertyValueFactory<>("frequency"));
        activeColumn.setCellValueFactory(new PropertyValueFactory<>("activeStatus"));

        if (eventRowNumberColumn != null) {
            eventRowNumberColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(
                    eventTable.getItems().indexOf(cell.getValue()) + 1));
        }
        eventPatientIdColumn.setCellValueFactory(new PropertyValueFactory<>("patientId"));
        eventMedicationColumn.setCellValueFactory(new PropertyValueFactory<>("medicationName"));
        givenByColumn.setCellValueFactory(new PropertyValueFactory<>("givenBy"));
        givenAtColumn.setCellValueFactory(new PropertyValueFactory<>("givenAt"));
        notesColumn.setCellValueFactory(new PropertyValueFactory<>("notes"));
    }

    private void renderOverview(MedicationOverviewService.MedicationOverview overview) {
        activeMedicationsLabel.setText(String.valueOf(overview.getActiveMedicationCount()));
        eventsTodayLabel.setText(String.valueOf(overview.getMedicationEventsToday()));
        missedOverdueLabel.setText(String.valueOf(overview.getMissedOverduePlaceholderCount()));
        patientsWithMedicationsLabel.setText(String.valueOf(overview.getPatientsWithActiveMedications()));
        latestEventTimeLabel.setText(overview.getLatestMedicationEventTime());

        SelectionHelper.safeClearSelection(medicationTable);
        SelectionHelper.safeClearSelection(eventTable);
        medicationRows.setAll(overview.getMedications());
        medicationTable.setItems(medicationRows);
        eventRows.setAll(overview.getEvents());
        eventTable.setItems(eventRows);
    }

    private MedicationOverviewService.MedicationRow selectedMedicationRow() {
        MedicationOverviewService.MedicationRow selected = medicationTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            NotificationHelper.showError(statusLabel, "Select a medication first.");
            return null;
        }
        return selected;
    }

    private String targetPatientId() {
        if (patientIdFilter != null && !patientIdFilter.isBlank()) {
            return patientIdFilter;
        }
        MedicationOverviewService.MedicationRow selected = medicationTable.getSelectionModel().getSelectedItem();
        return selected == null ? "" : selected.getPatientId();
    }

    private SqliteMedicationDao.MedicationRecord toMedicationRecord(MedicationOverviewService.MedicationRow row) {
        try {
            return medicationWriteService.findMedicationById(row.getId()).orElseGet(() -> fallbackMedicationRecord(row));
        } catch (Exception e) {
            return fallbackMedicationRecord(row);
        }
    }

    private SqliteMedicationDao.MedicationRecord fallbackMedicationRecord(MedicationOverviewService.MedicationRow row) {
        return new SqliteMedicationDao.MedicationRecord(
                row.getId(),
                row.getPatientId(),
                row.getMedicationName(),
                row.getDose(),
                row.getRoute(),
                row.getFrequency(),
                row.isActive()
        );
    }

    private void updatePatientFilterChip() {
        boolean patientFiltered = patientIdFilter != null && !patientIdFilter.isBlank();
        patientFilterChip.setVisible(patientFiltered);
        patientFilterChip.setManaged(patientFiltered);
        clearPatientFilterButton.setVisible(patientFiltered);
        clearPatientFilterButton.setManaged(patientFiltered);
        patientFilterChip.setText(patientFiltered ? "Patient ID = " + patientIdFilter : "");
    }

    private void logAudit(String action) {
        try {
            auditLogDao.log(SessionContext.username(), action);
        } catch (Exception e) {
            System.out.println("Medication overview audit skipped: " + e.getMessage());
        }
    }

    private boolean isAuthorized() {
        return isAdmin() || isClinical();
    }

    private boolean isAdmin() {
        return "ADMIN".equals(roleGroup(SessionContext.role()));
    }

    private boolean isClinical() {
        String role = roleGroup(SessionContext.role());
        return "DOCTOR".equals(role) || "NURSE".equals(role);
    }

    private String roleGroup(String role) {
        if (role == null) {
            return "UNKNOWN";
        }
        String upper = role.toUpperCase();
        if (upper.contains("ADMIN")) {
            return "ADMIN";
        }
        if (upper.contains("DOCTOR") || upper.contains("MEDICAL") || upper.contains("DEPARTMENT HEAD")) {
            return "DOCTOR";
        }
        if (upper.contains("NURSE") || upper.contains("NURSING")) {
            return "NURSE";
        }
        if (upper.isBlank() || upper.equals("UNKNOWN")) {
            return "UNKNOWN";
        }
        return "STAFF";
    }
}
