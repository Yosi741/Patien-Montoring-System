package pages.user.services;

import pages.user.dao.SqliteUserDao;
import pages.user.dao.SqliteUserProfileDao;
import app.helpers.FormValidationHelper;
import pages.user.User;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class UserProfileService {
    private static final Set<String> PHOTO_EXTENSIONS = Set.of("png", "jpg", "jpeg");
    private static final DateTimeFormatter PHOTO_STAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final SqliteUserDao userDao;
    private final SqliteUserProfileDao profileDao;

    public UserProfileService() {
        this(new SqliteUserDao(), new SqliteUserProfileDao());
    }

    public UserProfileService(SqliteUserDao userDao, SqliteUserProfileDao profileDao) {
        this.userDao = userDao;
        this.profileDao = profileDao;
    }

    public Optional<SqliteUserProfileDao.UserProfileRow> findProfile(String username) throws SQLException {
        return profileDao.findByUsername(username);
    }

    public void updateProfile(User user, String email, String phone) throws SQLException {
        String username = username(user);
        validateProfile(email, phone);
        profileDao.upsert(username, email, phone);
        userDao.updateEmail(username, email);
    }

    public void upsertStaffProfile(SqliteUserProfileDao.UserProfileWriteRecord record) throws SQLException {
        if (record == null || record.getUsername().isBlank()) {
            throw new IllegalArgumentException("Username is required for the staff profile.");
        }
        validateProfile(record.getEmail(), record.getPhone());
        validateExtendedProfile(record.getFullName(), record.getAddress(), record.getDutyStatus(), record.getProfilePhotoPath());
        profileDao.upsertProfile(record);
        userDao.updateEmail(record.getUsername(), record.getEmail());
    }

    public String copyProfilePhoto(String username, File sourceFile) throws IOException {
        if (sourceFile == null || !sourceFile.exists() || !sourceFile.isFile()) {
            throw new IllegalArgumentException("Selected profile photo does not exist.");
        }
        String extension = extension(sourceFile.getName());
        if (!PHOTO_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("Profile photo must be a PNG, JPG, or JPEG image.");
        }
        Path destinationDirectory = Path.of("data", "profile_photos");
        Files.createDirectories(destinationDirectory);
        String safeUser = username == null || username.isBlank() ? "staff" : username.replaceAll("[^A-Za-z0-9._-]", "_");
        String safeName = sourceFile.getName().replaceAll("[^A-Za-z0-9._-]", "_");
        String uniqueName = safeUser + "_" + LocalDateTime.now().format(PHOTO_STAMP) + "_" + UUID.randomUUID().toString().substring(0, 8) + "_" + safeName;
        Path destination = destinationDirectory.resolve(uniqueName);
        Files.copy(sourceFile.toPath(), destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
        return destination.toString().replace('\\', '/');
    }

    public void changeOwnPassword(User user, char[] currentPassword, char[] newPassword) throws SQLException {
        String username = username(user);
        if (!userDao.verifyPassword(username, currentPassword)) {
            throw new IllegalArgumentException("Current password is incorrect.");
        }
        validatePassword(newPassword);
        userDao.updatePassword(username, newPassword == null ? "" : new String(newPassword));
    }

    private void validateProfile(String email, String phone) {
        FormValidationHelper.ValidationResult validation = FormValidationHelper.combine(
                FormValidationHelper.validateMaxLength("Email", email, 120),
                FormValidationHelper.validateMaxLength("Phone", phone, 40)
        );
        if (!validation.isValid()) {
            throw new IllegalArgumentException(validation.getMessage());
        }
        if (email != null && !email.isBlank() && !email.trim().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new IllegalArgumentException("Email must be a valid email address.");
        }
    }

    private void validateExtendedProfile(String fullName, String address, String dutyStatus, String profilePhotoPath) {
        FormValidationHelper.ValidationResult validation = FormValidationHelper.combine(
                FormValidationHelper.validateMaxLength("Full Name", fullName, 120),
                FormValidationHelper.validateMaxLength("Address", address, 240),
                FormValidationHelper.validateMaxLength("Profile Photo Path", profilePhotoPath, 260)
        );
        if (!validation.isValid()) {
            throw new IllegalArgumentException(validation.getMessage());
        }
        if (fullName != null && !fullName.isBlank() && fullName.trim().length() < 2) {
            throw new IllegalArgumentException("Full Name must be at least 2 characters.");
        }
        if (dutyStatus != null && !dutyStatus.isBlank()
                && !"On Duty".equalsIgnoreCase(dutyStatus)
                && !"Off Duty".equalsIgnoreCase(dutyStatus)
                && !"On Leave".equalsIgnoreCase(dutyStatus)) {
            throw new IllegalArgumentException("Duty Status must be On Duty, Off Duty, or On Leave.");
        }
    }

    private void validatePassword(char[] newPassword) {
        if (newPassword == null || newPassword.length < 8) {
            throw new IllegalArgumentException("New password must be at least 8 characters.");
        }
    }

    private String username(User user) {
        if (user == null || user.getUsername() == null || user.getUsername().isBlank()) {
            throw new SecurityException("Login is required.");
        }
        return user.getUsername();
    }

    private String extension(String name) {
        int dot = name == null ? -1 : name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase();
    }
}
