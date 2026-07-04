package pages.user.user_form;

import pages.user.dao.SqliteUserDao;
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
import javafx.stage.Window;
import pages.room_section.SectionService;
import pages.user.services.UserWriteService;
import app.AppNavigator;
import pages.notification.NotificationHelper;
import app.SessionContext;
import pages.user.User;

public class UserFormController {

    private enum Mode {
        CREATE,
        EDIT,
        RESET_PASSWORD
    }

    private final UserWriteService userWriteService = new UserWriteService();
    private final SectionService sectionService = new SectionService();
    private User currentUser;
    private SqliteUserDao.UserDirectoryRow existingUser;
    private Mode mode;
    private boolean saved;

    @FXML private Label titleLabel;
    @FXML private Label helpLabel;
    @FXML private javafx.scene.control.TextField staffIdField;
    @FXML private javafx.scene.control.TextField usernameField;
    @FXML private Label usernameHelpLabel;
    @FXML private ComboBox<String> roleBox;
    @FXML private ComboBox<String> sectionBox;
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
            FXMLLoader loader = new FXMLLoader(AppNavigator.resolve("/pages/user/user_form/UserFormView.fxml"));
            Parent root = loader.load();
            UserFormController controller = loader.getController();
            controller.prepare(currentUser, user, mode);

            String action = mode == Mode.RESET_PASSWORD ? "Reset Password" : "Save";
            ButtonType saveButtonType = new ButtonType(action, ButtonBar.ButtonData.OK_DONE);
            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setTitle(dialogTitle(mode));
            app.helpers.DialogThemeHelper.apply(dialog);
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
        loadSectionsForRole("STAFF");
        roleBox.valueProperty().addListener((observable, oldValue, newValue) -> loadSectionsForRole(newValue));
        activeCheckBox.setSelected(true);
        NotificationHelper.showInfo(statusLabel, "Staff account form. Passwords are not displayed.");
    }

    private void prepare(User currentUser, SqliteUserDao.UserDirectoryRow user, Mode mode) {
        this.currentUser = currentUser;
        this.existingUser = user;
        this.mode = mode;

        if (mode == Mode.CREATE) {
            titleLabel.setText("Add User");
            helpLabel.setText("Create a staff account with role and section access.");
            staffIdField.setEditable(false);
            staffIdField.setDisable(false);
            staffIdField.setText(loadNextStaffId());
            usernameField.setEditable(true);
            usernameField.setDisable(false);
            usernameHelpLabel.setText("Enter a unique username for this new account.");
            passwordHelpLabel.setText("Password is stored safely and not displayed.");
            return;
        }

        if (user == null) {
            throw new IllegalArgumentException("A selected user is required.");
        }

        staffIdField.setEditable(false);
        staffIdField.setDisable(false);
        staffIdField.setText(user.getStaffId());
        usernameField.setText(user.getUsername());
        usernameField.setEditable(false);
        usernameField.setDisable(mode == Mode.RESET_PASSWORD);
        usernameHelpLabel.setText("Username cannot be changed after creation because it is used in system records.");
        roleBox.getSelectionModel().select(normalizeRole(user.getRole()));
        loadSectionsForRole(roleBox.getValue());
        selectSection(user.getSection());
        activeCheckBox.setSelected(user.isActive());

        if (mode == Mode.EDIT) {
            titleLabel.setText("Edit User");
            helpLabel.setText("Update role, section, and active status.");
            passwordField.setVisible(false);
            passwordField.setManaged(false);
            confirmPasswordField.setVisible(false);
            confirmPasswordField.setManaged(false);
            passwordHelpLabel.setText("Password is stored safely and not displayed. Use Reset Password to change it.");
            return;
        }

        titleLabel.setText("Reset Password");
        helpLabel.setText("Set a new password for this staff account.");
        roleBox.setDisable(true);
        sectionBox.setDisable(true);
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
                    userWriteService.updateUser(currentUser, existingUser == null ? record.getUsername() : existingUser.getUsername(), record);
                    if (existingUser != null
                            && existingUser.getUsername().equalsIgnoreCase(SessionContext.username())
                            && !existingUser.getUsername().equalsIgnoreCase(record.getUsername())) {
                        NotificationHelper.showInfo(statusLabel, "Current session display updates after logout/login.");
                    }
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
        String username = mode == Mode.CREATE || existingUser == null
                ? usernameField.getText()
                : existingUser.getUsername();
        return new SqliteUserDao.UserWriteRecord(
                staffIdField.getText(),
                username,
                roleBox.getValue(),
                sectionValue(),
                activeCheckBox.isSelected()
        );
    }

    private void loadSectionsForRole(String role) {
        String current = sectionValue();
        java.util.LinkedHashSet<String> sections = new java.util.LinkedHashSet<>();
        if ("ADMIN".equalsIgnoreCase(role)) {
            sections.add("All");
        }
        try {
            sections.addAll(sectionService.findActiveSectionNames());
        } catch (Exception e) {
            NotificationHelper.showInfo(statusLabel, "Active sections unavailable: " + e.getMessage());
        }
        sectionBox.setItems(FXCollections.observableArrayList(sections));
        if (current != null && !current.isBlank() && sections.contains(current)) {
            sectionBox.getSelectionModel().select(current);
        } else if ("ADMIN".equalsIgnoreCase(role)) {
            sectionBox.getSelectionModel().select("All");
        } else {
            sectionBox.getSelectionModel().clearSelection();
        }
    }

    private void selectSection(String section) {
        String value = section == null || section.isBlank() ? "" : section.trim();
        if (!value.isBlank() && !sectionBox.getItems().contains(value)) {
            sectionBox.getItems().add(value);
        }
        if (!value.isBlank()) {
            sectionBox.getSelectionModel().select(value);
        }
    }

    private String sectionValue() {
        String value = sectionBox == null ? "" : sectionBox.getValue();
        return value == null ? "" : value.trim();
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

    private String loadNextStaffId() {
        try {
            return userWriteService.generateNextStaffId();
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, "Could not generate Staff ID: " + e.getMessage());
            return "";
        }
    }
}
