package pages.user.user_form;

import app.navigation.AppNavigator;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.Node;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import pages.notification.NotificationHelper;
import pages.user.User;
import pages.user.UserRole;
import pages.user.profile_settings.SqliteUserDao;
import pages.user.profile_settings.SqliteUserProfileDao;
import pages.user.profile_settings.UserProfileService;
import pages.user.profile_settings.UserWriteService;

import java.io.File;

/**
 * Controls UserFormView.fxml for adding and editing staff accounts and profile photos.
 */
public class UserFormController {

    private enum Mode {
        CREATE,
        EDIT
    }

    private final UserWriteService userWriteService = new UserWriteService();
    private final UserProfileService profileService = new UserProfileService();
    private User currentUser;
    private SqliteUserDao.UserDirectoryRow existingUser;
    private Mode mode;
    private boolean saved;
    private Dialog<?> dialog;
    private File selectedPhotoFile;
    private String existingPhotoPath = "";
    private String preservedSection = "";

    @FXML private Label titleLabel;
    @FXML private Label helpLabel;
    @FXML private TextField staffIdField;
    @FXML private TextField fullNameField;
    @FXML private TextField usernameField;
    @FXML private Label usernameHelpLabel;
    @FXML private ComboBox<String> roleBox;
    @FXML private ComboBox<String> dutyStatusBox;
    @FXML private TextField emailField;
    @FXML private TextField phoneField;
    @FXML private TextArea addressArea;
    @FXML private CheckBox activeCheckBox;
    @FXML private Button uploadPhotoButton;
    @FXML private Button removePhotoButton;
    @FXML private Button saveButton;
    @FXML private Button cancelButton;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private ImageView photoPreviewImage;
    @FXML private Label photoInitialsLabel;
    @FXML private Label photoPathLabel;
    @FXML private Label passwordHelpLabel;
    @FXML private Label statusLabel;

    /**
     * Displays create dialog to the user.
     */
    public static boolean showCreateDialog(Window owner, User currentUser) {
        return showDialog(owner, currentUser, null, Mode.CREATE);
    }

    /**
     * Displays edit dialog to the user.
     */
    public static boolean showEditDialog(Window owner, User currentUser, SqliteUserDao.UserDirectoryRow user) {
        return showDialog(owner, currentUser, user, Mode.EDIT);
    }

