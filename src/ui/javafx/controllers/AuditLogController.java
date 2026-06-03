package ui.javafx.controllers;

import dao.SqliteAuditLogDao;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import ui.javafx.AppShell;
import ui.javafx.FxController;
import ui.javafx.SessionContext;
import ui.javafx.helpers.SelectionHelper;

public class AuditLogController implements FxController {

    private final SqliteAuditLogDao auditLogDao = new SqliteAuditLogDao();
    private final ObservableList<SqliteAuditLogDao.AuditLogRow> rows = FXCollections.observableArrayList();
    private AppShell appShell;

    @FXML private VBox accessDeniedPane;
    @FXML private VBox auditContentPane;
    @FXML private TextField searchField;
    @FXML private ComboBox<String> dateRangeFilter;
    @FXML private ComboBox<String> actionTypeFilter;
    @FXML private TableView<SqliteAuditLogDao.AuditLogRow> auditTable;
    @FXML private TableColumn<SqliteAuditLogDao.AuditLogRow, Long> idColumn;
    @FXML private TableColumn<SqliteAuditLogDao.AuditLogRow, String> usernameColumn;
    @FXML private TableColumn<SqliteAuditLogDao.AuditLogRow, String> actionColumn;
    @FXML private TableColumn<SqliteAuditLogDao.AuditLogRow, String> createdAtColumn;
    @FXML private Label statusLabel;

    @Override
    public void setAppShell(AppShell appShell) {
        this.appShell = appShell;
        configureAccess();
        configureFilters();
        configureTable();
        if (isAdmin()) {
            loadLogs();
        }
    }

    @FXML
    private void loadLogs() {
        if (!isAdmin()) {
            statusLabel.setText("Access denied.");
            return;
        }
        try {
            SelectionHelper.safeClearSelection(auditTable);
            rows.setAll(auditLogDao.findRows(searchField.getText(), dateRangeFilter.getValue(), actionTypeFilter.getValue()));
            auditTable.setItems(rows);
            statusLabel.setText(rows.isEmpty() ? "No audit logs match the selected filters." : "Audit logs loaded: " + rows.size());
        } catch (Exception e) {
            statusLabel.setText("Could not load audit logs: " + e.getMessage());
        }
    }

    @FXML
    private void clearFilters() {
        searchField.clear();
        dateRangeFilter.getSelectionModel().select("All");
        actionTypeFilter.getSelectionModel().select("All");
        loadLogs();
    }

    @FXML
    private void showDashboard() {
        appShell.showDashboard(users.Session.getCurrentUser());
    }

    private void configureAccess() {
        boolean admin = isAdmin();
        accessDeniedPane.setVisible(!admin);
        accessDeniedPane.setManaged(!admin);
        auditContentPane.setVisible(admin);
        auditContentPane.setManaged(admin);
    }

    private void configureFilters() {
        dateRangeFilter.setItems(FXCollections.observableArrayList("Today", "Last 7 days", "Last 30 days", "All"));
        actionTypeFilter.setItems(FXCollections.observableArrayList("All", "LOGIN", "LOGOUT", "ALERT", "PATIENT", "SYSTEM"));
        dateRangeFilter.getSelectionModel().select("All");
        actionTypeFilter.getSelectionModel().select("All");
        searchField.textProperty().addListener((observable, oldValue, newValue) -> loadLogs());
        dateRangeFilter.valueProperty().addListener((observable, oldValue, newValue) -> loadLogs());
        actionTypeFilter.valueProperty().addListener((observable, oldValue, newValue) -> loadLogs());
    }

    private void configureTable() {
        idColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        usernameColumn.setCellValueFactory(new PropertyValueFactory<>("username"));
        actionColumn.setCellValueFactory(new PropertyValueFactory<>("action"));
        createdAtColumn.setCellValueFactory(new PropertyValueFactory<>("createdAt"));
    }

    private boolean isAdmin() {
        String role = SessionContext.role();
        return role != null && role.toUpperCase().contains("ADMIN");
    }
}
