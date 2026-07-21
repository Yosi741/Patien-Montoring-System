package pages.scheduling.schedule_overview;

import pages.scheduling.*;
import pages.scheduling.appointment_form.AppointmentFormController;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import app.core.AppShell;
import app.contracts.AppController;
import app.helpers.DialogThemeHelper;
import pages.notification.NotificationHelper;
import app.helpers.PermissionHelper;
import app.helpers.SelectionHelper;
import pages.user.Session;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/**
 * Controls SchedulingView.fxml, including appointment filters, details, creation, editing, and status actions.
 */
public class SchedulingController implements AppController {

    private static final DateTimeFormatter TIME_DISPLAY_FORMAT = DateTimeFormatter.ofPattern("h:mm a");
    private static final DateTimeFormatter[] DATE_TIME_FORMATS = new DateTimeFormatter[] {
            DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    };

    private final SchedulingService schedulingService = new SchedulingService();
    private final ObservableList<SqliteAppointmentDao.AppointmentRow> appointments = FXCollections.observableArrayList();
    private AppShell appShell;
    private String patientIdFilter = "";

    @FXML private VBox accessDeniedPane;
    @FXML private VBox contentPane;
    @FXML private Label patientFilterChip;
    @FXML private Button clearPatientFilterButton;
    @FXML private TextField searchField;
    @FXML private ComboBox<String> appointmentTypeFilter;
    @FXML private ComboBox<String> appointmentStatusFilter;
    @FXML private Label appointmentsTodayLabel;
    @FXML private Label upcomingSurgeriesLabel;
    @FXML private Label completedAppointmentsLabel;
    @FXML private Label cancelledMissedLabel;
    @FXML private Button newAppointmentButton;
    @FXML private TableView<SqliteAppointmentDao.AppointmentRow> appointmentTable;
    @FXML private TableColumn<SqliteAppointmentDao.AppointmentRow, String> appointmentPatientNameColumn;
    @FXML private TableColumn<SqliteAppointmentDao.AppointmentRow, SqliteAppointmentDao.AppointmentRow> appointmentTypeColumn;
    @FXML private TableColumn<SqliteAppointmentDao.AppointmentRow, SqliteAppointmentDao.AppointmentRow> appointmentDateTimeColumn;
    @FXML private TableColumn<SqliteAppointmentDao.AppointmentRow, SqliteAppointmentDao.AppointmentRow> appointmentStatusColumn;
    @FXML private TableColumn<SqliteAppointmentDao.AppointmentRow, SqliteAppointmentDao.AppointmentRow> appointmentActionsColumn;
    @FXML private Label statusLabel;

    /**
     * Supplies the application shell used by this controller for navigation.
     */
    @Override
    public void setAppShell(AppShell appShell) {
        this.appShell = appShell;
        configureAccess();
        configureFilters();
        configureTables();
        configureWriteButtons();
        if (isAuthorized()) {
            loadScheduling();
        }
    }

    /**
     * Opens for patient for the selected record.
     */
    public void openForPatient(String patientId) {
        patientIdFilter = patientId == null ? "" : patientId;
        updatePatientFilterChip();
        if (isAuthorized()) {
            loadScheduling();
        }
    }

