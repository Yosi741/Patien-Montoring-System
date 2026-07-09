package pages.patient.patient_board;

import app.AppShell;
import app.FxController;
import app.helpers.DialogHelper;
import app.helpers.PermissionHelper;
import app.helpers.SelectionHelper;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import pages.notification.NotificationHelper;
import pages.patient.dao.SqlitePatientDao;
import pages.patient.patient_form.PatientFormController;
import pages.patient.services.PatientWriteService;
import users.Session;

import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

public class PatientListController implements FxController {

    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private final SqlitePatientDao patientDao = new SqlitePatientDao();
    private final PatientWriteService patientWriteService = new PatientWriteService();
    private final ObservableList<SqlitePatientDao.PatientListRow> patients = FXCollections.observableArrayList();

    private AppShell appShell;
    private boolean suppressFilterEvents;
    private boolean filterListenersConfigured;
    private boolean canWritePatients;
    private boolean canArchivePatients;

    @FXML private TextField searchField;
    @FXML private ComboBox<String> statusFilter;
    @FXML private Label statusLabel;
    @FXML private TableView<SqlitePatientDao.PatientListRow> patientTable;
    @FXML private TableColumn<SqlitePatientDao.PatientListRow, SqlitePatientDao.PatientListRow> patientColumn;
    @FXML private TableColumn<SqlitePatientDao.PatientListRow, String> idColumn;
    @FXML private TableColumn<SqlitePatientDao.PatientListRow, SqlitePatientDao.PatientListRow> ageGenderColumn;
    @FXML private TableColumn<SqlitePatientDao.PatientListRow, SqlitePatientDao.PatientListRow> contactColumn;
    @FXML private TableColumn<SqlitePatientDao.PatientListRow, SqlitePatientDao.PatientListRow> statusColumn;
    @FXML private TableColumn<SqlitePatientDao.PatientListRow, SqlitePatientDao.PatientListRow> actionsColumn;
    @FXML private Button addPatientButton;

