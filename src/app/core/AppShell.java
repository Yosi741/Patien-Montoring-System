package app.core;

import app.contracts.AppController;
import app.database.DatabaseManager;
import app.database.SchemaInitializer;
import app.helpers.PermissionHelper;
import app.layout.AppLayoutController;
import app.navigation.AppNavigator;
import app.placeholder.ComingSoonController;
import javafx.application.Application;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import pages.patient.medical_files.MedicalFilesController;
import pages.patient.patient_board.PatientListController;
import pages.patient.patient_detail.PatientDetailController;
import pages.scheduling.schedule_overview.SchedulingController;
import pages.user.User;
import pages.user.Session;

/**
 * Owns the ClinicPulse JavaFX stage, theme, session transitions, and page routing.
 * It loads the login screen and hosts authenticated pages inside the main layout.
 */
public class AppShell extends Application {

    public static final String PRESENTATION_THEME = "/app/styles/dark-theme.css";
    public static final String LIGHT_THEME = "/app/styles/light-theme.css";
    private static final String APP_NAME = "ClinicPulse";
    private static String activeThemePath = PRESENTATION_THEME;

    private Stage primaryStage;
    private AppNavigator navigator;
    private AppLayoutController layoutController;
    private AppController currentContentController;
    private String databaseStatus = "Local clinic database not initialized";

    /**
     * Launches app and displays its initial JavaFX stage.
     */
    public static void launchApp(String[] args) {
        launch(args);
    }

    /**
     * Starts start.
     */
    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        this.navigator = new AppNavigator(this);
        app.helpers.FxFileOpenHelper.registerHostServices(getHostServices());
        initializeDatabase();

