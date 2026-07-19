package app.layout;

import app.contracts.AppController;

import app.core.AppShell;
import app.core.SessionContext;
import app.helpers.PermissionHelper;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.util.Duration;
import pages.messages.SqliteMessageDao;
import pages.notification.SqliteNotificationDao;
import pages.patient.patient_form.PatientFormController;
import pages.user.UserRole;
import pages.user.Session;

import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

public class AppLayoutController implements AppController {

    private static final DateTimeFormatter TOP_BAR_TIME = DateTimeFormatter.ofPattern("hh:mm a | EEE, MMM d");

    private final SqliteNotificationDao notificationDao = new SqliteNotificationDao();
    private final SqliteMessageDao messageDao = new SqliteMessageDao();

    private AppShell appShell;
    private Timeline refreshTimeline;
    private final Map<String, Button> navButtons = new LinkedHashMap<>();

    @FXML private BorderPane contentPane;
    @FXML private Label userLabel;
    @FXML private Label roleLabel;
    @FXML private Label sidebarAvatarLabel;
    @FXML private Label profileChipAvatarLabel;
    @FXML private Label profileChipNameLabel;
    @FXML private Label profileChipRoleLabel;
    @FXML private Label breadcrumbLabel;
    @FXML private Label currentPageLabel;
    @FXML private Label dateTimeLabel;
    @FXML private Label contextNoticeLabel;
    @FXML private TextField globalSearchField;
    @FXML private Button dashboardButton;
    @FXML private Button appointmentsButton;
    @FXML private Button patientsButton;
    @FXML private Button medicalFilesButton;
    @FXML private Button billingButton;
    @FXML private Button staffManagementButton;
    @FXML private Button alertsIconButton;
    @FXML private Button messagesIconButton;
    @FXML private Button themeToggleButton;
    @FXML private MenuButton profileMenuButton;
    @FXML private ImageView sidebarLogoImage;

    @FXML
    private void initialize() {
        loadSidebarLogoImage();
        registerNavButtons();
        if (globalSearchField != null) {
            globalSearchField.setEditable(true);
            globalSearchField.setDisable(false);
            globalSearchField.setMouseTransparent(false);
            globalSearchField.setFocusTraversable(true);
            globalSearchField.setOnAction(event -> handleGlobalSearch());
        }
    }

    @Override
    public void setAppShell(AppShell appShell) {
        this.appShell = appShell;
        String username = SessionContext.username();
        String role = displayRole(SessionContext.role());
        String section = SessionContext.section();
        if (userLabel != null) {
            userLabel.setText(username);
        }
        if (roleLabel != null) {
            roleLabel.setText(role + " | " + section);
        }
        if (sidebarAvatarLabel != null) {
            sidebarAvatarLabel.setText(avatarText(username));
        }
        if (profileChipAvatarLabel != null) {
            profileChipAvatarLabel.setText(avatarText(username));
        }
        if (profileChipNameLabel != null) {
            profileChipNameLabel.setText(username);
        }
        if (profileChipRoleLabel != null) {
            profileChipRoleLabel.setText(role + " | " + section);
        }
        if (profileMenuButton != null) {
            profileMenuButton.setText("");
        }
        setCurrentRoute("dashboard", "Dashboard", "Home / Dashboard");
        refreshThemeState();
        refreshCounts();
        startRefreshTimeline();
        configurePermissions();
    }

    public void setContent(Parent content) {
        if (appShell != null) {
            appShell.applyThemeTo(content);
        }
        contentPane.setCenter(content);
    }

    public void setCurrentRoute(String routeKey, String pageTitle, String breadcrumb) {
        if (currentPageLabel != null) {
            currentPageLabel.setText(pageTitle);
        }
        if (breadcrumbLabel != null) {
            breadcrumbLabel.setText(breadcrumb);
        }
        navButtons.forEach((key, button) -> {
            if (button == null) {
                return;
            }
            button.getStyleClass().remove("sidebar-nav-button-active");
            if (!button.getStyleClass().contains("sidebar-nav-button")) {
                button.getStyleClass().add("sidebar-nav-button");
            }
        });
        Button activeButton = navButtons.get(routeKey);
        if (activeButton != null) {
            activeButton.getStyleClass().add("sidebar-nav-button-active");
        }
    }

