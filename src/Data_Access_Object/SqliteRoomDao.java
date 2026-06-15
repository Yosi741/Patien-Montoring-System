package Data_Access_Object;

import database.DatabaseManager;
import database.SchemaInitializer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SqliteRoomDao {

    public SqliteRoomDao() {
        ensureSchema();
    }

    public long insertRoom(RoomRecord room) throws SQLException {
        String sql = "INSERT INTO rooms(section, room_number, capacity, status, notes, updated_at) "
                + "VALUES(?, ?, ?, ?, ?, CURRENT_TIMESTAMP)";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bindRoom(statement, room);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : 0;
            }
        }
    }

    public long insertGeneratedRoom(RoomRecord room, int floorNumber, int roomSequence) throws SQLException {
        String sql = "INSERT INTO rooms(section, room_number, capacity, status, notes, floor_number, room_sequence, updated_at) "
                + "VALUES(?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bindRoom(statement, room);
            statement.setInt(6, floorNumber);
            statement.setInt(7, roomSequence);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : 0;
            }
        }
    }

    public void updateRoom(long id, RoomRecord room) throws SQLException {
        String sql = "UPDATE rooms SET section = ?, room_number = ?, capacity = ?, status = ?, notes = ?, "
                + "updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindRoom(statement, room);
            statement.setLong(6, id);
            statement.executeUpdate();
        }
    }

    public void deactivateRoom(long id) throws SQLException {
        String sql = "UPDATE rooms SET status = 'INACTIVE', updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            statement.executeUpdate();
        }
    }

    public Optional<RoomDetail> findById(long id) throws SQLException {
        String sql = selectRoomSql() + " WHERE r.id = ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapRoom(resultSet));
                }
            }
        }
        return Optional.empty();
    }

    public List<RoomDetail> findBySection(String section) throws SQLException {
        ArrayList<RoomDetail> rooms = new ArrayList<>();
        String sql = selectRoomSql() + " WHERE UPPER(r.section) = ? ORDER BY r.room_number COLLATE NOCASE";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value(section).toUpperCase());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rooms.add(mapRoom(resultSet));
                }
            }
        }
        return rooms;
    }

    public void updateRoomStatus(long id, String status) throws SQLException {
        String sql = "UPDATE rooms SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value(status).isBlank() ? "INACTIVE" : value(status).toUpperCase());
            statement.setLong(2, id);
            statement.executeUpdate();
        }
    }

    public List<RoomDetail> findAssignableRooms() throws SQLException {
        ArrayList<RoomDetail> rooms = new ArrayList<>();
        String sql = selectRoomSql()
                + " WHERE UPPER(COALESCE(r.status, 'ACTIVE')) = 'ACTIVE' "
                + "ORDER BY r.section COLLATE NOCASE, r.room_number COLLATE NOCASE";
        try (Connection connection = DatabaseManager.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                rooms.add(mapRoom(resultSet));
            }
        }
        return rooms;
    }

    public List<String> findActiveRoomsForSection(String section) throws SQLException {
        ArrayList<String> rooms = new ArrayList<>();
        String sql = "SELECT room_number FROM rooms "
                + "WHERE UPPER(section) = ? AND UPPER(COALESCE(status, 'ACTIVE')) = 'ACTIVE' "
                + "ORDER BY room_number COLLATE NOCASE";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value(section).toUpperCase());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rooms.add(resultSet.getString("room_number"));
                }
            }
        }
        return rooms;
    }

    public boolean existsSectionRoom(String section, String roomNumber, long excludedId) throws SQLException {
        String sql = "SELECT 1 FROM rooms WHERE UPPER(section) = ? AND UPPER(room_number) = ? AND id <> ? LIMIT 1";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value(section).toUpperCase());
            statement.setString(2, value(roomNumber).toUpperCase());
            statement.setLong(3, excludedId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    public int occupancy(String section, String roomNumber) throws SQLException {
        return occupancy(section, roomNumber, "");
    }

    public int occupancy(String section, String roomNumber, String excludedPatientId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM patients "
                + "WHERE UPPER(COALESCE(section, '')) = ? "
                + "AND UPPER(COALESCE(room, '')) = ? "
                + "AND UPPER(COALESCE(status, 'ACTIVE')) NOT IN ('DECEASED', 'DISCHARGED', 'INACTIVE') "
                + "AND patient_id <> ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value(section).toUpperCase());
            statement.setString(2, value(roomNumber).toUpperCase());
            statement.setString(3, value(excludedPatientId));
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt(1) : 0;
            }
        }
    }

    public List<RoomNumberMetadata> findRoomNumberMetadata() throws SQLException {
        ArrayList<RoomNumberMetadata> rows = new ArrayList<>();
        String sql = "SELECT section, room_number, floor_number, room_sequence FROM rooms";
        try (Connection connection = DatabaseManager.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                rows.add(new RoomNumberMetadata(
                        resultSet.getString("section"),
                        resultSet.getString("room_number"),
                        resultSet.getObject("floor_number") == null ? null : resultSet.getInt("floor_number"),
                        resultSet.getObject("room_sequence") == null ? null : resultSet.getInt("room_sequence")
                ));
            }
        }
        return rows;
    }

    private String selectRoomSql() {
        return "SELECT r.id, r.section, r.room_number, r.capacity, "
                + "COALESCE(r.status, 'ACTIVE') AS status, COALESCE(r.notes, '') AS notes, "
                + "COALESCE(r.updated_at, '') AS updated_at, "
                + "(SELECT COUNT(*) FROM patients p "
                + " WHERE UPPER(COALESCE(p.section, '')) = UPPER(r.section) "
                + " AND UPPER(COALESCE(p.room, '')) = UPPER(r.room_number) "
                + " AND UPPER(COALESCE(p.status, 'ACTIVE')) NOT IN ('DECEASED', 'DISCHARGED', 'INACTIVE')) AS occupied_count "
                + "FROM rooms r";
    }

    private void bindRoom(PreparedStatement statement, RoomRecord room) throws SQLException {
        statement.setString(1, value(room.getSection()));
        statement.setString(2, value(room.getRoomNumber()));
        statement.setInt(3, Math.max(1, room.getCapacity()));
        statement.setString(4, value(room.getStatus()).isBlank() ? "ACTIVE" : value(room.getStatus()).toUpperCase());
        statement.setString(5, value(room.getNotes()));
    }

    private RoomDetail mapRoom(ResultSet resultSet) throws SQLException {
        int capacity = Math.max(1, resultSet.getInt("capacity"));
        int occupied = Math.max(0, resultSet.getInt("occupied_count"));
        return new RoomDetail(
                resultSet.getLong("id"),
                resultSet.getString("section"),
                resultSet.getString("room_number"),
                capacity,
                resultSet.getString("status"),
                resultSet.getString("notes"),
                resultSet.getString("updated_at"),
                occupied,
                Math.max(0, capacity - occupied)
        );
    }

    private void ensureSchema() {
        try {
            SchemaInitializer.initialize();
        } catch (Exception e) {
            System.out.println("SQLite room schema check failed: " + e.getMessage());
        }
    }

    private String value(String value) {
        return value == null ? "" : value.trim();
    }

    public static class RoomRecord {
        private final String section;
        private final String roomNumber;
        private final int capacity;
        private final String status;
        private final String notes;

        public RoomRecord(String section, String roomNumber, int capacity, String status, String notes) {
            this.section = section;
            this.roomNumber = roomNumber;
            this.capacity = capacity;
            this.status = status;
            this.notes = notes;
        }

        public String getSection() { return section; }
        public String getRoomNumber() { return roomNumber; }
        public int getCapacity() { return capacity; }
        public String getStatus() { return status; }
        public String getNotes() { return notes; }
    }

    public static class RoomDetail {
        private final long id;
        private final String section;
        private final String roomNumber;
        private final int capacity;
        private final String status;
        private final String notes;
        private final String updatedAt;
        private final int occupiedCount;
        private final int availableCount;

        public RoomDetail(long id, String section, String roomNumber, int capacity, String status, String notes,
                          String updatedAt, int occupiedCount, int availableCount) {
            this.id = id;
            this.section = section;
            this.roomNumber = roomNumber;
            this.capacity = capacity;
            this.status = status;
            this.notes = notes;
            this.updatedAt = updatedAt;
            this.occupiedCount = occupiedCount;
            this.availableCount = availableCount;
        }

        public long getId() { return id; }
        public String getSection() { return section; }
        public String getRoomNumber() { return roomNumber; }
        public int getCapacity() { return capacity; }
        public String getStatus() { return status; }
        public String getNotes() { return notes; }
        public String getUpdatedAt() { return updatedAt; }
        public int getOccupiedCount() { return occupiedCount; }
        public int getAvailableCount() { return availableCount; }

        public String getDisplayName() {
            return section + " / Room " + roomNumber + " (" + availableCount + " available)";
        }
    }

    public static class RoomNumberMetadata {
        private final String section;
        private final String roomNumber;
        private final Integer floorNumber;
        private final Integer roomSequence;

        public RoomNumberMetadata(String section, String roomNumber, Integer floorNumber, Integer roomSequence) {
            this.section = section == null ? "" : section;
            this.roomNumber = roomNumber == null ? "" : roomNumber;
            this.floorNumber = floorNumber;
            this.roomSequence = roomSequence;
        }

        public String getSection() { return section; }
        public String getRoomNumber() { return roomNumber; }
        public Integer getFloorNumber() { return floorNumber; }
        public Integer getRoomSequence() { return roomSequence; }
    }
}
