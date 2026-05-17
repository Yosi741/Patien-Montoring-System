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
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import services.StaffActivityService;
import ui.javafx.AppShell;
import ui.javafx.FxController;
import ui.javafx.SessionContext;
import users.Session;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

public class StaffActivityController implements FxController {

    private final StaffActivityService activityService = new StaffActivityService();
    private final SqliteAuditLogDao auditLogDao = new SqliteAuditLogDao();
    private final ObservableList<StaffActivityService.ActivityRow> rows = FXCollections.observableArrayList();
    private AppShell appShell;

    @FXML private VBox accessDeniedPane;
    @FXML private VBox activityContentPane;
    @FXML private Label scopeLabel;
    @FXML private TextField searchField;
    @FXML private ComboBox<String> roleFilter;
    @FXML private ComboBox<String> actionTypeFilter;
    @FXML private ComboBox<String> dateRangeFilter;
    @FXML private Label loginsTodayLabel;
    @FXML private Label alertAckTodayLabel;
    @FXML private Label patientViewsTodayLabel;
    @FXML private Label syncTodayLabel;
    @FXML private Label recentActionsTodayLabel;
    @FXML private VBox activeAlertsBySectionBox;
    @FXML private TableView<StaffActivityService.ActivityRow> activityTable;
    @FXML private TableColumn<StaffActivityService.ActivityRow, String> timeColumn;
    @FXML private TableColumn<StaffActivityService.ActivityRow, String> usernameColumn;
    @FXML private TableColumn<StaffActivityService.ActivityRow, String> roleColumn;
    @FXML private TableColumn<StaffActivityService.ActivityRow, String> actionTypeColumn;
    @FXML private TableColumn<StaffActivityService.ActivityRow, String> descriptionColumn;
    @FXML private TableColumn<StaffActivityService.ActivityRow, String> patientIdColumn;
    @FXML private VBox handoverBox;
    @FXML private Label statusLabel;

    @Override
    public void setAppShell(AppShell appShell) {
        this.appShell = appShell;
        configureAccess();
        configureFilters();
        configureTable();
        if (isAuthorized()) {
            logAudit("JavaFX STAFF_ACTIVITY opened shift overview");
            loadActivity();
        }
    }

