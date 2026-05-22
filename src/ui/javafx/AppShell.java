package ui.javafx;

import database.DatabaseManager;
import database.SchemaInitializer;
import database.SqliteMigrationService;
import dao.SqliteAuditLogDao;
import dao.SqliteDeceasedRecordDao;
import dao.SqliteNewbornRecordDao;
import javafx.application.Application;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import ui.javafx.controllers.AlertCenterController;
import ui.javafx.controllers.AppLayoutController;
import ui.javafx.controllers.ClinicalTimelineController;
import ui.javafx.controllers.DeceasedRecordsController;
import ui.javafx.controllers.MedicationOverviewController;
import ui.javafx.controllers.MedicalDevicesController;
import ui.javafx.controllers.MedicalFilesController;
import ui.javafx.controllers.NewbornRecordsController;
import ui.javafx.controllers.NurseWorkQueueController;
import ui.javafx.controllers.PatientDetailController;
import ui.javafx.controllers.PlaceholderController;
import ui.javafx.controllers.SchedulingController;
import services.DeceasedPatientService;
import services.NewbornService;
import ui.javafx.helpers.AuditAction;
import ui.javafx.helpers.AuditWriteHelper;
import ui.javafx.helpers.DialogHelper;
import ui.javafx.helpers.PermissionHelper;
import users.Session;
import users.User;

import java.util.prefs.Preferences;

public class AppShell extends Application {

    private static final String LIGHT_THEME = "/ui/javafx/styles/light-theme.css";
    private static final String DARK_THEME = "/ui/javafx/styles/dark-theme.css";
    private static final String THEME_PREF_KEY = "darkMode";
    private static final Preferences PREFS = Preferences.userNodeForPackage(AppShell.class);

    private Stage primaryStage;
    private AppNavigator navigator;
    private AppLayoutController layoutController;
    private boolean darkMode;
    private String databaseStatus = "SQLite not initialized";
    private String migrationStatus = "Migration not checked";

    public static void launchApp(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        this.navigator = new AppNavigator(this);
        this.darkMode = PREFS.getBoolean(THEME_PREF_KEY, false);
        initializeDatabase();

        primaryStage.setTitle("Smart Patient Monitoring System - JavaFX Preview");
        showLogin();
        primaryStage.show();
    }

    public void showLogin() {
        setView("/ui/javafx/views/LoginView.fxml", "Smart Patient Monitoring System - Login");
    }

    public void showDashboard(User user) {
        showDashboard(user, SessionContext.authSource());
    }

    public void showDashboard(User user, String authSource) {
        Session.setCurrentUser(user);
        if (user != null && (SessionContext.getCurrent() == null || !user.getUsername().equals(SessionContext.username()))) {
            SessionContext.start(user, authSource);
        }
        setShellContent("/ui/javafx/views/DashboardView.fxml", "Smart Patient Monitoring System - Dashboard");
    }

    public void showPatientList() {
        setShellContent("/ui/javafx/views/PatientListView.fxml", "Smart Patient Monitoring System - Patients");
    }

    public void showMessaging() {
        setShellContent("/ui/javafx/views/MessagingView.fxml", "Smart Patient Monitoring System - Messaging");
    }

    public void showNotificationCenter() {
        setShellContent("/ui/javafx/views/NotificationCenterView.fxml", "Smart Patient Monitoring System - Notification Center");
    }

    public void showAlertCenter() {
        setShellContent("/ui/javafx/views/AlertCenterView.fxml", "Smart Patient Monitoring System - Alert Center");
    }

    public void showAlertCenterForAlert(long alertId) {
        ensureShell("Smart Patient Monitoring System - Alert Center");
        AppNavigator.LoadedView alertCenter = navigator.loadView("/ui/javafx/views/AlertCenterView.fxml");
        if (alertCenter.getController() instanceof AlertCenterController) {
            ((AlertCenterController) alertCenter.getController()).openWithAlert(alertId);
        }
        layoutController.setContent(alertCenter.getParent());
        primaryStage.setTitle("Smart Patient Monitoring System - Alert Center");
    }

