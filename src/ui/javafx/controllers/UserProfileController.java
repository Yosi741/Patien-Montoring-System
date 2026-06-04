package ui.javafx.controllers;

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
import services.UserProfileService;
import ui.javafx.AppShell;
import ui.javafx.FxController;
import ui.javafx.SessionContext;
import ui.javafx.helpers.AuditAction;
import ui.javafx.helpers.AuditWriteHelper;
import ui.javafx.helpers.NotificationHelper;
import ui.javafx.helpers.PermissionHelper;
import users.Session;
import users.User;

public class UserProfileController implements FxController {

    private AppShell appShell;
    private final UserProfileService profileService = new UserProfileService();

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
        usernameLabel.setText(SessionContext.username());
        roleLabel.setText(SessionContext.role());
        sectionLabel.setText(SessionContext.section());
        accountStatusLabel.setText(user == null ? "Unknown" : "Active");
        authSourceLabel.setText(SessionContext.authSource());
        loginTimeLabel.setText(SessionContext.loginTimeText());
        try {
            profileService.findProfile(SessionContext.username()).ifPresent(profile -> {
                emailField.setText(profile.getEmail());
                phoneField.setText(profile.getPhone());
            });
        } catch (Exception e) {
            NotificationHelper.showError(profileStatusLabel, "Could not load profile contact fields: " + e.getMessage());
        }

        String group = PermissionHelper.roleGroup(SessionContext.role());
        roleBadgeLabel.setText(group);
        roleBadgeLabel.getStyleClass().removeAll("role-admin", "role-doctor", "role-nurse", "role-staff", "role-unknown");
        roleBadgeLabel.getStyleClass().add(roleStyle(group));

        permissionListBox.getChildren().clear();
        addPermission("View patients", true, "Read-only JavaFX patient board");
        addPermission("View alerts", true, "Read-only JavaFX Alert Center");
        addPermission("Acknowledge alerts", true, "Local database JavaFX alert action");
        addPermission("View clinical timeline", true, "Read-only patient history preview");
        addPermission("Manage users", PermissionHelper.canCreateUser(user) || PermissionHelper.canUpdateUser(user), "Future JavaFX write workflow");
        addPermission("Edit patients", PermissionHelper.canUpdatePatient(user), "Future JavaFX write workflow");
        addPermission("Enter vitals", PermissionHelper.canEnterVitals(user), "Future JavaFX write workflow");
        addPermission("Add medications", PermissionHelper.canAddMedication(user), "Future JavaFX write workflow");
        addPermission("Give medications", PermissionHelper.canGiveMedication(user), "Future JavaFX write workflow");
        addPermission("Register devices", PermissionHelper.canRegisterDevice(user), "Future JavaFX write workflow");
        addPermission("Create appointments", PermissionHelper.canCreateAppointment(user), "Future JavaFX write workflow");
        addPermission("Create reminders", PermissionHelper.canCreateReminder(user), "Future JavaFX write workflow");

        boolean canTestWrite = PermissionHelper.canCreateTestAuditEvent(user);
        writeFoundationBox.setVisible(canTestWrite);
        writeFoundationBox.setManaged(canTestWrite);
        createTestAuditButton.setDisable(!canTestWrite);
        if (canTestWrite) {
            NotificationHelper.showInfo(writeFoundationStatusLabel, "Ready for an admin-only safe audit write.");
        }
    }

    @FXML
    private void saveProfile() {
        try {
            profileService.updateProfile(Session.getCurrentUser(), emailField.getText(), phoneField.getText());
            NotificationHelper.showSuccess(profileStatusLabel, "Profile contact fields saved in SQLite.");
        } catch (Exception e) {
            NotificationHelper.showError(profileStatusLabel, e.getMessage());
        }
    }

    @FXML
    private void changePassword() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Change Password");
        ui.javafx.helpers.DialogThemeHelper.apply(dialog);
        dialog.initOwner(usernameLabel.getScene().getWindow());

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
                NotificationHelper.showError(profileStatusLabel, "New password and confirmation do not match.");
                event.consume();
                return;
            }
            try {
                profileService.changeOwnPassword(Session.getCurrentUser(),
                        currentPassword.getText().toCharArray(),
                        newPassword.getText().toCharArray());
                NotificationHelper.showSuccess(profileStatusLabel, "Password changed. Raw passwords were not displayed or logged.");
            } catch (Exception e) {
                NotificationHelper.showError(profileStatusLabel, e.getMessage());
                event.consume();
            }
        });
        dialog.showAndWait();
    }

    @FXML
    private void createTestAuditEvent() {
        User user = Session.getCurrentUser();
        if (!PermissionHelper.canCreateTestAuditEvent(user)) {
            NotificationHelper.showError(writeFoundationStatusLabel, "Access denied. Admin role is required.");
            return;
        }

        try {
            AuditWriteHelper.write(
                    SessionContext.username(),
                    AuditAction.CREATE_TEST_AUDIT_EVENT,
                    "JavaFX Profile/Settings write foundation smoke test"
            );
            NotificationHelper.showSuccess(writeFoundationStatusLabel, "Test audit event created. Check Audit Logs.");
        } catch (Exception e) {
            NotificationHelper.showError(writeFoundationStatusLabel, "Could not create audit event: " + e.getMessage());
        }
    }

    private void addPermission(String label, boolean allowed, String note) {
        Label row = new Label((allowed ? "Allowed: " : "Preview/Future: ") + label + " - " + note);
        row.getStyleClass().add(allowed ? "permission-allowed" : "permission-future");
        row.setWrapText(true);
        permissionListBox.getChildren().add(row);
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
