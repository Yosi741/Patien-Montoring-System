package services;

import database.DatabaseManager;
import database.SchemaInitializer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class RoomBedOccupancyService {

    public RoomBedOccupancyService() {
        ensureSchema();
    }

    public OccupancyOverview loadOverview(OccupancyFilter filter) throws SQLException {
        List<RoomSeed> roomSeeds;
        List<PatientAssignment> patients;
        try (Connection connection = DatabaseManager.getConnection()) {
            roomSeeds = queryRooms(connection);
            patients = queryAssignedPatients(connection);
        }

        boolean derivedFromPatients = roomSeeds.isEmpty();
        List<RoomRow> rows = derivedFromPatients ? buildRowsFromPatients(patients) : buildRowsFromRooms(roomSeeds, patients);
        rows.removeIf(row -> !matches(row, filter));
        rows.sort(Comparator.comparing(RoomRow::getSection, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(RoomRow::getRoomNumber, String.CASE_INSENSITIVE_ORDER));

        return new OccupancyOverview(
                rows.size(),
                occupiedRooms(rows),
                occupiedBeds(rows),
                availableCapacity(rows),
                activePatientsBySection(patients, filter),
                criticalEmergencyBySection(patients, filter),
                derivedFromPatients,
                rows
        );
    }

    public List<String> findSections() throws SQLException {
        LinkedHashSet<String> sections = new LinkedHashSet<>();
        try (Connection connection = DatabaseManager.getConnection();
             Statement statement = connection.createStatement()) {
            try (ResultSet resultSet = statement.executeQuery("SELECT DISTINCT section FROM rooms WHERE section IS NOT NULL AND TRIM(section) <> '' ORDER BY section COLLATE NOCASE")) {
                while (resultSet.next()) {
                    sections.add(resultSet.getString("section"));
                }
            }
            try (ResultSet resultSet = statement.executeQuery("SELECT DISTINCT section FROM patients WHERE section IS NOT NULL AND TRIM(section) <> '' ORDER BY section COLLATE NOCASE")) {
                while (resultSet.next()) {
                    sections.add(resultSet.getString("section"));
                }
            }
        }
        return new ArrayList<>(sections);
    }

    private List<RoomSeed> queryRooms(Connection connection) throws SQLException {
        ArrayList<RoomSeed> rows = new ArrayList<>();
        String sql = "SELECT id, section, room_number, capacity, COALESCE(status, 'ACTIVE') AS status, "
                + "COALESCE(notes, '') AS notes FROM rooms ORDER BY section, room_number";
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                rows.add(new RoomSeed(
                        resultSet.getLong("id"),
                        value(resultSet.getString("section")),
                        value(resultSet.getString("room_number")),
                        Math.max(1, resultSet.getInt("capacity")),
                        defaultValue(resultSet.getString("status"), "ACTIVE"),
                        value(resultSet.getString("notes"))
                ));
            }
        }
        return rows;
    }

    private List<PatientAssignment> queryAssignedPatients(Connection connection) throws SQLException {
        ArrayList<PatientAssignment> rows = new ArrayList<>();
        String sql = "SELECT patient_id, first_name, last_name, section, room, status, priority "
                + "FROM patients WHERE COALESCE(room, '') <> '' OR COALESCE(section, '') <> ''";
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                rows.add(new PatientAssignment(
                        value(resultSet.getString("patient_id")),
                        fullName(resultSet.getString("first_name"), resultSet.getString("last_name")),
                        defaultValue(resultSet.getString("section"), "Unassigned"),
                        defaultValue(resultSet.getString("room"), "Unassigned"),
                        defaultValue(resultSet.getString("status"), "Unknown"),
                        defaultValue(resultSet.getString("priority"), "NORMAL")
                ));
            }
        }
        return rows;
    }

    private List<RoomRow> buildRowsFromRooms(List<RoomSeed> rooms, List<PatientAssignment> patients) {
        ArrayList<RoomRow> rows = new ArrayList<>();
        for (RoomSeed room : rooms) {
            List<PatientAssignment> assigned = assignedPatients(room.getSection(), room.getRoomNumber(), patients);
            rows.add(toRoomRow(room.getId(), room.getSection(), room.getRoomNumber(), room.getCapacity(),
                    room.getStatus(), room.getNotes(), assigned));
        }
        return rows;
    }

    private List<RoomRow> buildRowsFromPatients(List<PatientAssignment> patients) {
        LinkedHashMap<String, List<PatientAssignment>> grouped = new LinkedHashMap<>();
        for (PatientAssignment patient : patients) {
            String key = patient.getSection() + "\u0001" + patient.getRoom();
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(patient);
        }
        ArrayList<RoomRow> rows = new ArrayList<>();
        for (List<PatientAssignment> assigned : grouped.values()) {
            if (assigned.isEmpty()) {
                continue;
            }
            PatientAssignment first = assigned.get(0);
            rows.add(toRoomRow(0, first.getSection(), first.getRoom(), Math.max(assigned.size(), 1),
                    "DERIVED", "", assigned));
        }
        return rows;
    }

    private RoomRow toRoomRow(long roomId, String section, String roomNumber, int capacity, String roomStatus,
                              String roomNotes, List<PatientAssignment> assigned) {
        int occupied = assigned.size();
        String highestPriority = highestPriority(assigned);
        String selectedPatientId = selectedPatientId(assigned);
        return new RoomRow(
                roomId,
                section,
                roomNumber,
                capacity,
                roomStatus,
                occupied,
                Math.max(0, capacity - occupied),
                patientSummary(assigned),
                highestPriority,
                selectedPatientId,
                roomNotes,
                statuses(assigned),
                priorities(assigned)
        );
    }

    private List<PatientAssignment> assignedPatients(String section, String room, List<PatientAssignment> patients) {
        ArrayList<PatientAssignment> assigned = new ArrayList<>();
        for (PatientAssignment patient : patients) {
            if (patient.getSection().equalsIgnoreCase(section) && patient.getRoom().equalsIgnoreCase(room)) {
                assigned.add(patient);
            }
        }
        return assigned;
    }

    private boolean matches(RoomRow row, OccupancyFilter filter) {
        if (filter == null) {
            return true;
        }
        if (!isAll(filter.getSection()) && !row.getSection().equalsIgnoreCase(filter.getSection())) {
            return false;
        }
        if (filter.getRoomSearch() != null && !filter.getRoomSearch().trim().isEmpty()
                && !row.getRoomNumber().toLowerCase(Locale.ROOT).contains(filter.getRoomSearch().trim().toLowerCase(Locale.ROOT))) {
            return false;
        }
        if (!isAll(filter.getStatus()) && !row.getStatuses().contains(filter.getStatus().toUpperCase(Locale.ROOT))) {
            return false;
        }
        if (!isAll(filter.getPriority()) && !row.getPriorities().contains(filter.getPriority().toUpperCase(Locale.ROOT))) {
            return false;
        }
        return true;
    }

    private int occupiedRooms(List<RoomRow> rows) {
        int count = 0;
        for (RoomRow row : rows) {
            if (row.getOccupiedCount() > 0) {
                count++;
            }
        }
        return count;
    }

    private int occupiedBeds(List<RoomRow> rows) {
        int count = 0;
        for (RoomRow row : rows) {
            count += row.getOccupiedCount();
        }
        return count;
    }

    private int availableCapacity(List<RoomRow> rows) {
        int count = 0;
        for (RoomRow row : rows) {
            count += row.getAvailableCount();
        }
        return count;
    }

    private Map<String, Integer> activePatientsBySection(List<PatientAssignment> patients, OccupancyFilter filter) {
        LinkedHashMap<String, Integer> rows = new LinkedHashMap<>();
        for (PatientAssignment patient : patients) {
            if (!isAll(filter == null ? "All" : filter.getSection()) && !patient.getSection().equalsIgnoreCase(filter.getSection())) {
                continue;
            }
            if ("DECEASED".equalsIgnoreCase(patient.getStatus())) {
                continue;
            }
            rows.merge(patient.getSection(), 1, Integer::sum);
        }
        return rows;
    }

    private Map<String, Integer> criticalEmergencyBySection(List<PatientAssignment> patients, OccupancyFilter filter) {
        LinkedHashMap<String, Integer> rows = new LinkedHashMap<>();
        for (PatientAssignment patient : patients) {
            if (!isAll(filter == null ? "All" : filter.getSection()) && !patient.getSection().equalsIgnoreCase(filter.getSection())) {
                continue;
            }
            if (priorityRank(patient.getPriority()) >= priorityRank("CRITICAL")) {
                rows.merge(patient.getSection(), 1, Integer::sum);
            }
        }
        return rows;
    }

    private String highestPriority(List<PatientAssignment> assigned) {
        String highest = "NORMAL";
        for (PatientAssignment patient : assigned) {
            if (priorityRank(patient.getPriority()) > priorityRank(highest)) {
                highest = normalizedPriority(patient.getPriority());
            }
        }
        return highest;
    }

    private String selectedPatientId(List<PatientAssignment> assigned) {
        PatientAssignment selected = null;
        for (PatientAssignment patient : assigned) {
            if (selected == null || priorityRank(patient.getPriority()) > priorityRank(selected.getPriority())) {
                selected = patient;
            }
        }
        return selected == null ? "" : selected.getPatientId();
    }

    private String patientSummary(List<PatientAssignment> assigned) {
        if (assigned.isEmpty()) {
            return "No assigned patients";
        }
        ArrayList<String> parts = new ArrayList<>();
        for (PatientAssignment patient : assigned) {
            parts.add(patient.getPatientId() + " " + patient.getPatientName() + " (" + normalizedPriority(patient.getPriority()) + ")");
        }
        return String.join("; ", parts);
    }

    private Set<String> statuses(List<PatientAssignment> assigned) {
        LinkedHashSet<String> statuses = new LinkedHashSet<>();
        for (PatientAssignment patient : assigned) {
            statuses.add(patient.getStatus().toUpperCase(Locale.ROOT));
        }
        return statuses;
    }

    private Set<String> priorities(List<PatientAssignment> assigned) {
        LinkedHashSet<String> priorities = new LinkedHashSet<>();
        for (PatientAssignment patient : assigned) {
            priorities.add(normalizedPriority(patient.getPriority()));
        }
        return priorities;
    }

    private int priorityRank(String priority) {
        String normalized = normalizedPriority(priority);
        if ("EMERGENCY".equals(normalized)) {
            return 4;
        }
        if ("CRITICAL".equals(normalized)) {
            return 3;
        }
        if ("HIGH".equals(normalized) || "WARNING".equals(normalized)) {
            return 2;
        }
        return 1;
    }

    private String normalizedPriority(String priority) {
        if (priority == null || priority.isBlank()) {
            return "NORMAL";
        }
        String upper = priority.toUpperCase(Locale.ROOT);
        if ("WARNING".equals(upper)) {
            return "HIGH";
        }
        return upper;
    }

    private boolean isAll(String value) {
        return value == null || value.isBlank() || "All".equalsIgnoreCase(value);
    }

    private String fullName(String firstName, String lastName) {
        String name = (value(firstName) + " " + value(lastName)).trim();
        return name.isBlank() ? "Unknown patient" : name;
    }

    private String defaultValue(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private void ensureSchema() {
        try {
            SchemaInitializer.initialize();
        } catch (Exception e) {
            System.out.println("SQLite room occupancy schema check failed: " + e.getMessage());
        }
    }

    private static class RoomSeed {
        private final long id;
        private final String section;
        private final String roomNumber;
        private final int capacity;
        private final String status;
        private final String notes;

        private RoomSeed(long id, String section, String roomNumber, int capacity, String status, String notes) {
            this.id = id;
            this.section = section;
            this.roomNumber = roomNumber;
            this.capacity = capacity;
            this.status = status;
            this.notes = notes;
        }

        public long getId() { return id; }
        public String getSection() { return section; }
        public String getRoomNumber() { return roomNumber; }
        public int getCapacity() { return capacity; }
        public String getStatus() { return status; }
        public String getNotes() { return notes; }
    }

    private static class PatientAssignment {
        private final String patientId;
        private final String patientName;
        private final String section;
        private final String room;
        private final String status;
        private final String priority;

        private PatientAssignment(String patientId, String patientName, String section, String room, String status, String priority) {
            this.patientId = patientId;
            this.patientName = patientName;
            this.section = section;
            this.room = room;
            this.status = status;
            this.priority = priority;
        }

        public String getPatientId() { return patientId; }
        public String getPatientName() { return patientName; }
        public String getSection() { return section; }
        public String getRoom() { return room; }
        public String getStatus() { return status; }
        public String getPriority() { return priority; }
    }

    public static class OccupancyFilter {
        private final String section;
        private final String roomSearch;
        private final String status;
        private final String priority;

        public OccupancyFilter(String section, String roomSearch, String status, String priority) {
            this.section = section;
            this.roomSearch = roomSearch;
            this.status = status;
            this.priority = priority;
        }

        public String getSection() { return section; }
        public String getRoomSearch() { return roomSearch; }
        public String getStatus() { return status; }
        public String getPriority() { return priority; }
    }

    public static class OccupancyOverview {
        private final int totalRooms;
        private final int occupiedRooms;
        private final int occupiedBeds;
        private final int availableCapacity;
        private final Map<String, Integer> activePatientsBySection;
        private final Map<String, Integer> criticalEmergencyBySection;
        private final boolean derivedFromPatients;
        private final List<RoomRow> rooms;

        public OccupancyOverview(int totalRooms, int occupiedRooms, int occupiedBeds, int availableCapacity,
                                 Map<String, Integer> activePatientsBySection,
                                 Map<String, Integer> criticalEmergencyBySection,
                                 boolean derivedFromPatients, List<RoomRow> rooms) {
            this.totalRooms = totalRooms;
            this.occupiedRooms = occupiedRooms;
            this.occupiedBeds = occupiedBeds;
            this.availableCapacity = availableCapacity;
            this.activePatientsBySection = activePatientsBySection;
            this.criticalEmergencyBySection = criticalEmergencyBySection;
            this.derivedFromPatients = derivedFromPatients;
            this.rooms = rooms;
        }

        public int getTotalRooms() { return totalRooms; }
        public int getOccupiedRooms() { return occupiedRooms; }
        public int getOccupiedBeds() { return occupiedBeds; }
        public int getAvailableCapacity() { return availableCapacity; }
        public Map<String, Integer> getActivePatientsBySection() { return activePatientsBySection; }
        public Map<String, Integer> getCriticalEmergencyBySection() { return criticalEmergencyBySection; }
        public boolean isDerivedFromPatients() { return derivedFromPatients; }
        public List<RoomRow> getRooms() { return rooms; }
    }

    public static class RoomRow {
        private final long roomId;
        private final String section;
        private final String roomNumber;
        private final int capacity;
        private final String roomStatus;
        private final int occupiedCount;
        private final int availableCount;
        private final String patientsInRoom;
        private final String highestPatientPriority;
        private final String selectedPatientId;
        private final String roomNotes;
        private final Set<String> statuses;
        private final Set<String> priorities;

        public RoomRow(long roomId, String section, String roomNumber, int capacity, String roomStatus,
                       int occupiedCount, int availableCount, String patientsInRoom,
                       String highestPatientPriority, String selectedPatientId, String roomNotes,
                       Set<String> statuses, Set<String> priorities) {
            this.roomId = roomId;
            this.section = section;
            this.roomNumber = roomNumber;
            this.capacity = capacity;
            this.roomStatus = roomStatus;
            this.occupiedCount = occupiedCount;
            this.availableCount = availableCount;
            this.patientsInRoom = patientsInRoom;
            this.highestPatientPriority = highestPatientPriority;
            this.selectedPatientId = selectedPatientId;
            this.roomNotes = roomNotes;
            this.statuses = statuses;
            this.priorities = priorities;
        }

        public long getRoomId() { return roomId; }
        public String getSection() { return section; }
        public String getRoomNumber() { return roomNumber; }
        public int getCapacity() { return capacity; }
        public String getRoomStatus() { return roomStatus; }
        public int getOccupiedCount() { return occupiedCount; }
        public int getAvailableCount() { return availableCount; }
        public String getPatientsInRoom() { return patientsInRoom; }
        public String getHighestPatientPriority() { return highestPatientPriority; }
        public String getSelectedPatientId() { return selectedPatientId; }
        public String getRoomNotes() { return roomNotes; }
        public Set<String> getStatuses() { return statuses; }
        public Set<String> getPriorities() { return priorities; }
    }
}
