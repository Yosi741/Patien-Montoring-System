package ui.javafx.services;

import Data_Access_Object.SqliteDeceasedRecordDao;
import Data_Access_Object.SqliteMessageDao;
import Data_Access_Object.SqliteNewbornRecordDao;
import Data_Access_Object.SqliteNotificationDao;
import ui.javafx.helpers.AuditAction;
import ui.javafx.helpers.AuditWriteHelper;
import ui.javafx.helpers.PermissionHelper;
import users.User;

import java.sql.SQLException;

public class CertificateEventService {

    private final NotificationCenterService notificationService;
    private final MessagingService messagingService;

    public CertificateEventService() {
        this(new NotificationCenterService(), new MessagingService());
    }

    public CertificateEventService(NotificationCenterService notificationService, MessagingService messagingService) {
        this.notificationService = notificationService;
        this.messagingService = messagingService;
    }

    public void notifyDeathCertificateGenerated(User actor, SqliteDeceasedRecordDao.DeathRecord record) {
        if (record == null) {
            return;
        }
        notifyRole(actor, "ADMIN", record.getPatientId(), "Death certificate generated",
                deathSummary(record), "DEATH_CERTIFICATE", String.valueOf(record.getId()));
        notifyRole(actor, "DOCTOR", record.getPatientId(), "Death certificate generated",
                deathSummary(record), "DEATH_CERTIFICATE", String.valueOf(record.getId()));
    }

    public void notifyBirthCertificateGenerated(User actor, SqliteNewbornRecordDao.NewbornRecord record) {
        if (record == null) {
            return;
        }
        String sourceId = String.valueOf(record.getId());
        notifyRole(actor, "ADMIN", record.getMotherPatientId(), "Birth certificate generated",
                birthSummary(record), "BIRTH_CERTIFICATE", sourceId);
        notifyRole(actor, "DOCTOR", record.getMotherPatientId(), "Birth certificate generated",
                birthSummary(record), "BIRTH_CERTIFICATE", sourceId);
        notifyRole(actor, "NURSE", record.getMotherPatientId(), "Birth certificate generated",
                birthSummary(record), "BIRTH_CERTIFICATE", sourceId);
    }

    public long sendDeathCertificateNotice(User actor, SqliteDeceasedRecordDao.DeathRecord record) throws SQLException {
        if (!PermissionHelper.canSendDeathCertificateNotice(actor)) {
            throw new SecurityException("Only Admin and Doctor users can send death certificate notices.");
        }
        long id = messagingService.sendMessage(actor, new SqliteMessageDao.MessageWriteRecord(
                username(actor),
                "",
                "DOCTOR",
                "",
                record.getPatientId(),
                "Death certificate notice: " + record.getPatientId(),
                deathSummary(record) + "\n\n" + metadataBlock("DEATH", "DEATH_CERTIFICATE",
                String.valueOf(record.getId()), record.getPatientId(), "", record.getCertificatePath()),
                "HIGH"
        ));
        AuditWriteHelper.write(username(actor), AuditAction.SEND_DEATH_CERTIFICATE_NOTICE,
                "message_id=" + id + ", patient_id=" + record.getPatientId());
        return id;
    }

    public long sendBirthCertificateNotice(User actor, SqliteNewbornRecordDao.NewbornRecord record) throws SQLException {
        if (!PermissionHelper.canSendBirthCertificateNotice(actor)) {
            throw new SecurityException("Only Admin, Doctor, and Nurse users can send birth certificate notices.");
        }
        long id = messagingService.sendMessage(actor, new SqliteMessageDao.MessageWriteRecord(
                username(actor),
                "",
                "NURSE",
                "",
                blank(record.getMotherPatientId()),
                "Birth certificate notice: " + record.getNewbornId(),
                birthSummary(record) + "\n\n" + metadataBlock("BIRTH", "BIRTH_CERTIFICATE",
                String.valueOf(record.getId()), safe(record.getMotherPatientId()), record.getNewbornId(), record.getCertificatePath()),
                "NORMAL"
        ));
        AuditWriteHelper.write(username(actor), AuditAction.SEND_BIRTH_CERTIFICATE_NOTICE,
                "message_id=" + id + ", newborn_id=" + record.getNewbornId());
        return id;
    }

    public String deathSummary(SqliteDeceasedRecordDao.DeathRecord record) {
        return "Patient ID: " + record.getPatientId()
                + "\nPatient: " + record.getPatientName()
                + "\nDeath time: " + safe(record.getDeathTime())
                + "\nPronounced by: " + safe(record.getPronouncedBy())
                + "\nCause: " + safe(record.getCauseOfDeath())
                + "\nCertificate: " + certificateStatus(record.getCertificatePath())
                + "\nPath: " + safe(record.getCertificatePath());
    }

    public String birthSummary(SqliteNewbornRecordDao.NewbornRecord record) {
        return "Newborn ID: " + record.getNewbornId()
                + "\nBaby: " + safe(record.getBabyName())
                + "\nMother: " + safe(record.getMotherDisplay())
                + "\nBirth time: " + safe(record.getBirthTime())
                + "\nGender: " + safe(record.getGender())
                + "\nWeight: " + record.getBirthWeight() + " kg"
                + "\nCertificate: " + certificateStatus(record.getCertificatePath())
                + "\nPath: " + safe(record.getCertificatePath());
    }

    private void notifyRole(User actor, String role, String patientId, String title, String message,
                            String sourceType, String sourceId) {
        try {
            long id = notificationService.createNotification(new SqliteNotificationDao.NotificationWriteRecord(
                    "",
                    role,
                    "",
                    safe(patientId),
                    "INFO",
                    title,
                    message,
                    sourceType,
                    sourceId
            ));
            AuditWriteHelper.write(username(actor), AuditAction.CERTIFICATE_NOTIFICATION_CREATED,
                    "notification_id=" + id + ", role=" + role + ", source=" + sourceType + ":" + sourceId);
        } catch (Exception e) {
            System.out.println("SQLite certificate notification skipped: " + e.getMessage());
        }
    }

    private String certificateStatus(String path) {
        return path == null || path.isBlank() ? "Pending" : "Generated";
    }

    private String metadataBlock(String certificateType, String sourceType, String sourceId,
                                 String patientId, String newbornId, String certificatePath) {
        return "[SPMS_CERTIFICATE]\n"
                + "certificate_type=" + certificateType + "\n"
                + "source_type=" + sourceType + "\n"
                + "source_id=" + safe(sourceId) + "\n"
                + "patient_id=" + safe(patientId) + "\n"
                + "newborn_id=" + safe(newbornId) + "\n"
                + "certificate_path=" + safe(certificatePath) + "\n"
                + "[/SPMS_CERTIFICATE]";
    }

    private String username(User user) {
        return user == null || user.getUsername() == null || user.getUsername().isBlank() ? "Unknown" : user.getUsername();
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "-" : value.trim();
    }

    private String blank(String value) {
        return value == null ? "" : value.trim();
    }
}
