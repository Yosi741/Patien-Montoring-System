package ui.javafx.pages.scheduling;

import ui.javafx.pages.medications.SqliteMedicationDao;
import pages.patient.dao.SqlitePatientDao;
import ui.javafx.pages.audit_logs.AuditAction;
import ui.javafx.pages.audit_logs.AuditWriteHelper;
import app.helpers.FormValidationHelper;
import app.helpers.PermissionHelper;
import pages.user.User;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;

public class SchedulingService {

    private static final DateTimeFormatter DISPLAY_DATE_TIME = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
    private static final Set<String> APPOINTMENT_TYPES = Set.of("CHECKUP", "SURGERY", "FOLLOW_UP", "LAB_TEST", "MEDICATION_REVIEW", "OTHER");
    private static final Set<String> APPOINTMENT_STATUSES = Set.of("SCHEDULED", "COMPLETED", "CANCELLED", "MISSED");
    private static final Set<String> REMINDER_TYPES = Set.of("MEDICATION", "APPOINTMENT", "CHECKUP", "CUSTOM");
    private static final Set<String> REMINDER_STATUSES = Set.of("PENDING", "OVERDUE", "DONE", "MISSED", "CANCELLED");

    private final SqliteAppointmentDao appointmentDao;
    private final SqliteReminderDao reminderDao;
    private final SqlitePatientDao patientDao;
    private final SqliteMedicationDao medicationDao;

    public SchedulingService() {
        this(new SqliteAppointmentDao(), new SqliteReminderDao(), new SqlitePatientDao(), new SqliteMedicationDao());
    }

    public SchedulingService(SqliteAppointmentDao appointmentDao, SqliteReminderDao reminderDao,
                             SqlitePatientDao patientDao, SqliteMedicationDao medicationDao) {
        this.appointmentDao = appointmentDao;
        this.reminderDao = reminderDao;
        this.patientDao = patientDao;
        this.medicationDao = medicationDao;
    }

    public long createAppointment(User currentUser, AppointmentRequest request) throws SQLException {
        requireAppointmentPermission(currentUser);
        validateAppointment(request, false);
        SqliteAppointmentDao.AppointmentRecord record = cleanAppointment(request, 0, username(currentUser));
        long id = appointmentDao.insertAppointment(record);
        AuditWriteHelper.write(username(currentUser), AuditAction.CREATE_APPOINTMENT,
                "patient_id=" + record.getPatientId() + ", title=" + record.getTitle() + ", start=" + record.getStartTime());
        return id;
    }

    public void updateAppointment(User currentUser, AppointmentRequest request) throws SQLException {
        requireAppointmentPermission(currentUser);
        if (request.id <= 0) {
            throw new IllegalArgumentException("Appointment ID is required for update.");
        }
        validateAppointment(request, true);
        appointmentDao.updateAppointment(cleanAppointment(request, request.id, username(currentUser)));
        AuditWriteHelper.write(username(currentUser), AuditAction.UPDATE_APPOINTMENT,
                "appointment_id=" + request.id + ", patient_id=" + request.patientId + ", title=" + request.title);
    }

    public void cancelAppointment(User currentUser, long appointmentId) throws SQLException {
        requireAppointmentPermission(currentUser);
        SqliteAppointmentDao.AppointmentRecord appointment = appointmentDao.findById(appointmentId)
                .orElseThrow(() -> new IllegalArgumentException("Appointment not found in SQLite: " + appointmentId));
        appointmentDao.updateStatus(appointmentId, "CANCELLED");
        AuditWriteHelper.write(username(currentUser), AuditAction.CANCEL_APPOINTMENT,
                "appointment_id=" + appointmentId + ", patient_id=" + appointment.getPatientId() + ", title=" + appointment.getTitle());
    }

    public void markAppointmentCompleted(User currentUser, long appointmentId) throws SQLException {
        requireAppointmentPermission(currentUser);
        SqliteAppointmentDao.AppointmentRecord appointment = appointmentDao.findById(appointmentId)
                .orElseThrow(() -> new IllegalArgumentException("Appointment not found in SQLite: " + appointmentId));
        if ("CANCELLED".equalsIgnoreCase(appointment.getStatus())) {
            throw new IllegalArgumentException("Cannot complete a cancelled appointment.");
        }
        appointmentDao.updateStatus(appointmentId, "COMPLETED");
        AuditWriteHelper.write(username(currentUser), AuditAction.COMPLETE_APPOINTMENT,
                "appointment_id=" + appointmentId + ", patient_id=" + appointment.getPatientId() + ", title=" + appointment.getTitle());
    }