    @Override
    public void setAppShell(AppShell appShell) {
        this.appShell = appShell;
        configureTable();
        configureFilters();
        configureWritePermissions();
        loadPatients();
        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!suppressFilterEvents) {
                loadPatients();
            }
        });
    }

    @FXML
    private void loadPatients() {
        try {
            List<SqlitePatientDao.PatientListRow> loadedPatients = patientDao.findPatientListRows(buildFilter());
            SelectionHelper.runWhenTablesStable(() -> {
                SelectionHelper.safeReplaceItems(patientTable, patients, loadedPatients);
                updateStatusLine(loadedPatients.size());
            }, patientTable);
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, "Could not load patient records: " + e.getMessage());
        }
    }

    @FXML
    private void addPatient() {
        if (!canWritePatients) {
            NotificationHelper.showError(statusLabel, "Access denied. Admin or Doctor role is required.");
            return;
        }
        try {
            boolean saved = PatientFormController.showCreateDialog(patientTable.getScene().getWindow(), Session.getCurrentUser());
            if (saved) {
                loadPatients();
                NotificationHelper.showSuccess(statusLabel, "Patient record saved.");
            }
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
        }
    }

    @FXML
    private void clearFilters() {
        suppressFilterEvents = true;
        searchField.clear();
        if (statusFilter != null) {
            statusFilter.getSelectionModel().select("All");
        }
        suppressFilterEvents = false;
        loadPatients();
    }

    public void applySearchQuery(String query) {
        if (searchField == null) {
            return;
        }
        suppressFilterEvents = true;
        searchField.setText(query == null ? "" : query.trim());
        suppressFilterEvents = false;
        loadPatients();
    }

    private void configureTable() {
        patientTable.setItems(patients);
        patientTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        patientTable.setFixedCellSize(58);

        patientColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
        patientColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(SqlitePatientDao.PatientListRow row, boolean empty) {
                super.updateItem(row, empty);
                setAlignment(Pos.CENTER_LEFT);
                if (empty || row == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                setText(null);
                setGraphic(buildPatientCell(row));
            }
        });

        idColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().getPatientId()));

        ageGenderColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
        ageGenderColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(SqlitePatientDao.PatientListRow row, boolean empty) {
                super.updateItem(row, empty);
                setAlignment(Pos.CENTER_LEFT);
                if (empty || row == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                setText(null);
                setGraphic(buildSingleLineCell(formatAgeGender(row), "patient-primary-text"));
            }
        });

        contactColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
        contactColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(SqlitePatientDao.PatientListRow row, boolean empty) {
                super.updateItem(row, empty);
                setAlignment(Pos.CENTER_LEFT);
                if (empty || row == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                setText(null);
                setGraphic(buildContactCell(row));
            }
        });

        statusColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
        statusColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(SqlitePatientDao.PatientListRow row, boolean empty) {
                super.updateItem(row, empty);
                setAlignment(Pos.CENTER);
                getStyleClass().remove("patient-status-cell");
                if (empty || row == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                Label badge = new Label(visibleStatusText(row));
                badge.getStyleClass().addAll("badge-pill", "patient-status-badge", visibleStatusStyle(row));
                setText(null);
                setGraphic(badge);
            }
        });

        actionsColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
        actionsColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(SqlitePatientDao.PatientListRow row, boolean empty) {
                super.updateItem(row, empty);
                setAlignment(Pos.CENTER);
                if (empty || row == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                setText(null);
                setGraphic(buildActionsCell(row));
            }
        });

        patientTable.setRowFactory(table -> {
            TableRow<SqlitePatientDao.PatientListRow> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty() && row.getItem() != null) {
                    openPatientFile(row.getItem());
                }
            });
            return row;
        });
    }

    private void configureFilters() {
        if (statusFilter != null) {
            statusFilter.setItems(FXCollections.observableArrayList("All", "Active", "Critical", "Discharged", "Archived"));
            statusFilter.getSelectionModel().select("All");
            if (!filterListenersConfigured) {
                statusFilter.valueProperty().addListener((observable, oldValue, newValue) -> {
                    if (!suppressFilterEvents) {
                        loadPatients();
                    }
                });
                filterListenersConfigured = true;
            }
        }
    }

    private void configureWritePermissions() {
        canWritePatients = PermissionHelper.canCreatePatient(Session.getCurrentUser())
                || PermissionHelper.canUpdatePatient(Session.getCurrentUser());
        canArchivePatients = PermissionHelper.canDeactivatePatient(Session.getCurrentUser());
        setButtonVisible(addPatientButton, canWritePatients);
    }

    private SqlitePatientDao.PatientFilter buildFilter() {
        SqlitePatientDao.PatientFilter filter = new SqlitePatientDao.PatientFilter();
        filter.setSearch(searchField == null ? "" : searchField.getText());
        filter.setDisplayStatus(statusFilter == null || statusFilter.getValue() == null ? "All" : statusFilter.getValue());
        return filter;
    }

    private HBox buildPatientCell(SqlitePatientDao.PatientListRow row) {
        StackPane avatar = new StackPane();
        avatar.getStyleClass().add("patient-avatar");
        Label initials = new Label(initialsFor(row.getName()));
        initials.getStyleClass().add("patient-initials");
        avatar.getChildren().add(initials);

        Label nameLabel = new Label(row.getName());
        nameLabel.getStyleClass().add("patient-name");
        nameLabel.setWrapText(false);

        Label subtitleLabel = new Label(bloodTypeText(row));
        subtitleLabel.getStyleClass().add("patient-subtitle");
        subtitleLabel.setWrapText(false);

        VBox textBox = new VBox(2.0, nameLabel, subtitleLabel);
        textBox.setAlignment(Pos.CENTER_LEFT);
        textBox.setFillWidth(false);
        HBox.setHgrow(textBox, Priority.ALWAYS);

        HBox content = new HBox(12.0, avatar, textBox);
        content.setAlignment(Pos.CENTER_LEFT);
        return content;
    }

    private Label buildSingleLineCell(String text, String styleClass) {
        Label label = new Label(text == null || text.isBlank() ? "\u2014" : text);
        label.getStyleClass().add(styleClass);
        label.setWrapText(false);
        label.setAlignment(Pos.CENTER_LEFT);
        return label;
    }

    private VBox buildContactCell(SqlitePatientDao.PatientListRow row) {
        String phone = row == null ? "" : safeText(row.getPhone());
        String email = row == null ? "" : safeText(row.getEmail());
        if (phone.isBlank() && email.isBlank()) {
            return new VBox(buildSingleLineCell("\u2014", "contact-cell-primary"));
        }
        VBox box = new VBox(1.0);
        box.setAlignment(Pos.CENTER_LEFT);
        if (!phone.isBlank()) {
            box.getChildren().add(buildSingleLineCell(phone, "contact-cell-primary"));
        }
        if (!email.isBlank()) {
            box.getChildren().add(buildSingleLineCell(email, phone.isBlank() ? "contact-cell-primary" : "contact-cell-secondary"));
        }
        return box;
    }

    private HBox buildActionsCell(SqlitePatientDao.PatientListRow row) {
        Button viewButton = iconButton("\uD83D\uDC41", "patient-action-view");
        viewButton.setOnAction(event -> openPatientFile(row));

        Button editButton = iconButton("\u270E", "patient-action-edit");
        editButton.setDisable(!canWritePatients || isArchived(row));
        editButton.setOnAction(event -> editPatient(row));

        Button archiveButton = iconButton("\uD83D\uDDD1", "patient-action-delete");
        archiveButton.setDisable(!canArchivePatients || isArchived(row));
        archiveButton.setOnAction(event -> archivePatient(row));

        HBox actions = new HBox(8.0, viewButton, editButton, archiveButton);
        actions.setAlignment(Pos.CENTER);
        return actions;
    }

    private Button iconButton(String text, String extraClass) {
        Button button = new Button(text);
        button.getStyleClass().addAll("patient-action-button", extraClass);
        button.setFocusTraversable(false);
        return button;
    }

    private void openPatientFile(SqlitePatientDao.PatientListRow row) {
        if (row != null && appShell != null) {
            appShell.showPatientDetail(row.getPatientId());
        }
    }

    private void editPatient(SqlitePatientDao.PatientListRow row) {
        if (row == null) {
            return;
        }
        if (!canWritePatients) {
            NotificationHelper.showError(statusLabel, "Access denied. Admin or Doctor role is required.");
            return;
        }
        if (isArchived(row)) {
            NotificationHelper.showInfo(statusLabel, "Archived patient records can be viewed but not edited here.");
            return;
        }
        try {
            SqlitePatientDao.PatientDetail detail = patientDao.findDetailById(row.getPatientId())
                    .orElseThrow(() -> new IllegalArgumentException("Patient not found in SQLite: " + row.getPatientId()));
            boolean saved = PatientFormController.showEditDialog(patientTable.getScene().getWindow(), Session.getCurrentUser(), detail);
            if (saved) {
                loadPatients();
                NotificationHelper.showSuccess(statusLabel, "Patient record updated.");
            }
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
        }
    }

    private void archivePatient(SqlitePatientDao.PatientListRow row) {
        if (row == null) {
            return;
        }
        if (!canArchivePatients) {
            NotificationHelper.showError(statusLabel, "Access denied. Admin or Doctor role is required.");
            return;
        }
        if (isArchived(row)) {
            NotificationHelper.showInfo(statusLabel, "This patient is already archived.");
            return;
        }
        boolean confirmed = DialogHelper.confirm(
                "Remove Patient",
                "Are you sure you want to remove this patient from the active list?");
        if (!confirmed) {
            return;
        }
        try {
            patientWriteService.archivePatient(
                    Session.getCurrentUser(),
                    row.getPatientId(),
                    "Patient removed from the active clinic list.");
            loadPatients();
            NotificationHelper.showSuccess(statusLabel, "Patient removed from the active list.");
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
        }
    }

    private void updateStatusLine(int count) {
        if (statusLabel == null) {
            return;
        }
        String filterText = statusFilter == null || statusFilter.getValue() == null ? "All" : statusFilter.getValue();
        if ("All".equalsIgnoreCase(filterText)) {
            statusLabel.setText("Showing " + count + " patient records");
        } else {
            statusLabel.setText("Showing " + count + " " + filterText.toLowerCase(Locale.ROOT) + " patient records");
        }
    }

    private String bloodTypeText(SqlitePatientDao.PatientListRow row) {
        if (row == null || row.getBloodType() == null || row.getBloodType().isBlank() || "Unknown".equalsIgnoreCase(row.getBloodType())) {
            return "Blood type unknown";
        }
        return "Blood type: " + row.getBloodType().toUpperCase(Locale.ROOT);
    }

    private String formatAgeGender(SqlitePatientDao.PatientListRow row) {
        String ageText = ageText(row == null ? null : row.getBirthDate());
        String genderText = genderText(row == null ? null : row.getGender());
        if (!ageText.isBlank() && !genderText.isBlank()) {
            return ageText + " \u2022 " + genderText;
        }
        if (!ageText.isBlank()) {
            return ageText;
        }
        if (!genderText.isBlank()) {
            return genderText;
        }
        return "-";
    }

    private String ageText(String birthDate) {
        if (birthDate == null || birthDate.isBlank()) {
            return "";
        }
        try {
            LocalDate birth = LocalDate.parse(birthDate.trim(), DISPLAY_DATE);
            int years = Period.between(birth, LocalDate.now()).getYears();
            if (years < 0 || years > 130) {
                return "";
            }
            return years + " yrs";
        } catch (DateTimeParseException e) {
            return "";
        }
    }

    private String genderText(String gender) {
        if (gender == null || gender.isBlank() || "UNKNOWN".equalsIgnoreCase(gender)) {
            return "";
        }
        String trimmed = gender.trim().toLowerCase(Locale.ROOT);
        return trimmed.substring(0, 1).toUpperCase(Locale.ROOT) + trimmed.substring(1);
    }

    private String visibleStatusText(SqlitePatientDao.PatientListRow row) {
        if (row == null) {
            return "Active";
        }
        if (isArchived(row)) {
            return "Archived";
        }
        if (isDischarged(row)) {
            return "Discharged";
        }
        if (isCritical(row)) {
            return "Critical";
        }
        return "Active";
    }

    private String visibleStatusStyle(SqlitePatientDao.PatientListRow row) {
        if (row == null) {
            return "patient-status-active-badge";
        }
        if (isArchived(row)) {
            return "patient-status-archived-badge";
        }
        if (isDischarged(row)) {
            return "patient-status-discharged-badge";
        }
        if (isCritical(row)) {
            return "patient-status-critical-badge";
        }
        return "patient-status-active-badge";
    }

    private boolean isCritical(SqlitePatientDao.PatientListRow row) {
        if (row == null || row.getPriority() == null) {
            return false;
        }
        String priority = row.getPriority().trim().toUpperCase(Locale.ROOT);
        return "CRITICAL".equals(priority) || "EMERGENCY".equals(priority);
    }

    private boolean isDischarged(SqlitePatientDao.PatientListRow row) {
        return row != null && row.getStatus() != null && "DISCHARGED".equalsIgnoreCase(row.getStatus().trim());
    }

    private boolean isArchived(SqlitePatientDao.PatientListRow row) {
        if (row == null || row.getStatus() == null) {
            return false;
        }
        String status = row.getStatus().trim().toUpperCase(Locale.ROOT);
        return "DECEASED".equals(status) || "INACTIVE".equals(status) || "DEACTIVATED".equals(status);
    }

    private String initialsFor(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "P";
        }
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length == 1) {
            String name = parts[0].replaceAll("[^\\p{L}\\p{N}]", "");
            return name.length() >= 2
                    ? name.substring(0, 2).toUpperCase(Locale.ROOT)
                    : name.substring(0, 1).toUpperCase(Locale.ROOT);
        }
        String first = parts[0].substring(0, 1);
        String second = parts[1].substring(0, 1);
        return (first + second).toUpperCase(Locale.ROOT);
    }

    private void setButtonVisible(Button button, boolean visible) {
        if (button != null) {
            button.setVisible(visible);
            button.setManaged(visible);
        }
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }
}
