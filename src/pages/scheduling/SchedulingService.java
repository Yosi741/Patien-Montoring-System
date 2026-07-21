package pages.scheduling;

import app.helpers.FormValidationHelper;
import app.helpers.PermissionHelper;
import pages.patient.Add_Edit_Patient_Dao;
import pages.user.User;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;

/**
 * Validates appointment permissions, patient references, dates, and times before persistence.
 */
public class SchedulingService {

    private static final DateTimeFormatter DISPLAY_DATE_TIME = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
    private static final Set<String> APPOINTMENT_TYPES = Set.of("VISIT", "SURGERY", "FOLLOW_UP", "LAB_TEST", "OTHER");
    private static final Set<String> APPOINTMENT_STATUSES = Set.of("SCHEDULED", "COMPLETED", "CANCELLED", "MISSED");

    private final SqliteAppointmentDao appointmentDao;
    private final Add_Edit_Patient_Dao patientDao;

    /**
     * Creates the service with the dependencies used by the appointment workflow.
     */
    public SchedulingService() {
        this(new SqliteAppointmentDao(), new Add_Edit_Patient_Dao());
    }

    /**
     * Creates the service with the dependencies used by the appointment workflow.
     */
    public SchedulingService(SqliteAppointmentDao appointmentDao, Add_Edit_Patient_Dao patientDao) {
        this.appointmentDao = appointmentDao;
        this.patientDao = patientDao;
    }

    /**
     * Creates appointment for the appointment workflow.
     */
    public long createAppointment(User currentUser, AppointmentRequest request) throws SQLException {
        requireCreatePermission(currentUser);
        validateAppointment(request, false);
        SqliteAppointmentDao.AppointmentRecord record = cleanAppointment(request, 0, username(currentUser));
        return appointmentDao.insertAppointment(record);
    }

    /**
     * Updates appointment.
     */
    public void updateAppointment(User currentUser, AppointmentRequest request) throws SQLException {
        requireEditPermission(currentUser);
        if (request.id <= 0) {
            throw new IllegalArgumentException("Appointment ID is required for update.");
        }
        validateAppointment(request, true);
        appointmentDao.updateAppointment(cleanAppointment(request, request.id, username(currentUser)));
    }

    /**
     * Deletes appointment after the required checks.
     */
    public void deleteAppointment(User currentUser, long appointmentId) throws SQLException {
        requireDeletePermission(currentUser);
        SqliteAppointmentDao.AppointmentRecord appointment = appointmentDao.findById(appointmentId)
                .orElseThrow(() -> new IllegalArgumentException("Appointment not found in SQLite: " + appointmentId));
        if (!appointmentDao.deleteAppointment(appointmentId)) {
            throw new IllegalStateException("Appointment could not be deleted.");
        }
    }

    /**
     * Loads overview for the appointment workflow.
     */
    public SchedulingOverview loadOverview(String search, String appointmentType, String appointmentStatus,
                                           String patientId) throws SQLException {
        List<SqliteAppointmentDao.AppointmentRow> appointments =
                appointmentDao.findAppointments(search, appointmentType, appointmentStatus, patientId);
        return new SchedulingOverview(
                appointmentDao.countToday(),
                appointmentDao.countCancelledOrMissed(),
                appointments
        );
    }

    /**
     * Enforces create permission before the protected operation continues.
     */
    private void requireCreatePermission(User currentUser) {
        if (!PermissionHelper.canCreateAppointment(currentUser)) {
            throw new SecurityException("Only Admin or Secretary users can create appointments.");
        }
    }

    /**
     * Enforces edit permission before the protected operation continues.
     */
    private void requireEditPermission(User currentUser) {
        if (!PermissionHelper.canEditAppointment(currentUser)) {
            throw new SecurityException("Only Admin or Secretary users can edit, cancel, or complete appointments.");
        }
    }

    /**
     * Enforces delete permission before the protected operation continues.
     */
    private void requireDeletePermission(User currentUser) {
        if (!PermissionHelper.canDeleteAppointment(currentUser)) {
            throw new SecurityException("Only Admin users can delete appointments.");
        }
    }