    public long createReminder(User currentUser, ReminderRequest request) throws SQLException {
        requireReminderPermission(currentUser);
        validateReminder(request, false);
        SqliteReminderDao.ReminderRecord record = cleanReminder(request, 0, username(currentUser));
        long id = reminderDao.insertReminder(record);
        AuditWriteHelper.write(username(currentUser), AuditAction.CREATE_REMINDER,
                "patient_id=" + record.getPatientId() + ", title=" + record.getTitle() + ", due=" + record.getDueTime());
        return id;
    }

    public void updateReminder(User currentUser, ReminderRequest request) throws SQLException {
        requireReminderPermission(currentUser);
        if (request.id <= 0) {
            throw new IllegalArgumentException("Reminder ID is required for update.");
        }
        validateReminder(request, true);
        reminderDao.updateReminder(cleanReminder(request, request.id, username(currentUser)));
        AuditWriteHelper.write(username(currentUser), AuditAction.UPDATE_REMINDER,
                "reminder_id=" + request.id + ", patient_id=" + request.patientId + ", title=" + request.title);
    }

    public void markReminderDone(User currentUser, long reminderId) throws SQLException {
        if (!PermissionHelper.canCompleteReminder(currentUser)) {
            throw new SecurityException("Only Admin, Doctor, and Nurse users can mark reminders done.");
        }
        SqliteReminderDao.ReminderRecord reminder = reminderDao.findById(reminderId)
                .orElseThrow(() -> new IllegalArgumentException("Reminder not found in SQLite: " + reminderId));
        if ("CANCELLED".equalsIgnoreCase(reminder.getStatus())) {
            throw new IllegalArgumentException("Cannot mark a cancelled reminder done.");
        }
        reminderDao.updateStatus(reminderId, "DONE");
        AuditWriteHelper.write(username(currentUser), AuditAction.MARK_REMINDER_DONE,
                "reminder_id=" + reminderId + ", patient_id=" + reminder.getPatientId() + ", title=" + reminder.getTitle());
    }

    public void markReminderMissed(User currentUser, long reminderId) throws SQLException {
        if (!PermissionHelper.canCompleteReminder(currentUser)) {
            throw new SecurityException("Only Admin, Doctor, and Nurse users can mark reminders missed.");
        }
        SqliteReminderDao.ReminderRecord reminder = reminderDao.findById(reminderId)
                .orElseThrow(() -> new IllegalArgumentException("Reminder not found in SQLite: " + reminderId));
        if ("CANCELLED".equalsIgnoreCase(reminder.getStatus()) || "DONE".equalsIgnoreCase(reminder.getStatus())) {
            throw new IllegalArgumentException("Cannot mark a completed or cancelled reminder missed.");
        }
        reminderDao.updateStatus(reminderId, "MISSED");
        AuditWriteHelper.write(username(currentUser), AuditAction.MARK_REMINDER_MISSED,
                "reminder_id=" + reminderId + ", patient_id=" + reminder.getPatientId()
                        + ", type=" + reminder.getReminderType());
    }

    public void cancelReminder(User currentUser, long reminderId) throws SQLException {
        requireReminderPermission(currentUser);
        SqliteReminderDao.ReminderRecord reminder = reminderDao.findById(reminderId)
                .orElseThrow(() -> new IllegalArgumentException("Reminder not found in SQLite: " + reminderId));
        reminderDao.updateStatus(reminderId, "CANCELLED");
        AuditWriteHelper.write(username(currentUser), AuditAction.CANCEL_REMINDER,
                "reminder_id=" + reminderId + ", patient_id=" + reminder.getPatientId() + ", title=" + reminder.getTitle());
    }

    public SchedulingOverview loadOverview(String search, String appointmentType, String appointmentStatus,
                                           String reminderType, String reminderStatus, String patientId) throws SQLException {
        List<SqliteAppointmentDao.AppointmentRow> appointments =
                appointmentDao.findAppointments(search, appointmentType, appointmentStatus, patientId);
        List<SqliteReminderDao.ReminderRow> reminders =
                reminderDao.findReminders(search, reminderType, reminderStatus, patientId);
        return new SchedulingOverview(
                appointmentDao.countToday(),
                appointmentDao.countUpcomingSurgeries(),
                reminderDao.countOverdue(),
                reminderDao.countMedicationToday(),
                appointmentDao.countCancelledOrMissed() + reminderDao.countCancelledOrMissed(),
                appointments,
                reminders
        );
    }

    private void requireAppointmentPermission(User currentUser) {
        if (!PermissionHelper.canManageAppointment(currentUser)) {
            throw new SecurityException("Only Admin and Doctor users can create, edit, cancel, or complete appointments.");
        }
    }

    private void requireReminderPermission(User currentUser) {
        if (!PermissionHelper.canManageReminder(currentUser)) {
            throw new SecurityException("Only Admin, Doctor, and Nurse users can manage reminders.");
        }
    }

