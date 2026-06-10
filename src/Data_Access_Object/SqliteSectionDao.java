package dao;

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

public class SqliteSectionDao {

    public SqliteSectionDao() {
        ensureSchema();
    }

    public long insertSection(SectionRecord section) throws SQLException {
        String sql = "INSERT INTO sections(name, status, notes, created_at, updated_at) "
                + "VALUES(?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bindSection(statement, section);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : 0;
            }
        }
    }

    public void updateSection(long id, SectionRecord section) throws SQLException {
        String sql = "UPDATE sections SET name = ?, status = ?, notes = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindSection(statement, section);
            statement.setLong(4, id);
            statement.executeUpdate();
        }
    }

    public Optional<SectionRecord> findById(long id) throws SQLException {
        String sql = "SELECT id, name, status, COALESCE(notes, '') AS notes, created_at, updated_at FROM sections WHERE id = ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapSection(resultSet));
                }
            }
        }
        return Optional.empty();
    }

    public Optional<SectionRecord> findByName(String name) throws SQLException {
        String sql = "SELECT id, name, status, COALESCE(notes, '') AS notes, created_at, updated_at "
                + "FROM sections WHERE UPPER(name) = ? LIMIT 1";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value(name).toUpperCase());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapSection(resultSet));
                }
            }
        }
        return Optional.empty();
    }

    public boolean existsByName(String name, long excludedId) throws SQLException {
        String sql = "SELECT 1 FROM sections WHERE UPPER(name) = ? AND id <> ? LIMIT 1";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value(name).toUpperCase());
            statement.setLong(2, excludedId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    public List<SectionRecord> findAll() throws SQLException {
        ArrayList<SectionRecord> sections = new ArrayList<>();
        String sql = "SELECT id, name, status, COALESCE(notes, '') AS notes, created_at, updated_at "
                + "FROM sections ORDER BY status, name COLLATE NOCASE";
        try (Connection connection = DatabaseManager.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                sections.add(mapSection(resultSet));
            }
        }
        return sections;
    }

    public List<String> findActiveSectionNames() throws SQLException {
        ArrayList<String> sections = new ArrayList<>();
        String sql = "SELECT name FROM sections WHERE UPPER(status) = 'ACTIVE' ORDER BY name COLLATE NOCASE";
        try (Connection connection = DatabaseManager.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                sections.add(resultSet.getString("name"));
            }
        }
        return sections;
    }

    public int countActiveRooms(String sectionName) throws SQLException {
        String sql = "SELECT COUNT(*) FROM rooms WHERE UPPER(section) = ? AND UPPER(COALESCE(status, 'ACTIVE')) = 'ACTIVE'";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value(sectionName).toUpperCase());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt(1) : 0;
            }
        }
    }

    public int countActivePatients(String sectionName) throws SQLException {
        String sql = "SELECT COUNT(*) FROM patients WHERE UPPER(COALESCE(section, '')) = ? "
                + "AND UPPER(COALESCE(status, 'ACTIVE')) NOT IN ('DECEASED', 'DISCHARGED', 'INACTIVE')";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value(sectionName).toUpperCase());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt(1) : 0;
            }
        }
    }

    public int countActiveUsers(String sectionName) throws SQLException {
        String sql = "SELECT COUNT(*) FROM users WHERE UPPER(COALESCE(section, '')) = ? AND active = 1";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value(sectionName).toUpperCase());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt(1) : 0;
            }
        }
    }

    public void renameRoomsAndPatients(String oldName, String newName) throws SQLException {
        try (Connection connection = DatabaseManager.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement rooms = connection.prepareStatement("UPDATE rooms SET section = ?, updated_at = CURRENT_TIMESTAMP WHERE UPPER(section) = ?");
                 PreparedStatement patients = connection.prepareStatement("UPDATE patients SET section = ?, updated_at = CURRENT_TIMESTAMP WHERE UPPER(COALESCE(section, '')) = ?");
                 PreparedStatement users = connection.prepareStatement("UPDATE users SET section = ? WHERE UPPER(COALESCE(section, '')) = ?")) {
                rooms.setString(1, value(newName));
                rooms.setString(2, value(oldName).toUpperCase());
                rooms.executeUpdate();
                patients.setString(1, value(newName));
                patients.setString(2, value(oldName).toUpperCase());
                patients.executeUpdate();
                users.setString(1, value(newName));
                users.setString(2, value(oldName).toUpperCase());
                users.executeUpdate();
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private void bindSection(PreparedStatement statement, SectionRecord section) throws SQLException {
        statement.setString(1, value(section.getName()));
        statement.setString(2, value(section.getStatus()).isBlank() ? "ACTIVE" : value(section.getStatus()).toUpperCase());
        statement.setString(3, value(section.getNotes()));
    }

    private SectionRecord mapSection(ResultSet resultSet) throws SQLException {
        return new SectionRecord(
                resultSet.getLong("id"),
                resultSet.getString("name"),
                resultSet.getString("status"),
                resultSet.getString("notes"),
                resultSet.getString("created_at"),
                resultSet.getString("updated_at")
        );
    }

    private void ensureSchema() {
        try {
            SchemaInitializer.initialize();
        } catch (Exception e) {
            System.out.println("SQLite section schema check failed: " + e.getMessage());
        }
    }

    private String value(String value) {
        return value == null ? "" : value.trim();
    }

    public static class SectionRecord {
        private final long id;
        private final String name;
        private final String status;
        private final String notes;
        private final String createdAt;
        private final String updatedAt;

        public SectionRecord(long id, String name, String status, String notes, String createdAt, String updatedAt) {
            this.id = id;
            this.name = name;
            this.status = status;
            this.notes = notes;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
        }

        public SectionRecord(String name, String status, String notes) {
            this(0, name, status, notes, "", "");
        }

        public long getId() { return id; }
        public String getName() { return name; }
        public String getStatus() { return status; }
        public String getNotes() { return notes; }
        public String getCreatedAt() { return createdAt; }
        public String getUpdatedAt() { return updatedAt; }
    }
}
