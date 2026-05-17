package ui.javafx.controllers;

import dao.SqliteDeviceDao;
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
import services.DeviceWriteService;
import ui.javafx.AppShell;
import ui.javafx.FxController;
import ui.javafx.helpers.DialogHelper;
import ui.javafx.helpers.NotificationHelper;
import ui.javafx.helpers.PermissionHelper;
import users.Session;

public class MedicalDevicesController implements FxController {

    private final SqliteDeviceDao deviceDao = new SqliteDeviceDao();
    private final DeviceWriteService deviceWriteService = new DeviceWriteService();
    private final ObservableList<SqliteDeviceDao.DeviceRecord> devices = FXCollections.observableArrayList();
    private AppShell appShell;
    private String patientIdFilter = "";

    @FXML private VBox accessDeniedPane;
    @FXML private VBox contentPane;
    @FXML private Label patientFilterChip;
    @FXML private Button clearPatientFilterButton;
    @FXML private TextField searchField;
    @FXML private ComboBox<String> typeFilter;
    @FXML private ComboBox<String> statusFilter;
    @FXML private Button registerButton;
    @FXML private Button editButton;
    @FXML private Button deactivateButton;
    @FXML private Button assignButton;
    @FXML private Button unassignButton;
    @FXML private TableView<SqliteDeviceDao.DeviceRecord> deviceTable;
    @FXML private TableColumn<SqliteDeviceDao.DeviceRecord, String> deviceIdColumn;
    @FXML private TableColumn<SqliteDeviceDao.DeviceRecord, String> nameColumn;
    @FXML private TableColumn<SqliteDeviceDao.DeviceRecord, String> typeColumn;
    @FXML private TableColumn<SqliteDeviceDao.DeviceRecord, String> serialColumn;
    @FXML private TableColumn<SqliteDeviceDao.DeviceRecord, String> statusColumn;
    @FXML private TableColumn<SqliteDeviceDao.DeviceRecord, String> patientIdColumn;
    @FXML private TableColumn<SqliteDeviceDao.DeviceRecord, String> patientNameColumn;
    @FXML private TableColumn<SqliteDeviceDao.DeviceRecord, String> updatedAtColumn;
    @FXML private Label statusLabel;

    @Override
    public void setAppShell(AppShell appShell) {
        this.appShell = appShell;
        configureAccess();
        configureFilters();
        configureTable();
        configureButtons();
        if (isAuthorized()) {
            loadDevices();
        }
    }

    public void openForPatient(String patientId) {
        patientIdFilter = patientId == null ? "" : patientId;
        updatePatientFilterChip();
        if (isAuthorized()) {
            loadDevices();
        }
    }

