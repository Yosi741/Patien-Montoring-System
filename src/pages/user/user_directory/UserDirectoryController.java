package pages.user.user_directory;

import pages.user.dao.SqliteUserDao;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import users.roles.RolePermissionService;
import pages.user.user_form.UserFormController;
import pages.user.services.UserWriteService;
import app.AppShell;
import app.FxController;
import app.SessionContext;
import app.helpers.DialogHelper;
import pages.notification.NotificationHelper;
import app.helpers.PermissionHelper;
import app.helpers.SelectionHelper;
import users.Session;
import pages.user.User;

import java.util.ArrayList;

public class UserDirectoryController implements FxController {

    private final SqliteUserDao userDao = new SqliteUserDao();
    private final UserWriteService userWriteService = new UserWriteService();
    private final ObservableList<SqliteUserDao.UserDirectoryRow> rows = FXCollections.observableArrayList();
    private AppShell appShell;

    @FXML private VBox accessDeniedPane;
    @FXML private VBox directoryContentPane;
    @FXML private TextField searchField;
    @FXML private ComboBox<String> roleFilter;
    @FXML private ComboBox<String> sectionFilter;
    @FXML private ComboBox<String> activeFilter;
    @FXML private Button addUserButton;
    @FXML private Button editUserButton;
    @FXML private Button deactivateUserButton;
    @FXML private Button resetPasswordButton;
    @FXML private TableView<SqliteUserDao.UserDirectoryRow> userTable;
    @FXML private TableColumn<SqliteUserDao.UserDirectoryRow, Number> rowNumberColumn;
    @FXML private TableColumn<SqliteUserDao.UserDirectoryRow, Long> idColumn;
    @FXML private TableColumn<SqliteUserDao.UserDirectoryRow, String> staffIdColumn;
    @FXML private TableColumn<SqliteUserDao.UserDirectoryRow, String> usernameColumn;
    @FXML private TableColumn<SqliteUserDao.UserDirectoryRow, String> roleColumn;
    @FXML private TableColumn<SqliteUserDao.UserDirectoryRow, String> sectionColumn;
    @FXML private TableColumn<SqliteUserDao.UserDirectoryRow, String> emailColumn;
    @FXML private TableColumn<SqliteUserDao.UserDirectoryRow, String> activeColumn;
    @FXML private TableColumn<SqliteUserDao.UserDirectoryRow, String> createdAtColumn;
    @FXML private Label detailTitleLabel;
    @FXML private Label detailStaffIdLabel;
    @FXML private Label detailUsernameLabel;
    @FXML private Label detailRoleLabel;
    @FXML private Label detailRoleBadgeLabel;
    @FXML private Label detailSectionLabel;
    @FXML private Label detailEmailLabel;
    @FXML private Label detailStatusLabel;
    @FXML private Label detailAuthSourceLabel;
    @FXML private Label detailCreatedAtLabel;
    @FXML private VBox permissionListBox;
    @FXML private Label statusLabel;

    @Override
    public void setAppShell(AppShell appShell) {
        this.appShell = appShell;
        configureAccess();
        configureTable();
        configureSelection();
        clearDetail();
        if (isAdmin()) {
            loadUsers();
        }
    }

    @FXML
    private void loadUsers() {
        if (!isAdmin()) {
            statusLabel.setText("Access denied.");
            return;
        }
        try {
            SqliteUserDao.UserDirectoryFilter filter = new SqliteUserDao.UserDirectoryFilter(
                    searchField.getText(),
                    roleFilter.getValue(),
                    sectionFilter.getValue(),
                    activeFilter.getValue()
            );
            var loadedRows = userDao.findDirectoryRows(filter);
            SelectionHelper.runWhenTableStable(userTable, () -> {
                SelectionHelper.safeReplaceItems(userTable, rows, loadedRows);
                statusLabel.setText(rows.isEmpty()
                        ? "No users match the selected filters."
                        : "Users loaded: " + rows.size() + " | Sorted by role, section, username");
            });
        } catch (Exception e) {
            statusLabel.setText("Could not load users: " + e.getMessage());
        }
    }

    @FXML
    private void clearFilters() {
        searchField.clear();
        roleFilter.getSelectionModel().select("All");
        sectionFilter.getSelectionModel().select("All");
        activeFilter.getSelectionModel().select("All");
        loadUsers();
    }