    public void showAlertCenterForPatient(String patientId) {
        ensureShell("Smart Patient Monitoring System - Patient Alerts");
        AppNavigator.LoadedView alertCenter = navigator.loadView("/ui/javafx/views/AlertCenterView.fxml");
        if (alertCenter.getController() instanceof AlertCenterController) {
            ((AlertCenterController) alertCenter.getController()).openForPatient(patientId);
        }
        layoutController.setContent(alertCenter.getParent());
        primaryStage.setTitle("Smart Patient Monitoring System - Patient Alerts");
    }

    public void showPatientDetail(String patientId) {
        ensureShell("Smart Patient Monitoring System - Patient Detail");
        AppNavigator.LoadedView detail = navigator.loadView("/ui/javafx/views/PatientDetailView.fxml");
        if (detail.getController() instanceof PatientDetailController) {
            ((PatientDetailController) detail.getController()).loadPatient(patientId);
        }
        logAudit("JavaFX PATIENT opened detail for " + patientId);
        layoutController.setContent(detail.getParent());
        primaryStage.setTitle("Smart Patient Monitoring System - Patient Detail");
    }

    public void showClinicalTimeline(String patientId) {
        ensureShell("Smart Patient Monitoring System - Clinical Timeline");
        AppNavigator.LoadedView timeline = navigator.loadView("/ui/javafx/views/ClinicalTimelineView.fxml");
        if (timeline.getController() instanceof ClinicalTimelineController) {
            ((ClinicalTimelineController) timeline.getController()).loadPatient(patientId);
        }
        layoutController.setContent(timeline.getParent());
        primaryStage.setTitle("Smart Patient Monitoring System - Clinical Timeline");
    }

    public void showPlaceholder(String title, String subtitle, String body) {
        ensureShell("Smart Patient Monitoring System - " + title);
        AppNavigator.LoadedView placeholder = navigator.loadView("/ui/javafx/views/PlaceholderView.fxml");
        if (placeholder.getController() instanceof PlaceholderController) {
            ((PlaceholderController) placeholder.getController()).setContent(title, subtitle, body);
        }
        layoutController.setContent(placeholder.getParent());
        primaryStage.setTitle("Smart Patient Monitoring System - " + title);
    }

    public void showUserProfile() {
        setShellContent("/ui/javafx/views/UserProfileView.fxml", "Smart Patient Monitoring System - Staff Profile");
    }

    public void showAuditLogs() {
        setShellContent("/ui/javafx/views/AuditLogView.fxml", "Smart Patient Monitoring System - Audit Logs");
    }

    public void showUserDirectory() {
        setShellContent("/ui/javafx/views/UserDirectoryManagementView.fxml", "Smart Patient Monitoring System - Staff/User Directory");
    }

    public void showStaffActivity() {
        setShellContent("/ui/javafx/views/StaffActivityView.fxml", "Smart Patient Monitoring System - Staff Activity");
    }

    public void showMedicationOverview() {
        setShellContent("/ui/javafx/views/MedicationOverviewView.fxml", "Smart Patient Monitoring System - Medication Overview");
    }

    public void showMedicationOverviewForPatient(String patientId) {
        ensureShell("Smart Patient Monitoring System - Patient Medications");
        AppNavigator.LoadedView medicationOverview = navigator.loadView("/ui/javafx/views/MedicationOverviewView.fxml");
        if (medicationOverview.getController() instanceof MedicationOverviewController) {
            ((MedicationOverviewController) medicationOverview.getController()).openForPatient(patientId);
        }
        layoutController.setContent(medicationOverview.getParent());
        primaryStage.setTitle("Smart Patient Monitoring System - Patient Medications");
    }

    public void showRoomBedOccupancy() {
        setShellContent("/ui/javafx/views/RoomBedOccupancyView.fxml", "Smart Patient Monitoring System - Room/Bed Occupancy");
    }

    public void showDeceasedRecords() {
        setShellContent("/ui/javafx/views/DeceasedRecordsView.fxml", "Smart Patient Monitoring System - Deceased Records");
    }

    public void showDeceasedRecord(long recordId) {
        ensureShell("Smart Patient Monitoring System - Deceased Record");
        AppNavigator.LoadedView deceasedRecords = navigator.loadView("/ui/javafx/views/DeceasedRecordsView.fxml");
        if (deceasedRecords.getController() instanceof DeceasedRecordsController) {
            ((DeceasedRecordsController) deceasedRecords.getController()).openWithRecord(recordId);
        }
        layoutController.setContent(deceasedRecords.getParent());
        primaryStage.setTitle("Smart Patient Monitoring System - Deceased Record");
    }

