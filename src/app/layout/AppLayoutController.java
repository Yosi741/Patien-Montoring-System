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
import pages.patient.patient_registration.PatientRegistrationController;
import pages.user.User;
import pages.user.UserRole;
import pages.user.Session;

import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Controls AppLayout.fxml, including sidebar navigation, top-bar actions, badges, search, and theme switching.
 */
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

    /**
     * Initializes the FXML controls after the JavaFX view has been loaded.
     */
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

    /**
     * Supplies the application shell used by this controller for navigation.
     */
    @Override
    public void setAppShell(AppShell appShell) {
        this.appShell = appShell;
        String username = SessionContext.username();
        String role = displayRole(SessionContext.role());
        if (userLabel != null) {
            userLabel.setText(username);
        }
        if (roleLabel != null) {
            roleLabel.setText(role);
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
            profileChipRoleLabel.setText(role);
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

    /**
     * Updates content for the current object.
     */
    public void setContent(Parent content) {
        if (appShell != null) {
            appShell.applyThemeTo(content);
        }
        contentPane.setCenter(content);
    }

    /**
     * Updates current route for the current object.
     */
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

    /**
     * Displays context notice to the user.
     */
    public void showContextNotice(String message) {
        if (contextNoticeLabel == null) {
            return;
        }
        boolean hasText = message != null && !message.isBlank();
        contextNoticeLabel.setText(hasText ? message : "");
        contextNoticeLabel.setVisible(hasText);
        contextNoticeLabel.setManaged(hasText);
    }

    /**
     * Clears context notice and restores its default state.
     */
    public void clearContextNotice() {
        showContextNotice(null);
    }

    /**
     * Refreshes theme state from the current application state.
     */
    public void refreshThemeState() {
        if (themeToggleButton != null && appShell != null) {
            themeToggleButton.setText(appShell.isDarkTheme() ? "\u2600" : "\uD83C\uDF19");
        }
    }

    /**
     * Reapplies content theme after the active view changes.
     */
    public void reapplyContentTheme() {
        if (appShell != null && contentPane != null && contentPane.getCenter() instanceof Parent) {
            appShell.applyThemeTo((Parent) contentPane.getCenter());
        }
    }

    /**
     * Refreshes notification count from the current application state.
     */
    public void refreshNotificationCount() {
        refreshCounts();
    }

    /**
     * Handles the show dashboard UI action.
     */
    @FXML
    private void showDashboard() {
        appShell.showDashboard(Session.getCurrentUser());
    }

    /**
     * Handles the show appointments UI action.
     */
    @FXML
    private void showAppointments() {
        appShell.showScheduling();
    }

    /**
     * Handles the show patients UI action.
     */
    @FXML
    private void showPatients() {
        appShell.showPatientList();
    }

    /**
     * Handles the show medical files UI action.
     */
    @FXML
    private void showMedicalFiles() {
        appShell.showMedicalFiles();
    }

    /**
     * Handles the show billing UI action.
     */
    @FXML
    private void showBilling() {
        appShell.showBilling();
    }

    /**
     * Handles the show notifications UI action.
     */
    @FXML
    private void showNotifications() {
        appShell.showNotificationCenter();
    }

    /**
     * Handles the show messages UI action.
     */
    @FXML
    private void showMessages() {
        appShell.showMessaging();
    }

    /**
     * Handles the show user directory UI action.
     */
    @FXML
    private void showUserDirectory() {
        appShell.showUserDirectory();
    }

    /**
     * Handles the show settings UI action.
     */
    @FXML
    private void showSettings() {
        appShell.showUserProfile();
    }

    /**
     * Handles the toggle theme UI action.
     */
    @FXML
    private void toggleTheme() {
        appShell.toggleTheme();
    }

    /**
     * Handles the quick check in UI action.
     */
    @FXML
    private void handleQuickCheckIn() {
        appShell.showPatientsWithNotice("Use the patient workflow to check in a clinic visit.");
    }

    /**
     * Handles the quick add patient UI action.
     */
    @FXML
    private void handleQuickAddPatient() {
        if (!PermissionHelper.canCreatePatient(Session.getCurrentUser())) {
            appShell.showPatientsWithNotice("Only Admin, Doctor, Nurse, or Secretary users can add a patient from Quick Actions.");
            return;
        }
        try {
            boolean saved = PatientRegistrationController.showCreateDialog(contentPane.getScene().getWindow(), Session.getCurrentUser());
            if (saved) {
                appShell.showPatientsWithNotice("Patient record saved.");
            }
        } catch (Exception e) {
            showContextNotice(e.getMessage());
        }
    }

    /**
     * Handles the quick enter vitals UI action.
     */
    @FXML
    private void handleQuickEnterVitals() {
        appShell.showPatientsWithNotice("Select a patient first to enter vitals.");
    }

    /**
     * Handles the quick new appointment UI action.
     */
    @FXML
    private void handleQuickNewAppointment() {
        appShell.showScheduling();
        showContextNotice("Create the new appointment from the appointments page.");
    }

    /**
     * Handles the quick add payment UI action.
     */
    @FXML
    private void handleQuickAddPayment() {
        if (!PermissionHelper.canViewBilling(Session.getCurrentUser())) {
            showContextNotice("Billing is available only for Admin and Secretary users.");
            return;
        }
        appShell.showBilling();
    }

    /**
     * Handles the quick send message UI action.
     */
    @FXML
    private void handleQuickSendMessage() {
        appShell.showMessaging();
    }

    /**
     * Handles the logout UI action.
     */
    @FXML
    private void logout() {
        appShell.logout();
    }

    /**
     * Handles the global search UI action.
     */
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

    /**
     * Releases timers or other page resources when the current view is replaced.
     */
    @Override
    public void dispose() {
        if (refreshTimeline != null) {
            refreshTimeline.stop();
            refreshTimeline = null;
        }
    }

    /**
     * Configures permissions.
     */
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

    /**
     * Refreshes counts from the current application state.
     */
    private void refreshCounts() {
        if (dateTimeLabel != null) {
            dateTimeLabel.setText(LocalDateTime.now().format(TOP_BAR_TIME));
        }
        try {
            int unreadAlerts = notificationDao.unreadCountForUser(
                    SessionContext.username(),
                    PermissionHelper.roleGroup(SessionContext.role()),
                    currentUserSection());
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
                    currentUserSection());
            if (messagesIconButton != null) {
                messagesIconButton.setText("\uD83D\uDCAC " + unreadMessages);
            }
        } catch (Exception e) {
            if (messagesIconButton != null) {
                messagesIconButton.setText("\uD83D\uDCAC 0");
            }
        }
    }

    /**
     * Starts refresh timeline.
     */
    private void startRefreshTimeline() {
        if (refreshTimeline != null) {
            refreshTimeline.stop();
        }
        refreshTimeline = new Timeline(new KeyFrame(Duration.seconds(20), event -> refreshCounts()));
        refreshTimeline.setCycleCount(Timeline.INDEFINITE);
        refreshTimeline.play();
    }

    /**
     * Registers nav buttons for later application use.
     */
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

    /**
     * Loads sidebar logo image for the application workflow.
     */
    private void loadSidebarLogoImage() {
        if (sidebarLogoImage == null) {
            return;
        }
        String[] candidates = {
                "/photo/app-logo.png",
                "/photo/ICON-Logo.png"
        };
        for (String candidate : candidates) {
            URL logoUrl = getClass().getResource(candidate);
            if (logoUrl != null) {
                sidebarLogoImage.setImage(new Image(logoUrl.toExternalForm()));
                return;
            }
        }
    }

    /**
     * Returns formatted display text for avatar text.
     */
    private String avatarText(String username) {
        if (username == null || username.isBlank()) {
            return "S";
        }
        return username.substring(0, 1).toUpperCase();
    }

    /**
     * Updates button visible for the current object.
     */
    private void setButtonVisible(Button button, boolean visible) {
        if (button != null) {
            button.setVisible(visible);
            button.setManaged(visible);
        }
    }

    /**
     * Formats role for display in the JavaFX UI.
     */
    private String displayRole(String role) {
        try {
            return UserRole.fromValue(role).displayName();
        } catch (Exception e) {
            return role == null || role.isBlank() ? "Unknown" : role;
        }
    }

    /**
     * Returns the current user's section for section-targeted messages and notifications.
     */
    private String currentUserSection() {
        User currentUser = Session.getCurrentUser();
        if (currentUser == null || currentUser.getSection() == null || currentUser.getSection().isBlank()) {
            return "All";
        }
        return currentUser.getSection().trim();
    }

}