    @FXML
    private void showDashboard() {
        appShell.showDashboard(Session.getCurrentUser());
    }

    private void configureAccess() {
        boolean admin = isAdmin();
        accessDeniedPane.setVisible(!admin);
        accessDeniedPane.setManaged(!admin);
        directoryContentPane.setVisible(admin);
        directoryContentPane.setManaged(admin);
        setButtonVisible(addUserButton, admin);
        setButtonVisible(editUserButton, admin);
        setButtonVisible(deactivateUserButton, admin);
        setButtonVisible(resetPasswordButton, admin);
    }

    private void configureTable() {
        if (rowNumberColumn != null) {
            rowNumberColumn.setCellValueFactory(cell -> {
                int index = userTable.getItems() == null ? -1 : userTable.getItems().indexOf(cell.getValue());
                Number rowNumber = index >= 0 ? index + 1 : null;
                return new ReadOnlyObjectWrapper<>(rowNumber);
            });
        }
        idColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        staffIdColumn.setCellValueFactory(new PropertyValueFactory<>("staffId"));
        usernameColumn.setCellValueFactory(new PropertyValueFactory<>("username"));
        roleColumn.setCellValueFactory(new PropertyValueFactory<>("role"));
        sectionColumn.setCellValueFactory(new PropertyValueFactory<>("section"));
        emailColumn.setCellValueFactory(new PropertyValueFactory<>("email"));
        activeColumn.setCellValueFactory(new PropertyValueFactory<>("activeStatus"));
        createdAtColumn.setCellValueFactory(new PropertyValueFactory<>("createdAt"));
    }


    @FXML
    private void addUser() {
        if (!PermissionHelper.canCreateUser(Session.getCurrentUser())) {
            showDenied();
            return;
        }
        if (UserFormController.showCreateDialog(statusLabel.getScene().getWindow(), Session.getCurrentUser())) {

            loadUsers();
            NotificationHelper.showSuccess(statusLabel, "User account saved.");
        }
    }

    @FXML
    private void editSelectedUser() {
        SqliteUserDao.UserDirectoryRow selected = selectedUser();
        if (selected == null) {
            return;
        }
        if (!PermissionHelper.canUpdateUser(Session.getCurrentUser())) {
            showDenied();
            return;
        }
        if (UserFormController.showEditDialog(statusLabel.getScene().getWindow(), Session.getCurrentUser(), selected)) {
            loadUsers();
            selectUser(selected.getUsername());
            NotificationHelper.showSuccess(statusLabel, "User updated.");
        }
    }

    @FXML
    private void deactivateSelectedUser() {
        SqliteUserDao.UserDirectoryRow selected = selectedUser();
        if (selected == null) {
            return;
        }
        if (!PermissionHelper.canDeactivateUser(Session.getCurrentUser())) {
            showDenied();
            return;
        }
        boolean self = selected.getUsername().equalsIgnoreCase(SessionContext.username());
        String message = self
                ? "You are deactivating your own active account. Continue only if you have another admin account available."
                : "Deactivate user " + selected.getUsername() + "?";
        if (!DialogHelper.confirm("Deactivate User", message)) {
            return;
        }
        try {
            userWriteService.deactivateUser(Session.getCurrentUser(), selected.getUsername(), self);
            loadUsers();
            selectUser(selected.getUsername());
            NotificationHelper.showSuccess(statusLabel, "User deactivated.");
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
        }
    }

    @FXML
    private void resetSelectedPassword() {
        SqliteUserDao.UserDirectoryRow selected = selectedUser();
        if (selected == null) {
            return;
        }
        if (!PermissionHelper.canResetUserPassword(Session.getCurrentUser())) {
            showDenied();
            return;
        }
        if (UserFormController.showResetPasswordDialog(statusLabel.getScene().getWindow(), Session.getCurrentUser(), selected)) {
            loadUsers();
            selectUser(selected.getUsername());
            NotificationHelper.showSuccess(statusLabel, "Password reset. Raw password was not logged.");
        }
    }