    public void showNewbornRecords() {
        setShellContent("/ui/javafx/views/NewbornRecordsView.fxml", "Smart Patient Monitoring System - Newborn Records");
    }

    public void showNewbornRecord(long recordId) {
        ensureShell("Smart Patient Monitoring System - Newborn Record");
        AppNavigator.LoadedView newborns = navigator.loadView("/ui/javafx/views/NewbornRecordsView.fxml");
        if (newborns.getController() instanceof NewbornRecordsController) {
            ((NewbornRecordsController) newborns.getController()).openWithRecord(recordId);
        }
        layoutController.setContent(newborns.getParent());
        primaryStage.setTitle("Smart Patient Monitoring System - Newborn Record");
    }

    public void showNewbornRecordsForMother(String motherPatientId) {
        ensureShell("Smart Patient Monitoring System - Newborn Records");
        AppNavigator.LoadedView newborns = navigator.loadView("/ui/javafx/views/NewbornRecordsView.fxml");
        if (newborns.getController() instanceof NewbornRecordsController) {
            ((NewbornRecordsController) newborns.getController()).openForMother(motherPatientId);
        }
        layoutController.setContent(newborns.getParent());
        primaryStage.setTitle("Smart Patient Monitoring System - Newborn Records");
    }

    public void showCertificateRegistry() {
        setShellContent("/ui/javafx/views/CertificateRegistryView.fxml", "Smart Patient Monitoring System - Certificate Registry");
    }

    public void showAiRecommendations() {
        setShellContent("/ui/javafx/views/AiRecommendationsView.fxml", "Smart Patient Monitoring System - AI Recommendations");
    }

    public void showMedicalDevices() {
        setShellContent("/ui/javafx/views/MedicalDevicesView.fxml", "Smart Patient Monitoring System - Medical Devices");
    }

    public void showMedicalDevicesForPatient(String patientId) {
        ensureShell("Smart Patient Monitoring System - Patient Devices");
        AppNavigator.LoadedView devices = navigator.loadView("/ui/javafx/views/MedicalDevicesView.fxml");
        if (devices.getController() instanceof MedicalDevicesController) {
            ((MedicalDevicesController) devices.getController()).openForPatient(patientId);
        }
        layoutController.setContent(devices.getParent());
        primaryStage.setTitle("Smart Patient Monitoring System - Patient Devices");
    }

    public void showScheduling() {
        setShellContent("/ui/javafx/views/SchedulingView.fxml", "Smart Patient Monitoring System - Appointments & Reminders");
    }

    public void showSchedulingForPatient(String patientId) {
        ensureShell("Smart Patient Monitoring System - Patient Scheduling");
        AppNavigator.LoadedView scheduling = navigator.loadView("/ui/javafx/views/SchedulingView.fxml");
        if (scheduling.getController() instanceof SchedulingController) {
            ((SchedulingController) scheduling.getController()).openForPatient(patientId);
        }
        layoutController.setContent(scheduling.getParent());
        primaryStage.setTitle("Smart Patient Monitoring System - Patient Scheduling");
    }

    public void showNurseWorkQueue() {
        setShellContent("/ui/javafx/views/NurseWorkQueueView.fxml", "Smart Patient Monitoring System - Nurse Work Queue");
    }

    public void showMedicalFiles() {
        setShellContent("/ui/javafx/views/MedicalFilesView.fxml", "Smart Patient Monitoring System - Medical Files");
    }

    public void showBackupExport() {
        setShellContent("/ui/javafx/views/BackupExportView.fxml", "Smart Patient Monitoring System - Backup / Export");
    }

    public void showMedicalFilesForPatient(String patientId) {
        ensureShell("Smart Patient Monitoring System - Patient Medical Files");
        AppNavigator.LoadedView files = navigator.loadView("/ui/javafx/views/MedicalFilesView.fxml");
        if (files.getController() instanceof MedicalFilesController) {
            ((MedicalFilesController) files.getController()).openForPatient(patientId);
        }
        layoutController.setContent(files.getParent());
        primaryStage.setTitle("Smart Patient Monitoring System - Patient Medical Files");
    }

