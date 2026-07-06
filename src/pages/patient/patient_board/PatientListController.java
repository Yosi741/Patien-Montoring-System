package pages.patient.patient_board;

import app.AppShell;
import app.FxController;
import app.helpers.DialogHelper;
import app.helpers.PermissionHelper;
import app.helpers.SelectionHelper;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.FlowPane;
import pages.alert.AlertSoundService;
import pages.audit_log.AuditAction;
import pages.audit_log.SqliteAuditLogDao;
import pages.notification.NotificationHelper;
import pages.patient.dao.SqlitePatientDao;
import pages.patient.patient_form.PatientFormController;
import pages.patient.services.PatientWriteService;
import pages.patient.services.VitalThresholdService;
import pages.patient.services.VitalsWriteService;
import pages.patient.vitals_entry.VitalsEntryController;
import users.Session;

import java.util.ArrayList;
import java.util.List;

public class PatientListController implements FxController {

    private final SqlitePatientDao patientDao = new SqlitePatientDao();
    private final SqliteAuditLogDao auditLogDao = new SqliteAuditLogDao();
    private final PatientWriteService patientWriteService = new PatientWriteService();
    private final ObservableList<SqlitePatientDao.PatientListRow> patients = FXCollections.observableArrayList();
    private AppShell appShell;
    private String quickFilter = "All Patients";
    private boolean suppressFilterEvents;
    private boolean filterListenersConfigured;

    @FXML private TextField searchField;
    @FXML private ComboBox<String> sectionFilter;
    @FXML private ComboBox<String> roomFilter;
    @FXML private ComboBox<String> statusFilter;
    @FXML private ComboBox<String> priorityFilter;
    @FXML private FlowPane filterChipsBox;
    @FXML private Label statusLabel;
    @FXML private Label boardTitleLabel;
    @FXML private Label boardSubtitleLabel;
    @FXML private TableView<SqlitePatientDao.PatientListRow> patientTable;
    @FXML private TableColumn<SqlitePatientDao.PatientListRow, String> rowNumberColumn;
    @FXML private TableColumn<SqlitePatientDao.PatientListRow, String> idColumn;
    @FXML private TableColumn<SqlitePatientDao.PatientListRow, String> nameColumn;
    @FXML private TableColumn<SqlitePatientDao.PatientListRow, String> birthDateColumn;
    @FXML private TableColumn<SqlitePatientDao.PatientListRow, String> genderColumn;
    @FXML private TableColumn<SqlitePatientDao.PatientListRow, String> sectionColumn;
    @FXML private TableColumn<SqlitePatientDao.PatientListRow, String> roomColumn;
    @FXML private TableColumn<SqlitePatientDao.PatientListRow, String> statusColumn;
    @FXML private TableColumn<SqlitePatientDao.PatientListRow, String> priorityColumn;
    @FXML private Button addPatientButton;
    @FXML private Button editSelectedPatientButton;
    @FXML private Button dischargePatientButton;
    @FXML private Button enterVitalsButton;
    @FXML private Button viewPatientFileButton;