    /**
     * Validates appointment against the active business rules.
     */
    private void validateAppointment(AppointmentRequest request, boolean update) throws SQLException {
        FormValidationHelper.ValidationResult validation = FormValidationHelper.combine(
                FormValidationHelper.validatePatientId(request.patientId),
                FormValidationHelper.validateRequired("Appointment type", request.appointmentType),
                FormValidationHelper.validateRequired("Appointment status", request.status),
                FormValidationHelper.validateDateTime("Start time", request.startTime),
                FormValidationHelper.validateDateTime("End time", request.endTime),
                FormValidationHelper.validateMaxLength("Appointment title", request.title, 120),
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
            throw new IllegalArgumentException("Appointment type must be a valid visit, follow-up, lab test, surgery, or other appointment type.");
        }
        if (!APPOINTMENT_STATUSES.contains(status)) {
            throw new IllegalArgumentException("Appointment status must be SCHEDULED, COMPLETED, CANCELLED, or MISSED.");
        }
        LocalDateTime start = parseDateTime(request.startTime);
        LocalDateTime end = parseDateTime(request.endTime);
        if (start.toLocalDate().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Appointment date cannot be in the past.");
        }
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("Appointment start time must be before end time.");
        }
        if (shouldCheckOverlap(status) && hasOverlappingAppointment(request.patientId, start, end, request.id)) {
            throw new IllegalArgumentException("This patient already has an appointment during the selected time.");
        }
    }

    /**
     * Trims and normalizes appointment before storage or comparison.
     */
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

    /**
     * Parses date time without exposing format failures to the caller.
     */
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

    /**
     * Normalizes appointment type to the stored application format.
     */
    private String normalizeAppointmentType(String value) {
        String normalized = normalize(value, "VISIT").replace(' ', '_');
        return "CHECKUP".equalsIgnoreCase(normalized) ? "VISIT" : normalized;
    }

    /**
     * Normalizes appointment status to the stored application format.
     */
    private String normalizeAppointmentStatus(String value) {
        return normalize(value, "SCHEDULED");
    }

    /**
     * Determines whether has overlapping appointment for the current record or user.
     */
    private boolean hasOverlappingAppointment(String patientId, LocalDateTime newStart, LocalDateTime newEnd, long excludeAppointmentId)
            throws SQLException {
        List<SqliteAppointmentDao.AppointmentRecord> appointments = appointmentDao.findAppointmentsForPatient(trim(patientId));
        for (SqliteAppointmentDao.AppointmentRecord appointment : appointments) {
            if (appointment == null || appointment.getId() == excludeAppointmentId) {
                continue;
            }
            if (!shouldCheckOverlap(appointment.getStatus())) {
                continue;
            }
            LocalDateTime existingStart = parseDateTime(appointment.getStartTime());
            LocalDateTime existingEnd = parseDateTime(appointment.getEndTime());
            if (newStart.isBefore(existingEnd) && newEnd.isAfter(existingStart)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Determines whether check overlap applies to the current operation.
     */
    private boolean shouldCheckOverlap(String status) {
        String normalized = normalizeAppointmentStatus(status);
        return !"CANCELLED".equalsIgnoreCase(normalized)
                && !"COMPLETED".equalsIgnoreCase(normalized)
                && !"MISSED".equalsIgnoreCase(normalized);
    }

    /**
     * Normalizes normalize to the stored application format.
     */
    private String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim().toUpperCase();
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

        /**
         * Creates a appointment request from the supplied record values.
         */
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

    public static class SchedulingOverview {
        private final int appointmentsToday;
        private final int cancelledMissedItems;
        private final List<SqliteAppointmentDao.AppointmentRow> appointments;

        /**
         * Creates a scheduling overview from the supplied record values.
         */
        public SchedulingOverview(int appointmentsToday, int cancelledMissedItems,
                                  List<SqliteAppointmentDao.AppointmentRow> appointments) {
            this.appointmentsToday = appointmentsToday;
            this.cancelledMissedItems = cancelledMissedItems;
            this.appointments = appointments;
        }

        public int getAppointmentsToday() { return appointmentsToday; }
        public int getCancelledMissedItems() { return cancelledMissedItems; }
        public List<SqliteAppointmentDao.AppointmentRow> getAppointments() { return appointments; }
    }
}