    /**
     * Displays dialog to the user.
     */
    private static boolean showDialog(Window owner, User currentUser, SqliteUserDao.UserDirectoryRow user, Mode mode) {
        try {
            FXMLLoader loader = new FXMLLoader(AppNavigator.resolve("/pages/user/user_form/UserFormView.fxml"));
            Parent root = loader.load();
            UserFormController controller = loader.getController();
            Dialog<Void> dialog = new Dialog<>();
            controller.attachDialog(dialog);
            controller.prepare(currentUser, user, mode);

            dialog.setTitle(dialogTitle(mode));
            app.helpers.DialogThemeHelper.apply(dialog);
            dialog.initOwner(owner);
            dialog.getDialogPane().setContent(root);
            dialog.getDialogPane().getButtonTypes().setAll(new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE));
            Node cancelNode = dialog.getDialogPane().lookupButton(dialog.getDialogPane().getButtonTypes().get(0));
            if (cancelNode != null) {
                cancelNode.setVisible(false);
                cancelNode.setManaged(false);
            }
            dialog.setResultConverter(buttonType -> null);
            dialog.showAndWait();
            return controller.saved;
        } catch (Exception e) {
            throw new IllegalStateException("Could not open user form: " + e.getMessage(), e);
        }
    }

    /**
     * Initializes the FXML controls after the JavaFX view has been loaded.
     */
    @FXML
    private void initialize() {
        roleBox.setItems(FXCollections.observableArrayList("Admin", "Doctor", "Nurse", "Secretary"));
        roleBox.getSelectionModel().select("Secretary");
        dutyStatusBox.setItems(FXCollections.observableArrayList("On Duty", "Off Duty", "On Leave"));
        dutyStatusBox.getSelectionModel().select("On Duty");
        if (fullNameField != null) {
            fullNameField.textProperty().addListener((observable, oldValue, newValue) -> refreshPhotoPreview());
        }
        if (usernameField != null) {
            usernameField.textProperty().addListener((observable, oldValue, newValue) -> refreshPhotoPreview());
        }
        activeCheckBox.setSelected(true);
        refreshPhotoPreview();
        NotificationHelper.showInfo(statusLabel, "Staff account form. Passwords are not displayed.");
    }

    /**
     * Attaches dialog to the current controller.
     */
    private void attachDialog(Dialog<?> dialog) {
        this.dialog = dialog;
    }

    /**
     * Prepares the form with the selected staff record and mode.
     */
    private void prepare(User currentUser, SqliteUserDao.UserDirectoryRow user, Mode mode) {
        this.currentUser = currentUser;
        this.existingUser = user;
        this.mode = mode;
        this.preservedSection = user == null ? "" : safe(user.getSection());

        if (mode == Mode.CREATE) {
            titleLabel.setText("Add Staff");
            helpLabel.setText("Create a clinic staff account with role and duty status.");
            configureReadOnlyStaffIdField();
            staffIdField.setDisable(false);
            usernameField.setEditable(true);
            usernameField.setDisable(false);
            usernameHelpLabel.setText("Enter a unique username for login.");
            if (saveButton != null) {
                saveButton.setText("Save");
            }
            if (cancelButton != null) {
                cancelButton.setText("Cancel");
            }
            populateGeneratedStaffId();
            NotificationHelper.showInfo(statusLabel, "Staff ID is generated automatically. Full Name and Username are required.");
            passwordHelpLabel.setText("Password is used for local demo login and is never shown on screen.");
            setProfileFieldsForCreate();
            return;
        }

        if (user == null) {
            throw new IllegalArgumentException("A selected staff account is required.");
        }

        configureReadOnlyStaffIdField();
        staffIdField.setDisable(false);
        staffIdField.setText(user.getStaffId());
        usernameField.setText(user.getUsername());
        usernameField.setEditable(false);
        usernameHelpLabel.setText("Username cannot be changed after creation because it is used in system records.");
        roleBox.getSelectionModel().select(visibleRole(user.getRole()));
        activeCheckBox.setSelected(user.isActive());
        populateProfileFields(user);

        titleLabel.setText("Edit Staff");
        helpLabel.setText("Update profile details, role, and duty status.");
        if (saveButton != null) {
            saveButton.setText("Save");
        }
        hidePasswordFields();
        passwordHelpLabel.setText("Password changes use the Forgot Password workflow on the login screen.");
    }

    /**
     * Updates profile fields for create for the current object.
     */
    private void setProfileFieldsForCreate() {
        fullNameField.clear();
        emailField.clear();
        phoneField.clear();
        addressArea.clear();
        selectedPhotoFile = null;
        existingPhotoPath = "";
        preservedSection = "";
        photoPathLabel.setText("No profile photo selected");
        dutyStatusBox.getSelectionModel().select("On Duty");
        activeCheckBox.setSelected(true);
        refreshPhotoPreview();
    }

    /**
     * Populates profile fields from the selected record.
     */
    private void populateProfileFields(SqliteUserDao.UserDirectoryRow user) {
        try {
            SqliteUserProfileDao.UserProfileRow profile = profileService.findProfile(user.getUsername()).orElse(null);
            fullNameField.setText(profile != null && !profile.getFullName().isBlank() ? profile.getFullName() : user.getDisplayName());
            emailField.setText(profile != null ? safe(profile.getEmail()) : safe(user.getEmail()));
            phoneField.setText(profile != null ? safe(profile.getPhone()) : safe(user.getPhone()));
            addressArea.setText(profile != null ? safe(profile.getAddress()) : safe(user.getAddress()));
            dutyStatusBox.getSelectionModel().select(profile != null && !profile.getDutyStatus().isBlank() ? profile.getDutyStatus() : safeDutyStatus(user.getDutyStatus()));
            existingPhotoPath = profile != null ? safe(profile.getProfilePhotoPath()) : safe(user.getProfilePhotoPath());
            photoPathLabel.setText(existingPhotoPath.isBlank() ? "No profile photo selected" : existingPhotoPath);
            refreshPhotoPreview();
        } catch (Exception e) {
            NotificationHelper.showInfo(statusLabel, "Could not load staff profile extras: " + e.getMessage());
            fullNameField.setText(user.getDisplayName());
            emailField.setText(safe(user.getEmail()));
            phoneField.setText(safe(user.getPhone()));
            addressArea.setText(safe(user.getAddress()));
            dutyStatusBox.getSelectionModel().select(safeDutyStatus(user.getDutyStatus()));
            existingPhotoPath = safe(user.getProfilePhotoPath());
            photoPathLabel.setText(existingPhotoPath.isBlank() ? "No profile photo selected" : existingPhotoPath);
            refreshPhotoPreview();
        }
    }

    /**
     * Hides password fields and removes its layout space.
     */
    private void hidePasswordFields() {
        passwordField.setVisible(false);
        passwordField.setManaged(false);
        confirmPasswordField.setVisible(false);
        confirmPasswordField.setManaged(false);
    }

    /**
     * Handles the submit form UI action.
     */
    @FXML
    private void submitForm(ActionEvent event) {
        if (save()) {
            closeDialog();
        }
        if (event != null) {
            event.consume();
        }
    }

    /**
     * Handles the close dialog UI action.
     */
    @FXML
    private void closeDialog() {
        if (dialog != null) {
            dialog.close();
        }
    }

    /**
     * Handles the choose profile photo UI action.
     */
    @FXML
    private void chooseProfilePhoto() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose Staff Profile Photo");
        chooser.getExtensionFilters().setAll(
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg")
        );
        Window owner = statusLabel != null && statusLabel.getScene() != null ? statusLabel.getScene().getWindow() : null;
        File selected = chooser.showOpenDialog(owner);
        if (selected == null) {
            return;
        }
        selectedPhotoFile = selected;
        photoPathLabel.setText(selected.getAbsolutePath());
        refreshPhotoPreview();
    }

    /**
     * Handles the remove profile photo UI action.
     */
    @FXML
    private void removeProfilePhoto() {
        selectedPhotoFile = null;
        existingPhotoPath = "";
        photoPathLabel.setText("No profile photo selected");
        refreshPhotoPreview();
    }

    /**
     * Validates and saves save.
     */
    private boolean save() {
        try {
            String username = normalizedUsername();
            if (username.isBlank()) {
                NotificationHelper.showError(statusLabel, "Username is required.");
                return false;
            }
            if (fullNameField.getText() == null || fullNameField.getText().trim().isEmpty()) {
                NotificationHelper.showError(statusLabel, "Full Name is required.");
                return false;
            }

            if (mode == Mode.CREATE) {
                ensureGeneratedStaffIdAvailable();
            }

            SqliteUserDao.UserWriteRecord record = buildRecord();
            if (mode == Mode.CREATE) {
                char[] password = passwordChars();
                if (!passwordsMatch()) {
                    NotificationHelper.showError(statusLabel, "Passwords do not match.");
                    clear(password);
                    return false;
                }
                userWriteService.createUser(currentUser, record, password);
            } else {
                userWriteService.updateUser(currentUser, existingUser == null ? record.getUsername() : existingUser.getUsername(), record);
            }

            String photoPath = selectedPhotoFile != null
                    ? profileService.copyProfilePhoto(username, selectedPhotoFile)
                    : existingPhotoPath;
            profileService.upsertStaffProfile(new SqliteUserProfileDao.UserProfileWriteRecord(
                    username,
                    fullNameField.getText(),
                    emailField.getText(),
                    phoneField.getText(),
                    addressArea.getText(),
                    dutyStatusBox.getValue(),
                    photoPath
            ));
            saved = true;
            return true;
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
            return false;
        }
    }

    /**
     * Builds record used by the staff view.
     */
    private SqliteUserDao.UserWriteRecord buildRecord() {
        String username = mode == Mode.CREATE || existingUser == null
                ? normalizedUsername()
                : existingUser.getUsername();
        return new SqliteUserDao.UserWriteRecord(
                safe(staffIdField.getText()).toUpperCase(),
                username,
                internalRole(roleBox.getValue()),
                preservedSectionValue(),
                activeCheckBox.isSelected()
        );
    }

    /**
     * Refreshes photo preview from the current application state.
     */
    private void refreshPhotoPreview() {
        String initials = initials(fullNameField == null ? "" : fullNameField.getText(), normalizedUsername());
        if (photoInitialsLabel != null) {
            photoInitialsLabel.setText(initials);
            photoInitialsLabel.setVisible(true);
            photoInitialsLabel.setManaged(true);
        }
        if (photoPreviewImage == null) {
            return;
        }
        String imagePath = selectedPhotoFile != null ? selectedPhotoFile.getAbsolutePath() : existingPhotoPath;
        if (imagePath == null || imagePath.isBlank()) {
            photoPreviewImage.setImage(null);
            photoPreviewImage.setVisible(false);
            photoPreviewImage.setManaged(false);
            return;
        }
        try {
            File file = new File(imagePath);
            if (!file.exists()) {
                photoPreviewImage.setImage(null);
                photoPreviewImage.setVisible(false);
                photoPreviewImage.setManaged(false);
                return;
            }
            photoPreviewImage.setImage(new Image(file.toURI().toString(), 112, 112, true, true));
            photoPreviewImage.setVisible(true);
            photoPreviewImage.setManaged(true);
            if (photoInitialsLabel != null) {
                photoInitialsLabel.setVisible(false);
                photoInitialsLabel.setManaged(false);
            }
        } catch (Exception ignored) {
            photoPreviewImage.setImage(null);
            photoPreviewImage.setVisible(false);
            photoPreviewImage.setManaged(false);
        }
    }

    /**
     * Builds initials from initials for an avatar fallback.
     */
    private String initials(String fullName, String username) {
        String source = fullName == null || fullName.trim().isEmpty() ? username : fullName;
        if (source == null || source.isBlank()) {
            return "SC";
        }
        String[] parts = source.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
        }
        return (parts[0].substring(0, 1) + parts[1].substring(0, 1)).toUpperCase();
    }

    /**
     * Maps the stored role value to its visible clinic label.
     */
    private String visibleRole(String internalRole) {
        return UserRole.fromValue(internalRole).displayName();
    }

    /**
     * Maps the visible role label to the internal role value.
     */
    private String internalRole(String visibleRole) {
        return UserRole.fromValue(visibleRole).databaseValue();
    }

    /**
     * Normalizes normalized username to the stored application format.
     */
    private String normalizedUsername() {
        return usernameField.getText() == null ? "" : usernameField.getText().trim();
    }

    /**
     * Builds the JavaFX control used for configure read only staff ID field.
     */
    private void configureReadOnlyStaffIdField() {
        staffIdField.setEditable(false);
        staffIdField.setFocusTraversable(false);
    }

    /**
     * Populates generated staff ID from the selected record.
     */
    private void populateGeneratedStaffId() {
        try {
            staffIdField.setText(userWriteService.generateNextStaffId());
        } catch (Exception e) {
            staffIdField.setText("U0001");
            NotificationHelper.showInfo(statusLabel, "Could not read the latest staff ID. Defaulted to U0001.");
        }
    }

    /**
     * Ensures generated staff ID available exists before continuing.
     */
    private void ensureGeneratedStaffIdAvailable() throws Exception {
        String currentStaffId = safe(staffIdField.getText()).toUpperCase();
        if (currentStaffId.isBlank() || !currentStaffId.matches("U\\d{4,}") || userWriteService.staffIdExists(currentStaffId)) {
            staffIdField.setText(userWriteService.generateNextStaffId());
        }
    }

    /**
     * Returns section value without overwriting stored staff data.
     */
    private String preservedSectionValue() {
        if (mode == Mode.CREATE) {
            return "";
        }
        return safe(preservedSection);
    }

    /**
     * Evaluates password chars for the staff form.
     */
    private char[] passwordChars() {
        return passwordField.getText() == null ? new char[0] : passwordField.getText().toCharArray();
    }

    /**
     * Evaluates passwords match for the staff form.
     */
    private boolean passwordsMatch() {
        String password = passwordField.getText() == null ? "" : passwordField.getText();
        String confirm = confirmPasswordField.getText() == null ? "" : confirmPasswordField.getText();
        return password.equals(confirm);
    }

    /**
     * Clears clear and restores its default state.
     */
    private void clear(char[] password) {
        if (password != null) {
            java.util.Arrays.fill(password, '\0');
        }
    }

    /**
     * Returns a safe display or filesystem value for safe.
     */
    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * Returns a safe display or filesystem value for duty status.
     */
    private String safeDutyStatus(String value) {
        if ("Off Duty".equalsIgnoreCase(value)) {
            return "Off Duty";
        }
        if ("On Leave".equalsIgnoreCase(value)) {
            return "On Leave";
        }
        return "On Duty";
    }

    /**
     * Returns the title for the current add or edit dialog mode.
     */
    private static String dialogTitle(Mode mode) {
        if (mode == Mode.CREATE) {
            return "Add Staff";
        }
        return "Edit Staff";
    }
}
