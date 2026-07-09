package pages.user.dao;

import app.database.DatabaseManager;
import app.database.SchemaInitializer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class SqliteUserProfileDao {

    public SqliteUserProfileDao() {
        ensureSchema();
    }

    public Optional<UserProfileRow> findByUsername(String username) throws SQLException {
        String sql = "SELECT username, full_name, email, phone, address, duty_status, profile_photo_path, updated_at "
                + "FROM user_profiles WHERE username = ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(new UserProfileRow(
                            resultSet.getString("username"),
                            value(resultSet.getString("full_name")),
                            value(resultSet.getString("email")),
                            value(resultSet.getString("phone")),
                            value(resultSet.getString("address")),
                            value(resultSet.getString("duty_status")),
                            value(resultSet.getString("profile_photo_path")),
                            value(resultSet.getString("updated_at"))
                    ));
                }
            }
        }
        return Optional.empty();
    }

    public void upsert(String username, String email, String phone) throws SQLException {
        String sql = "INSERT INTO user_profiles(username, email, phone, updated_at) VALUES(?, ?, ?, CURRENT_TIMESTAMP) "
                + "ON CONFLICT(username) DO UPDATE SET "
                + "email = excluded.email, phone = excluded.phone, updated_at = CURRENT_TIMESTAMP";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            statement.setString(2, value(email));
            statement.setString(3, value(phone));
            statement.executeUpdate();
        }
    }

    public void upsertProfile(UserProfileWriteRecord record) throws SQLException {
        String sql = "INSERT INTO user_profiles(username, full_name, email, phone, address, duty_status, profile_photo_path, updated_at) "
                + "VALUES(?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP) "
                + "ON CONFLICT(username) DO UPDATE SET "
                + "full_name = excluded.full_name, "
                + "email = excluded.email, "
                + "phone = excluded.phone, "
                + "address = excluded.address, "
                + "duty_status = excluded.duty_status, "
                + "profile_photo_path = excluded.profile_photo_path, "
                + "updated_at = CURRENT_TIMESTAMP";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value(record.getUsername()));
            statement.setString(2, value(record.getFullName()));
            statement.setString(3, value(record.getEmail()));
            statement.setString(4, value(record.getPhone()));
            statement.setString(5, value(record.getAddress()));
            statement.setString(6, dutyStatus(record.getDutyStatus()));
            statement.setString(7, value(record.getProfilePhotoPath()));
            statement.executeUpdate();
        }
    }

    private void ensureSchema() {
        try {
            SchemaInitializer.initialize();
        } catch (Exception e) {
            System.out.println("SQLite user profile schema check failed: " + e.getMessage());
        }
    }

    private String value(String value) {
        return value == null ? "" : value.trim();
    }

    private String dutyStatus(String value) {
        String normalized = value(value);
        if (normalized.isEmpty()) {
            return "On Duty";
        }
        if ("On Duty".equalsIgnoreCase(normalized) || "Off Duty".equalsIgnoreCase(normalized) || "On Leave".equalsIgnoreCase(normalized)) {
            return capitalizeWords(normalized);
        }
        return "On Duty";
    }

    private String capitalizeWords(String value) {
        String lower = value == null ? "" : value.trim().toLowerCase();
        if (lower.isEmpty()) {
            return "";
        }
        String[] parts = lower.split("\\s+");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }
        return builder.toString();
    }

    public static class UserProfileRow {
        private final String username;
        private final String fullName;
        private final String email;
        private final String phone;
        private final String address;
        private final String dutyStatus;
        private final String profilePhotoPath;
        private final String updatedAt;

        public UserProfileRow(String username, String fullName, String email, String phone, String address, String dutyStatus, String profilePhotoPath, String updatedAt) {
            this.username = username;
            this.fullName = fullName;
            this.email = email;
            this.phone = phone;
            this.address = address;
            this.dutyStatus = dutyStatus;
            this.profilePhotoPath = profilePhotoPath;
            this.updatedAt = updatedAt;
        }

        public String getUsername() { return username; }
        public String getFullName() { return fullName; }
        public String getEmail() { return email; }
        public String getPhone() { return phone; }
        public String getAddress() { return address; }
        public String getDutyStatus() { return dutyStatus; }
        public String getProfilePhotoPath() { return profilePhotoPath; }
        public String getUpdatedAt() { return updatedAt; }
    }

    public static class UserProfileWriteRecord {
        private final String username;
        private final String fullName;
        private final String email;
        private final String phone;
        private final String address;
        private final String dutyStatus;
        private final String profilePhotoPath;

        public UserProfileWriteRecord(String username, String fullName, String email, String phone, String address, String dutyStatus, String profilePhotoPath) {
            this.username = username == null ? "" : username.trim();
            this.fullName = fullName == null ? "" : fullName.trim();
            this.email = email == null ? "" : email.trim();
            this.phone = phone == null ? "" : phone.trim();
            this.address = address == null ? "" : address.trim();
            this.dutyStatus = dutyStatus == null ? "" : dutyStatus.trim();
            this.profilePhotoPath = profilePhotoPath == null ? "" : profilePhotoPath.trim();
        }

        public String getUsername() { return username; }
        public String getFullName() { return fullName; }
        public String getEmail() { return email; }
        public String getPhone() { return phone; }
        public String getAddress() { return address; }
        public String getDutyStatus() { return dutyStatus; }
        public String getProfilePhotoPath() { return profilePhotoPath; }
    }
}
