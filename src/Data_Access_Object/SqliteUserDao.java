package Data_Access_Object;

import database.DatabaseManager;
import security.PasswordHasher;
import users.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SqliteUserDao implements UserDao {

    @Override
    public Optional<User> findById(String username) throws SQLException {
        return findByUsername(username);
    }

    @Override
    public Optional<User> findByUsername(String username) throws SQLException {
        String sql = "SELECT username, password_hash, role, section FROM users WHERE LOWER(username) = LOWER(?) AND active = 1";
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
        String sql = "SELECT username, password_hash, role, section FROM users ORDER BY username";
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
        String sql = "INSERT INTO users(username, password_hash, role, section, active) VALUES(?, ?, ?, ?, 1) "
                + "ON CONFLICT(username) DO UPDATE SET "
                + "password_hash = excluded.password_hash, "
                + "role = excluded.role, "
                + "section = excluded.section, "
                + "active = 1";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            statement.setString(2, passwordHash);
            statement.setString(3, role);
            statement.setString(4, section == null || section.isBlank() ? "All" : section);
            statement.executeUpdate();
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
        StringBuilder sql = new StringBuilder("SELECT id, username, role, section, active, created_at FROM users WHERE 1 = 1 ");
        ArrayList<String> params = new ArrayList<>();

        if (filter != null && filter.getSearch() != null && !filter.getSearch().trim().isEmpty()) {
            sql.append("AND username LIKE ? ");
            params.add("%" + filter.getSearch().trim() + "%");
        }
        if (filter != null && filter.getSection() != null && !filter.getSection().isBlank()
                && !"All".equalsIgnoreCase(filter.getSection())) {
            sql.append("AND section = ? ");
            params.add(filter.getSection());
        }
        if (filter != null && filter.getActiveStatus() != null && !filter.getActiveStatus().isBlank()
                && !"All".equalsIgnoreCase(filter.getActiveStatus())) {
            sql.append("AND active = ? ");
            params.add("Active".equalsIgnoreCase(filter.getActiveStatus()) ? "1" : "0");
        }
        if (filter != null && filter.getRoleGroup() != null && !filter.getRoleGroup().isBlank()
                && !"All".equalsIgnoreCase(filter.getRoleGroup())) {
            appendRoleGroupFilter(sql, params, filter.getRoleGroup());
        }

        sql.append("ORDER BY ");
        sql.append(roleSortExpression());
        sql.append(", COALESCE(section, ''), username COLLATE NOCASE");

        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                statement.setString(i + 1, params.get(i));
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(new UserDirectoryRow(
                            resultSet.getLong("id"),
                            resultSet.getString("username"),
                            resultSet.getString("role"),
                            resultSet.getString("section"),
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
        String sql = "SELECT id, username, role, section, active, created_at FROM users WHERE username = ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(new UserDirectoryRow(
                            resultSet.getLong("id"),
                            resultSet.getString("username"),
                            resultSet.getString("role"),
                            resultSet.getString("section"),
                            resultSet.getInt("active") == 1,
                            resultSet.getString("created_at")
                    ));
                }
            }
        }
        return Optional.empty();
    }

    public void insertUser(UserWriteRecord record, String passwordHash) throws SQLException {
        String sql = "INSERT INTO users(username, password_hash, role, section, active) VALUES(?, ?, ?, ?, ?)";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, record.getUsername());
            statement.setString(2, passwordHash);
            statement.setString(3, record.getRole());
            statement.setString(4, blankToAll(record.getSection()));
            statement.setInt(5, record.isActive() ? 1 : 0);
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
                resultSet.getString("section")
        );
    }

    private String blankToAll(String section) {
        return section == null || section.isBlank() ? "All" : section.trim();
    }

    private void appendRoleGroupFilter(StringBuilder sql, ArrayList<String> params, String roleGroup) {
        String group = roleGroup.toUpperCase();
        if ("ADMIN".equals(group)) {
            sql.append("AND UPPER(role) LIKE ? ");
            params.add("%ADMIN%");
        } else if ("DOCTOR".equals(group)) {
            sql.append("AND (UPPER(role) LIKE ? OR UPPER(role) LIKE ? OR UPPER(role) LIKE ?) ");
            params.add("%DOCTOR%");
            params.add("%MEDICAL%");
            params.add("%DEPARTMENT HEAD%");
        } else if ("NURSE".equals(group)) {
            sql.append("AND (UPPER(role) LIKE ? OR UPPER(role) LIKE ?) ");
            params.add("%NURSE%");
            params.add("%NURSING%");
        } else if ("STAFF".equals(group)) {
            sql.append("AND UPPER(role) NOT LIKE ? AND UPPER(role) NOT LIKE ? AND UPPER(role) NOT LIKE ? ");
            sql.append("AND UPPER(role) NOT LIKE ? AND UPPER(role) NOT LIKE ? AND UPPER(role) NOT LIKE ? ");
            sql.append("AND TRIM(COALESCE(role, '')) <> '' AND UPPER(role) <> ? ");
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
                + "WHEN UPPER(role) LIKE '%ADMIN%' THEN 1 "
                + "WHEN UPPER(role) LIKE '%DOCTOR%' OR UPPER(role) LIKE '%MEDICAL%' OR UPPER(role) LIKE '%DEPARTMENT HEAD%' THEN 2 "
                + "WHEN UPPER(role) LIKE '%NURSE%' OR UPPER(role) LIKE '%NURSING%' THEN 3 "
                + "WHEN TRIM(COALESCE(role, '')) = '' OR UPPER(role) = 'UNKNOWN' THEN 5 "
                + "ELSE 4 END";
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
        private final String username;
        private final String role;
        private final String section;
        private final boolean active;
        private final String createdAt;

        public UserDirectoryRow(long id, String username, String role, String section, boolean active, String createdAt) {
            this.id = id;
            this.username = username;
            this.role = role;
            this.section = section;
            this.active = active;
            this.createdAt = createdAt;
        }

        public long getId() { return id; }
        public String getUsername() { return username; }
        public String getRole() { return role; }
        public String getSection() { return section; }
        public boolean isActive() { return active; }
        public String getActiveStatus() { return active ? "Active" : "Inactive"; }
        public String getCreatedAt() { return createdAt; }
    }

    public static class UserWriteRecord {
        private final String username;
        private final String role;
        private final String section;
        private final boolean active;

        public UserWriteRecord(String username, String role, String section, boolean active) {
            this.username = username == null ? "" : username.trim();
            this.role = role == null ? "" : role.trim();
            this.section = section == null ? "" : section.trim();
            this.active = active;
        }

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
}
