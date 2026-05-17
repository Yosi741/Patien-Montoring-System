package services;

import database.DatabaseManager;
import database.SchemaInitializer;
import ui.javafx.SessionContext;
import ui.javafx.helpers.AuditAction;
import ui.javafx.helpers.AuditWriteHelper;
import ui.javafx.helpers.PermissionHelper;
import users.User;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class BackupService {

    private static final Path DEFAULT_BACKUP_DIR = Paths.get("data", "backups");
    private static final Path DEFAULT_EXPORT_DIR = Paths.get("data", "exports");
    private static final Path UPLOADS_DIR = Paths.get("data", "uploads");
    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final DateTimeFormatter DISPLAY_STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public BackupResult createBackup(User user, Path destinationFolder) throws IOException, SQLException {
        require(PermissionHelper.canCreateBackup(user), "Only ADMIN users can create local backups.");
        SchemaInitializer.initialize();
        checkpointDatabase();

        Path dbPath = Paths.get(DatabaseManager.getDatabasePath()).toAbsolutePath().normalize();
        if (!Files.exists(dbPath) || !Files.isRegularFile(dbPath)) {
            throw new IOException("SQLite database file was not found at " + dbPath);
        }

        Path backupDir = prepareDirectory(destinationFolder, DEFAULT_BACKUP_DIR);
        Path backupPath = uniqueFile(backupDir, "spms-backup-", ".zip");
        BackupCounter counter = new BackupCounter();

        try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(backupPath)))) {
            addFile(zip, dbPath, "data/smart_patient_monitoring.db");
            counter.entryCount++;

            Path uploadsRoot = UPLOADS_DIR.toAbsolutePath().normalize();
            if (Files.exists(uploadsRoot)) {
                Path realUploadsRoot = uploadsRoot.toRealPath();
                try (Stream<Path> paths = Files.walk(realUploadsRoot, Integer.MAX_VALUE, FileVisitOption.FOLLOW_LINKS)) {
                    paths.filter(Files::isRegularFile).forEach(path -> {
                        try {
                            Path realFile = path.toRealPath();
                            if (!realFile.startsWith(realUploadsRoot)) {
                                return;
                            }
                            String entryName = "data/uploads/" + toZipPath(realUploadsRoot.relativize(realFile));
                            addFile(zip, realFile, entryName);
                            counter.entryCount++;
                            counter.uploadFileCount++;
                        } catch (IOException e) {
                            throw new BackupRuntimeException(e);
                        }
                    });
                } catch (BackupRuntimeException e) {
                    throw e.getCause();
                }
            }

            addText(zip, "README-backup-info.txt", backupReadme(counter.uploadFileCount));
            counter.entryCount++;
        }

        VerificationResult verification = verifyBackupZip(backupPath);
        if (!verification.hasDatabase()) {
            throw new IOException("Backup ZIP verification failed: database entry is missing.");
        }

        auditQuietly(AuditAction.CREATE_BACKUP, "Created local backup " + backupPath.getFileName());
        return new BackupResult(backupPath, Files.size(backupPath), counter.entryCount, counter.uploadFileCount,
                "Backup created and verified.");
    }

    public ExportResult exportPatientsCsv(User user, Path destinationFolder) throws IOException, SQLException {
        require(PermissionHelper.canExportClinicalData(user), "Only ADMIN and DOCTOR users can export patient summaries.");
        String sql = "SELECT patient_id, first_name, last_name, birth_date, gender, section, room, status, priority, diagnosis, created_at, updated_at "
                + "FROM patients ORDER BY patient_id";
        String[] headers = {"patient_id", "first_name", "last_name", "birth_date", "gender", "section", "room", "status", "priority", "diagnosis", "created_at", "updated_at"};
        return exportCsv(destinationFolder, "patients-summary-", headers, sql, AuditAction.EXPORT_PATIENTS_CSV);
    }

    public ExportResult exportAlertsCsv(User user, Path destinationFolder) throws IOException, SQLException {
        require(PermissionHelper.canExportClinicalData(user), "Only ADMIN and DOCTOR users can export alert summaries.");
        String sql = "SELECT id, patient_id, severity, message, status, created_at, updated_at, acknowledged_by, acknowledged_at "
                + "FROM alerts ORDER BY datetime(created_at) DESC, id DESC";
        String[] headers = {"id", "patient_id", "severity", "message", "status", "created_at", "updated_at", "acknowledged_by", "acknowledged_at"};
        return exportCsv(destinationFolder, "alerts-summary-", headers, sql, AuditAction.EXPORT_ALERTS_CSV);
    }

    public ExportResult exportAuditLogsCsv(User user, Path destinationFolder) throws IOException, SQLException {
        require(PermissionHelper.canExportAuditLogs(user), "Only ADMIN users can export audit logs.");
        String sql = "SELECT id, username, action, created_at FROM audit_logs ORDER BY datetime(created_at) DESC, id DESC";
        String[] headers = {"id", "username", "action", "created_at"};
        return exportCsv(destinationFolder, "audit-logs-", headers, sql, AuditAction.EXPORT_AUDIT_LOGS_CSV);
    }

    public ExportResult exportMedicationEventsCsv(User user, Path destinationFolder) throws IOException, SQLException {
        require(PermissionHelper.canExportClinicalData(user), "Only ADMIN and DOCTOR users can export medication events.");
        String sql = "SELECT e.id, e.patient_id, COALESCE(p.first_name || ' ' || p.last_name, '') AS patient_name, "
                + "COALESCE(m.name, '') AS medication_name, COALESCE(m.dose, '') AS dose, COALESCE(m.route, '') AS route, "
                + "COALESCE(m.frequency, '') AS frequency, e.given_by, e.given_at, e.notes "
                + "FROM medication_events e "
                + "LEFT JOIN medications m ON e.medication_id = m.id "
                + "LEFT JOIN patients p ON e.patient_id = p.patient_id "
                + "ORDER BY datetime(e.given_at) DESC, e.id DESC";
        String[] headers = {"id", "patient_id", "patient_name", "medication_name", "dose", "route", "frequency", "given_by", "given_at", "notes"};
        return exportCsv(destinationFolder, "medication-events-", headers, sql, AuditAction.EXPORT_MEDICATION_CSV);
    }

    public ExportResult exportSchedulingCsv(User user, Path destinationFolder) throws IOException, SQLException {
        require(PermissionHelper.canExportClinicalData(user), "Only ADMIN and DOCTOR users can export scheduling data.");
        String sql = "SELECT 'APPOINTMENT' AS record_type, id, patient_id, title, appointment_type AS type, start_time AS start_or_due_time, "
                + "end_time, location, assigned_staff AS assigned_to, status, notes, created_by, created_at, updated_at "
                + "FROM appointments "
                + "UNION ALL "
                + "SELECT 'REMINDER' AS record_type, id, patient_id, title, reminder_type AS type, due_time AS start_or_due_time, "
                + "'' AS end_time, '' AS location, assigned_to, status, notes, created_by, created_at, updated_at "
                + "FROM reminders "
                + "ORDER BY start_or_due_time DESC";
        String[] headers = {"record_type", "id", "patient_id", "title", "type", "start_or_due_time", "end_time", "location", "assigned_to", "status", "notes", "created_by", "created_at", "updated_at"};
        return exportCsv(destinationFolder, "scheduling-", headers, sql, AuditAction.EXPORT_SCHEDULING_CSV);
    }

    public RestorePreview previewRestore(User user, Path backupZip) throws IOException {
        require(PermissionHelper.canPreviewRestore(user), "Only ADMIN users can preview restore backups.");
        if (backupZip == null) {
            throw new IOException("No backup ZIP selected.");
        }
        Path zipPath = backupZip.toAbsolutePath().normalize();
        if (!Files.exists(zipPath) || !Files.isRegularFile(zipPath)) {
            throw new IOException("Backup ZIP was not found: " + zipPath);
        }
        if (!zipPath.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip")) {
            throw new IOException("Selected file is not a ZIP backup.");
        }

        ArrayList<String> entries = new ArrayList<>();
        ArrayList<String> warnings = new ArrayList<>();
        boolean hasDatabase = false;
        int uploadCount = 0;
        String metadata = "";

        try (ZipFile zip = new ZipFile(zipPath.toFile())) {
            java.util.Enumeration<? extends ZipEntry> zipEntries = zip.entries();
            while (zipEntries.hasMoreElements()) {
                ZipEntry entry = zipEntries.nextElement();
                String name = entry.getName();
                entries.add(name + (entry.isDirectory() ? " [directory]" : " (" + entry.getSize() + " bytes)"));
                if (isUnsafeZipEntry(name)) {
                    warnings.add("Unsafe ZIP entry name detected and would be blocked: " + name);
                }
                if ("data/smart_patient_monitoring.db".equals(name)) {
                    hasDatabase = true;
                }
                if (!entry.isDirectory() && name.startsWith("data/uploads/")) {
                    uploadCount++;
                }
                if ("README-backup-info.txt".equals(name) && !entry.isDirectory()) {
                    metadata = readZipText(zip, entry, 6000);
                }
            }
        }

        if (!hasDatabase) {
            warnings.add("Expected SQLite database entry is missing: data/smart_patient_monitoring.db");
        }
        if (entries.isEmpty()) {
            warnings.add("Backup ZIP contains no entries.");
        }

        boolean valid = hasDatabase && warnings.stream().noneMatch(warning -> warning.startsWith("Unsafe ZIP entry"));
        auditQuietly(AuditAction.PREVIEW_RESTORE_BACKUP, "Previewed backup " + zipPath.getFileName());
        return new RestorePreview(zipPath, valid, hasDatabase, uploadCount, entries, warnings, metadata);
    }

    private ExportResult exportCsv(Path destinationFolder, String prefix, String[] headers, String sql, String auditAction)
            throws IOException, SQLException {
        SchemaInitializer.initialize();
        Path exportDir = prepareDirectory(destinationFolder, DEFAULT_EXPORT_DIR);
        Path outputPath = uniqueFile(exportDir, prefix, ".csv");
        int rows = 0;

        try (Connection connection = DatabaseManager.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql);
             BufferedOutputStream output = new BufferedOutputStream(Files.newOutputStream(outputPath))) {
            output.write(String.join(",", quoteAll(headers)).getBytes(StandardCharsets.UTF_8));
            output.write(System.lineSeparator().getBytes(StandardCharsets.UTF_8));
            while (resultSet.next()) {
                ArrayList<String> values = new ArrayList<>();
                for (String header : headers) {
                    values.add(csv(resultSet.getString(header)));
                }
                output.write(String.join(",", values).getBytes(StandardCharsets.UTF_8));
                output.write(System.lineSeparator().getBytes(StandardCharsets.UTF_8));
                rows++;
            }
        }

        auditQuietly(auditAction, "Exported " + rows + " rows to " + outputPath.getFileName());
        return new ExportResult(outputPath, rows, Files.size(outputPath));
    }

    private void checkpointDatabase() throws SQLException {
        try (Connection connection = DatabaseManager.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA wal_checkpoint(FULL)");
        }
    }

    private Path prepareDirectory(Path selectedFolder, Path defaultFolder) throws IOException {
        Path folder = selectedFolder == null ? defaultFolder : selectedFolder;
        Path normalized = folder.toAbsolutePath().normalize();
        Files.createDirectories(normalized);
        if (!Files.isDirectory(normalized)) {
            throw new IOException("Destination is not a directory: " + normalized);
        }
        return normalized;
    }

    private Path uniqueFile(Path folder, String prefix, String extension) {
        String name = prefix + LocalDateTime.now().format(FILE_STAMP) + "-"
                + UUID.randomUUID().toString().substring(0, 8) + extension;
        return folder.resolve(name).toAbsolutePath().normalize();
    }

    private void addFile(ZipOutputStream zip, Path file, String entryName) throws IOException {
        ZipEntry entry = new ZipEntry(entryName);
        entry.setTime(Files.getLastModifiedTime(file).toMillis());
        zip.putNextEntry(entry);
        try (BufferedInputStream input = new BufferedInputStream(Files.newInputStream(file))) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                zip.write(buffer, 0, read);
            }
        }
        zip.closeEntry();
    }

    private void addText(ZipOutputStream zip, String entryName, String content) throws IOException {
        ZipEntry entry = new ZipEntry(entryName);
        zip.putNextEntry(entry);
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private VerificationResult verifyBackupZip(Path backupPath) throws IOException {
        boolean hasDb = false;
        boolean readable = false;
        try (ZipFile zip = new ZipFile(backupPath.toFile())) {
            readable = true;
            hasDb = zip.getEntry("data/smart_patient_monitoring.db") != null;
        }
        return new VerificationResult(readable, hasDb);
    }

    private String backupReadme(int uploadFileCount) {
        return "Smart Patient Monitoring System Local Backup" + System.lineSeparator()
                + "Created at: " + LocalDateTime.now().format(DISPLAY_STAMP) + System.lineSeparator()
                + "Created by: " + SessionContext.username() + System.lineSeparator()
                + "SQLite database entry: data/smart_patient_monitoring.db" + System.lineSeparator()
                + "Upload files included from: data/uploads/" + System.lineSeparator()
                + "Upload file count: " + uploadFileCount + System.lineSeparator()
                + "Scope: Local backup only. No cloud upload and no encryption in Phase 30." + System.lineSeparator()
                + "Restore behavior: JavaFX currently supports preview/validation only. It does not overwrite the live database." + System.lineSeparator();
    }

    private String readZipText(ZipFile zip, ZipEntry entry, int limit) throws IOException {
        try (InputStream input = zip.getInputStream(entry)) {
            byte[] bytes = input.readNBytes(Math.max(1, limit));
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private boolean isUnsafeZipEntry(String name) {
        if (name == null || name.isBlank()) {
            return true;
        }
        String normalized = name.replace('\\', '/');
        return normalized.startsWith("/")
                || normalized.startsWith("../")
                || normalized.contains("/../")
                || normalized.equals("..")
                || normalized.contains(":");
    }

    private String toZipPath(Path path) {
        return path.toString().replace('\\', '/');
    }

    private List<String> quoteAll(String[] headers) {
        ArrayList<String> quoted = new ArrayList<>();
        for (String header : headers) {
            quoted.add(csv(header));
        }
        return quoted;
    }

    private String csv(String value) {
        String safe = value == null ? "" : value.replace("\r", " ").replace("\n", " ");
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    private void require(boolean allowed, String message) {
        if (!allowed) {
            throw new SecurityException(message);
        }
    }

    private void auditQuietly(String action, String detail) {
        try {
            AuditWriteHelper.write(SessionContext.username(), action, detail);
        } catch (Exception e) {
            System.out.println("Backup/export audit skipped: " + e.getMessage());
        }
    }

    public static class BackupResult {
        private final Path backupPath;
        private final long sizeBytes;
        private final int entryCount;
        private final int uploadFileCount;
        private final String message;

        public BackupResult(Path backupPath, long sizeBytes, int entryCount, int uploadFileCount, String message) {
            this.backupPath = backupPath;
            this.sizeBytes = sizeBytes;
            this.entryCount = entryCount;
            this.uploadFileCount = uploadFileCount;
            this.message = message;
        }

        public Path getBackupPath() { return backupPath; }
        public long getSizeBytes() { return sizeBytes; }
        public int getEntryCount() { return entryCount; }
        public int getUploadFileCount() { return uploadFileCount; }
        public String getMessage() { return message; }
    }

    public static class ExportResult {
        private final Path exportPath;
        private final int rowCount;
        private final long sizeBytes;

        public ExportResult(Path exportPath, int rowCount, long sizeBytes) {
            this.exportPath = exportPath;
            this.rowCount = rowCount;
            this.sizeBytes = sizeBytes;
        }

        public Path getExportPath() { return exportPath; }
        public int getRowCount() { return rowCount; }
        public long getSizeBytes() { return sizeBytes; }
    }

    public static class RestorePreview {
        private final Path backupPath;
        private final boolean valid;
        private final boolean hasDatabase;
        private final int uploadFileCount;
        private final List<String> entries;
        private final List<String> warnings;
        private final String metadata;

        public RestorePreview(Path backupPath, boolean valid, boolean hasDatabase, int uploadFileCount,
                              List<String> entries, List<String> warnings, String metadata) {
            this.backupPath = backupPath;
            this.valid = valid;
            this.hasDatabase = hasDatabase;
            this.uploadFileCount = uploadFileCount;
            this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
            this.warnings = Collections.unmodifiableList(new ArrayList<>(warnings));
            this.metadata = metadata == null ? "" : metadata;
        }

        public Path getBackupPath() { return backupPath; }
        public boolean isValid() { return valid; }
        public boolean hasDatabase() { return hasDatabase; }
        public int getUploadFileCount() { return uploadFileCount; }
        public List<String> getEntries() { return entries; }
        public List<String> getWarnings() { return warnings; }
        public String getMetadata() { return metadata; }
    }

    private static class VerificationResult {
        private final boolean readable;
        private final boolean hasDatabase;

        VerificationResult(boolean readable, boolean hasDatabase) {
            this.readable = readable;
            this.hasDatabase = hasDatabase;
        }

        boolean hasDatabase() {
            return readable && hasDatabase;
        }
    }

    private static class BackupCounter {
        private int entryCount;
        private int uploadFileCount;
    }

    private static class BackupRuntimeException extends RuntimeException {
        BackupRuntimeException(IOException cause) {
            super(cause);
        }

        @Override
        public IOException getCause() {
            return (IOException) super.getCause();
        }
    }
}
