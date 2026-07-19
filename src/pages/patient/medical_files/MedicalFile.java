package pages.patient.medical_files;

public class MedicalFile {

    private String fileId;
    private String patientId;
    private String originalName;
    private String storedPath;
    private String fileType;
    private String uploadedBy;
    private String uploadedAt;

    public MedicalFile(String fileId, String patientId, String originalName,
                       String storedPath, String fileType, String uploadedBy,
                       String uploadedAt) {
        this.fileId = fileId;
        this.patientId = patientId;
        this.originalName = originalName;
        this.storedPath = storedPath;
        this.fileType = fileType;
        this.uploadedBy = uploadedBy;
        this.uploadedAt = uploadedAt;
    }

    public String getFileId() {
        return fileId;
    }

    public String getPatientId() {
        return patientId;
    }

    public String getOriginalName() {
        return originalName;
    }

    public String getStoredPath() {
        return storedPath;
    }

    public String getFileType() {
        return fileType;
    }

    public String getUploadedBy() {
        return uploadedBy;
    }

    public String getUploadedAt() {
        return uploadedAt;
    }
}
