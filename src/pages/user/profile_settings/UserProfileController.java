package pages.user.profile_settings;

import javafx.fxml.FXML;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import java.util.List;

import app.core.AppShell;
import app.contracts.AppController;
import app.core.SessionContext;
import pages.notification.NotificationHelper;
import app.helpers.PermissionHelper;
import pages.user.Session;
import pages.user.User;
import pages.user.UserRole;

/**
 * Controls UserProfileView.fxml for the current user's profile, contact fields, password, and permissions summary.
 */
public class UserProfileController implements AppController {

    private final UserProfileService profileService = new UserProfileService();

    @FXML private Label staffIdLabel;
    @FXML private Label usernameLabel;
    @FXML private Label roleLabel;
    @FXML private Label accountStatusLabel;
    @FXML private Label authSourceLabel;
    @FXML private Label loginTimeLabel;
    @FXML private TextField emailField;
    @FXML private TextField phoneField;
    @FXML private Label profileStatusLabel;
    @FXML private VBox permissionListBox;

    /**
     * Supplies the application shell used by this controller for navigation.
     */
    @Override
    public void setAppShell(AppShell appShell) {
        renderProfile();
    }

    /**
     * Renders profile in the current JavaFX view.
     */
    private void renderProfile() {
        User user = Session.getCurrentUser();
        setLabel(staffIdLabel, user == null ? "-" : user.getStaffId());
        setLabel(usernameLabel, SessionContext.username());
        setLabel(roleLabel, displayRole(SessionContext.role()));
        setLabel(accountStatusLabel, user == null ? "Unknown" : "Active");
        setLabel(authSourceLabel, SessionContext.authSource());
        setLabel(loginTimeLabel, SessionContext.loginTimeText());
        try {
            profileService.findProfile(SessionContext.username()).ifPresent(profile -> {
                if (emailField != null) {
                    emailField.setText(safeText(profile.getEmail()));
                }
                if (phoneField != null) {
                    phoneField.setText(safeText(profile.getPhone()));
                }
            });
        } catch (Exception e) {
            showError(profileStatusLabel, "Could not load profile contact fields: " + e.getMessage());
        }

        renderPermissions(user);
    }

    /**
     * Handles the save profile UI action.
     */
    @FXML
    private void saveProfile() {
        try {
            String email = emailField == null ? "" : emailField.getText();
            String phone = phoneField == null ? "" : phoneField.getText();
            profileService.updateProfile(Session.getCurrentUser(), email, phone);
            showSuccess(profileStatusLabel, "Profile contact fields saved in SQLite.");
        } catch (Exception e) {
            showError(profileStatusLabel, e.getMessage());
        }
    }

    /**
     * Handles the change password UI action.
     */
    @FXML
    private void changePassword() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Change Password");
        app.helpers.DialogThemeHelper.apply(dialog);
        if (usernameLabel != null && usernameLabel.getScene() != null) {
            dialog.initOwner(usernameLabel.getScene().getWindow());
        }

        PasswordField currentPassword = new PasswordField();
        currentPassword.setPromptText("Current password");
        PasswordField newPassword = new PasswordField();
        newPassword.setPromptText("New password");
        PasswordField confirmPassword = new PasswordField();
        confirmPassword.setPromptText("Confirm new password");

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.add(new Label("Current password"), 0, 0);
        grid.add(currentPassword, 1, 0);
        grid.add(new Label("New password"), 0, 1);
        grid.add(newPassword, 1, 1);
        grid.add(new Label("Confirm password"), 0, 2);
        grid.add(confirmPassword, 1, 2);
        dialog.getDialogPane().setContent(grid);

        ButtonType saveType = new ButtonType("Change Password", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, saveType);
        dialog.getDialogPane().lookupButton(saveType).addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            if (!newPassword.getText().equals(confirmPassword.getText())) {
                showError(profileStatusLabel, "New password and confirmation do not match.");
                event.consume();
                return;
            }
            try {
                profileService.changeOwnPassword(Session.getCurrentUser(),
                        currentPassword.getText().toCharArray(),
                        newPassword.getText().toCharArray());
                showSuccess(profileStatusLabel, "Password changed for local demo login.");
            } catch (Exception e) {
                showError(profileStatusLabel, e.getMessage());
                event.consume();
            }
        });
        dialog.showAndWait();
    }

    /**
     * Renders permissions in the current JavaFX view.
     */
    private void renderPermissions(User user) {
        if (permissionListBox == null) {
            return;
        }
        permissionListBox.getChildren().clear();
        for (String permission : permissionsForRole(user)) {
            addPermission(permission);
        }
    }

    /**
     * Builds the permission summary for the selected role.
     */
    private List<String> permissionsForRole(User user) {
        String roleGroup = PermissionHelper.roleGroup(user);
        return switch (roleGroup) {
            case "ADMIN" -> List.of(
                    "Manage patient records",
                    "Delete patient records",
                    "Manage appointments",
                    "Delete appointments",
                    "Manage invoices",
                    "Delete invoices",
                    "Manage medical records",
                    "Delete medical records",
                    "Manage staff accounts",
                    "View and acknowledge alerts",
                    "Send internal messages"
            );
            case "DOCTOR" -> List.of(
                    "View patient records",
                    "View full patient file",
                    "Enter patient vitals",
                    "View appointments",
                    "View medical records",
                    "View and acknowledge alerts",
                    "Send internal messages"
            );
            case "NURSE" -> List.of(
                    "Add and update patient basic records",
                    "Enter patient vitals",
                    "View appointments",
                    "View and acknowledge alerts",
                    "Send internal messages"
            );
            case "SECRETARY" -> List.of(
                    "Register and update patient basic records",
                    "Manage appointments",
                    "Create invoices",
                    "View billing records",
                    "Upload and view medical records",
                    "Send internal messages"
            );
            default -> List.of(
                    "View profile and settings"
            );
        };
    }

    /**
     * Adds permission to the current staff workflow.
     */
    private void addPermission(String label) {
        if (permissionListBox == null) {
            return;
        }
        Label row = new Label(label);
        row.getStyleClass().add("permission-allowed");
        row.getStyleClass().add("profile-permission-item");
        row.setMaxWidth(Double.MAX_VALUE);
        row.setWrapText(true);
        permissionListBox.getChildren().add(row);
    }

    /**
     * Builds the JavaFX control used for set label.
     */
    private void setLabel(Label label, String value) {
        if (label != null) {
            label.setText(safeText(value));
        }
    }

    /**
     * Returns a safe display or filesystem value for text.
     */
    private String safeText(String value) {
        return value == null || value.trim().isEmpty() ? "-" : value;
    }

    /**
     * Formats role for display in the JavaFX UI.
     */
    private String displayRole(String role) {
        try {
            return UserRole.fromValue(role).displayName();
        } catch (Exception e) {
            return safeText(role);
        }
    }

    /**
     * Displays success to the user.
     */
    private void showSuccess(Label target, String message) {
        if (target != null) {
            NotificationHelper.showSuccess(target, message);
        }
    }

    /**
     * Displays error to the user.
     */
    private void showError(Label target, String message) {
        if (target != null) {
            NotificationHelper.showError(target, message);
        }
    }
}
