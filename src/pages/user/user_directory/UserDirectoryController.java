package pages.user.user_directory;

import app.AppShell;
import app.FxController;
import app.SessionContext;
import app.helpers.PermissionHelper;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import pages.notification.NotificationHelper;
import pages.user.dao.SqliteUserDao;
import pages.user.user_form.UserFormController;
import users.Session;

import java.io.File;
import java.util.Locale;

public class UserDirectoryController implements FxController {

    private final SqliteUserDao userDao = new SqliteUserDao();
    private final ObservableList<SqliteUserDao.UserDirectoryRow> rows = FXCollections.observableArrayList();

    private AppShell appShell;

    @FXML private VBox accessDeniedPane;
    @FXML private VBox directoryContentPane;
    @FXML private TextField searchField;
    @FXML private ComboBox<String> roleFilter;
    @FXML private ComboBox<String> activeFilter;
    @FXML private Button addUserButton;
    @FXML private Label totalStaffMetricLabel;
    @FXML private Label onDutyMetricLabel;
    @FXML private Label offDutyMetricLabel;
    @FXML private Label onLeaveMetricLabel;
    @FXML private TableView<SqliteUserDao.UserDirectoryRow> userTable;
    @FXML private TableColumn<SqliteUserDao.UserDirectoryRow, SqliteUserDao.UserDirectoryRow> usernameColumn;
    @FXML private TableColumn<SqliteUserDao.UserDirectoryRow, String> roleColumn;
    @FXML private TableColumn<SqliteUserDao.UserDirectoryRow, String> activeColumn;
    @FXML private TableColumn<SqliteUserDao.UserDirectoryRow, SqliteUserDao.UserDirectoryRow> actionsColumn;
    @FXML private Label statusLabel;

    @Override
    public void setAppShell(AppShell appShell) {
        this.appShell = appShell;
        configureAccess();
        configureFilters();
        configureTable();
        if (isAdmin()) {
            loadUsers();
        }
    }

