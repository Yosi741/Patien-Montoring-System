package ui.javafx.controllers;

import javafx.fxml.FXML;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.util.Duration;
import services.NotificationCenterService;
import ui.javafx.AppShell;
import ui.javafx.FxController;
import ui.javafx.SessionContext;
import ui.javafx.helpers.PermissionHelper;
import users.Session;

public class AppLayoutController implements FxController {

    private AppShell appShell;

    @FXML
    private BorderPane contentPane;

    @FXML
    private Label userLabel;

    @FXML
    private Label roleLabel;

    @FXML
    private Label topUserLabel;

    @FXML
    private Label roleBadgeLabel;

    @FXML
    private Button dashboardButton;

    @FXML
    private Button patientsButton;

    @FXML
    private Button messagesButton;

    @FXML
    private Button notificationsButton;

    @FXML
    private Button alertsButton;

    @FXML
    private Button settingsButton;

    @FXML
    private Button auditLogsButton;

    @FXML
    private Button userDirectoryButton;

    @FXML
    private Button staffActivityButton;

    @FXML
    private Button medicationOverviewButton;

    @FXML
    private Button medicalDevicesButton;

    @FXML
    private Button schedulingButton;

    @FXML
    private Button workQueueButton;

    @FXML
    private Button medicalFilesButton;

    @FXML
    private Button roomOccupancyButton;

    @FXML
    private Button deceasedRecordsButton;

    @FXML
    private Button aiRecommendationsButton;

    @FXML
    private Button backupExportButton;

    @FXML
    private Button topNotificationButton;

    @FXML
    private Label unreadCountLabel;

    private Timeline notificationRefreshTimeline;

    @Override
    public void setAppShell(AppShell appShell) {
        this.appShell = appShell;
        userLabel.setText(SessionContext.username());
        roleLabel.setText(SessionContext.role() + " | " + SessionContext.section());
        topUserLabel.setText(SessionContext.username() + " | " + SessionContext.section());
        roleBadgeLabel.setText(roleGroup(SessionContext.role()));
        roleBadgeLabel.getStyleClass().removeAll("role-admin", "role-doctor", "role-nurse", "role-staff", "role-unknown");
        roleBadgeLabel.getStyleClass().add(roleStyle(SessionContext.role()));
        boolean admin = isAdmin();
        boolean clinical = isClinical();
        boolean loggedIn = Session.getCurrentUser() != null;
        messagesButton.setVisible(PermissionHelper.canViewMessages(Session.getCurrentUser()));
        messagesButton.setManaged(PermissionHelper.canViewMessages(Session.getCurrentUser()));
        notificationsButton.setVisible(PermissionHelper.canViewNotifications(Session.getCurrentUser()));
        notificationsButton.setManaged(PermissionHelper.canViewNotifications(Session.getCurrentUser()));
        topNotificationButton.setVisible(loggedIn);
        topNotificationButton.setManaged(loggedIn);
        unreadCountLabel.setVisible(loggedIn);
        unreadCountLabel.setManaged(loggedIn);
        staffActivityButton.setVisible(admin || clinical);
        staffActivityButton.setManaged(admin || clinical);
        medicationOverviewButton.setVisible(admin || clinical);
        medicationOverviewButton.setManaged(admin || clinical);
        medicalDevicesButton.setVisible(admin || clinical);
        medicalDevicesButton.setManaged(admin || clinical);
        schedulingButton.setVisible(admin || clinical);
        schedulingButton.setManaged(admin || clinical);
        workQueueButton.setVisible(admin || clinical);
        workQueueButton.setManaged(admin || clinical);
        medicalFilesButton.setVisible(admin || clinical);
        medicalFilesButton.setManaged(admin || clinical);
        roomOccupancyButton.setVisible(admin || clinical);
        roomOccupancyButton.setManaged(admin || clinical);
        deceasedRecordsButton.setVisible(PermissionHelper.canViewDeceasedRecords(Session.getCurrentUser()));
        deceasedRecordsButton.setManaged(PermissionHelper.canViewDeceasedRecords(Session.getCurrentUser()));
        aiRecommendationsButton.setVisible(admin || clinical);
        aiRecommendationsButton.setManaged(admin || clinical);
        boolean backupTools = PermissionHelper.canViewBackupTools(Session.getCurrentUser());
        backupExportButton.setVisible(backupTools);
        backupExportButton.setManaged(backupTools);
        auditLogsButton.setVisible(admin);
        auditLogsButton.setManaged(admin);
        userDirectoryButton.setVisible(admin);
        userDirectoryButton.setManaged(admin);
        refreshNotificationCount();
        startNotificationRefresh();
    }

