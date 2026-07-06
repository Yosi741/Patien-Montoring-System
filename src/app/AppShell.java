package app;

import javafx.application.Application;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import pages.audit_log.SqliteAuditLogDao;
import pages.clinical_timeline.ClinicalTimelineController;
import pages.messages.MessagingController;
import pages.notification.NotificationCenterController;
import pages.patient.medical_files.MedicalFilesController;
import pages.patient.patient_detail.PatientDetailController;
import pages.scheduling.schedule_overview.SchedulingController;
import pages.user.User;
import users.Session;

public class AppShell extends Application {

    public static final String PRESENTATION_THEME = "/app/styles/dark-theme.css";

    private Stage primaryStage;
    private AppNavigator navigator;
    private AppLayoutController layoutController;
    private FxController currentContentController;
    private String databaseStatus = "Local database not initialized";

    public static void launchApp(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        this.navigator = new AppNavigator(this);
        app.helpers.FxFileOpenHelper.registerHostServices(getHostServices());
        initializeDatabase();

        primaryStage.setTitle("Smart Patient Monitoring System");
        showLogin();
        primaryStage.show();
    }

    public void showLogin() {
        setView("/pages/login/LoginView.fxml", "Smart Patient Monitoring System - Login");
        configureLoginWindow();
    }

    public void showDashboard(User user) {
        showDashboard(user, SessionContext.authSource());
    }

    public void showDashboard(User user, String authSource) {
        Session.setCurrentUser(user);
        if (user != null && (SessionContext.getCurrent() == null || !user.getUsername().equals(SessionContext.username()))) {
            SessionContext.start(user, authSource);
        }
        setShellContent("/pages/dashboard/DashboardView.fxml", "Smart Patient Monitoring System - Dashboard");
        primaryStage.setMaximized(true);
    }

    public void showPatientList() {
        setShellContent("/pages/patient/patient_board/PatientListView.fxml", "Smart Patient Monitoring System - Patients");
    }

    public void showPatientDetail(String patientId) {
        ensureShell("Smart Patient Monitoring System - Patient Detail");
        AppNavigator.LoadedView detail = navigator.loadView("/pages/patient/patient_detail/PatientDetailView.fxml");
        if (detail.getController() instanceof PatientDetailController) {
            ((PatientDetailController) detail.getController()).loadPatient(patientId);
        }
        logAudit("JavaFX PATIENT opened detail for " + patientId);
        setShellLoadedContent(detail);
        primaryStage.setTitle("Smart Patient Monitoring System - Patient Detail");
    }

    public void showClinicalTimeline(String patientId) {
        ensureShell("Smart Patient Monitoring System - Clinical Timeline");
        AppNavigator.LoadedView timeline = navigator.loadView("/pages/clinical_timeline/ClinicalTimelineView.fxml");
        if (timeline.getController() instanceof ClinicalTimelineController) {
            ((ClinicalTimelineController) timeline.getController()).loadPatient(patientId);
        }
        setShellLoadedContent(timeline);
        primaryStage.setTitle("Smart Patient Monitoring System - Clinical Timeline");
    }

    public void showMessaging() {
        setShellContent("/pages/messages/MessagingView.fxml", "Smart Patient Monitoring System - Messaging");
    }

    public void showNotificationCenter() {
        setShellContent("/pages/notification/NotificationCenterView.fxml", "Smart Patient Monitoring System - Notification Center");
    }

    public void showCertificateSourceRecord(String sourceType, String sourceId) {
        showPlaceholder(
                "Certificate Module Removed",
                "Certificate workflows are not part of the clinic presentation build.",
                "Requested source: " + safe(sourceType) + " #" + safe(sourceId)
        );
    }

    public void showCertificateFromNotification(String sourceType, String sourceId) {
        showCertificateSourceRecord(sourceType, sourceId);
    }

    public void showMessageCertificateSourceRecord(String sourceType, String sourceId) {
        showCertificateSourceRecord(sourceType, sourceId);
    }

    public void showNewbornRecord(long recordId) {
        showPlaceholder(
                "Newborn Module Removed",
                "Newborn workflows were removed from this clinic version.",
                "Requested record: " + recordId
        );
    }

    public void showDeceasedRecord(long recordId) {
        showPlaceholder(
                "Deceased Module Removed",
                "Deceased workflows were removed from this clinic version.",
                "Requested record: " + recordId
        );
    }

    public void showNewbornRecordsForMother(String patientId) {
        showPlaceholder(
                "Newborn Module Removed",
                "Linked newborn records are not available in this clinic version.",
                "Requested patient: " + safe(patientId)
        );
    }

    public void showAlertCenter() {
        showNotificationCenter();
    }

    public void showAlertCenterForAlert(long alertId) {
        showNotificationCenter();
    }

    public void showAlertCenterForPatient(String patientId) {
        showNotificationCenter();
    }

    public void showScheduling() {
        setShellContent("/pages/scheduling/schedule_overview/SchedulingView.fxml", "Smart Patient Monitoring System - Appointments & Reminders");
    }

    public void showSchedulingForPatient(String patientId) {
        ensureShell("Smart Patient Monitoring System - Patient Scheduling");
        AppNavigator.LoadedView scheduling = navigator.loadView("/pages/scheduling/schedule_overview/SchedulingView.fxml");
        if (scheduling.getController() instanceof SchedulingController) {
            ((SchedulingController) scheduling.getController()).openForPatient(patientId);
        }
        setShellLoadedContent(scheduling);
        primaryStage.setTitle("Smart Patient Monitoring System - Patient Scheduling");
    }