    @FXML
    private void loadUsers() {
        if (!isAdmin()) {
            NotificationHelper.showError(statusLabel, "Access denied.");
            return;
        }
        try {
            SqliteUserDao.UserDirectoryFilter filter = new SqliteUserDao.UserDirectoryFilter(
                    text(searchField),
                    internalRoleFilter(roleFilter == null ? null : roleFilter.getValue()),
                    null,
                    activeFilter == null ? null : activeFilter.getValue()
            );
            rows.setAll(userDao.findDirectoryRows(filter));
            userTable.setItems(rows);
            userTable.refresh();
            updateMetrics();
            if (rows.isEmpty()) {
                NotificationHelper.showInfo(statusLabel, "No staff members match the selected filters.");
            } else {
                NotificationHelper.showInfo(statusLabel, "Staff records loaded: " + rows.size());
            }
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, "Could not load staff records: " + e.getMessage());
        }
    }

    @FXML
    private void clearFilters() {
        if (searchField != null) {
            searchField.clear();
        }
        if (roleFilter != null) {
            roleFilter.getSelectionModel().select("All");
        }
        if (activeFilter != null) {
            activeFilter.getSelectionModel().select("All");
        }
        loadUsers();
    }

    @FXML
    private void addUser() {
        if (!PermissionHelper.canCreateUser(Session.getCurrentUser())) {
            NotificationHelper.showError(statusLabel, "Only administrators can add staff.");
            return;
        }
        if (UserFormController.showCreateDialog(statusLabel.getScene().getWindow(), Session.getCurrentUser())) {
            loadUsers();
            NotificationHelper.showSuccess(statusLabel, "Staff profile created.");
        }
    }

    private void configureAccess() {
        boolean admin = isAdmin();
        if (accessDeniedPane != null) {
            accessDeniedPane.setVisible(!admin);
            accessDeniedPane.setManaged(!admin);
        }
        if (directoryContentPane != null) {
            directoryContentPane.setVisible(admin);
            directoryContentPane.setManaged(admin);
        }
        if (addUserButton != null) {
            addUserButton.setVisible(admin);
            addUserButton.setManaged(admin);
        }
    }

    private void configureFilters() {
        if (roleFilter != null) {
            roleFilter.setItems(FXCollections.observableArrayList("All", "Admin", "Doctor", "Nurse", "Secretary"));
            roleFilter.getSelectionModel().select("All");
            roleFilter.valueProperty().addListener((observable, oldValue, newValue) -> {
                if (appShell != null && isAdmin()) {
                    loadUsers();
                }
            });
        }
        if (activeFilter != null) {
            activeFilter.setItems(FXCollections.observableArrayList("All", "On Duty", "Off Duty", "On Leave"));
            activeFilter.getSelectionModel().select("All");
            activeFilter.valueProperty().addListener((observable, oldValue, newValue) -> {
                if (appShell != null && isAdmin()) {
                    loadUsers();
                }
            });
        }
        if (searchField != null) {
            searchField.setOnAction(event -> loadUsers());
        }
    }

    private void configureTable() {
        if (userTable != null) {
            userTable.setItems(rows);
            userTable.setPlaceholder(new Label("No staff members found."));
        }
        if (usernameColumn != null) {
            usernameColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
            usernameColumn.setCellFactory(column -> new TableCell<>() {
                @Override
                protected void updateItem(SqliteUserDao.UserDirectoryRow item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setGraphic(null);
                        return;
                    }
                    setGraphic(buildStaffCell(item));
                    setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                }
            });
        }
        if (roleColumn != null) {
            roleColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(visibleRole(cell.getValue().getRole())));
            roleColumn.setCellFactory(column -> new TableCell<>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null || item.isBlank()) {
                        setText(null);
                        setGraphic(null);
                        return;
                    }
                    Label badge = new Label(item);
                    badge.getStyleClass().addAll("badge-pill", "role-badge", roleStyle(item));
                    setGraphic(badge);
                    setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                }
            });
        }
        if (activeColumn != null) {
            activeColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(dutyStatus(cell.getValue())));
            activeColumn.setCellFactory(column -> new TableCell<>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null || item.isBlank()) {
                        setText(null);
                        setGraphic(null);
                        return;
                    }
                    Label badge = new Label(item);
                    badge.getStyleClass().addAll("badge-pill", "status-badge", dutyStatusStyle(item));
                    setGraphic(badge);
                    setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                }
            });
        }
        if (actionsColumn != null) {
            actionsColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
            actionsColumn.setCellFactory(column -> new TableCell<>() {
                @Override
                protected void updateItem(SqliteUserDao.UserDirectoryRow item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setGraphic(null);
                        return;
                    }
                    Button viewButton = new Button("\uD83D\uDC41");
                    viewButton.getStyleClass().add("action-icon-button");
                    viewButton.setOnAction(event -> openProfile(item));

                    Button editButton = new Button("\u270E");
                    editButton.getStyleClass().add("action-icon-button");
                    editButton.setDisable(!PermissionHelper.canUpdateUser(Session.getCurrentUser()));
                    editButton.setOnAction(event -> editStaff(item));

                    HBox actions = new HBox(10.0, viewButton, editButton);
                    actions.setAlignment(Pos.CENTER_LEFT);
                    setGraphic(actions);
                    setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                }
            });
        }
    }

    private HBox buildStaffCell(SqliteUserDao.UserDirectoryRow row) {
        StackPane avatar = new StackPane();
        avatar.getStyleClass().add("staff-avatar");

        ImageView photoView = buildAvatarImage(row.getProfilePhotoPath());
        if (photoView != null) {
            avatar.getChildren().add(photoView);
        } else {
            Label initials = new Label(initials(row));
            initials.getStyleClass().add("staff-avatar-initials");
            avatar.getChildren().add(initials);
        }

        Label nameLabel = new Label(displayName(row));
        nameLabel.getStyleClass().add("staff-name");

        String subtitleText = row.getUsername();
        if (!blank(row.getStaffId()).equals("-")) {
            subtitleText = row.getStaffId() + "  |  " + row.getUsername();
        }
        Label subtitleLabel = new Label(subtitleText);
        subtitleLabel.getStyleClass().add("staff-subtitle");

        VBox textBox = new VBox(4.0, nameLabel, subtitleLabel);
        HBox.setHgrow(textBox, Priority.ALWAYS);

        HBox wrapper = new HBox(14.0, avatar, textBox);
        wrapper.setAlignment(Pos.CENTER_LEFT);
        wrapper.getStyleClass().add("staff-row");
        return wrapper;
    }

    private ImageView buildAvatarImage(String pathValue) {
        if (pathValue == null || pathValue.isBlank()) {
            return null;
        }
        File file = new File(pathValue);
        if (!file.exists() || !file.isFile()) {
            return null;
        }
        try {
            ImageView view = new ImageView(new Image(file.toURI().toString(), 48, 48, true, true));
            view.setFitWidth(48);
            view.setFitHeight(48);
            view.setPreserveRatio(true);
            view.getStyleClass().add("profile-photo");
            return view;
        } catch (Exception ignored) {
            return null;
        }
    }

    private void openProfile(SqliteUserDao.UserDirectoryRow row) {
        boolean edited = StaffProfileDialogController.showDialog(
                statusLabel.getScene().getWindow(),
                Session.getCurrentUser(),
                row
        );
        if (edited) {
            loadUsers();
        }
    }

    private void editStaff(SqliteUserDao.UserDirectoryRow row) {
        if (!PermissionHelper.canUpdateUser(Session.getCurrentUser())) {
            NotificationHelper.showError(statusLabel, "Only administrators can edit staff.");
            return;
        }
        if (UserFormController.showEditDialog(statusLabel.getScene().getWindow(), Session.getCurrentUser(), row)) {
            loadUsers();
            NotificationHelper.showSuccess(statusLabel, "Staff profile updated.");
        }
    }

    private void updateMetrics() {
        int total = rows.size();
        int onDuty = 0;
        int offDuty = 0;
        int onLeave = 0;
        for (SqliteUserDao.UserDirectoryRow row : rows) {
            String status = dutyStatus(row);
            if ("Off Duty".equalsIgnoreCase(status)) {
                offDuty++;
            } else if ("On Leave".equalsIgnoreCase(status)) {
                onLeave++;
            } else {
                onDuty++;
            }
        }
        if (totalStaffMetricLabel != null) {
            totalStaffMetricLabel.setText(String.valueOf(total));
        }
        if (onDutyMetricLabel != null) {
            onDutyMetricLabel.setText(String.valueOf(onDuty));
        }
        if (offDutyMetricLabel != null) {
            offDutyMetricLabel.setText(String.valueOf(offDuty));
        }
        if (onLeaveMetricLabel != null) {
            onLeaveMetricLabel.setText(String.valueOf(onLeave));
        }
    }

    private boolean isAdmin() {
        String role = SessionContext.role();
        return role != null && role.toUpperCase(Locale.ROOT).contains("ADMIN");
    }

    private String internalRoleFilter(String visibleRole) {
        if (visibleRole == null || visibleRole.isBlank() || "All".equalsIgnoreCase(visibleRole)) {
            return "All";
        }
        if ("Secretary".equalsIgnoreCase(visibleRole)) {
            return "STAFF";
        }
        return visibleRole.toUpperCase(Locale.ROOT);
    }

    private String visibleRole(String internalRole) {
        if (internalRole == null || internalRole.isBlank()) {
            return "Secretary";
        }
        String upper = internalRole.toUpperCase(Locale.ROOT);
        if (upper.contains("ADMIN")) {
            return "Admin";
        }
        if (upper.contains("DOCTOR") || upper.contains("MEDICAL") || upper.contains("DEPARTMENT HEAD")) {
            return "Doctor";
        }
        if (upper.contains("NURSE") || upper.contains("NURSING")) {
            return "Nurse";
        }
        return "Secretary";
    }

    private String roleStyle(String visibleRole) {
        if ("Admin".equalsIgnoreCase(visibleRole)) {
            return "role-admin";
        }
        if ("Doctor".equalsIgnoreCase(visibleRole)) {
            return "role-doctor";
        }
        if ("Nurse".equalsIgnoreCase(visibleRole)) {
            return "role-nurse";
        }
        return "role-staff";
    }

    private String dutyStatus(SqliteUserDao.UserDirectoryRow row) {
        if (row == null) {
            return "On Duty";
        }
        String status = row.getDutyStatus();
        if ("Off Duty".equalsIgnoreCase(status)) {
            return "Off Duty";
        }
        if ("On Leave".equalsIgnoreCase(status)) {
            return "On Leave";
        }
        return "On Duty";
    }

    private String dutyStatusStyle(String status) {
        if ("Off Duty".equalsIgnoreCase(status)) {
            return "staff-status-off-duty";
        }
        if ("On Leave".equalsIgnoreCase(status)) {
            return "staff-status-on-leave";
        }
        return "staff-status-on-duty";
    }

    private String displayName(SqliteUserDao.UserDirectoryRow row) {
        if (row == null) {
            return "-";
        }
        String value = row.getDisplayName();
        if (value == null || value.isBlank()) {
            return row.getUsername();
        }
        return value;
    }

    private String initials(SqliteUserDao.UserDirectoryRow row) {
        String source = displayName(row);
        if (source == null || source.isBlank() || "-".equals(source)) {
            source = row == null ? "" : row.getUsername();
        }
        if (source == null || source.isBlank()) {
            return "SC";
        }
        String[] parts = source.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase(Locale.ROOT);
        }
        return (parts[0].substring(0, 1) + parts[1].substring(0, 1)).toUpperCase(Locale.ROOT);
    }

    private String text(TextField field) {
        return field == null || field.getText() == null ? "" : field.getText().trim();
    }

    private String blank(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