    public void showContextNotice(String message) {
        if (contextNoticeLabel == null) {
            return;
        }
        boolean hasText = message != null && !message.isBlank();
        contextNoticeLabel.setText(hasText ? message : "");
        contextNoticeLabel.setVisible(hasText);
        contextNoticeLabel.setManaged(hasText);
    }

    public void clearContextNotice() {
        showContextNotice(null);
    }

    public void refreshThemeState() {
        if (themeToggleButton != null && appShell != null) {
            themeToggleButton.setText(appShell.isDarkTheme() ? "\u2600" : "\uD83C\uDF19");
        }
    }

    public void reapplyContentTheme() {
        if (appShell != null && contentPane != null && contentPane.getCenter() instanceof Parent) {
            appShell.applyThemeTo((Parent) contentPane.getCenter());
        }
    }

    public void refreshNotificationCount() {
        refreshCounts();
    }

    @FXML
    private void showDashboard() {
        appShell.showDashboard(Session.getCurrentUser());
    }

    @FXML
    private void showAppointments() {
        appShell.showScheduling();
    }

    @FXML
    private void showPatients() {
        appShell.showPatientList();
    }

    @FXML
    private void showMedicalFiles() {
        appShell.showMedicalFiles();
    }

    @FXML
    private void showBilling() {
        appShell.showBilling();
    }

    @FXML
    private void showNotifications() {
        appShell.showNotificationCenter();
    }

    @FXML
    private void showMessages() {
        appShell.showMessaging();
    }

    @FXML
    private void showUserDirectory() {
        appShell.showUserDirectory();
    }

    @FXML
    private void showSettings() {
        appShell.showUserProfile();
    }

    @FXML
    private void toggleTheme() {
        appShell.toggleTheme();
    }

    @FXML
    private void handleQuickCheckIn() {
        appShell.showPatientsWithNotice("Use the patient workflow to check in a clinic visit.");
    }

    @FXML
    private void handleQuickAddPatient() {
        if (!PermissionHelper.canCreatePatient(Session.getCurrentUser())) {
            appShell.showPatientsWithNotice("Only Admin, Doctor, Nurse, or Secretary users can add a patient from Quick Actions.");
            return;
        }
        try {
            boolean saved = PatientFormController.showCreateDialog(contentPane.getScene().getWindow(), Session.getCurrentUser());
            if (saved) {
                appShell.showPatientsWithNotice("Patient record saved.");
            }
        } catch (Exception e) {
            showContextNotice(e.getMessage());
        }
    }

    @FXML
    private void handleQuickEnterVitals() {
        appShell.showPatientsWithNotice("Select a patient first to enter vitals.");
    }

    @FXML
    private void handleQuickNewAppointment() {
        appShell.showScheduling();
        showContextNotice("Create the new appointment from the appointments page.");
    }

    @FXML
    private void handleQuickAddPayment() {
        if (!PermissionHelper.canViewBilling(Session.getCurrentUser())) {
            showContextNotice("Billing is available only for Admin and Secretary users.");
            return;
        }
        appShell.showBilling();
    }

    @FXML
    private void handleQuickSendMessage() {
        appShell.showMessaging();
    }

    @FXML
    private void logout() {
        appShell.logout();
    }

    @FXML
    private void handleGlobalSearch() {
        if (globalSearchField == null || appShell == null) {
            return;
        }
        String query = globalSearchField.getText() == null ? "" : globalSearchField.getText().trim();
        if (query.isBlank()) {
            return;
        }
        appShell.showPatientsWithSearch(query);
    }