    public void setContent(Parent content) {
        contentPane.setCenter(content);
    }

    @FXML
    private void showDashboard() {
        appShell.showDashboard(Session.getCurrentUser());
    }

    @FXML
    private void showPatients() {
        appShell.showPatientList();
    }

    @FXML
    private void showMessages() {
        appShell.showMessaging();
    }

    @FXML
    private void showNotifications() {
        appShell.showNotificationCenter();
    }

    @FXML
    private void showAlerts() {
        appShell.showAlertCenter();
    }

    @FXML
    private void showSettings() {
        appShell.showUserProfile();
    }

    @FXML
    private void showAuditLogs() {
        appShell.showAuditLogs();
    }

    @FXML
    private void showUserDirectory() {
        appShell.showUserDirectory();
    }

    @FXML
    private void showStaffActivity() {
        appShell.showStaffActivity();
    }

    @FXML
    private void showMedicationOverview() {
        appShell.showMedicationOverview();
    }

    @FXML
    private void showMedicalDevices() {
        appShell.showMedicalDevices();
    }

    @FXML
    private void showScheduling() {
        appShell.showScheduling();
    }

    @FXML
    private void showWorkQueue() {
        appShell.showNurseWorkQueue();
    }

    @FXML
    private void showMedicalFiles() {
        appShell.showMedicalFiles();
    }

    @FXML
    private void showRoomBedOccupancy() {
        appShell.showRoomBedOccupancy();
    }

    @FXML
    private void showDeceasedRecords() {
        appShell.showDeceasedRecords();
    }

    @FXML
    private void showAiRecommendations() {
        appShell.showAiRecommendations();
    }

    @FXML
    private void showBackupExport() {
        appShell.showBackupExport();
    }

    @FXML
    private void toggleTheme() {
        appShell.toggleTheme();
    }

    @FXML
    private void logout() {
        appShell.logout();
    }

    public void refreshNotificationCount() {
        int count = new NotificationCenterService().unreadCount(Session.getCurrentUser());
        unreadCountLabel.setText(String.valueOf(count));
        topNotificationButton.setText("Notifications");
    }

    private void startNotificationRefresh() {
        if (notificationRefreshTimeline != null) {
            notificationRefreshTimeline.stop();
        }
        notificationRefreshTimeline = new Timeline(new KeyFrame(Duration.seconds(25), event -> refreshNotificationCount()));
        notificationRefreshTimeline.setCycleCount(Timeline.INDEFINITE);
        notificationRefreshTimeline.play();
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

    private String roleStyle(String role) {
        switch (roleGroup(role)) {
            case "ADMIN":
                return "role-admin";
            case "DOCTOR":
                return "role-doctor";
            case "NURSE":
                return "role-nurse";
            case "STAFF":
                return "role-staff";
            default:
                return "role-unknown";
        }
    }

    private boolean isAdmin() {
        return roleGroup(SessionContext.role()).equals("ADMIN");
    }

    private boolean isClinical() {
        String role = roleGroup(SessionContext.role());
        return role.equals("DOCTOR") || role.equals("NURSE");
    }
}
