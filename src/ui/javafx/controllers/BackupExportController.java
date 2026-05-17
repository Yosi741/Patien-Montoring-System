package ui.javafx.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import services.BackupService;
import ui.javafx.AppShell;
import ui.javafx.FxController;
import ui.javafx.helpers.DialogHelper;
import ui.javafx.helpers.NotificationHelper;
import ui.javafx.helpers.PermissionHelper;
import users.Session;
import users.User;

import java.io.File;
import java.nio.file.Path;

public class BackupExportController implements FxController {

    private final BackupService backupService = new BackupService();
    private AppShell appShell;

    @FXML
    private VBox accessDeniedPane;

    @FXML
    private VBox contentPane;

    @FXML
    private Button createBackupButton;

    @FXML
    private Button previewRestoreButton;

    @FXML
    private Button exportPatientsButton;

    @FXML
    private Button exportAlertsButton;

    @FXML
    private Button exportAuditLogsButton;

    @FXML
    private Button exportMedicationButton;

    @FXML
    private Button exportSchedulingButton;

    @FXML
    private Label statusLabel;

    @FXML
    private TextArea resultArea;

    @Override
    public void setAppShell(AppShell appShell) {
        this.appShell = appShell;
        configureAccess();
    }

    @FXML
    private void showDashboard() {
        appShell.showDashboard(Session.getCurrentUser());
    }

    @FXML
    private void createBackup() {
        runAction("Create backup", () -> {
            Path folder = chooseDirectory("Choose Backup Folder or Cancel for data/backups");
            BackupService.BackupResult result = backupService.createBackup(Session.getCurrentUser(), folder);
            showResult("Backup Created",
                    "Status: " + result.getMessage() + "\n"
                            + "Location: " + result.getBackupPath() + "\n"
                            + "Size: " + readableSize(result.getSizeBytes()) + "\n"
                            + "ZIP entries: " + result.getEntryCount() + "\n"
                            + "Upload files included: " + result.getUploadFileCount() + "\n"
                            + "Verification: ZIP opened successfully and contains data/smart_patient_monitoring.db");
        });
    }

    @FXML
    private void exportPatientsCsv() {
        runExport("Patients CSV", () -> backupService.exportPatientsCsv(Session.getCurrentUser(), chooseExportDirectory()));
    }

    @FXML
    private void exportAlertsCsv() {
        runExport("Alerts CSV", () -> backupService.exportAlertsCsv(Session.getCurrentUser(), chooseExportDirectory()));
    }

    @FXML
    private void exportAuditLogsCsv() {
        runExport("Audit Logs CSV", () -> backupService.exportAuditLogsCsv(Session.getCurrentUser(), chooseExportDirectory()));
    }

    @FXML
    private void exportMedicationCsv() {
        runExport("Medication Events CSV", () -> backupService.exportMedicationEventsCsv(Session.getCurrentUser(), chooseExportDirectory()));
    }

    @FXML
    private void exportSchedulingCsv() {
        runExport("Scheduling CSV", () -> backupService.exportSchedulingCsv(Session.getCurrentUser(), chooseExportDirectory()));
    }

    @FXML
    private void previewRestoreBackup() {
        runAction("Preview restore backup", () -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Select Backup ZIP to Preview");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Backup ZIP", "*.zip"));
            File selected = chooser.showOpenDialog(statusLabel.getScene().getWindow());
            if (selected == null) {
                showResult("Restore Preview Cancelled", "No backup ZIP selected.");
                return;
            }

            BackupService.RestorePreview preview = backupService.previewRestore(Session.getCurrentUser(), selected.toPath());
            StringBuilder builder = new StringBuilder();
            builder.append("Restore Preview Only\n");
            builder.append("No files were extracted. The current database was not changed.\n\n");
            builder.append("Backup: ").append(preview.getBackupPath()).append('\n');
            builder.append("Valid for future restore: ").append(preview.isValid() ? "Yes" : "No").append('\n');
            builder.append("Contains SQLite DB: ").append(preview.hasDatabase() ? "Yes" : "No").append('\n');
            builder.append("Upload files: ").append(preview.getUploadFileCount()).append('\n');
            if (!preview.getMetadata().isBlank()) {
                builder.append("\nREADME-backup-info.txt\n").append(preview.getMetadata()).append('\n');
            }
            if (!preview.getWarnings().isEmpty()) {
                builder.append("\nWarnings\n");
                for (String warning : preview.getWarnings()) {
                    builder.append("- ").append(warning).append('\n');
                }
            }
            builder.append("\nZIP Contents\n");
            for (String entry : preview.getEntries()) {
                builder.append("- ").append(entry).append('\n');
            }
            showResult("Restore Preview Complete", builder.toString());
        });
    }

    private void configureAccess() {
        User user = Session.getCurrentUser();
        boolean canView = PermissionHelper.canViewBackupTools(user);
        accessDeniedPane.setVisible(!canView);
        accessDeniedPane.setManaged(!canView);
        contentPane.setVisible(canView);
        contentPane.setManaged(canView);

        boolean adminBackup = PermissionHelper.canCreateBackup(user);
        boolean auditExport = PermissionHelper.canExportAuditLogs(user);
        boolean clinicalExport = PermissionHelper.canExportClinicalData(user);

        createBackupButton.setVisible(adminBackup);
        createBackupButton.setManaged(adminBackup);
        previewRestoreButton.setVisible(adminBackup);
        previewRestoreButton.setManaged(adminBackup);
        exportAuditLogsButton.setVisible(auditExport);
        exportAuditLogsButton.setManaged(auditExport);

        exportPatientsButton.setDisable(!clinicalExport);
        exportAlertsButton.setDisable(!clinicalExport);
        exportMedicationButton.setDisable(!clinicalExport);
        exportSchedulingButton.setDisable(!clinicalExport);

        if (canView) {
            NotificationHelper.showInfo(statusLabel, "Backup/export tools ready. Restore is preview-only.");
            resultArea.setText("Choose an action. Backups include the SQLite DB and files under data/uploads/ only.");
        }
    }

    private void runExport(String label, ExportCallable callable) {
        runAction(label, () -> {
            BackupService.ExportResult result = callable.call();
            showResult(label + " Exported",
                    "File: " + result.getExportPath() + "\n"
                            + "Rows: " + result.getRowCount() + "\n"
                            + "Size: " + readableSize(result.getSizeBytes()));
        });
    }

    private void runAction(String label, ThrowingRunnable runnable) {
        try {
            runnable.run();
            NotificationHelper.showSuccess(statusLabel, label + " completed.");
        } catch (SecurityException e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
            DialogHelper.warning("Access denied", e.getMessage());
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, label + " failed: " + e.getMessage());
            resultArea.setText(label + " failed.\n\n" + e.getMessage());
        }
    }

    private Path chooseExportDirectory() {
        return chooseDirectory("Choose Export Folder or Cancel for data/exports");
    }

    private Path chooseDirectory(String title) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(title);
        File selected = chooser.showDialog(statusLabel.getScene().getWindow());
        return selected == null ? null : selected.toPath();
    }

    private void showResult(String title, String message) {
        resultArea.setText(title + "\n\n" + message);
    }

    private String readableSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " bytes";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format("%.1f KB", kb);
        }
        return String.format("%.2f MB", kb / 1024.0);
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private interface ExportCallable {
        BackupService.ExportResult call() throws Exception;
    }
}