    @Override
    public void setAppShell(AppShell appShell) {
        this.appShell = appShell;
        configureTable();
        configureFilters();
        configureWritePermissions();
        configureSelectionActions();
        loadPatients();
        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!suppressFilterEvents) {
                quickFilter = "Custom";
                loadPatients();
            }
        });
    }

    @FXML
    private void loadPatients() {
        try {
            List<SqlitePatientDao.PatientListRow> loadedPatients = patientDao.findPatientListRows(buildFilter());
            SelectionHelper.runWhenTablesStable(() -> {
                SelectionHelper.safeReplaceItems(patientTable, patients, loadedPatients);
                updateBoardTitle();
                statusLabel.setText(boardTitleLabel.getText() + " loaded: " + patients.size());
                renderFilterChips();
            }, patientTable);
        } catch (Exception e) {
            statusLabel.setText("Could not load patients: " + e.getMessage());
        }
    }

    @FXML
    private void backToDashboard() {
        appShell.showDashboard(Session.getCurrentUser());
    }

    @FXML
    private void viewDetails() {
        SqlitePatientDao.PatientListRow selected = selectedPatient();
        if (selected != null) {
            appShell.showPatientDetail(selected.getPatientId());
        }
    }

    @FXML
    private void enterVitalsForSelectedPatient() {
        if (!PermissionHelper.canEnterVitals(Session.getCurrentUser())) {
            NotificationHelper.showError(statusLabel, "Access denied. Admin, Doctor, or Nurse role is required.");
            return;
        }
        SqlitePatientDao.PatientListRow selected = selectedPatient();
        if (selected == null) {
            return;
        }
        if (isDeceased(selected)) {
            logAudit(AuditAction.BLOCK_DECEASED_CLINICAL_ACTION + " patient_id=" + selected.getPatientId() + ", action=enter_vitals");
            NotificationHelper.showError(statusLabel, "Clinical actions are blocked for deceased patients.");
            return;
        }
        try {
            VitalsWriteService.VitalsWriteResult result = VitalsEntryController.showDialog(
                    patientTable.getScene().getWindow(),
                    Session.getCurrentUser(),
                    selected.getPatientId());
            if (result != null) {
                loadPatients();
                if (appShell != null) {
                    appShell.refreshNotificationCount();
                }
                showVitalAlertPopupIfNeeded(selected, result);
                NotificationHelper.showSuccess(statusLabel,
                        "Saved " + result.getVitalType() + " for " + selected.getName()
                                + " as " + result.getStatus() + ".");
            }
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
        }
    }

    @FXML
    private void addPatient() {
        if (!PermissionHelper.canCreatePatient(Session.getCurrentUser())) {
            NotificationHelper.showError(statusLabel, "Access denied. Admin or Doctor role is required.");
            return;
        }
        try {
            boolean saved = PatientFormController.showCreateDialog(patientTable.getScene().getWindow(), Session.getCurrentUser());
            if (saved) {
                configureFilters();
                loadPatients();
                NotificationHelper.showSuccess(statusLabel, "Patient record saved.");
            }
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
        }
    }

    @FXML
    private void editSelectedPatient() {
        if (!PermissionHelper.canUpdatePatient(Session.getCurrentUser())) {
            NotificationHelper.showError(statusLabel, "Access denied. Admin or Doctor role is required.");
            return;
        }
        SqlitePatientDao.PatientListRow selected = selectedPatient();
        if (selected == null) {
            return;
        }
        if (isDeceased(selected)) {
            logAudit(AuditAction.BLOCK_DECEASED_CLINICAL_ACTION + " patient_id=" + selected.getPatientId() + ", action=edit_patient");
            NotificationHelper.showError(statusLabel, "Editing is blocked for deceased patients.");
            return;
        }
        try {
            SqlitePatientDao.PatientDetail detail = patientDao.findDetailById(selected.getPatientId())
                    .orElseThrow(() -> new IllegalArgumentException("Patient not found in SQLite: " + selected.getPatientId()));
            boolean saved = PatientFormController.showEditDialog(patientTable.getScene().getWindow(), Session.getCurrentUser(), detail);
            if (saved) {
                configureFilters();
                loadPatients();
                NotificationHelper.showSuccess(statusLabel, "Patient record updated.");
            }
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
        }
    }

    @FXML
    private void dischargeSelectedPatient() {
        if (!PermissionHelper.canDeactivatePatient(Session.getCurrentUser())) {
            NotificationHelper.showError(statusLabel, "Access denied. Admin or Doctor role is required.");
            return;
        }
        SqlitePatientDao.PatientListRow selected = selectedPatient();
        if (selected == null) {
            return;
        }
        if (isDeceased(selected)) {
            logAudit(AuditAction.BLOCK_DECEASED_CLINICAL_ACTION + " patient_id=" + selected.getPatientId() + ", action=discharge");
            NotificationHelper.showError(statusLabel, "Discharge/deactivate is blocked for deceased patients.");
            return;
        }
        boolean confirmed = DialogHelper.confirm(
                "Discharge Patient",
                "Discharge " + selected.getName() + "? This updates the patient status to DISCHARGED.");
        if (!confirmed) {
            return;
        }
        try {
            patientWriteService.deactivateOrDischargePatient(Session.getCurrentUser(), selected.getPatientId());
            configureFilters();
            loadPatients();
            NotificationHelper.showSuccess(statusLabel, "Patient discharged: " + selected.getPatientId());
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
        }
    }

    @FXML
    private void showAllPatients() {
        quickFilter = "All Patients";
        clearFilterControls();
        loadPatients();
    }

    @FXML
    private void showActivePatients() {
        clearFilterControls();
        statusFilter.getSelectionModel().select("ACTIVE");
        quickFilter = "Active Patients";
        loadPatients();
    }

    @FXML
    private void showCriticalEmergency() {
        quickFilter = "Critical / Emergency";
        clearFilterControls();
        loadPatients();
    }

    @FXML
    private void showHighPriority() {
        clearFilterControls();
        priorityFilter.getSelectionModel().select("HIGH");
        quickFilter = "High Priority";
        loadPatients();
    }

    @FXML
    private void showRecentlyUpdated() {
        quickFilter = "Recently Updated";
        clearFilterControls();
        loadPatients();
    }

    @FXML
    private void clearFilters() {
        quickFilter = "All Patients";
        clearFilterControls();
        loadPatients();
    }

    private void configureTable() {
        rowNumberColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                int index = getIndex();
                int size = getTableView() == null || getTableView().getItems() == null ? 0 : getTableView().getItems().size();
                setText(empty || index < 0 || index >= size ? null : String.valueOf(index + 1));
            }
        });
        idColumn.setCellValueFactory(new PropertyValueFactory<>("patientId"));
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        birthDateColumn.setCellValueFactory(new PropertyValueFactory<>("birthDate"));
        genderColumn.setCellValueFactory(new PropertyValueFactory<>("gender"));
        sectionColumn.setCellValueFactory(new PropertyValueFactory<>("section"));
        roomColumn.setCellValueFactory(new PropertyValueFactory<>("room"));
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        priorityColumn.setCellValueFactory(new PropertyValueFactory<>("priority"));
        statusColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                getStyleClass().removeAll("patient-status-active", "patient-status-discharged", "patient-status-deceased");
                if (empty || status == null) {
                    setText(null);
                    return;
                }
                setText(status);
                getStyleClass().add(statusStyle(status));
            }
        });
        priorityColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String priority, boolean empty) {
                super.updateItem(priority, empty);
                getStyleClass().removeAll("priority-normal", "priority-high", "priority-critical", "priority-emergency");
                if (empty || priority == null) {
                    setText(null);
                    return;
                }
                setText(priority);
                getStyleClass().add(priorityStyle(priority));
            }
        });
        patientTable.setRowFactory(table -> {
            TableRow<SqlitePatientDao.PatientListRow> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty() && row.getItem() != null) {
                    appShell.showPatientDetail(row.getItem().getPatientId());
                }
            });
            return row;
        });
    }

    private void configureSelectionActions() {
        updateSelectionActions();
        patientTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> updateSelectionActions());
    }

    private void configureFilters() {
        try {
            sectionFilter.setItems(FXCollections.observableArrayList(withAll(patientDao.findDistinctSections())));
            roomFilter.setItems(FXCollections.observableArrayList(withAll(patientDao.findDistinctRooms())));
        } catch (Exception e) {
            sectionFilter.setItems(FXCollections.observableArrayList("All"));
            roomFilter.setItems(FXCollections.observableArrayList("All"));
            statusLabel.setText("Could not load filter choices: " + e.getMessage());
        }
        statusFilter.setItems(FXCollections.observableArrayList("All", "ACTIVE", "DISCHARGED", "DECEASED"));
        priorityFilter.setItems(FXCollections.observableArrayList("All", "NORMAL", "HIGH", "CRITICAL", "EMERGENCY"));
        sectionFilter.getSelectionModel().select("All");
        roomFilter.getSelectionModel().select("All");
        statusFilter.getSelectionModel().select("All");
        priorityFilter.getSelectionModel().select("All");
        if (!filterListenersConfigured) {
            sectionFilter.valueProperty().addListener((observable, oldValue, newValue) -> handleFilterChange());
            roomFilter.valueProperty().addListener((observable, oldValue, newValue) -> handleFilterChange());
            statusFilter.valueProperty().addListener((observable, oldValue, newValue) -> handleFilterChange());
            priorityFilter.valueProperty().addListener((observable, oldValue, newValue) -> handleFilterChange());
            filterListenersConfigured = true;
        }
    }

    private SqlitePatientDao.PatientFilter buildFilter() {
        SqlitePatientDao.PatientFilter filter = new SqlitePatientDao.PatientFilter();
        filter.setSearch(searchField.getText());
        filter.setSection(value(sectionFilter));
        filter.setRoom(value(roomFilter));
        filter.setStatus(value(statusFilter));
        filter.setPriority(value(priorityFilter));
        filter.setCriticalEmergencyOnly("Critical / Emergency".equals(quickFilter));
        filter.setRecentlyUpdatedOnly("Recently Updated".equals(quickFilter));
        return filter;
    }

    private void configureWritePermissions() {
        boolean canWritePatients = PermissionHelper.canCreatePatient(Session.getCurrentUser())
                || PermissionHelper.canUpdatePatient(Session.getCurrentUser());
        boolean canDischarge = PermissionHelper.canDeactivatePatient(Session.getCurrentUser());
        setButtonVisible(addPatientButton, canWritePatients);
        setButtonVisible(editSelectedPatientButton, canWritePatients);
        setButtonVisible(dischargePatientButton, canDischarge);
        boolean canEnterVitals = PermissionHelper.canEnterVitals(Session.getCurrentUser());
        setButtonVisible(enterVitalsButton, canEnterVitals);
        setButtonVisible(viewPatientFileButton, true);
    }

    private SqlitePatientDao.PatientListRow selectedPatient() {
        SqlitePatientDao.PatientListRow selected = patientTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            NotificationHelper.showError(statusLabel, "Select a patient first.");
            return null;
        }
        return selected;
    }

    private List<String> withAll(List<String> values) {
        ArrayList<String> choices = new ArrayList<>();
        choices.add("All");
        choices.addAll(values);
        return choices;
    }

    private void clearFilterControls() {
        suppressFilterEvents = true;
        searchField.clear();
        sectionFilter.getSelectionModel().select("All");
        roomFilter.getSelectionModel().select("All");
        statusFilter.getSelectionModel().select("All");
        priorityFilter.getSelectionModel().select("All");
        suppressFilterEvents = false;
    }

    private void handleFilterChange() {
        if (suppressFilterEvents) {
            return;
        }
        quickFilter = "Custom";
        loadPatients();
    }

    private void renderFilterChips() {
        filterChipsBox.getChildren().clear();
        addChip("Quick: " + quickFilter);
        addChipIfPresent("Search", searchField.getText());
        addChipIfSelected("Section", value(sectionFilter));
        addChipIfSelected("Room", value(roomFilter));
        addChipIfSelected("Status", value(statusFilter));
        addChipIfSelected("Priority", value(priorityFilter));
    }

    private void addChipIfSelected(String label, String value) {
        if (value != null && !value.isBlank() && !"All".equalsIgnoreCase(value)) {
            addChip(label + ": " + value);
        }
    }

    private void addChipIfPresent(String label, String value) {
        if (value != null && !value.isBlank()) {
            addChip(label + ": " + value.trim());
        }
    }

    private void addChip(String text) {
        Label chip = new Label(text);
        chip.getStyleClass().add("filter-chip");
        filterChipsBox.getChildren().add(chip);
    }

    private String value(ComboBox<String> comboBox) {
        return comboBox == null || comboBox.getValue() == null ? "All" : comboBox.getValue();
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

    private String statusStyle(String status) {
        if (status == null) {
            return "patient-status-active";
        }
        if ("DECEASED".equalsIgnoreCase(status)) {
            return "patient-status-deceased";
        }
        if ("DISCHARGED".equalsIgnoreCase(status) || "INACTIVE".equalsIgnoreCase(status)) {
            return "patient-status-discharged";
        }
        return "patient-status-active";
    }

    private void updateSelectionActions() {
        SqlitePatientDao.PatientListRow selected = patientTable == null ? null : patientTable.getSelectionModel().getSelectedItem();
        boolean hasSelection = selected != null;
        boolean deceased = isDeceased(selected);
        setDisabledIfPresent(editSelectedPatientButton, !hasSelection || deceased);
        setDisabledIfPresent(dischargePatientButton, !hasSelection || deceased);
        setDisabledIfPresent(enterVitalsButton, !hasSelection || deceased);
        setDisabledIfPresent(viewPatientFileButton, !hasSelection);
    }

    private void setDisabledIfPresent(Button button, boolean disabled) {
        if (button != null) {
            button.setDisable(disabled);
        }
    }

    private void showVitalAlertPopupIfNeeded(SqlitePatientDao.PatientListRow selected, VitalsWriteService.VitalsWriteResult result) {
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
                "Patient: " + selected.getName()
                        + "\nPatient ID: " + selected.getPatientId()
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

    private void updateBoardTitle() {
        boardTitleLabel.setText("Patients");
        if ("Active Patients".equals(quickFilter)) {
            boardSubtitleLabel.setText("Active patient records filtered from the local clinic database.");
        } else if ("Critical / Emergency".equals(quickFilter)) {
            boardSubtitleLabel.setText("Critical and emergency patients filtered by current priority.");
        } else if ("High Priority".equals(quickFilter)) {
            boardSubtitleLabel.setText("High-priority patients filtered for quicker clinical review.");
        } else if ("Recently Updated".equals(quickFilter)) {
            boardSubtitleLabel.setText("Recently updated patient records from the local clinic database.");
        } else {
            boardSubtitleLabel.setText("Double-click a row to open the full patient file.");
        }
    }

    private boolean isDeceased(SqlitePatientDao.PatientListRow row) {
        return row != null && "DECEASED".equalsIgnoreCase(row.getStatus());
    }

    private void setButtonVisible(Button button, boolean visible) {
        if (button != null) {
            button.setVisible(visible);
            button.setManaged(visible);
        }
    }

    private void logAudit(String action) {
        try {
            auditLogDao.log(Session.getUsername(), action);
        } catch (Exception e) {
            System.out.println("SQLite patient board audit skipped: " + e.getMessage());
        }
    }
}
