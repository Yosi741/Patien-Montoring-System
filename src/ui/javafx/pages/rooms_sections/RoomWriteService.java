package ui.javafx.pages.rooms_sections;

import ui.javafx.pages.patients.dao.SqlitePatientDao;
import ui.javafx.pages.audit_logs.AuditAction;
import ui.javafx.pages.audit_logs.AuditWriteHelper;
import app.helpers.FormValidationHelper;
import app.helpers.PermissionHelper;
import users.User;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    public GenerateRoomsResult generateRooms(User currentUser, GenerateRoomsRequest request) throws SQLException {
        requireRoomManager(currentUser);
        GenerateRoomsRequest clean = cleanAndValidateGenerateRequest(request);
        int requested = clean.endRoomNumber - clean.startRoomNumber + 1;
        int created = 0;
        int skipped = 0;
        StringBuilder skippedRooms = new StringBuilder();

        for (int number = clean.startRoomNumber; number <= clean.endRoomNumber; number++) {
            String roomNumber = generatedRoomNumber(clean.roomPrefix, clean.floorNumber, number);
            if (roomDao.existsSectionRoom(clean.section, roomNumber, 0)) {
                skipped++;
                if (skippedRooms.length() < 180) {
                    if (skippedRooms.length() > 0) {
                        skippedRooms.append(", ");
                    }
                    skippedRooms.append(roomNumber);
                }
                continue;
            }
            roomDao.insertGeneratedRoom(new SqliteRoomDao.RoomRecord(clean.section, roomNumber, clean.capacity, "ACTIVE", "Generated room"),
                    floorNumber(clean.floorNumber), number);
            created++;
        }

        AuditWriteHelper.write(username(currentUser), AuditAction.GENERATE_ROOMS,
                "section=" + clean.section + ", requested=" + requested + ", created=" + created + ", skipped=" + skipped);
        return new GenerateRoomsResult(requested, created, skipped, skippedRooms.toString());
    }

    public GenerateRoomsResult syncSectionRoomRange(User currentUser, GenerateRoomsRequest request) throws SQLException {
        requireRoomManager(currentUser);
        GenerateRoomsRequest clean = cleanAndValidateGenerateRequest(request);
        GenerateRoomsResult generated = generateRooms(currentUser, clean);
        int deactivated = 0;
        int keptOccupied = 0;
        List<SqliteRoomDao.RoomDetail> rooms = roomDao.findBySection(clean.section);
        for (SqliteRoomDao.RoomDetail room : rooms) {
            Integer roomNumber = parseGeneratedRoomNumber(room.getRoomNumber(), clean.roomPrefix, clean.floorNumber);
            if (roomNumber == null || (roomNumber >= clean.startRoomNumber && roomNumber <= clean.endRoomNumber)) {
                continue;
            }
            if (room.getOccupiedCount() > 0) {
                keptOccupied++;
                continue;
            }
            if (!"INACTIVE".equalsIgnoreCase(room.getStatus())) {
                roomDao.updateRoomStatus(room.getId(), "INACTIVE");
                deactivated++;
            }
        }
        return new GenerateRoomsResult(generated.requestedCount, generated.createdCount, generated.skippedCount,
                generated.skippedRooms, deactivated, keptOccupied);
    }

    public GenerateRoomsResult createAutomaticRooms(User currentUser, AutoRoomsRequest request) throws SQLException {
        requireRoomManager(currentUser);
        AutoRoomsRequest clean = cleanAndValidateAutoRequest(request);
        if (clean.roomCount == 0) {
            return new GenerateRoomsResult(0, 0, 0, "", 0, 0, "", "");
        }
        int floor = floorNumber(clean.floorNumber);
        int startSequence = highestSequenceForFloor(floor) + 1;
        int endSequence = startSequence + clean.roomCount - 1;
        int created = 0;
        int skipped = 0;
        String firstRoom = "";
        String lastRoom = "";
        StringBuilder skippedRooms = new StringBuilder();

        for (int sequence = startSequence; sequence <= endSequence; sequence++) {
            String roomNumber = generatedRoomNumber(clean.roomPrefix, clean.floorNumber, sequence);
            if (roomDao.existsSectionRoom(clean.section, roomNumber, 0)) {
                skipped++;
                appendSkipped(skippedRooms, roomNumber);
                continue;
            }
            roomDao.insertGeneratedRoom(new SqliteRoomDao.RoomRecord(clean.section, roomNumber, clean.capacity, "ACTIVE", "Generated room"),
                    floor, sequence);
            if (firstRoom.isBlank()) {
                firstRoom = roomNumber;
            }
            lastRoom = roomNumber;
            created++;
        }

        AuditWriteHelper.write(username(currentUser), AuditAction.GENERATE_ROOMS,
                "section=" + clean.section + ", requested=" + clean.roomCount + ", created=" + created + ", skipped=" + skipped);
        return new GenerateRoomsResult(clean.roomCount, created, skipped, skippedRooms.toString(), 0, 0, firstRoom, lastRoom);
    }

    public GenerateRoomsResult syncSectionRoomTarget(User currentUser, AutoRoomsRequest request) throws SQLException {
        requireRoomManager(currentUser);
        AutoRoomsRequest clean = cleanAndValidateAutoRequest(request);
        List<GeneratedRoomMatch> matches = matchingGeneratedRooms(clean.section, clean.roomPrefix, clean.floorNumber);
        int currentCount = matches.size();
        if (clean.roomCount > currentCount) {
            AutoRoomsRequest addRequest = new AutoRoomsRequest(
                    clean.section,
                    clean.roomPrefix,
                    clean.floorNumber,
                    clean.roomCount - currentCount,
                    clean.capacity);
            return createAutomaticRooms(currentUser, addRequest);
        }

        int deactivated = 0;
        int keptOccupied = 0;
        if (clean.roomCount < currentCount) {
            matches.sort(Comparator.comparingInt(GeneratedRoomMatch::sequence).reversed());
            int toReduce = currentCount - clean.roomCount;
            for (GeneratedRoomMatch match : matches) {
                if (toReduce <= 0) {
                    break;
                }
                if (match.room.getOccupiedCount() > 0) {
                    keptOccupied++;
                    continue;
                }
                if (!"INACTIVE".equalsIgnoreCase(match.room.getStatus())) {
                    roomDao.updateRoomStatus(match.room.getId(), "INACTIVE");
                    deactivated++;
                }
                toReduce--;
            }
        }
        return new GenerateRoomsResult(clean.roomCount, 0, 0, "", deactivated, keptOccupied, "", "");
    }

    public RoomPlanPreview previewAutomaticRooms(AutoRoomsRequest request) throws SQLException {
        AutoRoomsRequest clean = cleanAndValidateAutoRequest(request);
        int currentCount = countMatchingGeneratedRooms(clean.section, clean.roomPrefix, clean.floorNumber);
        if (clean.roomCount == 0) {
            return new RoomPlanPreview(0, currentCount, "", "", "Preview: Section will be created without rooms.");
        }
        int startSequence = highestSequenceForFloor(floorNumber(clean.floorNumber)) + 1;
        String first = generatedRoomNumber(clean.roomPrefix, clean.floorNumber, startSequence);
        String last = generatedRoomNumber(clean.roomPrefix, clean.floorNumber, startSequence + clean.roomCount - 1);
        return new RoomPlanPreview(clean.roomCount, currentCount, first, last,
                "Preview: " + first + " to " + last);
    }

    public int countMatchingGeneratedRooms(String section, String prefix, String floor) throws SQLException {
        return matchingGeneratedRooms(section, prefix, floor).size();
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

    private GenerateRoomsRequest cleanAndValidateGenerateRequest(GenerateRoomsRequest request) {
        String section = safe(request == null ? "" : request.section);
        String roomPrefix = safe(request == null ? "" : request.roomPrefix).toUpperCase(Locale.ROOT);
        String floorNumber = safe(request == null ? "" : request.floorNumber);
        int start = request == null ? 0 : request.startRoomNumber;
        int end = request == null ? 0 : request.endRoomNumber;
        int capacity = request == null ? 0 : request.capacity;

        FormValidationHelper.ValidationResult validation = FormValidationHelper.combine(
                FormValidationHelper.validateRequired("Section/department", section),
                FormValidationHelper.validateMaxLength("Section/department", section, 80),
                FormValidationHelper.validateMaxLength("Room prefix", roomPrefix, 12),
                FormValidationHelper.validateMaxLength("Floor number", floorNumber, 12),
                FormValidationHelper.validateNumeric("Start room number", String.valueOf(start), 1, 999999),
                FormValidationHelper.validateNumeric("End room number", String.valueOf(end), 1, 999999),
                FormValidationHelper.validateNumeric("Default capacity", String.valueOf(capacity), 1, 500)
        );
        if (!validation.isValid()) {
            throw new IllegalArgumentException(validation.getMessage());
        }
        if (end < start) {
            throw new IllegalArgumentException("End room number must be greater than or equal to start room number.");
        }
        if (start < 1 || start > 500 || end < 1 || end > 500) {
            throw new IllegalArgumentException("Enter the room number within the floor, for example 1 to 46. Floor 1 will generate 1001 to 1046.");
        }
        if (capacity < 1 || capacity > 20) {
            throw new IllegalArgumentException("Default bed capacity must be between 1 and 20.");
        }
        return new GenerateRoomsRequest(section, roomPrefix, floorNumber, start, end, capacity);
    }

    private String generatedRoomNumber(String prefix, String floor, int number) {
        StringBuilder builder = new StringBuilder();
        if (prefix != null && !prefix.isBlank()) {
            builder.append(prefix.trim().toUpperCase(Locale.ROOT)).append("-");
        }
        builder.append(floorRoomCode(floor, number));
        return builder.toString();
    }

    private String floorRoomCode(String floor, int number) {
        String floorValue = safe(floor);
        if ("-1".equals(floorValue)) {
            return "B" + String.format("%03d", number);
        }
        if ("0".equals(floorValue)) {
            return "G" + String.format("%03d", number);
        }
        int floorNumber;
        try {
            floorNumber = Integer.parseInt(floorValue);
        } catch (NumberFormatException e) {
            floorNumber = 1;
        }
        return floorNumber + String.format("%03d", number);
    }

    private Integer parseGeneratedRoomNumber(String roomNumber, String prefix, String floor) {
        String safePrefix = safe(prefix).toUpperCase(Locale.ROOT);
        String normalized = safe(roomNumber).toUpperCase(Locale.ROOT);
        if (!safePrefix.isBlank()) {
            String expectedPrefix = safePrefix + "-";
            if (!normalized.startsWith(expectedPrefix)) {
                return null;
            }
            normalized = normalized.substring(expectedPrefix.length());
        }
        String floorValue = safe(floor);
        Pattern pattern;
        if ("-1".equals(floorValue)) {
            pattern = Pattern.compile("B(\\d{3})");
        } else if ("0".equals(floorValue)) {
            pattern = Pattern.compile("G(\\d{3})");
        } else {
            pattern = Pattern.compile(Pattern.quote(floorValue.isBlank() ? "1" : floorValue) + "(\\d{3})");
        }
        Matcher matcher = pattern.matcher(normalized);
        if (!matcher.matches()) {
            return null;
        }
        return Integer.parseInt(matcher.group(1));
    }

    private int highestSequenceForFloor(int floor) throws SQLException {
        int highest = 0;
        for (SqliteRoomDao.RoomNumberMetadata metadata : roomDao.findRoomNumberMetadata()) {
            if (metadata.getFloorNumber() != null && metadata.getRoomSequence() != null
                    && metadata.getFloorNumber() == floor && metadata.getRoomSequence() > highest) {
                highest = metadata.getRoomSequence();
                continue;
            }
            Integer parsed = parseFloorSequence(metadata.getRoomNumber(), floor);
            if (parsed != null && parsed > highest) {
                highest = parsed;
            }
        }
        return highest;
    }

    private List<GeneratedRoomMatch> matchingGeneratedRooms(String section, String prefix, String floor) throws SQLException {
        ArrayList<GeneratedRoomMatch> matches = new ArrayList<>();
        for (SqliteRoomDao.RoomDetail room : roomDao.findBySection(section)) {
            Integer sequence = parseGeneratedRoomNumber(room.getRoomNumber(), prefix, floor);
            if (sequence != null) {
                matches.add(new GeneratedRoomMatch(room, sequence));
            }
        }
        return matches;
    }

    private Integer parseFloorSequence(String roomNumber, int floor) {
        String normalized = safe(roomNumber).toUpperCase(Locale.ROOT);
        int dash = normalized.lastIndexOf('-');
        if (dash >= 0 && dash < normalized.length() - 1) {
            normalized = normalized.substring(dash + 1);
        }
        Pattern pattern;
        if (floor == -1) {
            pattern = Pattern.compile("B(\\d{3})");
        } else if (floor == 0) {
            pattern = Pattern.compile("G(\\d{3})");
        } else {
            pattern = Pattern.compile(Pattern.quote(String.valueOf(floor)) + "(\\d{3})");
        }
        Matcher matcher = pattern.matcher(normalized);
        return matcher.matches() ? Integer.parseInt(matcher.group(1)) : null;
    }

    private AutoRoomsRequest cleanAndValidateAutoRequest(AutoRoomsRequest request) {
        String section = safe(request == null ? "" : request.section);
        String roomPrefix = safe(request == null ? "" : request.roomPrefix).toUpperCase(Locale.ROOT);
        String floorNumber = safe(request == null ? "" : request.floorNumber);
        int roomCount = request == null ? 0 : request.roomCount;
        int capacity = request == null ? 0 : request.capacity;

        FormValidationHelper.ValidationResult validation = FormValidationHelper.combine(
                FormValidationHelper.validateRequired("Section/department", section),
                FormValidationHelper.validateMaxLength("Section/department", section, 80),
                FormValidationHelper.validateMaxLength("Room prefix", roomPrefix, 12),
                FormValidationHelper.validateMaxLength("Floor number", floorNumber, 12),
                FormValidationHelper.validateNumeric("Number of rooms", String.valueOf(roomCount), 0, 500),
                FormValidationHelper.validateNumeric("Default beds per room", String.valueOf(capacity), 1, 20)
        );
        if (!validation.isValid()) {
            throw new IllegalArgumentException(validation.getMessage());
        }
        floorNumber(floorNumber);
        return new AutoRoomsRequest(section, roomPrefix, floorNumber, roomCount, capacity);
    }

    private int floorNumber(String floorNumber) {
        try {
            int floor = Integer.parseInt(safe(floorNumber).isBlank() ? "1" : safe(floorNumber));
            if (floor < -1 || floor > 4) {
                throw new IllegalArgumentException("Floor number must be -1, 0, 1, 2, 3, or 4.");
            }
            return floor;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Floor number must be -1, 0, 1, 2, 3, or 4.");
        }
    }

    private void appendSkipped(StringBuilder skippedRooms, String roomNumber) {
        if (skippedRooms.length() < 180) {
            if (skippedRooms.length() > 0) {
                skippedRooms.append(", ");
            }
            skippedRooms.append(roomNumber);
        }
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

    public static class GenerateRoomsRequest {
        private final String section;
        private final String roomPrefix;
        private final String floorNumber;
        private final int startRoomNumber;
        private final int endRoomNumber;
        private final int capacity;

        public GenerateRoomsRequest(String section, String roomPrefix, String floorNumber,
                                    int startRoomNumber, int endRoomNumber, int capacity) {
            this.section = section;
            this.roomPrefix = roomPrefix;
            this.floorNumber = floorNumber;
            this.startRoomNumber = startRoomNumber;
            this.endRoomNumber = endRoomNumber;
            this.capacity = capacity;
        }
    }

    public static class AutoRoomsRequest {
        private final String section;
        private final String roomPrefix;
        private final String floorNumber;
        private final int roomCount;
        private final int capacity;

        public AutoRoomsRequest(String section, String roomPrefix, String floorNumber, int roomCount, int capacity) {
            this.section = section;
            this.roomPrefix = roomPrefix;
            this.floorNumber = floorNumber;
            this.roomCount = roomCount;
            this.capacity = capacity;
        }
    }

    public static class GenerateRoomsResult {
        private final int requestedCount;
        private final int createdCount;
        private final int skippedCount;
        private final String skippedRooms;
        private final int deactivatedCount;
        private final int keptOccupiedCount;
        private final String firstRoom;
        private final String lastRoom;

        public GenerateRoomsResult(int requestedCount, int createdCount, int skippedCount, String skippedRooms) {
            this(requestedCount, createdCount, skippedCount, skippedRooms, 0, 0, "", "");
        }

        public GenerateRoomsResult(int requestedCount, int createdCount, int skippedCount, String skippedRooms,
                                   int deactivatedCount, int keptOccupiedCount) {
            this(requestedCount, createdCount, skippedCount, skippedRooms, deactivatedCount, keptOccupiedCount, "", "");
        }

        public GenerateRoomsResult(int requestedCount, int createdCount, int skippedCount, String skippedRooms,
                                   int deactivatedCount, int keptOccupiedCount, String firstRoom, String lastRoom) {
            this.requestedCount = requestedCount;
            this.createdCount = createdCount;
            this.skippedCount = skippedCount;
            this.skippedRooms = skippedRooms == null ? "" : skippedRooms;
            this.deactivatedCount = deactivatedCount;
            this.keptOccupiedCount = keptOccupiedCount;
            this.firstRoom = firstRoom == null ? "" : firstRoom;
            this.lastRoom = lastRoom == null ? "" : lastRoom;
        }

        public int getRequestedCount() { return requestedCount; }
        public int getCreatedCount() { return createdCount; }
        public int getSkippedCount() { return skippedCount; }
        public String getSkippedRooms() { return skippedRooms; }
        public int getDeactivatedCount() { return deactivatedCount; }
        public int getKeptOccupiedCount() { return keptOccupiedCount; }
        public String getFirstRoom() { return firstRoom; }
        public String getLastRoom() { return lastRoom; }
    }

    public static class RoomPlanPreview {
        private final int requestedCount;
        private final int currentCount;
        private final String firstRoom;
        private final String lastRoom;
        private final String message;

        public RoomPlanPreview(int requestedCount, int currentCount, String firstRoom, String lastRoom, String message) {
            this.requestedCount = requestedCount;
            this.currentCount = currentCount;
            this.firstRoom = firstRoom == null ? "" : firstRoom;
            this.lastRoom = lastRoom == null ? "" : lastRoom;
            this.message = message == null ? "" : message;
        }

        public int getRequestedCount() { return requestedCount; }
        public int getCurrentCount() { return currentCount; }
        public String getFirstRoom() { return firstRoom; }
        public String getLastRoom() { return lastRoom; }
        public String getMessage() { return message; }
    }

    private static class GeneratedRoomMatch {
        private final SqliteRoomDao.RoomDetail room;
        private final int sequence;

        private GeneratedRoomMatch(SqliteRoomDao.RoomDetail room, int sequence) {
            this.room = room;
            this.sequence = sequence;
        }

        private int sequence() {
            return sequence;
        }
    }
}
