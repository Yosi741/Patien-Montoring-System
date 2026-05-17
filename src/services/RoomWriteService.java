package services;

import dao.SqlitePatientDao;
import dao.SqliteRoomDao;
import ui.javafx.helpers.AuditAction;
import ui.javafx.helpers.AuditWriteHelper;
import ui.javafx.helpers.FormValidationHelper;
import ui.javafx.helpers.PermissionHelper;
import users.User;

import java.sql.SQLException;
import java.util.Locale;
import java.util.Set;

public class RoomWriteService {

    private static final Set<String> ROOM_STATUSES = Set.of("ACTIVE", "MAINTENANCE", "INACTIVE");

    private final SqliteRoomDao roomDao;
    private final SqlitePatientDao patientDao;

    public RoomWriteService() {
        this(new SqliteRoomDao(), new SqlitePatientDao());
    }

    public RoomWriteService(SqliteRoomDao roomDao, SqlitePatientDao patientDao) {
        this.roomDao = roomDao;
        this.patientDao = patientDao;
    }

    public long createRoom(User currentUser, RoomRequest request) throws SQLException {
        requireRoomManager(currentUser);
        SqliteRoomDao.RoomRecord room = cleanAndValidateRoom(request);
        if (roomDao.existsSectionRoom(room.getSection(), room.getRoomNumber(), 0)) {
            throw new IllegalArgumentException("Room already exists for this section and room number.");
        }
        long id = roomDao.insertRoom(room);
        AuditWriteHelper.write(username(currentUser), AuditAction.CREATE_ROOM,
                "section=" + room.getSection() + ", room=" + room.getRoomNumber());
        return id;
    }

    public void updateRoom(User currentUser, long roomId, RoomRequest request) throws SQLException {
        requireRoomManager(currentUser);
        SqliteRoomDao.RoomDetail existing = findRoom(roomId);
        SqliteRoomDao.RoomRecord room = cleanAndValidateRoom(request);
        if (roomDao.existsSectionRoom(room.getSection(), room.getRoomNumber(), roomId)) {
            throw new IllegalArgumentException("Room already exists for this section and room number.");
        }
        int occupied = roomDao.occupancy(existing.getSection(), existing.getRoomNumber());
        if (occupied > room.getCapacity()) {
            throw new IllegalArgumentException("Capacity cannot be lower than current occupancy (" + occupied + ").");
        }
        if (!"ACTIVE".equals(room.getStatus()) && occupied > 0) {
            throw new IllegalArgumentException("Move or remove assigned patients before setting a room to " + room.getStatus() + ".");
        }
        roomDao.updateRoom(roomId, room);
        if (!sameLocation(existing, room)) {
            patientDao.updatePatientsRoom(existing.getSection(), existing.getRoomNumber(), room.getSection(), room.getRoomNumber());
        }
        AuditWriteHelper.write(username(currentUser), AuditAction.UPDATE_ROOM,
                "section=" + room.getSection() + ", room=" + room.getRoomNumber() + ", status=" + room.getStatus());
    }

    public void deactivateRoom(User currentUser, long roomId) throws SQLException {
        requireRoomManager(currentUser);
        SqliteRoomDao.RoomDetail room = findRoom(roomId);
        int occupied = roomDao.occupancy(room.getSection(), room.getRoomNumber());
        if (occupied > 0) {
            throw new IllegalArgumentException("Cannot deactivate an occupied room. Remove or move assigned patients first.");
        }
        roomDao.deactivateRoom(roomId);
        AuditWriteHelper.write(username(currentUser), AuditAction.DEACTIVATE_ROOM,
                "section=" + room.getSection() + ", room=" + room.getRoomNumber());
    }

    public void assignPatientToRoom(User currentUser, String patientId, long roomId) throws SQLException {
        assignOrMove(currentUser, patientId, roomId, false);
    }

    public void movePatientToRoom(User currentUser, String patientId, long roomId) throws SQLException {
        assignOrMove(currentUser, patientId, roomId, true);
    }

    public void removePatientFromRoom(User currentUser, String patientId) throws SQLException {
        requireAssignmentPermission(currentUser);
        SqlitePatientDao.PatientDetail patient = findActivePatient(patientId);
        patientDao.updatePatientRoom(patient.getPatientId(), "", "");
        AuditWriteHelper.write(username(currentUser), AuditAction.REMOVE_PATIENT_ROOM,
                "patient_id=" + patient.getPatientId() + ", from=" + safe(patient.getSection()) + "/" + safe(patient.getRoom()));
    }

