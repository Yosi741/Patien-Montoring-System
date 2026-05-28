package ui.javafx.controllers;

import dao.SqlitePatientDao;
import dao.SqliteAuditLogDao;
import dao.SqliteNewbornRecordDao;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.FlowPane;
import services.AlertSoundService;
import services.PatientWriteService;
import services.VitalThresholdService;
import services.VitalsWriteService;
import ui.javafx.AppShell;
import ui.javafx.FxController;
import ui.javafx.helpers.DialogHelper;
import ui.javafx.helpers.NotificationHelper;
import ui.javafx.helpers.PermissionHelper;
import users.Session;

import java.util.ArrayList;
import java.util.List;

public class PatientListController implements FxController {

    private final SqlitePatientDao patientDao = new SqlitePatientDao();
    private final SqliteNewbornRecordDao newbornDao = new SqliteNewbornRecordDao();
    private final SqliteAuditLogDao auditLogDao = new SqliteAuditLogDao();
    private final PatientWriteService patientWriteService = new PatientWriteService();
    private final ObservableList<SqlitePatientDao.PatientListRow> patients = FXCollections.observableArrayList();
    private final ObservableList<SqliteNewbornRecordDao.NewbornRecord> newborns = FXCollections.observableArrayList();
    private AppShell appShell;
    private String quickFilter = "All Patients";
    private boolean newbornMode;
    private boolean suppressFilterEvents;
    private boolean filterListenersConfigured;

    @FXML
    private TextField searchField;

    @FXML
    private ComboBox<String> sectionFilter;

    @FXML
    private ComboBox<String> roomFilter;

    @FXML
    private ComboBox<String> statusFilter;

    @FXML
    private ComboBox<String> priorityFilter;

    @FXML
    private FlowPane filterChipsBox;

    @FXML
    private Label statusLabel;

    @FXML
    private Label boardTitleLabel;

    @FXML
    private Label boardSubtitleLabel;

    @FXML
    private TableView<SqlitePatientDao.PatientListRow> patientTable;

    @FXML
    private TableView<SqliteNewbornRecordDao.NewbornRecord> newbornTable;

    @FXML
    private TableColumn<SqlitePatientDao.PatientListRow, String> idColumn;

    @FXML
    private TableColumn<SqlitePatientDao.PatientListRow, String> nameColumn;

    @FXML
    private TableColumn<SqlitePatientDao.PatientListRow, String> birthDateColumn;

    @FXML
    private TableColumn<SqlitePatientDao.PatientListRow, String> genderColumn;

    @FXML
    private TableColumn<SqlitePatientDao.PatientListRow, String> sectionColumn;

    @FXML
    private TableColumn<SqlitePatientDao.PatientListRow, String> roomColumn;

    @FXML
    private TableColumn<SqlitePatientDao.PatientListRow, String> statusColumn;

    @FXML
    private TableColumn<SqlitePatientDao.PatientListRow, String> priorityColumn;

    @FXML
    private TableColumn<SqliteNewbornRecordDao.NewbornRecord, String> newbornIdColumn;

    @FXML
    private TableColumn<SqliteNewbornRecordDao.NewbornRecord, String> babyNameColumn;

    @FXML
    private TableColumn<SqliteNewbornRecordDao.NewbornRecord, String> newbornGenderColumn;

    @FXML
    private TableColumn<SqliteNewbornRecordDao.NewbornRecord, String> birthTimeColumn;

    @FXML
    private TableColumn<SqliteNewbornRecordDao.NewbornRecord, String> motherColumn;

    @FXML
    private TableColumn<SqliteNewbornRecordDao.NewbornRecord, String> newbornSectionColumn;

    @FXML
    private TableColumn<SqliteNewbornRecordDao.NewbornRecord, String> newbornRoomColumn;

    @FXML
    private TableColumn<SqliteNewbornRecordDao.NewbornRecord, String> certificateStatusColumn;

    @FXML
    private Button addPatientButton;

    @FXML
    private Button editSelectedPatientButton;

    @FXML
    private Button dischargePatientButton;

    @FXML
    private Button enterVitalsButton;

    @FXML
    private Button viewPatientFileButton;

    @FXML
    private Button deceasedQuickButton;