    public void showMedicalFileDetails(String patientId, String fileId) {
        ensureShell("Smart Patient Monitoring System - Medical File Details");
        AppNavigator.LoadedView files = navigator.loadView("/ui/javafx/views/MedicalFilesView.fxml");
        if (files.getController() instanceof MedicalFilesController) {
            ((MedicalFilesController) files.getController()).openForFile(patientId, fileId);
        }
        layoutController.setContent(files.getParent());
        primaryStage.setTitle("Smart Patient Monitoring System - Medical File Details");
    }

    public void showCertificateSourceRecord(String sourceType, String sourceId) {
        openCertificateSourceRecord(sourceType, sourceId, AuditAction.OPEN_CERTIFICATE_SOURCE_RECORD);
    }

    public void showMessageCertificateSourceRecord(String sourceType, String sourceId) {
        openCertificateSourceRecord(sourceType, sourceId, AuditAction.OPEN_MESSAGE_SOURCE_RECORD);
    }

    public void showCertificateFromNotification(String sourceType, String sourceId) {
        openCertificate(sourceType, sourceId, AuditAction.OPEN_CERTIFICATE_FROM_NOTIFICATION);
    }

    public void showCertificateFromMessage(String sourceType, String sourceId) {
        openCertificate(sourceType, sourceId, AuditAction.OPEN_CERTIFICATE_FROM_MESSAGE);
    }

    public void showCertificateFromRegistry(String sourceType, String sourceId) {
        openCertificate(sourceType, sourceId, AuditAction.OPEN_CERTIFICATE_FROM_REGISTRY);
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
        SessionContext.clear();
        Session.setCurrentUser(null);
        showLogin();
    }

    public String syncFromLegacyStorage() {
        SqliteMigrationService.MigrationResult result = new SqliteMigrationService().migrateFromTextFiles();
        migrationStatus = result.getSummary().trim();
        databaseStatus = DatabaseManager.testConnection()
                ? "SQLite ready: " + DatabaseManager.getDatabasePath()
                : "SQLite connection check failed after sync";
        logAudit("JavaFX SYSTEM sync from legacy storage");
        return migrationStatus;
    }

    public void toggleTheme() {
        darkMode = !darkMode;
        PREFS.putBoolean(THEME_PREF_KEY, darkMode);
        applyTheme(primaryStage.getScene());
    }

    public boolean isDarkMode() {
        return darkMode;
    }

    public String getDatabaseStatus() {
        return databaseStatus;
    }

    public String getMigrationStatus() {
        return migrationStatus;
    }

    private void initializeDatabase() {
        try {
            SchemaInitializer.initialize();
            SqliteMigrationService.MigrationResult migrationResult = new SqliteMigrationService().migrateIfNeeded();
            migrationStatus = migrationResult.getSummary().trim();
            databaseStatus = DatabaseManager.testConnection()
                    ? "SQLite ready: " + DatabaseManager.getDatabasePath()
                    : "SQLite schema created, connection check failed";
        } catch (Exception e) {
            databaseStatus = "SQLite initialization failed: " + e.getMessage();
            System.out.println(databaseStatus);
        }
    }

    private void setView(String fxmlPath, String title) {
        Parent root = navigator.load(fxmlPath);
        Scene scene = new Scene(root, 1180, 760);
        applyTheme(scene);
        primaryStage.setTitle(title);
        primaryStage.setMinWidth(980);
        primaryStage.setMinHeight(640);
        primaryStage.setScene(scene);
    }

    private void setShellContent(String fxmlPath, String title) {
        ensureShell(title);
        layoutController.setContent(navigator.load(fxmlPath));
        primaryStage.setTitle(title);
    }

    private void ensureShell(String title) {
        if (layoutController != null && primaryStage.getScene() != null) {
            return;
        }

        AppNavigator.LoadedView layout = navigator.loadView("/ui/javafx/views/AppLayout.fxml");
        layoutController = (AppLayoutController) layout.getController();
        Scene scene = new Scene(layout.getParent(), 1240, 780);
        applyTheme(scene);
        primaryStage.setTitle(title);
        primaryStage.setMinWidth(1040);
        primaryStage.setMinHeight(680);
        primaryStage.setScene(scene);
    }

