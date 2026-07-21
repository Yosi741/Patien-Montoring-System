package pages.patient.medical_files;

import pages.patient.Add_Edit_Patient_Dao;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import app.helpers.FormValidationHelper;
import app.helpers.PermissionHelper;
import pages.patient.medical_files.Upload.MedicalFile;
import pages.user.User;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Validates upload permissions, patient identity, and file metadata before storing local medical files.
 */
public class MedicalFileUploadService {

    private static final long MAX_FILE_SIZE_BYTES = 10L * 1024L * 1024L;
    private static final DateTimeFormatter DISPLAY_DATE_TIME = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("txt", "csv", "pdf", "png", "jpg", "jpeg");
    private static final Set<String> CATEGORIES = Set.of("LAB_RESULT", "DISCHARGE_SUMMARY", "IMAGING", "PRESCRIPTION", "OTHER");

    private final SqliteMedicalFileDao medicalFileDao;
    private final Add_Edit_Patient_Dao patientDao;

    /**
     * Creates the service with the dependencies used by the patient workflow.
     */
    public MedicalFileUploadService() {
        this(new SqliteMedicalFileDao(), new Add_Edit_Patient_Dao());
    }

    /**
     * Creates the service with the dependencies used by the patient workflow.
     */
    public MedicalFileUploadService(SqliteMedicalFileDao medicalFileDao, Add_Edit_Patient_Dao patientDao) {
        this.medicalFileDao = medicalFileDao;
        this.patientDao = patientDao;
    }

    /**
     * Uploads medical file and stores its metadata in SQLite.
     */
    public UploadResult uploadMedicalFile(User currentUser, UploadRequest request) throws IOException, SQLException {
        if (!PermissionHelper.canUploadMedicalFile(currentUser)) {
            throw new SecurityException("Only Admin, Doctor, Nurse, and Secretary users can upload medical records.");
        }
        validateRequest(request);

        File sourceFile = new File(request.filePath);
        String extension = extension(sourceFile.getName());
        long fileSize = sourceFile.length();
        String uploadedAt = LocalDateTime.now().format(DISPLAY_DATE_TIME);
        if (medicalFileDao.hasRecentDuplicate(request.patientId.trim(), sourceFile.getName(), fileSize, uploadedAt)) {
            throw new IllegalArgumentException("A file with the same patient, filename, and size was already uploaded today.");
        }

        String fileId = UUID.randomUUID().toString();
        Path destination = destinationPath(request.patientId.trim(), sourceFile.getName(), fileId);
        Files.createDirectories(destination.getParent());
        Files.copy(sourceFile.toPath(), destination, StandardCopyOption.COPY_ATTRIBUTES);

        String summary = extractSummary(destination.toFile(), extension, request.category);
        try {
            MedicalFile medicalFile = new MedicalFile(
                    fileId,
                    request.patientId.trim(),
                    sourceFile.getName(),
                    destination.toString(),
                    normalizeCategory(request.category),
                    username(currentUser),
                    uploadedAt
            );
            boolean inserted = medicalFileDao.insertUploadedFile(new SqliteMedicalFileDao.MedicalFileRecord(
                    0,
                    fileId,
                    medicalFile.getPatientId(),
                    "",
                    medicalFile.getOriginalName(),
                    medicalFile.getStoredPath(),
                    medicalFile.getFileType(),
                    medicalFile.getUploadedBy(),
                    medicalFile.getUploadedAt(),
                    summary,
                    fileSize,
                    trim(request.notes)
            ));
            if (!inserted) {
                throw new SQLException("SQLite did not insert the medical file record.");
            }
        } catch (SQLException e) {
            cleanupCopiedFile(destination);
            throw new SQLException("File copy completed, but SQLite record insert failed. The copied file was removed to prevent an orphan upload. " + e.getMessage(), e);
        }
        return new UploadResult(fileId, destination.toString(), summary);
    }