    @Override
    public void dispose() {
        if (refreshTimeline != null) {
            refreshTimeline.stop();
            refreshTimeline = null;
        }
    }

    private void configurePermissions() {
        boolean canSeeMedicalFiles = PermissionHelper.canViewMedicalFiles(Session.getCurrentUser());
        boolean canSeeBilling = PermissionHelper.canViewBilling(Session.getCurrentUser());
        boolean canSeeAlerts = PermissionHelper.canViewNotifications(Session.getCurrentUser());
        boolean canSeeStaff = PermissionHelper.canViewUserDirectory(Session.getCurrentUser());
        setButtonVisible(medicalFilesButton, canSeeMedicalFiles);
        setButtonVisible(billingButton, canSeeBilling);
        setButtonVisible(alertsIconButton, canSeeAlerts);
        setButtonVisible(staffManagementButton, canSeeStaff);
        setButtonVisible(messagesIconButton, true);
    }

    private void refreshCounts() {
        if (dateTimeLabel != null) {
            dateTimeLabel.setText(LocalDateTime.now().format(TOP_BAR_TIME));
        }
        try {
            int unreadAlerts = notificationDao.unreadCountForUser(
                    SessionContext.username(),
                    PermissionHelper.roleGroup(SessionContext.role()),
                    SessionContext.section());
            if (alertsIconButton != null) {
                alertsIconButton.setText("\uD83D\uDD14 " + unreadAlerts);
            }
        } catch (Exception e) {
            if (alertsIconButton != null) {
                alertsIconButton.setText("\uD83D\uDD14 0");
            }
        }
        try {
            int unreadMessages = messageDao.unreadInboxCount(
                    SessionContext.username(),
                    PermissionHelper.roleGroup(SessionContext.role()),
                    SessionContext.section());
            if (messagesIconButton != null) {
                messagesIconButton.setText("\uD83D\uDCAC " + unreadMessages);
            }
        } catch (Exception e) {
            if (messagesIconButton != null) {
                messagesIconButton.setText("\uD83D\uDCAC 0");
            }
        }
    }

    private void startRefreshTimeline() {
        if (refreshTimeline != null) {
            refreshTimeline.stop();
        }
        refreshTimeline = new Timeline(new KeyFrame(Duration.seconds(20), event -> refreshCounts()));
        refreshTimeline.setCycleCount(Timeline.INDEFINITE);
        refreshTimeline.play();
    }

    private void registerNavButtons() {
        navButtons.put("dashboard", dashboardButton);
        navButtons.put("appointments", appointmentsButton);
        navButtons.put("patients", patientsButton);
        navButtons.put("medical-files", medicalFilesButton);
        navButtons.put("billing", billingButton);
        navButtons.put("messages", null);
        navButtons.put("staff", staffManagementButton);
        navButtons.put("profile", null);
    }

    private void loadSidebarLogoImage() {
        if (sidebarLogoImage == null) {
            return;
        }
        String[] candidates = {
                "/photo/app-logo.png",
                "/photo/spms-icon.png",
                "/photo/spms-logo.png",
                "/photo/SPMS-Logo.jpeg",
                "/photo/1.png"
        };
        for (String candidate : candidates) {
            URL logoUrl = getClass().getResource(candidate);
            if (logoUrl != null) {
                sidebarLogoImage.setImage(new Image(logoUrl.toExternalForm()));
                return;
            }
        }
    }

    private String avatarText(String username) {
        if (username == null || username.isBlank()) {
            return "S";
        }
        return username.substring(0, 1).toUpperCase();
    }

    private void setButtonVisible(Button button, boolean visible) {
        if (button != null) {
            button.setVisible(visible);
            button.setManaged(visible);
        }
    }

    private String displayRole(String role) {
        try {
            return UserRole.fromValue(role).displayName();
        } catch (Exception e) {
            return role == null || role.isBlank() ? "Unknown" : role;
        }
    }

}