    @FXML
    private Button newbornQuickButton;

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
                if (!newbornMode) {
                    quickFilter = "Custom";
                }
                loadPatients();
            }
        });
    }

    @FXML
    private void loadPatients() {
        try {
            if (newbornMode) {
                loadNewborns();
                return;
            }
            showPatientTable();
            patients.setAll(patientDao.findPatientListRows(buildFilter()));
            patientTable.setItems(patients);
            updateBoardTitle();
            statusLabel.setText(boardTitleLabel.getText() + " loaded from SQLite: " + patients.size());
            renderFilterChips();
        } catch (Exception e) {
            statusLabel.setText("Could not load SQLite patients: " + e.getMessage());
        }
    }

    private void loadNewborns() {
        try {
            showNewbornTable();
            SqliteNewbornRecordDao.RecordFilter filter = new SqliteNewbornRecordDao.RecordFilter();
            filter.setSearch(searchField.getText());
            filter.setSection(value(sectionFilter));
            newborns.setAll(newbornDao.findRecords(filter));
            newbornTable.setItems(newborns);
            updateBoardTitle();
            statusLabel.setText("Newborn records loaded from SQLite: " + newborns.size());
            renderFilterChips();
        } catch (Exception e) {
            statusLabel.setText("Could not load SQLite newborn records: " + e.getMessage());
        }
    }

    @FXML
    private void backToDashboard() {
        appShell.showDashboard(Session.getCurrentUser());
    }

    @FXML
    private void viewDetails() {
        SqlitePatientDao.PatientListRow selected = patientTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            statusLabel.setText("Select a patient first.");
            return;
        }
        appShell.showPatientDetail(selected.getPatientId());
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
                NotificationHelper.showSuccess(statusLabel, "Patient created in SQLite. Legacy text files were not changed.");
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
        try {
            SqlitePatientDao.PatientDetail detail = patientDao.findDetailById(selected.getPatientId())
                    .orElseThrow(() -> new IllegalArgumentException("Patient not found in SQLite: " + selected.getPatientId()));
            boolean saved = PatientFormController.showEditDialog(patientTable.getScene().getWindow(), Session.getCurrentUser(), detail);
            if (saved) {
                configureFilters();
                loadPatients();
                NotificationHelper.showSuccess(statusLabel, "Patient updated in SQLite. Legacy text files were not changed.");
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
        boolean confirmed = DialogHelper.confirm(
                "Discharge Patient",
                "Discharge " + selected.getName() + "? This updates SQLite status to DISCHARGED only. Legacy text files are not changed.");
        if (!confirmed) {
            return;
        }
        try {
            patientWriteService.deactivateOrDischargePatient(Session.getCurrentUser(), selected.getPatientId());
            configureFilters();
            loadPatients();
            NotificationHelper.showSuccess(statusLabel, "Patient discharged in SQLite: " + selected.getPatientId());
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
        }
    }

    @FXML
    private void syncFromLegacyStorage() {
        String result = appShell.syncFromLegacyStorage();
        configureFilters();
        loadPatients();
        statusLabel.setText(result.replace(System.lineSeparator(), " "));
    }

    @FXML
    private void showAllPatients() {
        quickFilter = "All Patients";
        newbornMode = false;
        clearFilterControls();
        loadPatients();
    }

    @FXML
    private void showActivePatients() {
        newbornMode = false;
        clearFilterControls();
        statusFilter.getSelectionModel().select("ACTIVE");
        quickFilter = "Active Patients";
        loadPatients();
    }

    @FXML
    private void showDeceasedPatients() {
        if (!PermissionHelper.canViewDeceasedRecords(Session.getCurrentUser())) {
            NotificationHelper.showError(statusLabel, "Access denied. Admin, Doctor, or Nurse role is required.");
            return;
        }
        newbornMode = false;
        clearFilterControls();
        statusFilter.getSelectionModel().select("DECEASED");
        quickFilter = "Deceased Patients";
        logAudit("JavaFX PATIENT_BOARD opened Deceased Patients subsection");
        loadPatients();
    }

    @FXML
    private void showNewborns() {
        if (!PermissionHelper.canViewNewbornRecords(Session.getCurrentUser())) {
            NotificationHelper.showError(statusLabel, "Access denied. Admin, Doctor, or Nurse role is required.");
            return;
        }
        quickFilter = "Newborns";
        newbornMode = true;
        clearFilterControls();
        logAudit("JavaFX PATIENT_BOARD opened Newborns subsection");
        loadPatients();
    }

    @FXML
    private void showCriticalEmergency() {
        quickFilter = "Critical/Emergency";
        newbornMode = false;
        clearFilterControls();
        loadPatients();
    }

    @FXML
    private void showHighPriority() {
        newbornMode = false;
        clearFilterControls();
        priorityFilter.getSelectionModel().select("HIGH");
        quickFilter = "High Priority";
        loadPatients();
    }

    @FXML
    private void showRecentlyUpdated() {
        quickFilter = "Recently Updated";
        newbornMode = false;
        clearFilterControls();
        loadPatients();
    }

    @FXML
    private void clearFilters() {
        quickFilter = "All Patients";
        newbornMode = false;
        clearFilterControls();
        loadPatients();
    }

    private void configureTable() {
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
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    appShell.showPatientDetail(row.getItem().getPatientId());
                }
            });
            return row;
        });

        newbornIdColumn.setCellValueFactory(new PropertyValueFactory<>("newbornId"));
        babyNameColumn.setCellValueFactory(new PropertyValueFactory<>("babyName"));
        newbornGenderColumn.setCellValueFactory(new PropertyValueFactory<>("gender"));
        birthTimeColumn.setCellValueFactory(new PropertyValueFactory<>("birthTime"));
        motherColumn.setCellValueFactory(new PropertyValueFactory<>("motherDisplay"));
        newbornSectionColumn.setCellValueFactory(new PropertyValueFactory<>("section"));
        newbornRoomColumn.setCellValueFactory(new PropertyValueFactory<>("room"));
        certificateStatusColumn.setCellValueFactory(new PropertyValueFactory<>("certificateStatus"));
        certificateStatusColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                getStyleClass().removeAll("patient-status-active", "patient-status-discharged");
                if (empty || status == null) {
                    setText(null);
                    return;
                }
                setText(status);
                getStyleClass().add("Generated".equalsIgnoreCase(status) ? "patient-status-active" : "patient-status-discharged");
            }
        });
        newbornTable.setRowFactory(table -> {
            TableRow<SqliteNewbornRecordDao.NewbornRecord> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    appShell.showNewbornRecord(row.getItem().getId());
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
        filter.setCriticalEmergencyOnly("Critical/Emergency".equals(quickFilter));
        filter.setRecentlyUpdatedOnly("Recently Updated".equals(quickFilter));
        return filter;
    }

    private void configureWritePermissions() {
        boolean canWritePatients = PermissionHelper.canCreatePatient(Session.getCurrentUser())
                || PermissionHelper.canUpdatePatient(Session.getCurrentUser());
        boolean canDischarge = PermissionHelper.canDeactivatePatient(Session.getCurrentUser());
        addPatientButton.setVisible(canWritePatients);
        addPatientButton.setManaged(canWritePatients);
        editSelectedPatientButton.setVisible(canWritePatients);
        editSelectedPatientButton.setManaged(canWritePatients);
        dischargePatientButton.setVisible(canDischarge);
        dischargePatientButton.setManaged(canDischarge);
        boolean canEnterVitals = PermissionHelper.canEnterVitals(Session.getCurrentUser());
        enterVitalsButton.setVisible(canEnterVitals);
        enterVitalsButton.setManaged(canEnterVitals);
        boolean canViewDeceased = PermissionHelper.canViewDeceasedRecords(Session.getCurrentUser());
        deceasedQuickButton.setVisible(canViewDeceased);
        deceasedQuickButton.setManaged(canViewDeceased);
        boolean canViewNewborns = PermissionHelper.canViewNewbornRecords(Session.getCurrentUser());
        newbornQuickButton.setVisible(canViewNewborns);
        newbornQuickButton.setManaged(canViewNewborns);
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
        if (!newbornMode) {
            quickFilter = "Custom";
        }
        loadPatients();
    }

    private void renderFilterChips() {
        filterChipsBox.getChildren().clear();
        addChip("Quick: " + quickFilter);
        addChipIfPresent("Search", searchField.getText());
        addChipIfSelected("Section", value(sectionFilter));
        if (!newbornMode) {
            addChipIfSelected("Room", value(roomFilter));
            addChipIfSelected("Status", value(statusFilter));
            addChipIfSelected("Priority", value(priorityFilter));
        }
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
        boolean hasSelection = patientTable != null && patientTable.getSelectionModel().getSelectedItem() != null;
        setDisabledIfPresent(editSelectedPatientButton, !hasSelection);
        setDisabledIfPresent(dischargePatientButton, !hasSelection);
        setDisabledIfPresent(enterVitalsButton, !hasSelection);
        setDisabledIfPresent(viewPatientFileButton, !hasSelection);
    }

    private void setDisabledIfPresent(Button button, boolean disabled) {
        if (button != null) {
            button.setDisable(disabled);
        }
    }

    private void showVitalAlertPopupIfNeeded(SqlitePatientDao.PatientListRow selected, VitalsWriteService.VitalsWriteResult result) {
        if (result.getStatus() != VitalThresholdService.VitalStatus.CRITICAL
                && result.getStatus() != VitalThresholdService.VitalStatus.WARNING) {
            return;
        }

        Alert.AlertType alertType = result.getStatus() == VitalThresholdService.VitalStatus.CRITICAL
                ? Alert.AlertType.ERROR
                : Alert.AlertType.WARNING;
        Alert alert = new Alert(alertType);
        alert.setTitle(result.getStatus() + " Vital Alert");
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

    private void showPatientTable() {
        patientTable.setVisible(true);
        patientTable.setManaged(true);
        newbornTable.setVisible(false);
        newbornTable.setManaged(false);
        updateSelectionActions();
    }

    private void showNewbornTable() {
        patientTable.setVisible(false);
        patientTable.setManaged(false);
        newbornTable.setVisible(true);
        newbornTable.setManaged(true);
        setDisabledIfPresent(editSelectedPatientButton, true);
        setDisabledIfPresent(dischargePatientButton, true);
        setDisabledIfPresent(enterVitalsButton, true);
        setDisabledIfPresent(viewPatientFileButton, true);
    }

    private void updateBoardTitle() {
        if (newbornMode) {
            boardTitleLabel.setText("Newborn Records");
            boardSubtitleLabel.setText("Double-click a newborn row to open the existing newborn record view.");
        } else if ("Deceased Patients".equals(quickFilter)) {
            boardTitleLabel.setText("Deceased Patients");
            boardSubtitleLabel.setText("Deceased patients are filtered from SQLite patient records. Certificate drill-down remains available internally.");
        } else {
            boardTitleLabel.setText("Patients");
            boardSubtitleLabel.setText("Double-click a row to open the full patient file.");
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