    private void assignOrMove(User currentUser, String patientId, long roomId, boolean move) throws SQLException {
        requireAssignmentPermission(currentUser);
        SqlitePatientDao.PatientDetail patient = findActivePatient(patientId);
        SqliteRoomDao.RoomDetail room = findRoom(roomId);
        if (!"ACTIVE".equalsIgnoreCase(room.getStatus())) {
            throw new IllegalArgumentException("Patients can only be assigned to ACTIVE rooms.");
        }
        int occupancyWithoutPatient = roomDao.occupancy(room.getSection(), room.getRoomNumber(), patient.getPatientId());
        if (occupancyWithoutPatient >= room.getCapacity()) {
            throw new IllegalArgumentException("Room capacity is full. Choose another room.");
        }
        patientDao.updatePatientRoom(patient.getPatientId(), room.getSection(), room.getRoomNumber());
        AuditWriteHelper.write(username(currentUser), move ? AuditAction.MOVE_PATIENT_ROOM : AuditAction.ASSIGN_PATIENT_ROOM,
                "patient_id=" + patient.getPatientId() + ", room=" + room.getSection() + "/" + room.getRoomNumber());
    }

    private SqliteRoomDao.RoomRecord cleanAndValidateRoom(RoomRequest request) {
        String section = safe(request == null ? "" : request.section);
        String roomNumber = safe(request == null ? "" : request.roomNumber);
        String status = normalizeStatus(request == null ? "" : request.status);
        String notes = safe(request == null ? "" : request.notes);
        int capacity = request == null ? 0 : request.capacity;

        FormValidationHelper.ValidationResult validation = FormValidationHelper.combine(
                FormValidationHelper.validateRequired("Section/department", section),
                FormValidationHelper.validateMaxLength("Section/department", section, 80),
                FormValidationHelper.validateRequired("Room number", roomNumber),
                FormValidationHelper.validateMaxLength("Room number", roomNumber, 40),
                FormValidationHelper.validateNumeric("Capacity", String.valueOf(capacity), 1, 500),
                FormValidationHelper.validateRequired("Room status", status),
                FormValidationHelper.validateMaxLength("Notes", notes, 300)
        );
        if (!validation.isValid()) {
            throw new IllegalArgumentException(validation.getMessage());
        }
        if (!ROOM_STATUSES.contains(status)) {
            throw new IllegalArgumentException("Room status must be ACTIVE, MAINTENANCE, or INACTIVE.");
        }
        return new SqliteRoomDao.RoomRecord(section, roomNumber, capacity, status, notes);
    }

    private SqlitePatientDao.PatientDetail findActivePatient(String patientId) throws SQLException {
        FormValidationHelper.ValidationResult validation = FormValidationHelper.validatePatientId(patientId);
        if (!validation.isValid()) {
            throw new IllegalArgumentException(validation.getMessage());
        }
        SqlitePatientDao.PatientDetail patient = patientDao.findDetailById(patientId.trim())
                .orElseThrow(() -> new IllegalArgumentException("Patient does not exist in SQLite: " + patientId));
        String status = safe(patient.getStatus()).toUpperCase(Locale.ROOT);
        if ("DECEASED".equals(status) || "DISCHARGED".equals(status) || "INACTIVE".equals(status)) {
            throw new IllegalArgumentException("Only active patients can be assigned to rooms.");
        }
        return patient;
    }

    private SqliteRoomDao.RoomDetail findRoom(long roomId) throws SQLException {
        if (roomId <= 0) {
            throw new IllegalArgumentException("Select a room from the SQLite rooms table first.");
        }
        return roomDao.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Room not found in SQLite."));
    }

    private void requireRoomManager(User currentUser) {
        if (!PermissionHelper.canManageRooms(currentUser)) {
            throw new SecurityException("Only Admin users can create, edit, or deactivate rooms.");
        }
    }

    private void requireAssignmentPermission(User currentUser) {
        if (!PermissionHelper.canAssignPatientRoom(currentUser)) {
            throw new SecurityException("Only Admin, Doctor, and Nurse users can assign or move patients.");
        }
    }

    private boolean sameLocation(SqliteRoomDao.RoomDetail existing, SqliteRoomDao.RoomRecord room) {
        return safe(existing.getSection()).equalsIgnoreCase(room.getSection())
                && safe(existing.getRoomNumber()).equalsIgnoreCase(room.getRoomNumber());
    }

    private String normalizeStatus(String status) {
        String normalized = safe(status).toUpperCase(Locale.ROOT);
        return normalized.isBlank() ? "ACTIVE" : normalized;
    }

    private String username(User currentUser) {
        return currentUser == null || currentUser.getUsername() == null || currentUser.getUsername().isBlank()
                ? "Unknown"
                : currentUser.getUsername();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    public static class RoomRequest {
        private final String section;
        private final String roomNumber;
        private final int capacity;
        private final String status;
        private final String notes;

        public RoomRequest(String section, String roomNumber, int capacity, String status, String notes) {
            this.section = section;
            this.roomNumber = roomNumber;
            this.capacity = capacity;
            this.status = status;
            this.notes = notes;
        }
    }
}
