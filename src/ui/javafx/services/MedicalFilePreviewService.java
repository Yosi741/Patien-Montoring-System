package ui.javafx.services;

import Data_Access_Object.SqliteMedicalFileDao;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import ui.javafx.helpers.AuditAction;
import ui.javafx.helpers.AuditWriteHelper;
import ui.javafx.helpers.FxFileOpenHelper;
import ui.javafx.helpers.PermissionHelper;
import users.User;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MedicalFilePreviewService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("txt", "csv", "pdf", "png", "jpg", "jpeg");
    private final SqliteMedicalFileDao medicalFileDao;

    public MedicalFilePreviewService() {
        this(new SqliteMedicalFileDao());
    }

    public MedicalFilePreviewService(SqliteMedicalFileDao medicalFileDao) {
        this.medicalFileDao = medicalFileDao;
    }

    public PreviewResult loadPreview(User currentUser, String fileId) throws SQLException, IOException {
        requireViewPermission(currentUser);
        SqliteMedicalFileDao.MedicalFileRecord file = loadFile(fileId);
        Path path = validateStoredPath(file);
        String extension = extension(path.getFileName().toString());
        switch (extension) {
            case "txt":
                return new PreviewResult(file, "TEXT", summarizeLines(Files.readAllLines(path, StandardCharsets.UTF_8), 80), path.toString());
            case "csv":
                return new PreviewResult(file, "CSV", csvPreview(Files.readAllLines(path, StandardCharsets.UTF_8)), path.toString());
            case "pdf":
                return new PreviewResult(file, "PDF", pdfPreview(path.toFile()), path.toString());
            case "png":
            case "jpg":
            case "jpeg":
                return new PreviewResult(file, "IMAGE", "Image preview loaded locally. OCR is not implemented.", path.toString());
            default:
                return new PreviewResult(file, "UNSUPPORTED", "Unsupported file type.", path.toString());
        }
    }

    public String openFile(User currentUser, String fileId) throws SQLException, IOException {
        requireViewPermission(currentUser);
        SqliteMedicalFileDao.MedicalFileRecord file = loadFile(fileId);
        Path path = validateStoredPath(file);
        String result = FxFileOpenHelper.open(path);
        AuditWriteHelper.write(username(currentUser), AuditAction.OPEN_MEDICAL_FILE,
                "patient_id=" + file.getPatientId() + ", file_id=" + file.getFileId());
        return result;
    }

    public SqliteMedicalFileDao.MedicalFileRecord loadFile(String fileId) throws SQLException {
        if (fileId == null || fileId.isBlank()) {
            throw new IllegalArgumentException("File ID is required.");
        }
        return medicalFileDao.findByFileId(fileId)
                .orElseThrow(() -> new IllegalArgumentException("Medical file not found in SQLite: " + fileId));
    }

    public Path validateStoredPath(SqliteMedicalFileDao.MedicalFileRecord file) throws IOException {
        if (file == null) {
            throw new IllegalArgumentException("Medical file metadata is required.");
        }
        String storedPath = file.getStoredPath();
        if (storedPath == null || storedPath.isBlank()) {
            throw new IllegalArgumentException("Stored file path is missing.");
        }

        Path uploadRoot = Path.of("data", "uploads").toAbsolutePath().normalize();
        if (!Files.exists(uploadRoot)) {
            throw new IOException("Allowed upload directory does not exist: " + uploadRoot);
        }
        Path realRoot = uploadRoot.toRealPath();
        Path requested = Path.of(storedPath).toAbsolutePath().normalize();
        if (!requested.startsWith(uploadRoot)) {
            throw new SecurityException("Stored path is outside the allowed data/uploads directory.");
        }
        if (!Files.exists(requested) || !Files.isRegularFile(requested)) {
            throw new IOException("Stored file does not exist: " + requested);
        }
        Path realFile = requested.toRealPath();
        if (!realFile.startsWith(realRoot)) {
            throw new SecurityException("Stored path resolves outside data/uploads and was blocked.");
        }
        String extension = extension(realFile.getFileName().toString());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new SecurityException("File extension is not allowed for preview/open: " + extension);
        }
        return realFile;
    }

    private void requireViewPermission(User currentUser) {
        if (!PermissionHelper.canViewMedicalFiles(currentUser)) {
            throw new SecurityException("Only Admin, Doctor, and Nurse users can view medical files.");
        }
    }

    private String summarizeLines(List<String> lines, int maxLines) {
        ArrayList<String> preview = new ArrayList<>();
        for (String line : lines) {
            if (line != null && !line.isBlank()) {
                preview.add(line);
            }
            if (preview.size() >= maxLines) {
                break;
            }
        }
        return preview.isEmpty() ? "No readable text found." : String.join(System.lineSeparator(), preview);
    }

    private String csvPreview(List<String> lines) {
        if (lines.isEmpty()) {
            return "CSV file is empty.";
        }
        ArrayList<String> preview = new ArrayList<>();
        preview.add("Headers: " + lines.get(0));
        for (int i = 1; i < lines.size() && i <= 12; i++) {
            if (lines.get(i) != null && !lines.get(i).isBlank()) {
                preview.add("Row " + i + ": " + lines.get(i));
            }
        }
        return String.join(System.lineSeparator(), preview);
    }

    private String pdfPreview(File file) throws IOException {
        try (PDDocument document = PDDocument.load(file)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setEndPage(Math.min(3, document.getNumberOfPages()));
            String text = stripper.getText(document);
            String compact = text == null ? "" : text.replaceAll("\\s+", " ").trim();
            if (compact.isBlank()) {
                return "No selectable PDF text found. Scanned PDF/OCR is not supported yet.";
            }
            return compact.substring(0, Math.min(3000, compact.length()));
        }
    }

    private String extension(String name) {
        int dot = name == null ? -1 : name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String username(User currentUser) {
        return currentUser == null || currentUser.getUsername() == null || currentUser.getUsername().isBlank()
                ? "Unknown"
                : currentUser.getUsername();
    }

    public static class PreviewResult {
        private final SqliteMedicalFileDao.MedicalFileRecord file;
        private final String previewType;
        private final String previewText;
        private final String safePath;

        public PreviewResult(SqliteMedicalFileDao.MedicalFileRecord file, String previewType, String previewText, String safePath) {
            this.file = file;
            this.previewType = previewType;
            this.previewText = previewText;
            this.safePath = safePath;
        }

        public SqliteMedicalFileDao.MedicalFileRecord getFile() { return file; }
        public String getPreviewType() { return previewType; }
        public String getPreviewText() { return previewText; }
        public String getSafePath() { return safePath; }
        public boolean isImage() { return "IMAGE".equalsIgnoreCase(previewType); }
    }
}