    @FXML
    private void loadDevices() {
        if (!isAuthorized()) {
            NotificationHelper.showError(statusLabel, "Access denied.");
            return;
        }
        try {
            devices.setAll(deviceDao.findDevices(searchField.getText(), typeFilter.getValue(), statusFilter.getValue(), patientIdFilter));
            deviceTable.setItems(devices);
            NotificationHelper.showInfo(statusLabel, "Devices loaded from SQLite: " + devices.size());
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, "Could not load devices: " + e.getMessage());
        }
    }

    @FXML
    private void registerDevice() {
        if (!PermissionHelper.canManageDevice(Session.getCurrentUser())) {
            NotificationHelper.showError(statusLabel, "Access denied. Admin or Doctor role is required.");
            return;
        }
        try {
            if (DeviceFormController.showCreateDialog(deviceTable.getScene().getWindow(), Session.getCurrentUser())) {
                loadDevices();
                NotificationHelper.showSuccess(statusLabel, "Device registered in SQLite.");
            }
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
        }
    }

    @FXML
    private void editSelectedDevice() {
        if (!PermissionHelper.canManageDevice(Session.getCurrentUser())) {
            NotificationHelper.showError(statusLabel, "Access denied. Admin or Doctor role is required.");
            return;
        }
        SqliteDeviceDao.DeviceRecord selected = selectedDevice();
        if (selected == null) {
            return;
        }
        try {
            if (DeviceFormController.showEditDialog(deviceTable.getScene().getWindow(), Session.getCurrentUser(), selected)) {
                loadDevices();
                NotificationHelper.showSuccess(statusLabel, "Device updated in SQLite.");
            }
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
        }
    }

    @FXML
    private void deactivateSelectedDevice() {
        if (!PermissionHelper.canManageDevice(Session.getCurrentUser())) {
            NotificationHelper.showError(statusLabel, "Access denied. Admin or Doctor role is required.");
            return;
        }
        SqliteDeviceDao.DeviceRecord selected = selectedDevice();
        if (selected == null) {
            return;
        }
        if (!DialogHelper.confirm("Deactivate Device", "Deactivate " + selected.getDeviceId() + "? This also clears patient assignment in SQLite.")) {
            return;
        }
        try {
            deviceWriteService.deactivateDevice(Session.getCurrentUser(), selected.getDeviceId());
            loadDevices();
            NotificationHelper.showSuccess(statusLabel, "Device deactivated in SQLite.");
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
        }
    }

    @FXML
    private void assignSelectedDevice() {
        if (!PermissionHelper.canAssignDevice(Session.getCurrentUser())) {
            NotificationHelper.showError(statusLabel, "Access denied. Admin, Doctor, or Nurse role is required.");
            return;
        }
        SqliteDeviceDao.DeviceRecord selected = selectedDevice();
        if (selected == null) {
            return;
        }
        try {
            if (DeviceAssignmentController.showDialog(deviceTable.getScene().getWindow(), Session.getCurrentUser(), selected, patientIdFilter)) {
                loadDevices();
                NotificationHelper.showSuccess(statusLabel, "Device assigned in SQLite.");
            }
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
        }
    }

    @FXML
    private void unassignSelectedDevice() {
        if (!PermissionHelper.canAssignDevice(Session.getCurrentUser())) {
            NotificationHelper.showError(statusLabel, "Access denied. Admin, Doctor, or Nurse role is required.");
            return;
        }
        SqliteDeviceDao.DeviceRecord selected = selectedDevice();
        if (selected == null) {
            return;
        }
        try {
            deviceWriteService.unassignDevice(Session.getCurrentUser(), selected.getDeviceId());
            loadDevices();
            NotificationHelper.showSuccess(statusLabel, "Device unassigned in SQLite.");
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
        }
    }

    @FXML
    private void clearFilters() {
        searchField.clear();
        typeFilter.getSelectionModel().select("All");
        statusFilter.getSelectionModel().select("All");
        loadDevices();
    }

    @FXML
    private void clearPatientFilter() {
        patientIdFilter = "";
        updatePatientFilterChip();
        loadDevices();
    }

    @FXML
    private void showDashboard() {
        appShell.showDashboard(Session.getCurrentUser());
    }

    private void configureAccess() {
        boolean authorized = isAuthorized();
        accessDeniedPane.setVisible(!authorized);
        accessDeniedPane.setManaged(!authorized);
        contentPane.setVisible(authorized);
        contentPane.setManaged(authorized);
    }

    private void configureFilters() {
        typeFilter.setItems(FXCollections.observableArrayList("All", "Watch", "Blood Pressure Monitor", "Oximeter", "Thermometer", "Glucose Meter", "Other"));
        statusFilter.setItems(FXCollections.observableArrayList("All", "AVAILABLE", "ASSIGNED", "MAINTENANCE", "INACTIVE"));
        typeFilter.getSelectionModel().select("All");
        statusFilter.getSelectionModel().select("All");
        searchField.textProperty().addListener((observable, oldValue, newValue) -> loadDevices());
        typeFilter.valueProperty().addListener((observable, oldValue, newValue) -> loadDevices());
        statusFilter.valueProperty().addListener((observable, oldValue, newValue) -> loadDevices());
    }

    private void configureTable() {
        deviceIdColumn.setCellValueFactory(new PropertyValueFactory<>("deviceId"));
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        typeColumn.setCellValueFactory(new PropertyValueFactory<>("type"));
        serialColumn.setCellValueFactory(new PropertyValueFactory<>("serial"));
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        patientIdColumn.setCellValueFactory(new PropertyValueFactory<>("patientId"));
        patientNameColumn.setCellValueFactory(new PropertyValueFactory<>("patientName"));
        updatedAtColumn.setCellValueFactory(new PropertyValueFactory<>("updatedAt"));
    }

    private void configureButtons() {
        boolean canManage = PermissionHelper.canManageDevice(Session.getCurrentUser());
        boolean canAssign = PermissionHelper.canAssignDevice(Session.getCurrentUser());
        registerButton.setVisible(canManage);
        registerButton.setManaged(canManage);
        editButton.setVisible(canManage);
        editButton.setManaged(canManage);
        deactivateButton.setVisible(canManage);
        deactivateButton.setManaged(canManage);
        assignButton.setVisible(canAssign);
        assignButton.setManaged(canAssign);
        unassignButton.setVisible(canAssign);
        unassignButton.setManaged(canAssign);
        updatePatientFilterChip();
    }

    private void updatePatientFilterChip() {
        boolean patientFiltered = patientIdFilter != null && !patientIdFilter.isBlank();
        patientFilterChip.setVisible(patientFiltered);
        patientFilterChip.setManaged(patientFiltered);
        clearPatientFilterButton.setVisible(patientFiltered);
        clearPatientFilterButton.setManaged(patientFiltered);
        patientFilterChip.setText(patientFiltered ? "Patient ID = " + patientIdFilter : "");
    }

    private SqliteDeviceDao.DeviceRecord selectedDevice() {
        SqliteDeviceDao.DeviceRecord selected = deviceTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            NotificationHelper.showError(statusLabel, "Select a device first.");
            return null;
        }
        return selected;
    }

    private boolean isAuthorized() {
        return PermissionHelper.canAssignDevice(Session.getCurrentUser()) || PermissionHelper.canManageDevice(Session.getCurrentUser());
    }
}