    private void validateAppointment(AppointmentRequest request, boolean update) throws SQLException {
        FormValidationHelper.ValidationResult validation = FormValidationHelper.combine(
                FormValidationHelper.validatePatientId(request.patientId),
                FormValidationHelper.validateRequired("Appointment title", request.title),
                FormValidationHelper.validateRequired("Appointment type", request.appointmentType),
                FormValidationHelper.validateRequired("Appointment status", request.status),
                FormValidationHelper.validateDateTime("Start time", request.startTime),
                FormValidationHelper.validateDateTime("End time", request.endTime),
                FormValidationHelper.validateMaxLength("Appointment title", request.title, 120),
                FormValidationHelper.validateMaxLength("Location", request.location, 120),
                FormValidationHelper.validateMaxLength("Assigned staff", request.assignedStaff, 80),
                FormValidationHelper.validateMaxLength("Notes", request.notes, 400)
        );
        if (!validation.isValid()) {
            throw new IllegalArgumentException(validation.getMessage());
        }
        if (update && request.id <= 0) {
            throw new IllegalArgumentException("Appointment ID is required for update.");
        }
        if (!patientDao.existsByPatientId(request.patientId)) {
            throw new IllegalArgumentException("Patient does not exist in SQLite: " + request.patientId);
        }
        String type = normalizeAppointmentType(request.appointmentType);
        String status = normalizeAppointmentStatus(request.status);
        if (!APPOINTMENT_TYPES.contains(type)) {
            throw new IllegalArgumentException("Appointment type must be CHECKUP, SURGERY, FOLLOW_UP, LAB_TEST, MEDICATION_REVIEW, or OTHER.");
        }
        if (!APPOINTMENT_STATUSES.contains(status)) {
            throw new IllegalArgumentException("Appointment status must be SCHEDULED, COMPLETED, CANCELLED, or MISSED.");
        }
        LocalDateTime start = parseDateTime(request.startTime);
        LocalDateTime end = parseDateTime(request.endTime);
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("Appointment start time must be before end time.");
        }
    }

    private void validateReminder(ReminderRequest request, boolean update) throws SQLException {
        FormValidationHelper.ValidationResult validation = FormValidationHelper.combine(
                FormValidationHelper.validatePatientId(request.patientId),
                FormValidationHelper.validateRequired("Reminder title", request.title),
                FormValidationHelper.validateRequired("Reminder type", request.reminderType),
                FormValidationHelper.validateRequired("Reminder status", request.status),
                FormValidationHelper.validateDateTime("Due time", request.dueTime),
                FormValidationHelper.validateMaxLength("Reminder title", request.title, 120),
                FormValidationHelper.validateMaxLength("Repeat rule", request.repeatRule, 80),
                FormValidationHelper.validateMaxLength("Assigned staff", request.assignedTo, 80),
                FormValidationHelper.validateMaxLength("Notes", request.notes, 400)
        );
        if (!validation.isValid()) {
            throw new IllegalArgumentException(validation.getMessage());
        }
        if (update && request.id <= 0) {
            throw new IllegalArgumentException("Reminder ID is required for update.");
        }
        if (!patientDao.existsByPatientId(request.patientId)) {
            throw new IllegalArgumentException("Patient does not exist in SQLite: " + request.patientId);
        }
        String type = normalizeReminderType(request.reminderType);
        String status = normalizeReminderStatus(request.status);
        if (!REMINDER_TYPES.contains(type)) {
            throw new IllegalArgumentException("Reminder type must be MEDICATION, APPOINTMENT, CHECKUP, or CUSTOM.");
        }
        if (!REMINDER_STATUSES.contains(status)) {
            throw new IllegalArgumentException("Reminder status must be PENDING, OVERDUE, DONE, MISSED, or CANCELLED.");
        }
        if (request.medicationId != null && request.medicationId > 0) {
            SqliteMedicationDao.MedicationRecord medication = medicationDao.findMedicationById(request.medicationId)
                    .orElseThrow(() -> new IllegalArgumentException("Medication not found in SQLite: " + request.medicationId));
            if (!medication.getPatientId().equals(request.patientId)) {
                throw new IllegalArgumentException("Medication does not belong to patient " + request.patientId + ".");
            }
            if ("MEDICATION".equals(type) && !medication.isActive()) {
                throw new IllegalArgumentException("Medication reminder must reference an active medication.");
            }
        }
        parseDateTime(request.dueTime);
    }

    private SqliteAppointmentDao.AppointmentRecord cleanAppointment(AppointmentRequest request, long id, String createdBy) {
        return new SqliteAppointmentDao.AppointmentRecord(
                id,
                trim(request.patientId),
                trim(request.title),
                normalizeAppointmentType(request.appointmentType),
                parseDateTime(request.startTime).format(DISPLAY_DATE_TIME),
                parseDateTime(request.endTime).format(DISPLAY_DATE_TIME),
                trim(request.location),
                trim(request.assignedStaff),
                normalizeAppointmentStatus(request.status),
                trim(request.notes),
                createdBy,
                "",
                ""
        );
    }

    private SqliteReminderDao.ReminderRecord cleanReminder(ReminderRequest request, long id, String createdBy) {
        return new SqliteReminderDao.ReminderRecord(
                id,
                trim(request.patientId),
                request.medicationId == null || request.medicationId <= 0 ? null : request.medicationId,
                normalizeReminderType(request.reminderType),
                trim(request.title),
                parseDateTime(request.dueTime).format(DISPLAY_DATE_TIME),
                trim(request.repeatRule),
                normalizeReminderStatus(request.status),
                trim(request.assignedTo),
                createdBy,
                trim(request.notes),
                "",
                ""
        );
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Date/time is required.");
        }
        String trimmed = value.trim();
        try {
            return LocalDateTime.parse(trimmed, DISPLAY_DATE_TIME);
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDateTime.parse(trimmed, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            } catch (DateTimeParseException ignoredAgain) {
                return LocalDateTime.parse(trimmed.replace(" ", "T"));
            }
        }
    }

    private String normalizeAppointmentType(String value) {
        return normalize(value, "CHECKUP").replace(' ', '_');
    }

    private String normalizeAppointmentStatus(String value) {
        return normalize(value, "SCHEDULED");
    }

    private String normalizeReminderType(String value) {
        return normalize(value, "CUSTOM").replace(' ', '_');
    }

    private String normalizeReminderStatus(String value) {
        return normalize(value, "PENDING");
    }

    private String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim().toUpperCase();
    }

    private String username(User currentUser) {
        return currentUser == null || currentUser.getUsername() == null || currentUser.getUsername().isBlank()
                ? "Unknown"
                : currentUser.getUsername();
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    public static class AppointmentRequest {
        public final long id;
        public final String patientId;
        public final String title;
        public final String appointmentType;
        public final String startTime;
        public final String endTime;
        public final String location;
        public final String assignedStaff;
        public final String status;
        public final String notes;

        public AppointmentRequest(long id, String patientId, String title, String appointmentType, String startTime,
                                  String endTime, String location, String assignedStaff, String status, String notes) {
            this.id = id;
            this.patientId = patientId;
            this.title = title;
            this.appointmentType = appointmentType;
            this.startTime = startTime;
            this.endTime = endTime;
            this.location = location;
            this.assignedStaff = assignedStaff;
            this.status = status;
            this.notes = notes;
        }
    }

    public static class ReminderRequest {
        public final long id;
        public final String patientId;
        public final Long medicationId;
        public final String reminderType;
        public final String title;
        public final String dueTime;
        public final String repeatRule;
        public final String status;
        public final String assignedTo;
        public final String notes;

        public ReminderRequest(long id, String patientId, Long medicationId, String reminderType, String title,
                               String dueTime, String repeatRule, String status, String assignedTo, String notes) {
            this.id = id;
            this.patientId = patientId;
            this.medicationId = medicationId;
            this.reminderType = reminderType;
            this.title = title;
            this.dueTime = dueTime;
            this.repeatRule = repeatRule;
            this.status = status;
            this.assignedTo = assignedTo;
            this.notes = notes;
        }
    }

    public static class SchedulingOverview {
        private final int appointmentsToday;
        private final int upcomingSurgeries;
        private final int overdueReminders;
        private final int medicationRemindersToday;
        private final int cancelledMissedItems;
        private final List<SqliteAppointmentDao.AppointmentRow> appointments;
        private final List<SqliteReminderDao.ReminderRow> reminders;

        public SchedulingOverview(int appointmentsToday, int upcomingSurgeries, int overdueReminders,
                                  int medicationRemindersToday, int cancelledMissedItems,
                                  List<SqliteAppointmentDao.AppointmentRow> appointments,
                                  List<SqliteReminderDao.ReminderRow> reminders) {
            this.appointmentsToday = appointmentsToday;
            this.upcomingSurgeries = upcomingSurgeries;
            this.overdueReminders = overdueReminders;
            this.medicationRemindersToday = medicationRemindersToday;
            this.cancelledMissedItems = cancelledMissedItems;
            this.appointments = appointments;
            this.reminders = reminders;
        }

        public int getAppointmentsToday() { return appointmentsToday; }
        public int getUpcomingSurgeries() { return upcomingSurgeries; }
        public int getOverdueReminders() { return overdueReminders; }
        public int getMedicationRemindersToday() { return medicationRemindersToday; }
        public int getCancelledMissedItems() { return cancelledMissedItems; }
        public List<SqliteAppointmentDao.AppointmentRow> getAppointments() { return appointments; }
        public List<SqliteReminderDao.ReminderRow> getReminders() { return reminders; }
    }
}