    public void showNurseWorkQueue() {
        setShellContent("/pages/nurse_work/NurseWorkQueueView.fxml", "Smart Patient Monitoring System - Nurse Work Queue");
    }

    public void showMedicalFiles() {
        setShellContent("/pages/patient/medical_files/MedicalFilesView.fxml", "Smart Patient Monitoring System - Medical Files");
    }

    public void showMedicalFilesForPatient(String patientId) {
        ensureShell("Smart Patient Monitoring System - Patient Medical Files");
        AppNavigator.LoadedView files = navigator.loadView("/pages/patient/medical_files/MedicalFilesView.fxml");
        if (files.getController() instanceof MedicalFilesController) {
            ((MedicalFilesController) files.getController()).openForPatient(patientId);
        }
        setShellLoadedContent(files);
        primaryStage.setTitle("Smart Patient Monitoring System - Patient Medical Files");
    }

    public void showMedicalFileDetails(String patientId, String fileId) {
        ensureShell("Smart Patient Monitoring System - Medical File Details");
        AppNavigator.LoadedView files = navigator.loadView("/pages/patient/medical_files/MedicalFilesView.fxml");
        if (files.getController() instanceof MedicalFilesController) {
            ((MedicalFilesController) files.getController()).openForFile(patientId, fileId);
        }
        setShellLoadedContent(files);
        primaryStage.setTitle("Smart Patient Monitoring System - Medical File Details");
    }

    public void showUserProfile() {
        setShellContent("/pages/user/profile_settings/UserProfileView.fxml", "Smart Patient Monitoring System - Staff Profile");
    }

    public void showAuditLogs() {
        setShellContent("/pages/audit_log/AuditLogView.fxml", "Smart Patient Monitoring System - Audit Logs");
    }

    public void showUserDirectory() {
        setShellContent("/pages/user/user_directory/UserDirectoryManagementView.fxml", "Smart Patient Monitoring System - Staff/User Directory");
    }

    public void showPlaceholder(String title, String subtitle, String body) {
        ensureShell("Smart Patient Monitoring System - " + title);
        AppNavigator.LoadedView placeholder = navigator.loadView("/app/PlaceholderView.fxml");
        if (placeholder.getController() instanceof PlaceholderController) {
            ((PlaceholderController) placeholder.getController()).setContent(title, subtitle, body);
        }
        setShellLoadedContent(placeholder);
        primaryStage.setTitle("Smart Patient Monitoring System - " + title);
    }

    public void refreshNotificationCount() {
        if (layoutController != null) {
            layoutController.refreshNotificationCount();
        }
    }

    public void logout() {
        String username = SessionContext.username();
        try {
            new SqliteAuditLogDao().log(username, "JavaFX logout");
        } catch (Exception e) {
            System.out.println("SQLite logout audit skipped: " + e.getMessage());
        }
        disposeCurrentContent();
        disposeLayout();
        SessionContext.clear();
        Session.setCurrentUser(null);
        showLogin();
    }

    public String getDatabaseStatus() {
        return databaseStatus;
    }

    public void applyThemeTo(Parent parent) {
        // Stylesheets are attached at the Scene level.
    }

    private void initializeDatabase() {
        try {
            SchemaInitializer.initialize();
            databaseStatus = DatabaseManager.testConnection()
                    ? "Local database ready: " + DatabaseManager.getDatabasePath()
                    : "Local database schema initialized, connection check failed";
        } catch (Exception e) {
            databaseStatus = "Local database initialization failed: " + e.getMessage();
            System.out.println(databaseStatus);
        }
    }

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

    private void configureLoginWindow() {
        primaryStage.setMaximized(false);
        primaryStage.setMinWidth(1100);
        primaryStage.setMinHeight(720);
        primaryStage.setWidth(1180);
        primaryStage.setHeight(760);
        primaryStage.centerOnScreen();
    }

    private void setShellContent(String fxmlPath, String title) {
        ensureShell(title);
        AppNavigator.LoadedView loaded = navigator.loadView(fxmlPath);
        setShellLoadedContent(loaded);
        primaryStage.setTitle(title);
    }

    private void ensureShell(String title) {
        if (layoutController != null && primaryStage.getScene() != null) {
            return;
        }

        AppNavigator.LoadedView layout = navigator.loadView("/app/AppLayout.fxml");
        layoutController = (AppLayoutController) layout.getController();
        Scene scene = new Scene(layout.getParent(), 1240, 780);
        applyTheme(scene);
        primaryStage.setTitle(title);
        primaryStage.setMinWidth(1040);
        primaryStage.setMinHeight(680);
        primaryStage.setScene(scene);
    }

    private void setShellLoadedContent(AppNavigator.LoadedView loaded) {
        disposeCurrentContent();
        if (loaded.getController() instanceof FxController) {
            currentContentController = (FxController) loaded.getController();
        } else {
            currentContentController = null;
        }
        layoutController.setContent(loaded.getParent());
    }

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

    private void applyTheme(Scene scene) {
        if (scene == null) {
            return;
        }
        scene.getStylesheets().clear();
        scene.getStylesheets().add(AppNavigator.resolve(PRESENTATION_THEME).toExternalForm());
    }

    private void logAudit(String action) {
        try {
            new SqliteAuditLogDao().log(SessionContext.username(), action);
        } catch (Exception e) {
            System.out.println("SQLite JavaFX audit skipped: " + e.getMessage());
        }
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
