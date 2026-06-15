package ui.javafx.services;

import Data_Access_Object.SqliteDeceasedRecordDao;
import Data_Access_Object.SqliteNewbornRecordDao;
import Data_Access_Object.SqliteNotificationDao;
import ui.javafx.helpers.AuditAction;
import ui.javafx.helpers.AuditWriteHelper;
import ui.javafx.helpers.FormValidationHelper;
import ui.javafx.helpers.PermissionHelper;
import users.User;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public class CertificateReviewService {

    private static final DateTimeFormatter SQLITE_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final List<String> REVIEW_STATUSES = List.of("DRAFT", "PENDING_REVIEW", "APPROVED", "REJECTED");

    private final SqliteDeceasedRecordDao deceasedRecordDao;
    private final SqliteNewbornRecordDao newbornRecordDao;
    private final CertificateRegistryService registryService;
    private final NotificationCenterService notificationService;

    public CertificateReviewService() {
        this(new SqliteDeceasedRecordDao(), new SqliteNewbornRecordDao(),
                new CertificateRegistryService(), new NotificationCenterService());
    }

    public CertificateReviewService(SqliteDeceasedRecordDao deceasedRecordDao, SqliteNewbornRecordDao newbornRecordDao,
                                    CertificateRegistryService registryService,
                                    NotificationCenterService notificationService) {
        this.deceasedRecordDao = deceasedRecordDao;
        this.newbornRecordDao = newbornRecordDao;
        this.registryService = registryService;
        this.notificationService = notificationService;
    }

    public void submitForReview(User user, String certificateType, long sourceRecordId) throws SQLException {
        require(PermissionHelper.canSubmitCertificateReview(user), "Only Admin and Doctor users can submit certificates for review.");
        CertificateRegistryService.CertificateRow row = registryService.getCertificate(certificateType, sourceRecordId);
        updateReview(row, "PENDING_REVIEW", "", "", "");
        notifyRole("ADMIN", row, "Certificate submitted for review",
                row.getCertificateType() + " certificate for " + row.getPersonName() + " is awaiting review.");
        notifyRole("DOCTOR", row, "Certificate submitted for review",
                row.getCertificateType() + " certificate for " + row.getPersonName() + " is awaiting review.");
        AuditWriteHelper.write(username(user), AuditAction.SUBMIT_CERTIFICATE_REVIEW, detail(row));
    }

    public void approveCertificate(User user, String certificateType, long sourceRecordId) throws SQLException {
        require(PermissionHelper.canApproveCertificateReview(user), "Only authorized Admin/Doctor users can approve certificates.");
        CertificateRegistryService.CertificateRow row = registryService.getCertificate(certificateType, sourceRecordId);
        require("GENERATED".equals(row.getCertificateStatus()), "Certificate must be generated before approval.");
        require("PENDING_REVIEW".equals(row.getReviewStatus()), "Certificate must be PENDING_REVIEW before approval.");
        updateReview(row, "APPROVED", username(user), now(), "");
        notifyCompletion(row, "Certificate approved",
                row.getCertificateType() + " certificate for " + row.getPersonName() + " was approved by " + username(user) + ".");
        AuditWriteHelper.write(username(user), AuditAction.APPROVE_CERTIFICATE, detail(row));
    }

    public void rejectCertificate(User user, String certificateType, long sourceRecordId, String reason) throws SQLException {
        require(PermissionHelper.canApproveCertificateReview(user), "Only authorized Admin/Doctor users can reject certificates.");
        CertificateRegistryService.CertificateRow row = registryService.getCertificate(certificateType, sourceRecordId);
        require("PENDING_REVIEW".equals(row.getReviewStatus()), "Certificate must be PENDING_REVIEW before rejection.");
        FormValidationHelper.ValidationResult validation = FormValidationHelper.combine(
                FormValidationHelper.validateRequired("Rejection reason", reason),
                FormValidationHelper.validateMaxLength("Rejection reason", reason, 300)
        );
        require(validation.isValid(), validation.getMessage());
        updateReview(row, "REJECTED", username(user), now(), reason);
        notifyCompletion(row, "Certificate rejected",
                row.getCertificateType() + " certificate for " + row.getPersonName() + " was rejected: " + reason);
        AuditWriteHelper.write(username(user), AuditAction.REJECT_CERTIFICATE, detail(row) + ", reason=" + truncate(reason));
    }

    public void resetToDraft(User user, String certificateType, long sourceRecordId) throws SQLException {
        require(PermissionHelper.canSubmitCertificateReview(user), "Only Admin and Doctor users can reset review status.");
        CertificateRegistryService.CertificateRow row = registryService.getCertificate(certificateType, sourceRecordId);
        updateReview(row, "DRAFT", "", "", "");
        AuditWriteHelper.write(username(user), AuditAction.RESET_CERTIFICATE_DRAFT, detail(row));
    }

    public List<CertificateRegistryService.CertificateRow> queryReviewQueue(CertificateRegistryService.CertificateFilter filter) throws SQLException {
        return registryService.findCertificates(filter);
    }

    private void updateReview(CertificateRegistryService.CertificateRow row, String status, String reviewedBy,
                              String reviewedAt, String rejectionReason) throws SQLException {
        String cleanStatus = normalizeStatus(status);
        if ("DEATH".equals(row.getCertificateType())) {
            deceasedRecordDao.updateReviewStatus(row.getSourceRecordId(), cleanStatus, reviewedBy, reviewedAt, rejectionReason);
        } else {
            newbornRecordDao.updateReviewStatus(row.getSourceRecordId(), cleanStatus, reviewedBy, reviewedAt, rejectionReason);
        }
    }

    private void notifyCompletion(CertificateRegistryService.CertificateRow row, String title, String message) {
        String targetRole = "DEATH".equals(row.getCertificateType()) ? "DOCTOR" : "NURSE";
        notifyRole(targetRole, row, title, message);
    }

    private void notifyRole(String role, CertificateRegistryService.CertificateRow row, String title, String message) {
        try {
            notificationService.createNotification(new SqliteNotificationDao.NotificationWriteRecord(
                    "",
                    role,
                    "",
                    "DEATH".equals(row.getCertificateType()) ? row.getSubjectId() : "",
                    "INFO",
                    title,
                    message,
                    row.getSourceType(),
                    String.valueOf(row.getSourceRecordId())
            ));
        } catch (Exception e) {
            System.out.println("SQLite certificate review notification skipped: " + e.getMessage());
        }
    }

    private String normalizeStatus(String status) {
        String value = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if (!REVIEW_STATUSES.contains(value)) {
            throw new IllegalArgumentException("Review status must be DRAFT, PENDING_REVIEW, APPROVED, or REJECTED.");
        }
        return value;
    }

    private String now() {
        return LocalDateTime.now().format(SQLITE_DATE_TIME);
    }

    private String detail(CertificateRegistryService.CertificateRow row) {
        return "type=" + row.getCertificateType() + ", source_id=" + row.getSourceRecordId()
                + ", subject_id=" + row.getSubjectId();
    }

    private String username(User user) {
        return user == null || user.getUsername() == null || user.getUsername().isBlank() ? "Unknown" : user.getUsername();
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new SecurityException(message);
        }
    }

    private String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() > 80 ? value.substring(0, 80) : value;
    }
}
