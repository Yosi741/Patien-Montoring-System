package pages.user.dao;

import app.DatabaseManager;
import app.SchemaInitializer;
import app.PasswordHasher;
import pages.user.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class SqliteUserDao implements UserDao {

    public SqliteUserDao() {
        ensureSchema();
    }

    @Override
    public Optional<User> findById(String username) throws SQLException {
        return findByUsername(username);
    }

    @Override
    public Optional<User> findByUsername(String username) throws SQLException {
        String sql = "SELECT username, password_hash, role, section, staff_id FROM users WHERE LOWER(username) = LOWER(?) AND active = 1";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapUser(resultSet));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean usernameExists(String username) throws SQLException {
        String sql = "SELECT 1 FROM users WHERE LOWER(username) = LOWER(?)";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    public boolean usernameExistsExcept(String username, String excludedUsername) throws SQLException {
        String sql = "SELECT 1 FROM users WHERE LOWER(username) = LOWER(?) AND LOWER(username) <> LOWER(?)";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            statement.setString(2, excludedUsername);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    @Override
    public List<User> findAll() throws SQLException {
        ArrayList<User> users = new ArrayList<>();
        String sql = "SELECT username, password_hash, role, section, staff_id FROM users ORDER BY username";
        try (Connection connection = DatabaseManager.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                users.add(mapUser(resultSet));
            }
        }
        return users;
    }

    @Override
    public void save(User user) throws SQLException {
        String passwordHash = PasswordHasher.isHashed(user.getPassword())
                ? user.getPassword()
                : PasswordHasher.hash(user.getPassword().toCharArray());
        saveHashed(user.getUsername(), passwordHash, user.getRole(), user.getSection());
    }

    public void saveHashed(String username, String passwordHash, String role, String section) throws SQLException {
        String sql = "INSERT INTO users(username, password_hash, role, section, staff_id, active) VALUES(?, ?, ?, ?, ?, 1) "
                + "ON CONFLICT(username) DO UPDATE SET "
                + "password_hash = excluded.password_hash, "
                + "role = excluded.role, "
                + "section = excluded.section, "
                + "staff_id = CASE WHEN COALESCE(TRIM(staff_id), '') = '' THEN excluded.staff_id ELSE staff_id END, "
                + "active = 1";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            String staffId = existingStaffId(username).orElseGet(() -> {
                try {
                    return generateNextStaffId();
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            });
            statement.setString(1, username);
            statement.setString(2, passwordHash);
            statement.setString(3, role);
            statement.setString(4, section == null || section.isBlank() ? "All" : section);
            statement.setString(5, staffId);
            statement.executeUpdate();
        } catch (RuntimeException e) {
            if (e.getCause() instanceof SQLException sqlException) {
                throw sqlException;
            }
            throw e;
        }
    }

    @Override
    public void deleteById(String username) throws SQLException {
        String sql = "UPDATE users SET active = 0 WHERE username = ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            statement.executeUpdate();
        }
    }

    public boolean verifyPassword(String username, char[] password) throws SQLException {
        return findByUsername(username)
                .map(user -> PasswordHasher.verify(password, user.getPassword()))
                .orElse(false);
    }

    public int count() throws SQLException {
        String sql = "SELECT COUNT(*) FROM users";
        try (Connection connection = DatabaseManager.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            return resultSet.next() ? resultSet.getInt(1) : 0;
        }
    }

    public List<UserDirectoryRow> findDirectoryRows(UserDirectoryFilter filter) throws SQLException {
        ArrayList<UserDirectoryRow> rows = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT u.id, u.staff_id, u.username, u.role, u.section, "
                + "COALESCE(NULLIF(p.email, ''), NULLIF(u.email, ''), '') AS email, u.active, u.created_at "
                + "FROM users u LEFT JOIN user_profiles p ON p.username = u.username WHERE 1 = 1 ");
        ArrayList<String> params = new ArrayList<>();

        if (filter != null && filter.getSearch() != null && !filter.getSearch().trim().isEmpty()) {
            sql.append("AND (u.staff_id LIKE ? OR u.username LIKE ? OR COALESCE(NULLIF(p.email, ''), NULLIF(u.email, ''), '') LIKE ?) ");
            String like = "%" + filter.getSearch().trim() + "%";
            params.add(like);
            params.add(like);
            params.add(like);
        }
        if (filter != null && filter.getSection() != null && !filter.getSection().isBlank()
                && !"All".equalsIgnoreCase(filter.getSection())) {
            sql.append("AND u.section = ? ");
            params.add(filter.getSection());
        }
        if (filter != null && filter.getActiveStatus() != null && !filter.getActiveStatus().isBlank()
                && !"All".equalsIgnoreCase(filter.getActiveStatus())) {
            sql.append("AND u.active = ? ");
            params.add("Active".equalsIgnoreCase(filter.getActiveStatus()) ? "1" : "0");
        }
        if (filter != null && filter.getRoleGroup() != null && !filter.getRoleGroup().isBlank()
                && !"All".equalsIgnoreCase(filter.getRoleGroup())) {
            appendRoleGroupFilter(sql, params, filter.getRoleGroup());
        }

        sql.append("ORDER BY ");
        sql.append(roleSortExpression());
        sql.append(", COALESCE(u.section, ''), u.username COLLATE NOCASE");

        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                statement.setString(i + 1, params.get(i));
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(new UserDirectoryRow(
                            resultSet.getLong("id"),
                            blank(resultSet.getString("staff_id")),
                            resultSet.getString("username"),
                            resultSet.getString("role"),
                            resultSet.getString("section"),
                            blank(resultSet.getString("email")),
                            resultSet.getInt("active") == 1,
                            resultSet.getString("created_at")
                    ));
                }
            }
        }
        return rows;
    }

    public List<String> findDistinctSections() throws SQLException {
        ArrayList<String> sections = new ArrayList<>();
        String sql = "SELECT DISTINCT section FROM users WHERE section IS NOT NULL AND TRIM(section) <> '' ORDER BY section COLLATE NOCASE";
        try (Connection connection = DatabaseManager.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                sections.add(resultSet.getString("section"));
            }
        }
        return sections;
    }

    public List<UserTarget> findMessageTargets() throws SQLException {
        return findMessageTargetsExcept("");
    }

    public List<UserTarget> findMessageTargetsExcept(String excludedUsername) throws SQLException {
        ArrayList<UserTarget> targets = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT u.username, u.role, u.section, "
                + "COALESCE(NULLIF(p.email, ''), NULLIF(u.email, ''), '') AS email "
                + "FROM users u LEFT JOIN user_profiles p ON p.username = u.username "
                + "WHERE u.active = 1 ");
        boolean exclude = excludedUsername != null && !excludedUsername.trim().isEmpty();
        if (exclude) {
            sql.append("AND LOWER(u.username) <> LOWER(?) ");
        }
        sql.append("ORDER BY u.username COLLATE NOCASE");
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            if (exclude) {
                statement.setString(1, excludedUsername.trim());
            }
            try (ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                String username = resultSet.getString("username");
                targets.add(new UserTarget(
                        username,
                        username,
                        resultSet.getString("role"),
                        resultSet.getString("section"),
                        resultSet.getString("email")
                ));
            }
            }
        }
        return targets;
    }

    public List<String> findActiveUsernamesByRoleGroupAndSection(String roleGroup, String section) throws SQLException {
        ArrayList<String> usernames = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT username FROM users WHERE active = 1 ");
        ArrayList<String> params = new ArrayList<>();
        if (roleGroup != null && !roleGroup.isBlank() && !"All".equalsIgnoreCase(roleGroup)) {
            appendRoleGroupFilter(sql, params, roleGroup);
        }
        if (section != null && !section.isBlank() && !"All".equalsIgnoreCase(section)) {
            sql.append("AND section = ? ");
            params.add(section);
        }
        sql.append("ORDER BY username COLLATE NOCASE");
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                statement.setString(i + 1, params.get(i));
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    usernames.add(resultSet.getString("username"));
                }
            }
        }
        return usernames;
    }

    public Optional<UserDirectoryRow> findDirectoryRowByUsername(String username) throws SQLException {
        String sql = "SELECT u.id, u.staff_id, u.username, u.role, u.section, "
                + "COALESCE(NULLIF(p.email, ''), NULLIF(u.email, ''), '') AS email, u.active, u.created_at "
                + "FROM users u LEFT JOIN user_profiles p ON p.username = u.username WHERE u.username = ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(new UserDirectoryRow(
                            resultSet.getLong("id"),
                            blank(resultSet.getString("staff_id")),
                            resultSet.getString("username"),
                            resultSet.getString("role"),
                            resultSet.getString("section"),
                            blank(resultSet.getString("email")),
                            resultSet.getInt("active") == 1,
                            resultSet.getString("created_at")
                    ));
                }
            }
        }
        return Optional.empty();
    }

    public Optional<PasswordResetContact> findPasswordResetContact(String username) throws SQLException {
        String sql = "SELECT u.username, COALESCE(NULLIF(p.email, ''), NULLIF(u.email, ''), '') AS email "
                + "FROM users u LEFT JOIN user_profiles p ON p.username = u.username "
                + "WHERE LOWER(u.username) = LOWER(?) AND u.active = 1";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username == null ? "" : username.trim());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(new PasswordResetContact(
                            resultSet.getString("username"),
                            blank(resultSet.getString("email"))
                    ));
                }
            }
        }
        return Optional.empty();
    }

    public void insertUser(UserWriteRecord record, String passwordHash) throws SQLException {
        String sql = "INSERT INTO users(username, password_hash, role, section, staff_id, active) VALUES(?, ?, ?, ?, ?, ?)";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, record.getUsername());
            statement.setString(2, passwordHash);
            statement.setString(3, record.getRole());
            statement.setString(4, blankToAll(record.getSection()));
            statement.setString(5, record.getStaffId());
            statement.setInt(6, record.isActive() ? 1 : 0);
            statement.executeUpdate();
        }
    }

    public void updateUser(UserWriteRecord record) throws SQLException {
        updateUser(record.getUsername(), record);
    }

    public void updateUser(String originalUsername, UserWriteRecord record) throws SQLException {
        String oldUsername = originalUsername == null ? "" : originalUsername.trim();
        String sql = "UPDATE users SET role = ?, section = ?, active = ? WHERE LOWER(username) = LOWER(?)";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, record.getRole());
            statement.setString(2, blankToAll(record.getSection()));
            statement.setInt(3, record.isActive() ? 1 : 0);
            statement.setString(4, oldUsername);
            statement.executeUpdate();
        }
    }

    public void deactivateUser(String username) throws SQLException {
        String sql = "UPDATE users SET active = 0 WHERE username = ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            statement.executeUpdate();
        }
    }

    public void resetPasswordHash(String username, String passwordHash) throws SQLException {
        String sql = "UPDATE users SET password_hash = ? WHERE username = ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, passwordHash);
            statement.setString(2, username);
            statement.executeUpdate();
        }
    }

    public void updateEmail(String username, String email) throws SQLException {
        String sql = "UPDATE users SET email = ? WHERE LOWER(username) = LOWER(?)";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, email == null ? "" : email.trim());
            statement.setString(2, username == null ? "" : username.trim());
            statement.executeUpdate();
        }
    }

    private User mapUser(ResultSet resultSet) throws SQLException {
        return new User(
                resultSet.getString("username"),
                resultSet.getString("password_hash"),
                resultSet.getString("role"),
                resultSet.getString("section"),
                blank(resultSet.getString("staff_id"))
        );
    }

    public boolean staffIdExists(String staffId) throws SQLException {
        return staffIdExistsExcept(staffId, null);
    }

    public boolean staffIdExistsExcept(String staffId, String excludedUsername) throws SQLException {
        String normalized = normalizeStaffId(staffId);
        if (normalized.isBlank()) {
            return false;
        }
        StringBuilder sql = new StringBuilder("SELECT 1 FROM users WHERE UPPER(staff_id) = UPPER(?)");
        boolean exclude = excludedUsername != null && !excludedUsername.trim().isEmpty();
        if (exclude) {
            sql.append(" AND LOWER(username) <> LOWER(?)");
        }
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            statement.setString(1, normalized);
            if (exclude) {
                statement.setString(2, excludedUsername.trim());
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    public String generateNextStaffId() throws SQLException {
        String sql = "SELECT COALESCE(MAX(CAST(SUBSTR(staff_id, 2) AS INTEGER)), 0) FROM users "
                + "WHERE staff_id IS NOT NULL AND UPPER(staff_id) GLOB 'U[0-9]*'";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            long nextNumber = resultSet.next() ? resultSet.getLong(1) + 1 : 1L;
            return formatStaffId(nextNumber);
        }
    }

    public Optional<String> existingStaffId(String username) throws SQLException {
        String sql = "SELECT staff_id FROM users WHERE LOWER(username) = LOWER(?)";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username == null ? "" : username.trim());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    String staffId = normalizeStaffId(resultSet.getString("staff_id"));
                    if (!staffId.isBlank()) {
                        return Optional.of(staffId);
                    }
                }
            }
        }
        return Optional.empty();
    }

    private String normalizeStaffId(String staffId) {
        if (staffId == null) {
            return "";
        }
        String trimmed = staffId.trim().toUpperCase(Locale.ROOT);
        return trimmed.matches("U\\d{4,}") ? trimmed : "";
    }

    private String formatStaffId(long number) {
        return String.format(Locale.ROOT, "U%04d", Math.max(1L, number));
    }

    private String blank(String value) {
        return value == null ? "" : value.trim();
    }

    private String blankToAll(String section) {
        return section == null || section.isBlank() ? "All" : section.trim();
    }

    private void appendRoleGroupFilter(StringBuilder sql, ArrayList<String> params, String roleGroup) {
        String group = roleGroup.toUpperCase();
        if ("ADMIN".equals(group)) {
            sql.append("AND UPPER(u.role) LIKE ? ");
            params.add("%ADMIN%");
        } else if ("DOCTOR".equals(group)) {
            sql.append("AND (UPPER(u.role) LIKE ? OR UPPER(u.role) LIKE ? OR UPPER(u.role) LIKE ?) ");
            params.add("%DOCTOR%");
            params.add("%MEDICAL%");
            params.add("%DEPARTMENT HEAD%");
        } else if ("NURSE".equals(group)) {
            sql.append("AND (UPPER(u.role) LIKE ? OR UPPER(u.role) LIKE ?) ");
            params.add("%NURSE%");
            params.add("%NURSING%");
        } else if ("STAFF".equals(group)) {
            sql.append("AND UPPER(u.role) NOT LIKE ? AND UPPER(u.role) NOT LIKE ? AND UPPER(u.role) NOT LIKE ? ");
            sql.append("AND UPPER(u.role) NOT LIKE ? AND UPPER(u.role) NOT LIKE ? AND UPPER(u.role) NOT LIKE ? ");
            sql.append("AND TRIM(COALESCE(u.role, '')) <> '' AND UPPER(u.role) <> ? ");
            params.add("%ADMIN%");
            params.add("%DOCTOR%");
            params.add("%MEDICAL%");
            params.add("%DEPARTMENT HEAD%");
            params.add("%NURSE%");
            params.add("%NURSING%");
            params.add("UNKNOWN");
        }
    }

    private String roleSortExpression() {
        return "CASE "
                + "WHEN UPPER(u.role) LIKE '%ADMIN%' THEN 1 "
                + "WHEN UPPER(u.role) LIKE '%DOCTOR%' OR UPPER(u.role) LIKE '%MEDICAL%' OR UPPER(u.role) LIKE '%DEPARTMENT HEAD%' THEN 2 "
                + "WHEN UPPER(u.role) LIKE '%NURSE%' OR UPPER(u.role) LIKE '%NURSING%' THEN 3 "
                + "WHEN TRIM(COALESCE(u.role, '')) = '' OR UPPER(u.role) = 'UNKNOWN' THEN 5 "
                + "ELSE 4 END";
    }

    private void ensureSchema() {
        try {
            SchemaInitializer.initialize();
        } catch (Exception e) {
            System.out.println("SQLite user schema check failed: " + e.getMessage());
        }
    }

    public static class UserDirectoryFilter {
        private final String search;
        private final String roleGroup;
        private final String section;
        private final String activeStatus;

        public UserDirectoryFilter(String search, String roleGroup, String section, String activeStatus) {
            this.search = search;
            this.roleGroup = roleGroup;
            this.section = section;
            this.activeStatus = activeStatus;
        }

        public String getSearch() { return search; }
        public String getRoleGroup() { return roleGroup; }
        public String getSection() { return section; }
        public String getActiveStatus() { return activeStatus; }
    }

    public static class UserDirectoryRow {
        private final long id;
        private final String staffId;
        private final String username;
        private final String role;
        private final String section;
        private final String email;
        private final boolean active;
        private final String createdAt;

        public UserDirectoryRow(long id, String staffId, String username, String role, String section, String email, boolean active, String createdAt) {
            this.id = id;
            this.staffId = staffId == null ? "" : staffId;
            this.username = username;
            this.role = role;
            this.section = section;
            this.email = email == null ? "" : email;
            this.active = active;
            this.createdAt = createdAt;
        }

        public long getId() { return id; }
        public String getStaffId() { return staffId; }
        public String getUsername() { return username; }
        public String getRole() { return role; }
        public String getSection() { return section; }
        public String getEmail() { return email; }
        public boolean isActive() { return active; }
        public String getActiveStatus() { return active ? "Active" : "Inactive"; }
        public String getCreatedAt() { return createdAt; }
    }

    public static class UserWriteRecord {
        private final String staffId;
        private final String username;
        private final String role;
        private final String section;
        private final boolean active;

        public UserWriteRecord(String staffId, String username, String role, String section, boolean active) {
            this.staffId = staffId == null ? "" : staffId.trim().toUpperCase(Locale.ROOT);
            this.username = username == null ? "" : username.trim();
            this.role = role == null ? "" : role.trim();
            this.section = section == null ? "" : section.trim();
            this.active = active;
        }

        public String getStaffId() { return staffId; }
        public String getUsername() { return username; }
        public String getRole() { return role; }
        public String getSection() { return section; }
        public boolean isActive() { return active; }
    }

    public static class UserTarget {
        private final String username;
        private final String displayName;
        private final String role;
        private final String section;
        private final String email;

        public UserTarget(String username, String displayName, String role, String section, String email) {
            this.username = username == null ? "" : username;
            this.displayName = displayName == null || displayName.isBlank() ? this.username : displayName;
            this.role = role == null ? "" : role;
            this.section = section == null ? "" : section;
            this.email = email == null ? "" : email;
        }

        public String getUsername() { return username; }
        public String getDisplayName() { return displayName; }
        public String getRole() { return role; }
        public String getSection() { return section; }
        public String getEmail() { return email; }

        public String getDisplayText() {
            String mail = email.isBlank() ? "no email" : email;
            return username + " | " + role + " | " + section + " | " + mail;
        }

        @Override
        public String toString() {
            return getDisplayText();
        }
    }

    public record PasswordResetContact(String username, String email) {
    }
}