    /**
     * Validates request against the active business rules.
     */
    private void validateRequest(UploadRequest request) throws SQLException {
        FormValidationHelper.ValidationResult validation = FormValidationHelper.combine(
                FormValidationHelper.validatePatientId(request.patientId),
                FormValidationHelper.validateRequired("File path", request.filePath),
                FormValidationHelper.validateRequired("File type/category", request.category),
                FormValidationHelper.validateMaxLength("Notes", request.notes, 500)
        );
        if (!validation.isValid()) {
            throw new IllegalArgumentException(validation.getMessage());
        }
        if (!patientDao.existsByPatientId(request.patientId.trim())) {
            throw new IllegalArgumentException("Patient does not exist in SQLite: " + request.patientId);
        }
        File file = new File(request.filePath);
        if (!file.exists() || !file.isFile()) {
            throw new IllegalArgumentException("Selected file does not exist.");
        }
        if (file.length() <= 0) {
            throw new IllegalArgumentException("Selected file is empty.");
        }
        if (file.length() > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("Selected file must be 10 MB or smaller.");
        }
        String extension = extension(file.getName());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("Allowed file extensions: txt, csv, pdf, png, jpg, jpeg.");
        }
        if (!CATEGORIES.contains(normalizeCategory(request.category))) {
            throw new IllegalArgumentException("File category must be LAB_RESULT, DISCHARGE_SUMMARY, IMAGING, PRESCRIPTION, or OTHER.");
        }
    }

    /**
     * Builds a collision-safe upload destination for the selected medical file.
     */
    private Path destinationPath(String patientId, String originalName, String fileId) {
        String safeName = safeFilename(originalName);
        String stamp = LocalDateTime.now().format(FILE_STAMP);
        return Path.of("data", "uploads", patientId, stamp + "_" + fileId.substring(0, 8) + "_" + safeName);
    }

    /**
     * Cleans up copied file after a failed or cancelled operation.
     */
    private void cleanupCopiedFile(Path destination) {
        try {
            Files.deleteIfExists(destination);
        } catch (IOException cleanupError) {
            System.out.println("Orphan upload cleanup needed for " + destination + ": " + cleanupError.getMessage());
        }
    }

    /**
     * Extracts summary from the selected file.
     */
    private String extractSummary(File file, String extension, String category) {
        try {
            switch (extension) {
                case "txt":
                    return summarizeText(Files.readAllLines(file.toPath(), StandardCharsets.UTF_8));
                case "csv":
                    return summarizeCsv(Files.readAllLines(file.toPath(), StandardCharsets.UTF_8));
                case "pdf":
                    return summarizePdf(file);
                case "png":
                case "jpg":
                case "jpeg":
                    return "Image file attached as " + normalizeCategory(category) + ". OCR is not implemented yet.";
                default:
                    return "";
            }
        } catch (Exception e) {
            return "Summary extraction failed: " + e.getMessage();
        }
    }

    /**
     * Summarizes text into concise display text.
     */
    private String summarizeText(List<String> lines) {
        ArrayList<String> important = new ArrayList<>();
        for (String line : lines) {
            String trimmed = line == null ? "" : line.trim();
            if (trimmed.isBlank()) {
                continue;
            }
            important.add(trimmed);
            if (important.size() >= 5) {
                break;
            }
        }
        return important.isEmpty() ? "Text file uploaded; no readable non-empty lines found." : "TXT summary: " + String.join(" | ", important);
    }

    /**
     * Summarizes csv into concise display text.
     */
    private String summarizeCsv(List<String> lines) {
        if (lines.isEmpty()) {
            return "CSV file uploaded; no rows found.";
        }
        String headers = lines.get(0).trim();
        ArrayList<String> samples = new ArrayList<>();
        for (int i = 1; i < lines.size() && samples.size() < 3; i++) {
            if (lines.get(i) != null && !lines.get(i).isBlank()) {
                samples.add(lines.get(i).trim());
            }
        }
        String lowerHeaders = headers.toLowerCase(Locale.ROOT);
        String detection = lowerHeaders.contains("glucose") || lowerHeaders.contains("oxygen") || lowerHeaders.contains("heart")
                || lowerHeaders.contains("blood") || lowerHeaders.contains("temperature")
                ? "Possible lab/vital values detected from headers."
                : "No obvious lab/vital headers detected.";
        return "CSV headers: " + headers + ". " + detection
                + (samples.isEmpty() ? "" : " Sample rows: " + String.join(" | ", samples));
    }

    /**
     * Summarizes pdf into concise display text.
     */
    private String summarizePdf(File file) throws IOException {
        try (PDDocument document = PDDocument.load(file)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setEndPage(Math.min(2, document.getNumberOfPages()));
            String text = stripper.getText(document);
            String compact = text == null ? "" : text.replaceAll("\\s+", " ").trim();
            if (compact.isBlank()) {
                return "PDF uploaded; no extractable text found. Scanned PDF/OCR is not supported yet.";
            }
            return "PDF text extract: " + compact.substring(0, Math.min(900, compact.length()));
        }
    }

    /**
     * Returns a safe display or filesystem value for filename.
     */
    private String safeFilename(String name) {
        String safe = name == null ? "upload" : name.replaceAll("[^A-Za-z0-9._-]", "_");
        return safe.isBlank() ? "upload" : safe;
    }

    /**
     * Returns the normalized file extension for the supplied path.
     */
    private String extension(String name) {
        int dot = name == null ? -1 : name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * Normalizes category to the stored application format.
     */
    private String normalizeCategory(String category) {
        return category == null || category.isBlank() ? "OTHER" : category.trim().toUpperCase();
    }

    /**
     * Returns the username associated with the current session or workflow record.
     */
    private String username(User currentUser) {
        return currentUser == null || currentUser.getUsername() == null || currentUser.getUsername().isBlank()
                ? "Unknown"
                : currentUser.getUsername();
    }

    /**
     * Trims trim while preserving null handling.
     */
    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    public static class UploadRequest {
        private final String patientId;
        private final String filePath;
        private final String category;
        private final String notes;

        /**
         * Creates a upload request from the supplied record values.
         */
        public UploadRequest(String patientId, String filePath, String category, String notes) {
            this.patientId = patientId;
            this.filePath = filePath;
            this.category = category;
            this.notes = notes;
        }
    }

    public static class UploadResult {
        private final String fileId;
        private final String storedPath;
        private final String extractedSummary;

        /**
         * Creates a upload result from the supplied record values.
         */
        public UploadResult(String fileId, String storedPath, String extractedSummary) {
            this.fileId = fileId;
            this.storedPath = storedPath;
            this.extractedSummary = extractedSummary;
        }

        public String getFileId() { return fileId; }
        public String getExtractedSummary() { return extractedSummary; }
    }
}