        primaryStage.setTitle("ClinicPulse");
        showLogin();
        primaryStage.show();
    }

    /**
     * Displays login to the user.
     */
    public void showLogin() {
        setView("/pages/login/LoginView.fxml", "ClinicPulse - Login");
        configureLoginWindow();
    }

    /**
     * Displays dashboard to the user.
     */
    public void showDashboard(User user) {
        showDashboard(user, SessionContext.authSource());
    }

    /**
     * Displays dashboard to the user.
     */
    public void showDashboard(User user, String authSource) {
        Session.setCurrentUser(user);
        if (user != null && (SessionContext.getCurrent() == null || !user.getUsername().equals(SessionContext.username()))) {
            SessionContext.start(user, authSource);
        }
        setShellContent("/pages/dashboard/DashboardView.fxml", windowTitle("Dashboard"));
        updateShellContext("dashboard", "Dashboard", "Home / Dashboard");
        primaryStage.setMaximized(true);
    }

    /**
     * Displays patient list to the user.
     */
    public void showPatientList() {
        showPatientList("Patients", "Home / Patients", "patients", null);
    }

    /**
     * Displays patient detail to the user.
     */
    public void showPatientDetail(String patientId) {
        if (!PermissionHelper.canViewPatientFile(Session.getCurrentUser())) {
            showPlaceholder(
                    "Access Denied",
                    "You do not have permission to view the full patient file.",
                    "Only Admin and Doctor users can open full patient files."
            );
            return;
        }
        ensureShell(windowTitle("Patient File"));
        AppNavigator.LoadedView detail = navigator.loadView("/pages/patient/patient_detail/PatientDetailView.fxml");
        if (detail.getController() instanceof PatientDetailController) {
            ((PatientDetailController) detail.getController()).loadPatient(patientId);
        }
        setShellLoadedContent(detail);
        updateShellContext("patients", "Patient File", "Home / Patients / Patient File");
        primaryStage.setTitle(windowTitle("Patient File"));
    }

    /**
     * Displays messaging to the user.
     */
    public void showMessaging() {
        setShellContent("/pages/messages/MessagingView.fxml", windowTitle("Messages"));
        updateShellContext("messages", "Messages", "Home / Messages");
    }

    /**
     * Displays notification center to the user.
     */
    public void showNotificationCenter() {
        if (!PermissionHelper.canViewNotifications(Session.getCurrentUser())) {
            showPlaceholder(
                    "Access Denied",
                    "Alerts are not available for your role.",
                    "Only Admin, Doctor, and Nurse users can view clinical alerts."
            );
            return;
        }
        setShellContent("/pages/notification/NotificationCenterView.fxml", windowTitle("Alerts"));
        updateShellContext("alerts", "Alerts", "Home / Alerts");
    }

    /**
     * Displays scheduling to the user.
     */
    public void showScheduling() {
        setShellContent("/pages/scheduling/schedule_overview/SchedulingView.fxml", windowTitle("Appointments"));
        updateShellContext("appointments", "Appointments", "Home / Appointments");
    }

    /**
     * Displays scheduling for patient to the user.
     */
    public void showSchedulingForPatient(String patientId) {
        ensureShell(windowTitle("Patient Appointments"));
        AppNavigator.LoadedView scheduling = navigator.loadView("/pages/scheduling/schedule_overview/SchedulingView.fxml");
        if (scheduling.getController() instanceof SchedulingController) {
            ((SchedulingController) scheduling.getController()).openForPatient(patientId);
        }
        setShellLoadedContent(scheduling);
        updateShellContext("appointments", "Patient Appointments", "Home / Appointments / Patient");
        primaryStage.setTitle(windowTitle("Patient Appointments"));
    }

    /**
     * Displays medical files to the user.
     */
    public void showMedicalFiles() {
        if (!PermissionHelper.canViewMedicalFiles(Session.getCurrentUser())) {
            showPlaceholder(
                    "Access Denied",
                    "Medical records are not available for your role.",
                    "Only Admin, Doctor, Nurse, and Secretary users can open the medical records page."
            );
            return;
        }
        setShellContent("/pages/patient/medical_files/MedicalFilesView.fxml", windowTitle("Medical Files"));
        updateShellContext("medical-files", "Medical Files", "Home / Medical Files");
    }

    /**
     * Displays medical files for patient to the user.
     */
    public void showMedicalFilesForPatient(String patientId) {
        if (!PermissionHelper.canViewMedicalFiles(Session.getCurrentUser())) {
            showPlaceholder(
                    "Access Denied",
                    "Medical records are not available for your role.",
                    "Only Admin, Doctor, Nurse, and Secretary users can open patient medical records."
            );
            return;
        }
        ensureShell(windowTitle("Patient Medical Files"));
        AppNavigator.LoadedView files = navigator.loadView("/pages/patient/medical_files/MedicalFilesView.fxml");
        if (files.getController() instanceof MedicalFilesController) {
            ((MedicalFilesController) files.getController()).openForPatient(patientId);
        }
        setShellLoadedContent(files);
        updateShellContext("medical-files", "Patient Medical Files", "Home / Medical Files / Patient");
        primaryStage.setTitle(windowTitle("Patient Medical Files"));
    }

    /**
     * Displays user profile to the user.
     */
    public void showUserProfile() {
        setShellContent("/pages/user/profile_settings/UserProfileView.fxml", windowTitle("Profile / Settings"));
        updateShellContext("profile", "Profile / Settings", "Home / Profile / Settings");
    }

    /**
     * Displays user directory to the user.
     */
    public void showUserDirectory() {
        if (!PermissionHelper.canViewUserDirectory(Session.getCurrentUser())) {
            showPlaceholder(
                    "Access Denied",
                    "Staff management is not available for your role.",
                    "Only Admin users can manage staff accounts."
            );
            return;
        }
        setShellContent("/pages/user/user_directory/UserDirectoryManagementView.fxml", windowTitle("Staff Management"));
        updateShellContext("staff", "Staff Management", "Home / Staff Management");
    }

    /**
     * Displays billing to the user.
     */
    public void showBilling() {
        if (!PermissionHelper.canViewBilling(Session.getCurrentUser())) {
            showPlaceholder(
                    "Access Denied",
                    "Billing is not available for your role.",
                    "Only Admin and Secretary users can open the billing page."
            );
            return;
        }
        setShellContent("/pages/billing/billing_overview/BillingView.fxml", windowTitle("Billing / Payments"));
        updateShellContext("billing", "Billing / Payments", "Home / Billing");
    }

    /**
     * Displays patients with notice to the user.
     */
    public void showPatientsWithNotice(String notice) {
        showPatientList("Patients", "Home / Patients", "patients", notice);
    }

    /**
     * Displays patients with search to the user.
     */
    public void showPatientsWithSearch(String query) {
        ensureShell(windowTitle("Patients"));
        AppNavigator.LoadedView patientList = navigator.loadView("/pages/patient/patient_board/PatientListView.fxml");
        if (patientList.getController() instanceof PatientListController) {
            ((PatientListController) patientList.getController()).applySearchQuery(query);
        }
        setShellLoadedContent(patientList);
        updateShellContext("patients", "Patients", "Home / Patients");
        primaryStage.setTitle(windowTitle("Patients"));
    }

    /**
     * Displays placeholder to the user.
     */
    public void showPlaceholder(String title, String subtitle, String body) {
        ensureShell(windowTitle(title));
        AppNavigator.LoadedView placeholder = navigator.loadView("/app/placeholder/ComingSoonView.fxml");
        if (placeholder.getController() instanceof ComingSoonController) {
            ((ComingSoonController) placeholder.getController()).setContent(title, subtitle, body);
        }
        setShellLoadedContent(placeholder);
        primaryStage.setTitle(windowTitle(title));
    }

    /**
     * Refreshes notification count from the current application state.
     */
    public void refreshNotificationCount() {
        if (layoutController != null) {
            layoutController.refreshNotificationCount();
        }
    }

    /**
     * Ends the current session and returns the application to the login view.
     */
    public void logout() {
        disposeCurrentContent();
        disposeLayout();
        SessionContext.clear();
        Session.setCurrentUser(null);
        showLogin();
    }

    public String getDatabaseStatus() {
        return databaseStatus;
    }

    /**
     * Applies theme to to the current control or record.
     */
    public void applyThemeTo(Parent parent) {
        if (parent == null) {
            return;
        }
        parent.getStylesheets().clear();
        parent.getStylesheets().add(AppNavigator.resolve(activeThemePath).toExternalForm());
    }

    public boolean isDarkTheme() {
        return PRESENTATION_THEME.equals(activeThemePath);
    }

    /**
     * Toggles theme in the active view.
     */
    public void toggleTheme() {
        activeThemePath = isDarkTheme() ? LIGHT_THEME : PRESENTATION_THEME;
        if (primaryStage.getScene() != null) {
            applyTheme(primaryStage.getScene());
            applyThemeTo(primaryStage.getScene().getRoot());
        }
        if (layoutController != null) {
            layoutController.reapplyContentTheme();
            layoutController.refreshThemeState();
        }
    }

    public static String getActiveThemePath() {
        return activeThemePath;
    }

    /**
     * Initializes database required by the application.
     */
    private void initializeDatabase() {
        try {
            SchemaInitializer.initialize();
            databaseStatus = DatabaseManager.testConnection()
                    ? "Local clinic database ready: " + DatabaseManager.getDatabasePath()
                    : "Local clinic database schema initialized, connection check failed";
        } catch (Exception e) {
            databaseStatus = "Local clinic database initialization failed: " + e.getMessage();
            System.out.println(databaseStatus);
        }
    }

    /**
     * Updates view for the current object.
     */
    private void setView(String fxmlPath, String title) {
        disposeCurrentContent();
        disposeLayout();
        Parent root = navigator.load(fxmlPath);
        Scene scene = new Scene(root, 1180, 760);
        applyTheme(scene);
        primaryStage.setTitle(title);
        primaryStage.setMinWidth(980);
        primaryStage.setMinHeight(640);
        primaryStage.setScene(scene);
    }

    /**
     * Configures login window.
     */
    private void configureLoginWindow() {
        primaryStage.setMaximized(false);
        primaryStage.setMinWidth(1100);
        primaryStage.setMinHeight(720);
        primaryStage.setWidth(1180);
        primaryStage.setHeight(760);
        primaryStage.centerOnScreen();
    }

    /**
     * Updates shell content for the current object.
     */
    private void setShellContent(String fxmlPath, String title) {
        ensureShell(title);
        AppNavigator.LoadedView loaded = navigator.loadView(fxmlPath);
        setShellLoadedContent(loaded);
        primaryStage.setTitle(title);
    }

    /**
     * Ensures shell exists before continuing.
     */
    private void ensureShell(String title) {
        if (layoutController != null && primaryStage.getScene() != null) {
            return;
        }

        AppNavigator.LoadedView layout = navigator.loadView("/app/layout/AppLayout.fxml");
        layoutController = (AppLayoutController) layout.getController();
        Scene scene = new Scene(layout.getParent(), 1240, 780);
        applyTheme(scene);
        primaryStage.setTitle(title);
        primaryStage.setMinWidth(1040);
        primaryStage.setMinHeight(680);
        primaryStage.setScene(scene);
    }

    /**
     * Updates shell loaded content for the current object.
     */
    private void setShellLoadedContent(AppNavigator.LoadedView loaded) {
        disposeCurrentContent();
        if (loaded.getController() instanceof AppController) {
            currentContentController = (AppController) loaded.getController();
        } else {
            currentContentController = null;
        }
        if (layoutController != null) {
            layoutController.clearContextNotice();
        }
        layoutController.setContent(loaded.getParent());
    }

    /**
     * Disposes current content and releases its active resources.
     */
    private void disposeCurrentContent() {
        if (currentContentController != null) {
            try {
                currentContentController.dispose();
            } catch (Exception e) {
                System.out.println("JavaFX content dispose skipped: " + e.getMessage());
            }
            currentContentController = null;
        }
    }

    /**
     * Disposes layout and releases its active resources.
     */
    private void disposeLayout() {
        if (layoutController != null) {
            try {
                layoutController.dispose();
            } catch (Exception e) {
                System.out.println("JavaFX shell dispose skipped: " + e.getMessage());
            }
            layoutController = null;
        }
    }

    /**
     * Applies theme to the current control or record.
     */
    private void applyTheme(Scene scene) {
        if (scene == null) {
            return;
        }
        scene.getStylesheets().clear();
        scene.getStylesheets().add(AppNavigator.resolve(activeThemePath).toExternalForm());
    }

    /**
     * Displays patient list to the user.
     */
    private void showPatientList(String pageTitle, String breadcrumb, String routeKey, String notice) {
        setShellContent("/pages/patient/patient_board/PatientListView.fxml", windowTitle(pageTitle));
        updateShellContext(routeKey, pageTitle, breadcrumb);
        if (notice != null && layoutController != null) {
            layoutController.showContextNotice(notice);
        }
    }

    /**
     * Returns the title shown on the ClinicPulse application window.
     */
    private String windowTitle(String pageTitle) {
        if (pageTitle == null || pageTitle.isBlank()) {
            return APP_NAME;
        }
        return APP_NAME + " - " + pageTitle;
    }

    /**
     * Updates shell context.
     */
    private void updateShellContext(String routeKey, String pageTitle, String breadcrumb) {
        if (layoutController != null) {
            layoutController.setCurrentRoute(routeKey, pageTitle, breadcrumb);
        }
    }
}