    private void configureSelection() {
        userTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == null) {
                clearDetail();
            } else {
                renderDetail(newValue);
            }
        });
    }

    private void renderDetail(SqliteUserDao.UserDirectoryRow row) {
        detailTitleLabel.setText(row.getUsername());
        detailStaffIdLabel.setText(blank(row.getStaffId()));
        detailUsernameLabel.setText(row.getUsername());
        detailRoleLabel.setText(row.getRole());
        detailSectionLabel.setText(row.getSection());
        detailEmailLabel.setText(blank(row.getEmail()));
        detailStatusLabel.setText(row.getActiveStatus());
        detailAuthSourceLabel.setText(row.getUsername().equalsIgnoreCase(SessionContext.username())
                ? SessionContext.authSource()
                : "SQLite user table");
        detailCreatedAtLabel.setText(row.getCreatedAt() == null ? "Unknown" : row.getCreatedAt());

        String group = roleGroup(row.getRole());
        detailRoleBadgeLabel.setText(group);
        detailRoleBadgeLabel.getStyleClass().removeAll("role-admin", "role-doctor", "role-nurse", "role-staff", "role-unknown");
        detailRoleBadgeLabel.getStyleClass().add(roleStyle(group));

        permissionListBox.getChildren().clear();
        User user = new User(row.getUsername(), "", row.getRole(), row.getSection());
        boolean adminRole = "ADMIN".equals(group);
        addPermission("View patients", true);
        addPermission("Enter vitals", PermissionHelper.canEnterVitals(user));
        addPermission("Manage patient reminders", "ADMIN".equals(group) || "DOCTOR".equals(group) || "NURSE".equals(group));
        addPermission("Manage appointments", "ADMIN".equals(group) || "DOCTOR".equals(group));
        addPermission("Review alerts through Notifications", true);
        addPermission("Manage users", adminRole || RolePermissionService.canManageUsers(user));
        addPermission("Manage rooms and sections", adminRole);
    }

    private void clearDetail() {
        detailTitleLabel.setText("Select a staff user");
        detailStaffIdLabel.setText("-");
        detailUsernameLabel.setText("-");
        detailRoleLabel.setText("-");
        detailSectionLabel.setText("-");
        detailEmailLabel.setText("-");
        detailStatusLabel.setText("-");
        detailAuthSourceLabel.setText("-");
        detailCreatedAtLabel.setText("-");
        detailRoleBadgeLabel.setText("UNKNOWN");
        detailRoleBadgeLabel.getStyleClass().removeAll("role-admin", "role-doctor", "role-nurse", "role-staff", "role-unknown");
        detailRoleBadgeLabel.getStyleClass().add("role-unknown");
        permissionListBox.getChildren().clear();
        Label empty = new Label("Select a user to preview role-based access.");
        empty.getStyleClass().add("muted-text");
        empty.setWrapText(true);
        permissionListBox.getChildren().add(empty);
    }

    private void addPermission(String label, boolean allowed) {
        Label row = new Label((allowed ? "Allowed: " : "Restricted: ") + label);
        row.getStyleClass().add(allowed ? "permission-allowed" : "permission-future");
        row.setWrapText(true);
        permissionListBox.getChildren().add(row);
    }

    private SqliteUserDao.UserDirectoryRow selectedUser() {
        SqliteUserDao.UserDirectoryRow selected = userTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            NotificationHelper.showError(statusLabel, "Select a user first.");
        }
        return selected;
    }

    private void selectUser(String username) {
        if (username == null) {
            return;
        }
        for (SqliteUserDao.UserDirectoryRow row : rows) {
            if (username.equalsIgnoreCase(row.getUsername())) {
                int index = userTable.getItems() == null ? -1 : userTable.getItems().indexOf(row);
                SelectionHelper.safeSelectIndex(userTable, index);
                return;
            }
        }
    }



    private void showDenied() {
        NotificationHelper.showError(statusLabel, "Access denied. Admin role is required.");
    }

    private boolean isAdmin() {
        return "ADMIN".equals(roleGroup(SessionContext.role()));
    }

    private void setButtonVisible(Button button, boolean visible) {
        if (button == null) {
            return;
        }
        button.setVisible(visible);
        button.setManaged(visible);
    }

    private String roleGroup(String role) {
        if (role == null) {
            return "UNKNOWN";
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
        if (upper.isBlank() || upper.equals("UNKNOWN")) {
            return "UNKNOWN";
        }
        return "STAFF";
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

    private String blank(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