    private void openCertificateSourceRecord(String sourceType, String sourceId, String auditAction) {
        try {
            if ("DEATH_CERTIFICATE".equalsIgnoreCase(sourceType)) {
                require(PermissionHelper.canViewDeceasedRecords(Session.getCurrentUser()), "Access denied for deceased records.");
                long recordId = parseId(sourceId, "death record");
                new SqliteDeceasedRecordDao().findById(recordId)
                        .orElseThrow(() -> new IllegalArgumentException("Death record not found in SQLite: " + recordId));
                showDeceasedRecord(recordId);
                AuditWriteHelper.write(SessionContext.username(), auditAction, "source=DEATH_CERTIFICATE:" + recordId);
            } else if ("BIRTH_CERTIFICATE".equalsIgnoreCase(sourceType)) {
                require(PermissionHelper.canViewNewbornRecords(Session.getCurrentUser()), "Access denied for newborn records.");
                long recordId = resolveNewbornRecordId(sourceId);
                showNewbornRecord(recordId);
                AuditWriteHelper.write(SessionContext.username(), auditAction, "source=BIRTH_CERTIFICATE:" + recordId);
            } else {
                DialogHelper.warning("Unsupported source", "This notification source cannot open a certificate record: " + sourceType);
            }
        } catch (Exception e) {
            DialogHelper.warning("Source unavailable", e.getMessage());
        }
    }

    private void openCertificate(String sourceType, String sourceId, String auditAction) {
        try {
            if ("DEATH_CERTIFICATE".equalsIgnoreCase(sourceType)) {
                require(PermissionHelper.canViewDeceasedRecords(Session.getCurrentUser()), "Access denied for death certificates.");
                long recordId = parseId(sourceId, "death record");
                new DeceasedPatientService().openDeathCertificate(Session.getCurrentUser(), recordId);
                AuditWriteHelper.write(SessionContext.username(), auditAction, "source=DEATH_CERTIFICATE:" + recordId);
            } else if ("BIRTH_CERTIFICATE".equalsIgnoreCase(sourceType)) {
                require(PermissionHelper.canViewNewbornRecords(Session.getCurrentUser()), "Access denied for birth certificates.");
                SqliteNewbornRecordDao.NewbornRecord record = resolveNewbornRecord(sourceId);
                new NewbornService().openBirthCertificate(Session.getCurrentUser(), record.getNewbornId());
                AuditWriteHelper.write(SessionContext.username(), auditAction, "source=BIRTH_CERTIFICATE:" + record.getId());
            } else {
                DialogHelper.warning("Unsupported certificate", "This source does not point to a certificate: " + sourceType);
            }
        } catch (Exception e) {
            DialogHelper.warning("Certificate unavailable", e.getMessage());
        }
    }

    private SqliteNewbornRecordDao.NewbornRecord resolveNewbornRecord(String sourceId) throws Exception {
        SqliteNewbornRecordDao newbornDao = new SqliteNewbornRecordDao();
        if (sourceId != null && sourceId.matches("\\d+")) {
            long id = Long.parseLong(sourceId);
            return newbornDao.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Newborn record not found in SQLite: " + id));
        }
        return newbornDao.findByNewbornId(sourceId)
                .orElseThrow(() -> new IllegalArgumentException("Newborn record not found in SQLite: " + sourceId));
    }

    private long resolveNewbornRecordId(String sourceId) throws Exception {
        return resolveNewbornRecord(sourceId).getId();
    }

    private long parseId(String sourceId, String label) {
        if (sourceId == null || !sourceId.matches("\\d+")) {
            throw new IllegalArgumentException("Invalid " + label + " ID: " + sourceId);
        }
        return Long.parseLong(sourceId);
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new SecurityException(message);
        }
    }

    private void applyTheme(Scene scene) {
        if (scene == null) {
            return;
        }
        scene.getStylesheets().clear();
        scene.getStylesheets().add(AppNavigator.resolve(darkMode ? DARK_THEME : LIGHT_THEME).toExternalForm());
    }

    private void logAudit(String action) {
        try {
            new SqliteAuditLogDao().log(SessionContext.username(), action);
        } catch (Exception e) {
            System.out.println("SQLite JavaFX audit skipped: " + e.getMessage());
        }
    }
}
