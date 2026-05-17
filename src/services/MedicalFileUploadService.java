package services;

import dao.SqliteAiNoteDao;
import dao.SqliteMedicalFileDao;
import dao.SqlitePatientDao;
import models.MedicalFile;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import ui.javafx.helpers.AuditAction;
import ui.javafx.helpers.AuditWriteHelper;
import ui.javafx.helpers.FormValidationHelper;
import ui.javafx.helpers.PermissionHelper;
import users.User;

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

public class MedicalFileUploadService {

    private static final long MAX_FILE_SIZE_BYTES = 10L * 1024L * 1024L;
    private static final DateTimeFormatter LEGACY_DATE_TIME = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("txt", "csv", "pdf", "png", "jpg", "jpeg");
    private static final Set<String> CATEGORIES = Set.of("LAB_RESULT", "DISCHARGE_SUMMARY", "IMAGING", "PRESCRIPTION", "OTHER");

    private final SqliteMedicalFileDao medicalFileDao;
    private final SqlitePatientDao patientDao;
    private final SqliteAiNoteDao aiNoteDao;

    public MedicalFileUploadService() {
        this(new SqliteMedicalFileDao(), new SqlitePatientDao(), new SqliteAiNoteDao());
    }

    public MedicalFileUploadService(SqliteMedicalFileDao medicalFileDao, SqlitePatientDao patientDao, SqliteAiNoteDao aiNoteDao) {
        this.medicalFileDao = medicalFileDao;
        this.patientDao = patientDao;
        this.aiNoteDao = aiNoteDao;
    }

    public UploadResult uploadMedicalFile(User currentUser, UploadRequest request) throws IOException, SQLException {
        if (!PermissionHelper.canUploadMedicalFile(currentUser)) {
            throw new SecurityException("Only Admin, Doctor, and Nurse users can upload medical files.");
        }
        validateRequest(request);

        File sourceFile = new File(request.filePath);
        String extension = extension(sourceFile.getName());
        long fileSize = sourceFile.length();
        String uploadedAt = LocalDateTime.now().format(LEGACY_DATE_TIME);
        if (medicalFileDao.hasRecentDuplicate(request.patientId.trim(), sourceFile.getName(), fileSize, uploadedAt)) {
            throw new IllegalArgumentException("A file with the same patient, filename, and size was already uploaded today.");
        }

        String fileId = UUID.randomUUID().toString();
        Path destination = destinationPath(request.patientId.trim(), sourceFile.getName(), fileId);
        Files.createDirectories(destination.getParent());
        Files.copy(sourceFile.toPath(), destination, StandardCopyOption.COPY_ATTRIBUTES);

        String summary = extractSummary(destination.toFile(), extension, request.category);
        MedicalFile medicalFile = new MedicalFile(
                fileId,
                request.patientId.trim(),
                sourceFile.getName(),
                destination.toString(),
                normalizeCategory(request.category),
                username(currentUser),
                uploadedAt
        );
        medicalFileDao.insertUploadedFile(new SqliteMedicalFileDao.MedicalFileRecord(
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
        AuditWriteHelper.write(username(currentUser), AuditAction.UPLOAD_MEDICAL_FILE,
                "patient_id=" + request.patientId + ", file_id=" + fileId + ", name=" + sourceFile.getName());
        return new UploadResult(fileId, destination.toString(), summary);
    }

    public void generateAiSummaryNote(User currentUser, String fileId) throws SQLException {
        if (!PermissionHelper.canViewMedicalFiles(currentUser)) {
            throw new SecurityException("Only Admin, Doctor, and Nurse users can generate file summary notes.");
        }
        SqliteMedicalFileDao.MedicalFileRecord file = medicalFileDao.findByFileId(fileId)
                .orElseThrow(() -> new IllegalArgumentException("Medical file not found in SQLite: " + fileId));
        if (file.getExtractedSummary() == null || file.getExtractedSummary().isBlank()) {
            throw new IllegalArgumentException("No extracted summary is available for this file.");
        }
        String createdAt = LocalDateTime.now().format(LEGACY_DATE_TIME);
        String note = "Rule-based file summary only. Not a medical diagnosis. "
                + file.getOriginalName() + ": " + file.getExtractedSummary();
        aiNoteDao.saveLegacyNote(file.getPatientId(), "File Summary: " + file.getOriginalName(), note, createdAt, 0);
        AuditWriteHelper.write(username(currentUser), AuditAction.GENERATE_FILE_AI_SUMMARY,
                "patient_id=" + file.getPatientId() + ", file_id=" + fileId);
    }

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

    private Path destinationPath(String patientId, String originalName, String fileId) {
        String safeName = safeFilename(originalName);
        String stamp = LocalDateTime.now().format(FILE_STAMP);
        return Path.of("data", "uploads", patientId, stamp + "_" + fileId.substring(0, 8) + "_" + safeName);
    }

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

    private String safeFilename(String name) {
        String safe = name == null ? "upload" : name.replaceAll("[^A-Za-z0-9._-]", "_");
        return safe.isBlank() ? "upload" : safe;
    }

    private String extension(String name) {
        int dot = name == null ? -1 : name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String normalizeCategory(String category) {
        return category == null || category.isBlank() ? "OTHER" : category.trim().toUpperCase();
    }

    private String username(User currentUser) {
        return currentUser == null || currentUser.getUsername() == null || currentUser.getUsername().isBlank()
                ? "Unknown"
                : currentUser.getUsername();
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    public static class UploadRequest {
        private final String patientId;
        private final String filePath;
        private final String category;
        private final String notes;

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

        public UploadResult(String fileId, String storedPath, String extractedSummary) {
            this.fileId = fileId;
            this.storedPath = storedPath;
            this.extractedSummary = extractedSummary;
        }

        public String getFileId() { return fileId; }
        public String getStoredPath() { return storedPath; }
        public String getExtractedSummary() { return extractedSummary; }
    }
}
