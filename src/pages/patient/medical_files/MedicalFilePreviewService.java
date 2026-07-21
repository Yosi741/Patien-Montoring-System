package pages.patient.medical_files;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import app.helpers.FxFileOpenHelper;
import app.helpers.PermissionHelper;
import pages.user.User;

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

/**
 * Builds safe text, PDF, and image previews for authorized local medical files.
 */
public class MedicalFilePreviewService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("txt", "csv", "pdf", "png", "jpg", "jpeg");
    private final SqliteMedicalFileDao medicalFileDao;

    /**
     * Creates the service with the dependencies used by the patient workflow.
     */
    public MedicalFilePreviewService() {
        this(new SqliteMedicalFileDao());
    }

    /**
     * Creates the service with the dependencies used by the patient workflow.
     */
    public MedicalFilePreviewService(SqliteMedicalFileDao medicalFileDao) {
        this.medicalFileDao = medicalFileDao;
    }

    /**
     * Loads preview for the patient workflow.
     */
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

    /**
     * Opens file for the selected record.
     */
    public String openFile(User currentUser, String fileId) throws SQLException, IOException {
        requireViewPermission(currentUser);
        SqliteMedicalFileDao.MedicalFileRecord file = loadFile(fileId);
        Path path = validateStoredPath(file);
        String result = FxFileOpenHelper.open(path);
        return result;
    }

    /**
     * Loads file for the patient workflow.
     */
    public SqliteMedicalFileDao.MedicalFileRecord loadFile(String fileId) throws SQLException {
        if (fileId == null || fileId.isBlank()) {
            throw new IllegalArgumentException("File ID is required.");
        }
        return medicalFileDao.findByFileId(fileId)
                .orElseThrow(() -> new IllegalArgumentException("Medical file not found in SQLite: " + fileId));
    }

    /**
     * Validates stored path against the active business rules.
     */
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

    /**
     * Enforces view permission before the protected operation continues.
     */
    private void requireViewPermission(User currentUser) {
        if (!PermissionHelper.canViewMedicalFiles(currentUser)) {
            throw new SecurityException("Only Admin, Doctor, and Nurse users can view medical records.");
        }
    }

    /**
     * Summarizes lines into concise display text.
     */
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

    /**
     * Builds a safe text preview from the selected CSV medical file.
     */
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

    /**
     * Extracts a safe text preview from the selected PDF medical file.
     */
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

    /**
     * Returns the normalized file extension for the supplied path.
     */
    private String extension(String name) {
        int dot = name == null ? -1 : name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    public static class PreviewResult {
        private final SqliteMedicalFileDao.MedicalFileRecord file;
        private final String previewType;
        private final String previewText;
        private final String safePath;

        /**
         * Creates a preview result from the supplied record values.
         */
        public PreviewResult(SqliteMedicalFileDao.MedicalFileRecord file, String previewType, String previewText, String safePath) {
            this.file = file;
            this.previewType = previewType;
            this.previewText = previewText;
            this.safePath = safePath;
        }

        public SqliteMedicalFileDao.MedicalFileRecord getFile() { return file; }
        public String getPreviewText() { return previewText; }
        public String getSafePath() { return safePath; }
        public boolean isImage() { return "IMAGE".equalsIgnoreCase(previewType); }
    }
}
