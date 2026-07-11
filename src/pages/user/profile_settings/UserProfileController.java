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
import pages.user.services.UserProfileService;
import app.core.AppShell;
import app.contracts.AppController;
import app.core.SessionContext;
import pages.notification.NotificationHelper;
import app.helpers.PermissionHelper;
import users.Session;
import pages.user.User;

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

    @Override
    public void setAppShell(AppShell appShell) {
        renderProfile();
    }

    private void renderProfile() {
        User user = Session.getCurrentUser();
        setLabel(staffIdLabel, user == null ? "-" : user.getStaffId());
        setLabel(usernameLabel, SessionContext.username());
        setLabel(roleLabel, SessionContext.role());
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

    private void renderPermissions(User user) {
        if (permissionListBox == null) {
            return;
        }
        permissionListBox.getChildren().clear();
        for (String permission : permissionsForRole(user)) {
            addPermission(permission);
        }
    }

    private List<String> permissionsForRole(User user) {
        String roleGroup = PermissionHelper.roleGroup(user);
        return switch (roleGroup) {
            case "ADMIN" -> List.of(
                    "Manage patient records",
                    "Add and edit staff accounts",
                    "Create and manage appointments",
                    "View and acknowledge alerts",
                    "Upload and view medical records",
                    "Create and manage invoices",
                    "Send and view internal messages",
                    "Update own profile and password"
            );
            case "DOCTOR" -> List.of(
                    "View patient records",
                    "Enter patient vitals",
                    "View medical records",
                    "Create appointments",
                    "View and acknowledge clinical alerts",
                    "Send and view internal messages",
                    "Update own profile and password"
            );
            case "NURSE" -> List.of(
                    "View patient records",
                    "Enter patient vitals",
                    "View alerts",
                    "View appointments",
                    "Send and view internal messages",
                    "Update own profile and password"
            );
            case "STAFF" -> List.of(
                    "Register and update patient contact details",
                    "Create appointments",
                    "Create invoices",
                    "View basic patient records",
                    "Send and view internal messages",
                    "Update own profile and password"
            );
            default -> List.of(
                    "Update own profile and password"
            );
        };
    }

    private void addPermission(String label) {
        if (permissionListBox == null) {
            return;
        }
        Label row = new Label(label);
        row.getStyleClass().add("permission-allowed");
        row.setWrapText(true);
        permissionListBox.getChildren().add(row);
    }

    private void setLabel(Label label, String value) {
        if (label != null) {
            label.setText(safeText(value));
        }
    }

    private String safeText(String value) {
        return value == null || value.trim().isEmpty() ? "-" : value;
    }

    private void showSuccess(Label target, String message) {
        if (target != null) {
            NotificationHelper.showSuccess(target, message);
        }
    }

    private void showError(Label target, String message) {
        if (target != null) {
            NotificationHelper.showError(target, message);
        }
    }
}