    /**
     * Handles the load scheduling UI action.
     */
    @FXML
    private void loadScheduling() {
        if (!isAuthorized()) {
            NotificationHelper.showError(statusLabel, "Access denied.");
            return;
        }
        try {
            SchedulingService.SchedulingOverview overview = schedulingService.loadOverview(
                    searchField.getText(),
                    appointmentTypeFilterValue(),
                    appointmentStatusFilter.getValue(),
                    patientIdFilter);
            appointmentsTodayLabel.setText(String.valueOf(overview.getAppointmentsToday()));
            upcomingSurgeriesLabel.setText(String.valueOf(countAppointmentsByStatus(overview, "SCHEDULED")));
            completedAppointmentsLabel.setText(String.valueOf(countAppointmentsByStatus(overview, "COMPLETED")));
            cancelledMissedLabel.setText(String.valueOf(overview.getCancelledMissedItems()));
            SelectionHelper.runWhenTableStable(appointmentTable, () -> {
                SelectionHelper.safeReplaceItems(appointmentTable, appointments, overview.getAppointments());
                NotificationHelper.showInfo(statusLabel, "Appointments refreshed from the local database. Appointments: "
                        + appointments.size());
            });
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, "Could not load scheduling data: " + e.getMessage());
        }
    }

    /**
     * Handles the create appointment UI action.
     */
    @FXML
    private void createAppointment() {
        if (!PermissionHelper.canCreateAppointment(Session.getCurrentUser())) {
            NotificationHelper.showError(statusLabel, "Access denied. Only Admin or Staff users can create appointments.");
            return;
        }
        try {
            if (AppointmentFormController.showCreateDialog(appointmentTable.getScene().getWindow(), Session.getCurrentUser(), patientIdFilter)) {
                loadScheduling();
                NotificationHelper.showSuccess(statusLabel, "Appointment saved.");
            }
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
        }
    }

    /**
     * Handles the edit appointment UI action.
     */
    @FXML
    private void editAppointment() {
        if (!PermissionHelper.canEditAppointment(Session.getCurrentUser())) {
            NotificationHelper.showError(statusLabel, "Access denied. Only Admin or Staff users can edit appointments.");
            return;
        }
        SqliteAppointmentDao.AppointmentRow selected = selectedAppointment();
        if (selected == null) {
            return;
        }
        try {
            if (AppointmentFormController.showEditDialog(appointmentTable.getScene().getWindow(), Session.getCurrentUser(), selected)) {
                loadScheduling();
                NotificationHelper.showSuccess(statusLabel, "Appointment updated.");
            }
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
        }
    }

    /**
     * Handles the clear filters UI action.
     */
    @FXML
    private void clearFilters() {
        searchField.clear();
        appointmentTypeFilter.getSelectionModel().select("All");
        appointmentStatusFilter.getSelectionModel().select("All");
        loadScheduling();
    }

    /**
     * Handles the clear patient filter UI action.
     */
    @FXML
    private void clearPatientFilter() {
        patientIdFilter = "";
        updatePatientFilterChip();
        loadScheduling();
    }

    /**
     * Configures access.
     */
    private void configureAccess() {
        boolean authorized = isAuthorized();
        accessDeniedPane.setVisible(!authorized);
        accessDeniedPane.setManaged(!authorized);
        contentPane.setVisible(authorized);
        contentPane.setManaged(authorized);
    }

    /**
     * Configures filters.
     */
    private void configureFilters() {
        appointmentTypeFilter.setItems(FXCollections.observableArrayList("All", "VISIT", "FOLLOW_UP", "LAB_TEST", "OTHER", "SURGERY"));
        appointmentStatusFilter.setItems(FXCollections.observableArrayList("All", "SCHEDULED", "COMPLETED", "CANCELLED", "MISSED"));
        appointmentTypeFilter.getSelectionModel().select("All");
        appointmentStatusFilter.getSelectionModel().select("All");
        searchField.textProperty().addListener((observable, oldValue, newValue) -> loadScheduling());
        appointmentTypeFilter.valueProperty().addListener((observable, oldValue, newValue) -> loadScheduling());
        appointmentStatusFilter.valueProperty().addListener((observable, oldValue, newValue) -> loadScheduling());
    }

    /**
     * Configures tables.
     */
    private void configureTables() {
        if (appointmentTable != null) {
            appointmentTable.setFixedCellSize(52);
            appointmentTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        }
        appointmentPatientNameColumn.setMinWidth(280);
        appointmentTypeColumn.setMinWidth(150);
        appointmentDateTimeColumn.setMinWidth(190);
        appointmentStatusColumn.setMinWidth(150);
        appointmentActionsColumn.setMinWidth(150);
        appointmentActionsColumn.setMaxWidth(170);
        appointmentPatientNameColumn.setCellValueFactory(new PropertyValueFactory<>("patientName"));
        appointmentPatientNameColumn.setCellFactory(column -> new TableCell<>() {
            /**
             * Updates item.
             */
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                SqliteAppointmentDao.AppointmentRow row = getTableRow().getItem();
                setText(nullTo(row.getPatientName(), "Unknown Patient"));
                getStyleClass().removeAll("appointments-patient-name-cell", "appointment-patient-table-cell");
                getStyleClass().addAll("appointments-patient-name-cell", "appointment-patient-table-cell");
                setAlignment(Pos.CENTER_LEFT);
                setGraphic(null);
            }
        });
        appointmentTypeColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
        appointmentTypeColumn.setCellFactory(column -> new TableCell<>() {
            /**
             * Updates item.
             */
            @Override
            protected void updateItem(SqliteAppointmentDao.AppointmentRow row, boolean empty) {
                super.updateItem(row, empty);
                if (empty || row == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                setAlignment(Pos.CENTER);
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                setGraphic(createBadgeLabel(formatEnumValue(row.getAppointmentType()), badgeStyleForType(row.getAppointmentType())));
                setText(null);
            }
        });
        appointmentDateTimeColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
        appointmentDateTimeColumn.setCellFactory(column -> new TableCell<>() {
            /**
             * Updates item.
             */
            @Override
            protected void updateItem(SqliteAppointmentDao.AppointmentRow row, boolean empty) {
                super.updateItem(row, empty);
                if (empty || row == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                setAlignment(Pos.CENTER);
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                setText(null);
                setGraphic(createAppointmentTimeCell(row));
            }
        });
        appointmentStatusColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
        appointmentStatusColumn.setCellFactory(column -> new TableCell<>() {
            /**
             * Updates item.
             */
            @Override
            protected void updateItem(SqliteAppointmentDao.AppointmentRow row, boolean empty) {
                super.updateItem(row, empty);
                if (empty || row == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                setAlignment(Pos.CENTER);
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                setGraphic(createBadgeLabel(formatEnumValue(row.getStatus()), badgeStyleForAppointmentStatus(row.getStatus())));
                setText(null);
            }
        });
        appointmentActionsColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
        appointmentActionsColumn.setCellFactory(column -> new TableCell<>() {
            /**
             * Updates item.
             */
            @Override
            protected void updateItem(SqliteAppointmentDao.AppointmentRow row, boolean empty) {
                super.updateItem(row, empty);
                if (empty || row == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                Button viewButton = actionButton("\uD83D\uDC41", "appointment-action-icon-button", event -> viewAppointment(row));
                viewButton.setAccessibleText("View appointment details");
                Button editButton = actionButton("\u270E", "appointment-action-icon-button", event -> editAppointment(row));
                editButton.setAccessibleText("Edit appointment");
                Button deleteButton = actionButton("\uD83D\uDDD1", "appointment-action-icon-button table-action-danger-button", event -> deleteAppointment(row));
                deleteButton.setAccessibleText("Delete appointment");
                boolean canEdit = PermissionHelper.canEditAppointment(Session.getCurrentUser());
                boolean canDelete = PermissionHelper.canDeleteAppointment(Session.getCurrentUser());
                editButton.setDisable(!canEdit);
                deleteButton.setDisable(!canDelete);
                HBox actions = new HBox(8, viewButton, editButton, deleteButton);
                actions.setAlignment(Pos.CENTER);
                getStyleClass().remove("appointment-action-cell");
                getStyleClass().add("appointment-action-cell");
                setAlignment(Pos.CENTER);
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                setGraphic(actions);
                setText(null);
            }
        });
        appointmentTable.setRowFactory(table -> {
            TableRow<SqliteAppointmentDao.AppointmentRow> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    viewAppointment(row.getItem());
                }
            });
            return row;
        });
    }

    /**
     * Configures write buttons.
     */
    private void configureWriteButtons() {
        boolean canCreateAppointments = PermissionHelper.canCreateAppointment(Session.getCurrentUser());
        newAppointmentButton.setVisible(canCreateAppointments);
        newAppointmentButton.setManaged(canCreateAppointments);
        updatePatientFilterChip();
    }

    /**
     * Updates patient filter chip.
     */
    private void updatePatientFilterChip() {
        boolean filtered = patientIdFilter != null && !patientIdFilter.isBlank();
        patientFilterChip.setVisible(filtered);
        patientFilterChip.setManaged(filtered);
        clearPatientFilterButton.setVisible(filtered);
        clearPatientFilterButton.setManaged(filtered);
        patientFilterChip.setText(filtered ? "Patient ID = " + patientIdFilter : "");
    }

    /**
     * Selects selected appointment without using an invalid index.
     */
    private SqliteAppointmentDao.AppointmentRow selectedAppointment() {
        SqliteAppointmentDao.AppointmentRow selected = appointmentTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            NotificationHelper.showError(statusLabel, "Select an appointment first.");
            return null;
        }
        return selected;
    }

    private boolean isAuthorized() {
        return PermissionHelper.canViewScheduling(Session.getCurrentUser());
    }

    /**
     * Opens the read-only details for appointment.
     */
    private void viewAppointment(SqliteAppointmentDao.AppointmentRow appointment) {
        if (appointment == null || appointmentTable == null || appointmentTable.getScene() == null) {
            return;
        }
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Appointment Details");
        DialogThemeHelper.apply(dialog);
        dialog.initOwner(appointmentTable.getScene().getWindow());
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CLOSE, ButtonType.APPLY);

        Button closeButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.CLOSE);
        if (closeButton != null) {
            closeButton.setText("Close");
            closeButton.getStyleClass().add("secondary-button");
        }
        Button editButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.APPLY);
        if (editButton != null) {
            editButton.setText("Edit Appointment");
            editButton.getStyleClass().add("primary-button");
            editButton.setDisable(!PermissionHelper.canEditAppointment(Session.getCurrentUser()));
        }

        VBox content = new VBox(12,
                detailLine("Patient", appointment.getPatientName()),
                detailLine("Patient ID", appointment.getPatientId()),
                detailLine("Title", appointment.getTitle()),
                detailLine("Type", formatEnumValue(appointment.getAppointmentType())),
                detailLine("Start", appointment.getStartTime()),
                detailLine("End", appointment.getEndTime()),
                detailLine("Location", appointment.getLocation()),
                detailLine("Assigned Staff", appointment.getAssignedStaff()),
                detailLine("Status", formatEnumValue(appointment.getStatus())),
                detailLine("Notes", nullTo(appointment.getNotes(), "-"))
        );
        content.setPadding(new Insets(6));
        content.getStyleClass().add("record-detail-dialog");

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("scroll-clear");

        dialog.getDialogPane().setContent(scrollPane);
        dialog.getDialogPane().setPrefSize(560, 520);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.APPLY) {
            editAppointment(appointment);
        }
    }

    /**
     * Converts a null value to null to for display.
     */
    private String nullTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    /**
     * Opens the edit form for appointment.
     */
    private void editAppointment(SqliteAppointmentDao.AppointmentRow selected) {
        if (selected == null) {
            return;
        }
        if (!PermissionHelper.canManageAppointment(Session.getCurrentUser())) {
            NotificationHelper.showError(statusLabel, "Access denied. Only Admin or Staff users can edit appointments.");
            return;
        }
        try {
            if (AppointmentFormController.showEditDialog(appointmentTable.getScene().getWindow(), Session.getCurrentUser(), selected)) {
                loadScheduling();
                NotificationHelper.showSuccess(statusLabel, "Appointment updated.");
            }
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
        }
    }

    /**
     * Deletes appointment after the required checks.
     */
    private void deleteAppointment(SqliteAppointmentDao.AppointmentRow selected) {
        if (selected == null) {
            return;
        }
        if (!PermissionHelper.canDeleteAppointment(Session.getCurrentUser())) {
            NotificationHelper.showError(statusLabel, "Access denied. Only Admin users can delete appointments.");
            return;
        }
        if (!showDeleteConfirmation()) {
            return;
        }
        try {
            schedulingService.deleteAppointment(Session.getCurrentUser(), selected.getId());
            loadScheduling();
            NotificationHelper.showSuccess(statusLabel, "Appointment deleted.");
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
        }
    }

    /**
     * Displays delete confirmation to the user.
     */
    private boolean showDeleteConfirmation() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Delete Appointment");
        alert.setHeaderText("Delete Appointment");
        alert.setContentText("Are you sure you want to delete this appointment? This action cannot be undone.");
        ButtonType deleteType = new ButtonType("Delete", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(deleteType, cancelType);
        DialogThemeHelper.apply(alert);
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == deleteType;
    }

    /**
     * Builds a styled table action button with its handler and tooltip.
     */
    private Button actionButton(String text, String styleClass, javafx.event.EventHandler<javafx.event.ActionEvent> handler) {
        Button button = new Button(text);
        for (String token : styleClass.split(" ")) {
            if (!token.isBlank()) {
                button.getStyleClass().add(token);
            }
        }
        button.setOnAction(handler);
        return button;
    }

    /**
     * Builds the JavaFX control used for create badge label.
     */
    private Label createBadgeLabel(String text, String styleClass) {
        Label label = new Label(text);
        label.getStyleClass().addAll("badge-pill");
        for (String token : styleClass.split(" ")) {
            if (!token.isBlank()) {
                label.getStyleClass().add(token);
            }
        }
        return label;
    }

    /**
     * Builds one labeled line in the appointment details dialog.
     */
    private VBox detailLine(String name, String value) {
        Label title = new Label(name);
        title.getStyleClass().add("detail-field-name");
        Label content = new Label(blankTo(value, "-"));
        content.getStyleClass().add("detail-field-value");
        content.setWrapText(true);
        VBox box = new VBox(4, title, content);
        box.setFillWidth(true);
        return box;
    }

    /**
     * Normalizes blank to to the workflow fallback value.
     */
    private String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    /**
     * Formats enum value for display.
     */
    private String formatEnumValue(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        String normalizedValue = "CHECKUP".equalsIgnoreCase(value) ? "VISIT" : value;
        String[] tokens = normalizedValue.split("_");
        StringBuilder builder = new StringBuilder();
        for (String token : tokens) {
            if (token.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(token.charAt(0)).append(token.substring(1).toLowerCase());
        }
        return builder.toString();
    }

    /**
     * Builds the JavaFX control used for create appointment time cell.
     */
    private VBox createAppointmentTimeCell(SqliteAppointmentDao.AppointmentRow row) {
        Label timeLabel = new Label(formatAppointmentStart(row));
        timeLabel.getStyleClass().addAll("appointments-primary-text", "appointment-time-primary");

        Label durationLabel = new Label(formatAppointmentDuration(row));
        durationLabel.getStyleClass().addAll("appointments-secondary-text", "appointment-time-duration");

        VBox box = new VBox(2, timeLabel, durationLabel);
        box.getStyleClass().add("appointment-time-box");
        box.setAlignment(Pos.CENTER);
        box.setFillWidth(false);
        return box;
    }

    /**
     * Formats appointment start for display.
     */
    private String formatAppointmentStart(SqliteAppointmentDao.AppointmentRow row) {
        if (row == null) {
            return "-";
        }
        LocalDateTime start = parseDateTime(row.getStartTime());
        if (start != null) {
            return start.format(TIME_DISPLAY_FORMAT);
        }
        String fallback = nullTo(row.getStartTime(), "").trim();
        return fallback.isBlank() ? "-" : fallback;
    }

    /**
     * Formats appointment duration for display.
     */
    private String formatAppointmentDuration(SqliteAppointmentDao.AppointmentRow row) {
        if (row == null) {
            return "\u2014";
        }
        LocalDateTime start = parseDateTime(row.getStartTime());
        LocalDateTime end = parseDateTime(row.getEndTime());
        if (start == null || end == null) {
            return "\u2014";
        }
        long minutes = Duration.between(start, end).toMinutes();
        if (minutes < 0) {
            return "\u2014";
        }
        return minutes + " min";
    }

    /**
     * Parses date time without exposing format failures to the caller.
     */
    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        for (DateTimeFormatter formatter : DATE_TIME_FORMATS) {
            try {
                return LocalDateTime.parse(trimmed, formatter);
            } catch (DateTimeParseException ignored) {
                // Try the next supported date-time format.
            }
        }
        try {
            return LocalDateTime.parse(trimmed);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    /**
     * Returns the badge CSS class for type.
     */
    private String badgeStyleForType(String type) {
        if (type == null) {
            return "appointment-type-badge appointment-type-neutral";
        }
        String normalizedType = "CHECKUP".equalsIgnoreCase(type) ? "VISIT" : type.toUpperCase();
        return switch (normalizedType) {
            case "VISIT" -> "appointment-type-badge appointment-type-blue";
            case "FOLLOW_UP" -> "appointment-type-badge appointment-type-purple";
            case "LAB_TEST" -> "appointment-type-badge appointment-type-cyan";
            case "SURGERY" -> "appointment-type-badge appointment-type-danger";
            default -> "appointment-type-badge appointment-type-neutral";
        };
    }

    /**
     * Maps the visible appointment type filter to its stored query value.
     */
    private String appointmentTypeFilterValue() {
        return appointmentTypeFilter == null ? "All" : appointmentTypeFilter.getValue();
    }

    /**
     * Returns the badge CSS class for appointment status.
     */
    private String badgeStyleForAppointmentStatus(String status) {
        if (status == null) {
            return "appointment-status-badge appointment-status-neutral";
        }
        return switch (status.toUpperCase()) {
            case "SCHEDULED" -> "appointment-status-badge appointment-status-scheduled";
            case "COMPLETED" -> "appointment-status-badge appointment-status-completed";
            case "CANCELLED" -> "appointment-status-badge appointment-status-cancelled";
            case "MISSED" -> "appointment-status-badge appointment-status-missed";
            default -> "appointment-status-badge appointment-status-neutral";
        };
    }

    /**
     * Counts appointments by status.
     */
    private int countAppointmentsByStatus(SchedulingService.SchedulingOverview overview, String status) {
        if (overview == null || overview.getAppointments() == null || status == null) {
            return 0;
        }
        int count = 0;
        for (SqliteAppointmentDao.AppointmentRow row : overview.getAppointments()) {
            if (row != null && status.equalsIgnoreCase(row.getStatus())) {
                count++;
            }
        }
        return count;
    }
}