    @FXML
    private void loadActivity() {
        if (!isAuthorized()) {
            statusLabel.setText("Access denied.");
            return;
        }

        try {
            StaffActivityService.ActivityFilter filter = new StaffActivityService.ActivityFilter(
                    searchField.getText(),
                    roleFilter.getValue(),
                    actionTypeFilter.getValue(),
                    dateRangeFilter.getValue()
            );
            StaffActivityService.ViewerScope scope = new StaffActivityService.ViewerScope(
                    SessionContext.username(),
                    SessionContext.role(),
                    SessionContext.section(),
                    isAdmin()
            );
            StaffActivityService.StaffActivityOverview overview = activityService.loadOverview(filter, scope);
            renderOverview(overview);
            statusLabel.setText("Staff activity refreshed from SQLite at "
                    + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        } catch (Exception e) {
            statusLabel.setText("Could not load staff activity: " + e.getMessage());
        }
    }

    @FXML
    private void clearFilters() {
        searchField.clear();
        roleFilter.getSelectionModel().select("All");
        actionTypeFilter.getSelectionModel().select("All");
        dateRangeFilter.getSelectionModel().select("Today");
        loadActivity();
    }

    @FXML
    private void showDashboard() {
        appShell.showDashboard(Session.getCurrentUser());
    }

    private void configureAccess() {
        boolean authorized = isAuthorized();
        accessDeniedPane.setVisible(!authorized);
        accessDeniedPane.setManaged(!authorized);
        activityContentPane.setVisible(authorized);
        activityContentPane.setManaged(authorized);

        if (isAdmin()) {
            scopeLabel.setText("Admin view: all SQLite staff activity, alerts, and shift handover notes.");
        } else if (isClinical()) {
            scopeLabel.setText("Clinical limited view: your audit actions, section alerts, and handover notes for " + SessionContext.section() + ".");
        }
    }

    private void configureFilters() {
        roleFilter.setItems(FXCollections.observableArrayList("All", "ADMIN", "DOCTOR", "NURSE", "STAFF", "SYSTEM", "UNKNOWN"));
        actionTypeFilter.setItems(FXCollections.observableArrayList("All", "LOGIN", "LOGOUT", "ALERT", "PATIENT", "SYNC", "SYSTEM", "HANDOVER"));
        dateRangeFilter.setItems(FXCollections.observableArrayList("Today", "Last 7 days", "Last 30 days", "All"));
        roleFilter.getSelectionModel().select("All");
        actionTypeFilter.getSelectionModel().select("All");
        dateRangeFilter.getSelectionModel().select("Today");

        searchField.textProperty().addListener((observable, oldValue, newValue) -> loadActivity());
        roleFilter.valueProperty().addListener((observable, oldValue, newValue) -> loadActivity());
        actionTypeFilter.valueProperty().addListener((observable, oldValue, newValue) -> loadActivity());
        dateRangeFilter.valueProperty().addListener((observable, oldValue, newValue) -> loadActivity());
    }

    private void configureTable() {
        timeColumn.setCellValueFactory(new PropertyValueFactory<>("time"));
        usernameColumn.setCellValueFactory(new PropertyValueFactory<>("username"));
        roleColumn.setCellValueFactory(new PropertyValueFactory<>("role"));
        actionTypeColumn.setCellValueFactory(new PropertyValueFactory<>("actionType"));
        descriptionColumn.setCellValueFactory(new PropertyValueFactory<>("description"));
        patientIdColumn.setCellValueFactory(new PropertyValueFactory<>("relatedPatientId"));
    }

    private void renderOverview(StaffActivityService.StaffActivityOverview overview) {
        loginsTodayLabel.setText(String.valueOf(overview.getLoginsToday()));
        alertAckTodayLabel.setText(String.valueOf(overview.getAlertAcknowledgementsToday()));
        patientViewsTodayLabel.setText(String.valueOf(overview.getPatientDetailViewsToday()));
        syncTodayLabel.setText(String.valueOf(overview.getSyncOperationsToday()));
        recentActionsTodayLabel.setText(String.valueOf(overview.getRecentStaffActionsToday()));

        activeAlertsBySectionBox.getChildren().setAll();
        if (overview.getActiveAlertsBySection().isEmpty()) {
            activeAlertsBySectionBox.getChildren().add(emptyRow("No active SQLite alerts for this scope."));
        } else {
            for (Map.Entry<String, Integer> entry : overview.getActiveAlertsBySection().entrySet()) {
                activeAlertsBySectionBox.getChildren().add(sectionAlertRow(entry.getKey(), entry.getValue()));
            }
        }

        rows.setAll(overview.getActivities());
        activityTable.setItems(rows);

        handoverBox.getChildren().setAll();
        if (overview.getHandovers().isEmpty()) {
            handoverBox.getChildren().add(emptyRow("No shift handover notes are available in SQLite for this scope."));
        } else {
            for (StaffActivityService.HandoverRow handover : overview.getHandovers()) {
                handoverBox.getChildren().add(handoverRow(handover));
            }
        }
    }

    private HBox sectionAlertRow(String section, int count) {
        Label badge = new Label(section == null || section.isBlank() ? "Unassigned" : section);
        badge.getStyleClass().addAll("dashboard-badge", count > 0 ? "severity-critical" : "priority-normal");
        Label value = new Label(String.valueOf(count));
        value.getStyleClass().add("dashboard-row-count");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(12, badge, spacer, value);
        row.getStyleClass().add("dashboard-summary-row");
        return row;
    }

    private HBox handoverRow(StaffActivityService.HandoverRow handover) {
        Label type = new Label(handover.getToSection());
        type.getStyleClass().addAll("dashboard-badge", "timeline-type-handover");
        Label text = new Label("From: " + handover.getFromUser() + " | Patient: " + handover.getPatientId()
                + "\n" + handover.getNote());
        text.getStyleClass().add("dashboard-list-text");
        text.setWrapText(true);
        Label time = new Label(handover.getCreatedAt());
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
        label.setWrapText(true);
        HBox row = new HBox(label);
        row.getStyleClass().add("dashboard-list-row");
        return row;
    }

    private void logAudit(String action) {
        try {
            auditLogDao.log(SessionContext.username(), action);
        } catch (Exception e) {
            System.out.println("SQLite staff activity audit skipped: " + e.getMessage());
        }
    }

    private boolean isAuthorized() {
        return isAdmin() || isClinical();
    }

    private boolean isAdmin() {
        return "ADMIN".equals(roleGroup(SessionContext.role()));
    }

    private boolean isClinical() {
        String roleGroup = roleGroup(SessionContext.role());
        return "DOCTOR".equals(roleGroup) || "NURSE".equals(roleGroup);
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
