package ui.javafx.controllers;

import dao.SqliteUserDao;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Window;
import services.UserWriteService;
import ui.javafx.AppNavigator;
import ui.javafx.helpers.NotificationHelper;
import users.User;

public class UserFormController {

    private enum Mode {
        CREATE,
        EDIT,
        RESET_PASSWORD
    }

    private final UserWriteService userWriteService = new UserWriteService();
    private User currentUser;
    private SqliteUserDao.UserDirectoryRow existingUser;
    private Mode mode;
    private boolean saved;

    @FXML private Label titleLabel;
    @FXML private Label helpLabel;
    @FXML private TextField usernameField;
    @FXML private ComboBox<String> roleBox;
    @FXML private TextField sectionField;
    @FXML private CheckBox activeCheckBox;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Label passwordHelpLabel;
    @FXML private Label statusLabel;

    public static boolean showCreateDialog(Window owner, User currentUser) {
        return showDialog(owner, currentUser, null, Mode.CREATE);
    }

    public static boolean showEditDialog(Window owner, User currentUser, SqliteUserDao.UserDirectoryRow user) {
        return showDialog(owner, currentUser, user, Mode.EDIT);
    }

    public static boolean showResetPasswordDialog(Window owner, User currentUser, SqliteUserDao.UserDirectoryRow user) {
        return showDialog(owner, currentUser, user, Mode.RESET_PASSWORD);
    }

    private static boolean showDialog(Window owner, User currentUser, SqliteUserDao.UserDirectoryRow user, Mode mode) {
        try {
            FXMLLoader loader = new FXMLLoader(AppNavigator.resolve("/ui/javafx/views/UserFormView.fxml"));
            Parent root = loader.load();
            UserFormController controller = loader.getController();
            controller.prepare(currentUser, user, mode);

            String action = mode == Mode.RESET_PASSWORD ? "Reset Password" : "Save";
            ButtonType saveButtonType = new ButtonType(action, ButtonBar.ButtonData.OK_DONE);
            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setTitle(dialogTitle(mode));
            dialog.initOwner(owner);
            dialog.getDialogPane().setContent(root);
            dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, saveButtonType);
            dialog.getDialogPane().lookupButton(saveButtonType).addEventFilter(ActionEvent.ACTION, event -> {
                if (!controller.save()) {
                    event.consume();
                }
            });
            dialog.showAndWait();
            return controller.saved;
        } catch (Exception e) {
            throw new IllegalStateException("Could not open user form: " + e.getMessage(), e);
        }
    }

    @FXML
    private void initialize() {
        roleBox.setItems(FXCollections.observableArrayList("ADMIN", "DOCTOR", "NURSE", "STAFF"));
        roleBox.getSelectionModel().select("STAFF");
        activeCheckBox.setSelected(true);
        NotificationHelper.showInfo(statusLabel, "Staff account form. System data is stored in the local database.");
    }

    private void prepare(User currentUser, SqliteUserDao.UserDirectoryRow user, Mode mode) {
        this.currentUser = currentUser;
        this.existingUser = user;
        this.mode = mode;

        if (mode == Mode.CREATE) {
            titleLabel.setText("Add User");
            helpLabel.setText("Create a staff account with a hashed password.");
            passwordHelpLabel.setText("Password is required and will be stored only as a PBKDF2 hash.");
            return;
        }

        if (user == null) {
            throw new IllegalArgumentException("A selected user is required.");
        }

        usernameField.setText(user.getUsername());
        usernameField.setDisable(true);
        roleBox.getSelectionModel().select(normalizeRole(user.getRole()));
        sectionField.setText(user.getSection());
        activeCheckBox.setSelected(user.isActive());

        if (mode == Mode.EDIT) {
            titleLabel.setText("Edit User");
            helpLabel.setText("Update SQLite role, section, and active status. Password hash is never displayed.");
            passwordField.setVisible(false);
            passwordField.setManaged(false);
            confirmPasswordField.setVisible(false);
            confirmPasswordField.setManaged(false);
            passwordHelpLabel.setText("Use Reset Password to change credentials.");
            return;
        }

        titleLabel.setText("Reset Password");
        helpLabel.setText("Set a new hashed SQLite password. Raw passwords are not logged.");
        roleBox.setDisable(true);
        sectionField.setDisable(true);
        activeCheckBox.setDisable(true);
        passwordHelpLabel.setText("Enter the new password twice. Minimum length is 8 characters.");
    }

    private boolean save() {
        try {
            if (mode == Mode.RESET_PASSWORD) {
                char[] password = passwordChars();
                if (!passwordsMatch()) {
                    NotificationHelper.showError(statusLabel, "Passwords do not match.");
                    clear(password);
                    return false;
                }
                userWriteService.resetPassword(currentUser, usernameField.getText(), password);
            } else {
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
                    userWriteService.updateUser(currentUser, record);
                }
            }
            saved = true;
            return true;
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
            return false;
        }
    }

    private SqliteUserDao.UserWriteRecord buildRecord() {
        return new SqliteUserDao.UserWriteRecord(
                usernameField.getText(),
                roleBox.getValue(),
                sectionField.getText(),
                activeCheckBox.isSelected()
        );
    }

    private char[] passwordChars() {
        return passwordField.getText() == null ? new char[0] : passwordField.getText().toCharArray();
    }

    private boolean passwordsMatch() {
        String password = passwordField.getText() == null ? "" : passwordField.getText();
        String confirm = confirmPasswordField.getText() == null ? "" : confirmPasswordField.getText();
        return password.equals(confirm);
    }

    private void clear(char[] password) {
        if (password != null) {
            java.util.Arrays.fill(password, '\0');
        }
    }

    private String normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            return "STAFF";
        }
        String upper = role.toUpperCase();
        if (upper.contains("ADMIN")) {
            return "ADMIN";
        }
        if (upper.contains("DOCTOR") || upper.contains("MEDICAL") || upper.contains("DEPARTMENT HEAD")) {
            return "DOCTOR";
        }
        if (upper.contains("NURSE") || upper.contains("NURSING")) {
            return "NURSE";
        }
        return "STAFF";
    }

    private static String dialogTitle(Mode mode) {
        if (mode == Mode.CREATE) {
            return "Add User";
        }
        if (mode == Mode.RESET_PASSWORD) {
            return "Reset User Password";
        }
        return "Edit User";
    }
}
