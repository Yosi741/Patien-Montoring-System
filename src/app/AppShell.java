package app;

import pages.audit_log.SqliteAuditLogDao;
import pages.deceased.SqliteDeceasedRecordDao;
import pages.newborn.SqliteNewbornRecordDao;
import javafx.application.Application;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import pages.clinical_timeline.ClinicalTimelineController;
import pages.deceased.deceased_records.DeceasedRecordsController;
import pages.patient.medical_files.MedicalFilesController;
import pages.newborn.newborn_records.NewbornRecordsController;
import pages.patient.patient_detail.PatientDetailController;
import pages.scheduling.schedule_overview.SchedulingController;
import pages.deceased.DeceasedPatientService;
import pages.newborn.NewbornService;
import pages.audit_log.AuditAction;
import pages.audit_log.AuditWriteHelper;
import app.helpers.DialogHelper;
import app.helpers.FxFileOpenHelper;
import app.helpers.PermissionHelper;
import users.Session;
import pages.user.User;

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
        FxFileOpenHelper.registerHostServices(getHostServices());
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

    public void showMessaging() {
        setShellContent("/pages/messages/MessagingView.fxml", "Smart Patient Monitoring System - Messaging");
    }

    public void showNotificationCenter() {
        setShellContent("/pages/notification/NotificationCenterView.fxml", "Smart Patient Monitoring System - Notification Center");
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

    public void showPlaceholder(String title, String subtitle, String body) {
        ensureShell("Smart Patient Monitoring System - " + title);
        AppNavigator.LoadedView placeholder = navigator.loadView("/app/PlaceholderView.fxml");
        if (placeholder.getController() instanceof PlaceholderController) {
            ((PlaceholderController) placeholder.getController()).setContent(title, subtitle, body);
        }
        setShellLoadedContent(placeholder);
        primaryStage.setTitle("Smart Patient Monitoring System - " + title);
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

    public void showRoomBedOccupancy() {
        setShellContent("/pages/room_section/occupancy/RoomBedOccupancyView.fxml", "Smart Patient Monitoring System - Room/Bed Occupancy");
    }

    public void showDeceasedRecords() {
        setShellContent("/pages/deceased/deceased_records/DeceasedRecordsView.fxml", "Smart Patient Monitoring System - Deceased Records");
    }

    public void showDeceasedRecord(long recordId) {
        ensureShell("Smart Patient Monitoring System - Deceased Record");
        AppNavigator.LoadedView deceasedRecords = navigator.loadView("/pages/deceased/deceased_records/DeceasedRecordsView.fxml");
        if (deceasedRecords.getController() instanceof DeceasedRecordsController) {
            ((DeceasedRecordsController) deceasedRecords.getController()).openWithRecord(recordId);
        }
        setShellLoadedContent(deceasedRecords);
        primaryStage.setTitle("Smart Patient Monitoring System - Deceased Record");
    }

    public void showNewbornRecords() {
        setShellContent("/pages/newborn/newborn_records/NewbornRecordsView.fxml", "Smart Patient Monitoring System - Newborn Records");
    }

    public void showNewbornRecord(long recordId) {
        ensureShell("Smart Patient Monitoring System - Newborn Record");
        AppNavigator.LoadedView newborns = navigator.loadView("/pages/newborn/newborn_records/NewbornRecordsView.fxml");
        if (newborns.getController() instanceof NewbornRecordsController) {
            ((NewbornRecordsController) newborns.getController()).openWithRecord(recordId);
        }
        setShellLoadedContent(newborns);
        primaryStage.setTitle("Smart Patient Monitoring System - Newborn Record");
    }

    public void showNewbornRecordsForMother(String motherPatientId) {
        ensureShell("Smart Patient Monitoring System - Newborn Records");
        AppNavigator.LoadedView newborns = navigator.loadView("/pages/newborn/newborn_records/NewbornRecordsView.fxml");
        if (newborns.getController() instanceof NewbornRecordsController) {
            ((NewbornRecordsController) newborns.getController()).openForMother(motherPatientId);
        }
        setShellLoadedContent(newborns);
        primaryStage.setTitle("Smart Patient Monitoring System - Newborn Records");
    }

    public void showCertificateRegistry() {
        setShellContent("/pages/certificate/certificate_registry/CertificateRegistryView.fxml", "Smart Patient Monitoring System - Certificate Registry");
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
        // Stylesheets are attached at the Scene level. This hook is kept for callers
        // that load content before it is placed into the shell.
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
        scene.getStylesheets().add(AppNavigator.resolve(PRESENTATION_THEME).toExternalForm());
    }

    private void logAudit(String action) {
        try {
            new SqliteAuditLogDao().log(SessionContext.username(), action);
        } catch (Exception e) {
            System.out.println("SQLite JavaFX audit skipped: " + e.getMessage());
        }
    }
}
