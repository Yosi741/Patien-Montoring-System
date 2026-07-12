package pages.user.user_directory;

import app.navigation.AppNavigator;
import app.helpers.PermissionHelper;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import pages.user.User;
import pages.user.dao.SqliteUserDao;
import pages.user.user_form.UserFormController;

import java.io.File;

public class StaffProfileDialogController {

    private User currentUser;
    private SqliteUserDao.UserDirectoryRow row;
    private Dialog<ButtonType> dialog;
    private boolean edited;

    @FXML private ImageView profilePhotoView;
    @FXML private Label profileInitialsLabel;
    @FXML private Label fullNameLabel;
    @FXML private Label roleBadgeLabel;
    @FXML private Label dutyStatusBadgeLabel;
    @FXML private Label staffIdLabel;
    @FXML private Label usernameLabel;
    @FXML private Label sectionLabel;
    @FXML private Label emailLabel;
    @FXML private Label phoneLabel;
    @FXML private Label addressLabel;
    @FXML private Label accountStatusLabel;
    @FXML private Label createdAtLabel;
    @FXML private VBox permissionListBox;

    public static boolean showDialog(Window owner, User currentUser, SqliteUserDao.UserDirectoryRow row) {
        try {
            FXMLLoader loader = new FXMLLoader(AppNavigator.resolve("/pages/user/user_directory/StaffProfileDialogView.fxml"));
            Parent root = loader.load();
            StaffProfileDialogController controller = loader.getController();
            Dialog<ButtonType> dialog = new Dialog<>();
            app.helpers.DialogThemeHelper.apply(dialog);
            dialog.setTitle("Staff Profile");
            dialog.initOwner(owner);
            dialog.getDialogPane().setContent(root);
            dialog.getDialogPane().getButtonTypes().setAll(new ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE));
            controller.prepare(dialog, currentUser, row);
            dialog.showAndWait();
            return controller.edited;
        } catch (Exception e) {
            throw new IllegalStateException("Could not open staff profile: " + e.getMessage(), e);
        }
    }

    private void prepare(Dialog<ButtonType> dialog, User currentUser, SqliteUserDao.UserDirectoryRow row) {
        this.dialog = dialog;
        this.currentUser = currentUser;
        this.row = row;
        render();
    }

    private void render() {
        setLabel(fullNameLabel, row == null ? "-" : row.getDisplayName());
        setLabel(staffIdLabel, row == null ? "-" : row.getStaffId());
        setLabel(usernameLabel, row == null ? "-" : row.getUsername());
        setLabel(sectionLabel, row == null ? "-" : safe(row.getSection()));
        setLabel(emailLabel, row == null ? "-" : safe(row.getEmail()));
        setLabel(phoneLabel, row == null ? "-" : safe(row.getPhone()));
        setLabel(addressLabel, row == null ? "-" : safe(row.getAddress()));
        setLabel(accountStatusLabel, row == null ? "-" : row.getActiveStatus());
        setLabel(createdAtLabel, row == null ? "-" : safe(row.getCreatedAt()));
        renderRoleBadge();
        renderDutyBadge();
        renderPhoto();
        renderPermissions();
    }

    @FXML
    private void closeDialog() {
        if (dialog != null) {
            dialog.close();
        }
    }

    @FXML
    private void editStaff() {
        if (row == null || dialog == null) {
            return;
        }
        Window owner = dialog.getDialogPane().getScene() == null ? null : dialog.getDialogPane().getScene().getWindow();
        if (UserFormController.showEditDialog(owner, currentUser, row)) {
            edited = true;
            dialog.close();
        }
    }

    private void renderRoleBadge() {
        String group = PermissionHelper.roleGroup(row == null ? "" : row.getRole());
        roleBadgeLabel.setText(displayRole(row == null ? "" : row.getRole()));
        roleBadgeLabel.getStyleClass().removeAll("role-admin", "role-doctor", "role-nurse", "role-staff", "role-unknown");
        roleBadgeLabel.getStyleClass().add(roleStyle(group));
    }

    private void renderDutyBadge() {
        String duty = row == null ? "On Duty" : safeDuty(row.getDutyStatus());
        dutyStatusBadgeLabel.setText(duty);
        dutyStatusBadgeLabel.getStyleClass().removeAll("staff-status-active", "staff-status-on-duty", "staff-status-off-duty", "staff-status-on-leave", "staff-status-inactive");
        if ("Off Duty".equalsIgnoreCase(duty)) {
            dutyStatusBadgeLabel.getStyleClass().add("staff-status-off-duty");
        } else if ("On Leave".equalsIgnoreCase(duty)) {
            dutyStatusBadgeLabel.getStyleClass().add("staff-status-on-leave");
        } else {
            dutyStatusBadgeLabel.getStyleClass().add("staff-status-on-duty");
        }
    }

    private void renderPhoto() {
        if (profileInitialsLabel != null) {
            profileInitialsLabel.setText(initials(row == null ? "" : row.getDisplayName(), row == null ? "" : row.getUsername()));
            profileInitialsLabel.setVisible(true);
            profileInitialsLabel.setManaged(true);
        }
        if (profilePhotoView == null || row == null || row.getProfilePhotoPath() == null || row.getProfilePhotoPath().isBlank()) {
            return;
        }
        try {
            File imageFile = new File(row.getProfilePhotoPath());
            if (!imageFile.exists()) {
                return;
            }
            profilePhotoView.setImage(new Image(imageFile.toURI().toString(), 136, 136, true, true));
            profilePhotoView.setVisible(true);
            profilePhotoView.setManaged(true);
            if (profileInitialsLabel != null) {
                profileInitialsLabel.setVisible(false);
                profileInitialsLabel.setManaged(false);
            }
        } catch (Exception ignored) {
            profilePhotoView.setImage(null);
            profilePhotoView.setVisible(false);
            profilePhotoView.setManaged(false);
        }
    }

    private void renderPermissions() {
        if (permissionListBox == null) {
            return;
        }
        permissionListBox.getChildren().clear();
        User user = row == null ? null : new User(row.getUsername(), "", row.getRole(), row.getSection(), row.getStaffId());
        addPermission("View patients", true);
        addPermission("Enter vitals", PermissionHelper.canEnterVitals(user));
        addPermission("Manage appointments", PermissionHelper.canCreateAppointment(user));
        addPermission("Manage staff accounts", PermissionHelper.canViewUserDirectory(user));
    }

    private void addPermission(String text, boolean allowed) {
        Label label = new Label((allowed ? "Allowed: " : "Restricted: ") + text);
        label.getStyleClass().addAll("badge-pill", allowed ? "permission-allowed" : "permission-future");
        label.setWrapText(true);
        permissionListBox.getChildren().add(label);
    }

    private String displayRole(String role) {
        String group = PermissionHelper.roleGroup(role);
        return switch (group) {
            case "ADMIN" -> "Admin";
            case "DOCTOR" -> "Doctor";
            case "NURSE" -> "Nurse";
            case "STAFF" -> "Secretary";
            default -> "Staff";
        };
    }

    private String roleStyle(String group) {
        return switch (group) {
            case "ADMIN" -> "role-admin";
            case "DOCTOR" -> "role-doctor";
            case "NURSE" -> "role-nurse";
            case "STAFF" -> "role-staff";
            default -> "role-unknown";
        };
    }

    private String initials(String displayName, String username) {
        String source = displayName == null || displayName.isBlank() ? username : displayName;
        if (source == null || source.isBlank()) {
            return "SC";
        }
        String[] parts = source.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
        }
        return (parts[0].substring(0, 1) + parts[1].substring(0, 1)).toUpperCase();
    }

    private void setLabel(Label label, String value) {
        if (label != null) {
            label.setText(safe(value));
        }
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String safeDuty(String value) {
        if ("Off Duty".equalsIgnoreCase(value)) {
            return "Off Duty";
        }
        if ("On Leave".equalsIgnoreCase(value)) {
            return "On Leave";
        }
        return "On Duty";
    }
}
