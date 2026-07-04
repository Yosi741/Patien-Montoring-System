package ui.javafx.users.profile_settings;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import ui.javafx.users.services.UserProfileService;
import app.AppShell;
import app.FxController;
import app.SessionContext;
import ui.javafx.pages.audit_logs.AuditAction;
import ui.javafx.pages.audit_logs.AuditWriteHelper;
import ui.javafx.pages.notifications.NotificationHelper;
import app.helpers.PermissionHelper;
import users.Session;
import users.User;

public class UserProfileController implements FxController {

    private AppShell appShell;
    private final UserProfileService profileService = new UserProfileService();

    @FXML private Label staffIdLabel;
    @FXML private Label usernameLabel;
    @FXML private Label roleLabel;
    @FXML private Label roleBadgeLabel;
    @FXML private Label sectionLabel;
    @FXML private Label accountStatusLabel;
    @FXML private Label authSourceLabel;
    @FXML private Label loginTimeLabel;
    @FXML private TextField emailField;
    @FXML private TextField phoneField;
    @FXML private Label profileStatusLabel;
    @FXML private VBox permissionListBox;
    @FXML private VBox writeFoundationBox;
    @FXML private Button createTestAuditButton;
    @FXML private Label writeFoundationStatusLabel;

    @Override
    public void setAppShell(AppShell appShell) {
        this.appShell = appShell;
        renderProfile();
    }

    @FXML
    private void goDashboard() {
        appShell.showDashboard(Session.getCurrentUser());
    }

    private void renderProfile() {
        User user = Session.getCurrentUser();
        setLabel(staffIdLabel, user == null ? "-" : user.getStaffId());
        setLabel(usernameLabel, SessionContext.username());
        setLabel(roleLabel, SessionContext.role());
        setLabel(sectionLabel, SessionContext.section());
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

        String group = PermissionHelper.roleGroup(SessionContext.role());
        if (roleBadgeLabel != null) {
            roleBadgeLabel.setText(group);
            roleBadgeLabel.getStyleClass().removeAll("role-admin", "role-doctor", "role-nurse", "role-staff", "role-unknown");
            roleBadgeLabel.getStyleClass().add(roleStyle(group));
        }

        if (permissionListBox != null) {
            permissionListBox.getChildren().clear();
            addPermission("View patients", true, "Read-only JavaFX patient board");
            addPermission("View alerts", true, "Alerts are surfaced through Notifications");
            addPermission("Acknowledge alerts", true, "Local database JavaFX alert action");
            addPermission("View clinical timeline", true, "Read-only patient history preview");
            addPermission("Manage users", PermissionHelper.canCreateUser(user) || PermissionHelper.canUpdateUser(user), "Future JavaFX write workflow");
            addPermission("Edit patients", PermissionHelper.canUpdatePatient(user), "Future JavaFX write workflow");
            addPermission("Enter vitals", PermissionHelper.canEnterVitals(user), "Future JavaFX write workflow");
            addPermission("Add medications", PermissionHelper.canAddMedication(user), "Future JavaFX write workflow");
            addPermission("Give medications", PermissionHelper.canGiveMedication(user), "Future JavaFX write workflow");
            addPermission("Create appointments", PermissionHelper.canCreateAppointment(user), "Future JavaFX write workflow");
            addPermission("Create reminders", PermissionHelper.canCreateReminder(user), "Future JavaFX write workflow");
        }

        boolean canTestWrite = PermissionHelper.canCreateTestAuditEvent(user);
        if (writeFoundationBox != null) {
            writeFoundationBox.setVisible(canTestWrite);
            writeFoundationBox.setManaged(canTestWrite);
        }
        if (createTestAuditButton != null) {
            createTestAuditButton.setDisable(!canTestWrite);
        }
        if (canTestWrite && writeFoundationStatusLabel != null) {
            NotificationHelper.showInfo(writeFoundationStatusLabel, "Ready for an admin-only safe audit write.");
        }
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
                showSuccess(profileStatusLabel, "Password changed. Raw passwords were not displayed or logged.");
            } catch (Exception e) {
                showError(profileStatusLabel, e.getMessage());
                event.consume();
            }
        });
        dialog.showAndWait();
    }

    @FXML
    private void createTestAuditEvent() {
        User user = Session.getCurrentUser();
        if (!PermissionHelper.canCreateTestAuditEvent(user)) {
            showError(writeFoundationStatusLabel, "Access denied. Admin role is required.");
            return;
        }

        try {
            AuditWriteHelper.write(
                    SessionContext.username(),
                    AuditAction.CREATE_TEST_AUDIT_EVENT,
                    "JavaFX Profile/Settings write foundation smoke test"
            );
            showSuccess(writeFoundationStatusLabel, "Test audit event created. Check Audit Logs.");
        } catch (Exception e) {
            showError(writeFoundationStatusLabel, "Could not create audit event: " + e.getMessage());
        }
    }

    private void addPermission(String label, boolean allowed, String note) {
        if (permissionListBox == null) {
            return;
        }
        Label row = new Label((allowed ? "Allowed: " : "Preview/Future: ") + label + " - " + note);
        row.getStyleClass().add(allowed ? "permission-allowed" : "permission-future");
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

    private String roleStyle(String group) {
        switch (group) {
            case "ADMIN":
                return "role-admin";
            case "DOCTOR":
                return "role-doctor";
            case "NURSE":
                return "role-nurse";
            case "STAFF":
                return "role-staff";
            default:
                return "role-unknown";
        }
    }
}
