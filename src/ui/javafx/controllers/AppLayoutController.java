package ui.javafx.controllers;

import javafx.fxml.FXML;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.util.Duration;
import ui.javafx.services.NotificationCenterService;
import ui.javafx.AppFeatures;
import ui.javafx.AppShell;
import ui.javafx.FxController;
import ui.javafx.SessionContext;
import ui.javafx.helpers.PermissionHelper;
import users.Session;

import java.net.URL;

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
    private Button settingsButton;

    @FXML
    private Button auditLogsButton;

    @FXML
    private Button userDirectoryButton;

    @FXML
    private Button medicationOverviewButton;

    @FXML
    private Button schedulingButton;

    @FXML
    private Button workQueueButton;

    @FXML
    private Button roomOccupancyButton;

    @FXML
    private Button certificateRegistryButton;

    @FXML
    private Button topNotificationButton;

    @FXML
    private Label unreadCountLabel;

    @FXML
    private MenuButton profileMenuButton;

    @FXML
    private ImageView sidebarLogoImage;

    private Timeline notificationRefreshTimeline;

    @FXML
    private void initialize() {
        loadSidebarLogoImage();
    }

    @Override
    public void setAppShell(AppShell appShell) {
        this.appShell = appShell;
        userLabel.setText(SessionContext.username());
        roleLabel.setText(SessionContext.role() + " | " + SessionContext.section());
        topUserLabel.setText(SessionContext.username() + " | " + SessionContext.section());
        profileMenuButton.setText(avatarText(SessionContext.username()) + " " + SessionContext.username());
        roleBadgeLabel.setText(roleGroup(SessionContext.role()));
        roleBadgeLabel.getStyleClass().removeAll("role-admin", "role-doctor", "role-nurse", "role-staff", "role-unknown");
        roleBadgeLabel.getStyleClass().add(roleStyle(SessionContext.role()));
        boolean admin = isAdmin();
        boolean clinical = isClinical();
        boolean loggedIn = Session.getCurrentUser() != null;
        setButtonVisible(messagesButton, false);
        topNotificationButton.setVisible(loggedIn);
        topNotificationButton.setManaged(loggedIn);
        unreadCountLabel.setVisible(loggedIn);
        unreadCountLabel.setManaged(loggedIn);
        medicationOverviewButton.setVisible(admin || clinical);
        medicationOverviewButton.setManaged(admin || clinical);
        schedulingButton.setVisible(admin || clinical);
        schedulingButton.setManaged(admin || clinical);
        workQueueButton.setVisible(admin || clinical);
        workQueueButton.setManaged(admin || clinical);
        roomOccupancyButton.setVisible(admin || clinical);
        roomOccupancyButton.setManaged(admin || clinical);
        certificateRegistryButton.setVisible(PermissionHelper.canViewCertificateRegistry(Session.getCurrentUser()));
        certificateRegistryButton.setManaged(PermissionHelper.canViewCertificateRegistry(Session.getCurrentUser()));
        auditLogsButton.setVisible(admin);
        auditLogsButton.setManaged(admin);
        userDirectoryButton.setVisible(admin);
        userDirectoryButton.setManaged(admin);
        refreshNotificationCount();
        startNotificationRefresh();
    }

    public void setContent(Parent content) {
        appShell.applyThemeTo(content);
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
        if (!AppFeatures.messagesEnabled()) {
            appShell.showUserProfile();
            return;
        }
        appShell.showMessaging();
    }

    @FXML
    private void showNotifications() {
        if (!AppFeatures.notificationsEnabled()) {
            appShell.showDashboard(Session.getCurrentUser());
            return;
        }
        appShell.showNotificationCenter();
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
    private void showMedicationOverview() {
        appShell.showMedicationOverview();
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
    private void showRoomBedOccupancy() {
        appShell.showRoomBedOccupancy();
    }

    @FXML
    private void showCertificateRegistry() {
        appShell.showCertificateRegistry();
    }

    @FXML
    private void logout() {
        appShell.logout();
    }

    @Override
    public void dispose() {
        if (notificationRefreshTimeline != null) {
            notificationRefreshTimeline.stop();
            notificationRefreshTimeline = null;
        }
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
        notificationRefreshTimeline = new Timeline(new KeyFrame(Duration.seconds(10), event -> refreshNotificationCount()));
        notificationRefreshTimeline.setCycleCount(Timeline.INDEFINITE);
        notificationRefreshTimeline.play();
    }

    private String avatarText(String username) {
        if (username == null || username.isBlank()) {
            return "User";
        }
        return username.trim().substring(0, 1).toUpperCase();
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

    private void setButtonVisible(Button button, boolean visible) {
        button.setVisible(visible);
        button.setManaged(visible);
    }

    private void loadSidebarLogoImage() {
        if (sidebarLogoImage == null) {
            return;
        }
        String[] candidates = {
                "/ui/javafx/Photos/spms-logo.png",
                "/ui/javafx/Photos/spms-logo.png",
                "/ui/javafx/Photos/spms-logo.png"
        };
        for (String candidate : candidates) {
            URL logoUrl = getClass().getResource(candidate);
            if (logoUrl != null) {
                sidebarLogoImage.setImage(new Image(logoUrl.toExternalForm()));
                sidebarLogoImage.setVisible(true);
                sidebarLogoImage.setManaged(true);
                return;
            }
        }
        sidebarLogoImage.setVisible(false);
        sidebarLogoImage.setManaged(false);
    }
}
