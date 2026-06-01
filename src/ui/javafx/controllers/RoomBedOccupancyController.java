package ui.javafx.controllers;

import dao.SqliteRoomDao;
import dao.SqliteAuditLogDao;
import dao.SqliteSectionDao;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import services.RoomWriteService;
import services.RoomBedOccupancyService;
import services.SectionService;
import ui.javafx.AppShell;
import ui.javafx.FxController;
import ui.javafx.SessionContext;
import ui.javafx.helpers.DialogHelper;
import ui.javafx.helpers.NotificationHelper;
import ui.javafx.helpers.PermissionHelper;
import users.Session;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Map;

public class RoomBedOccupancyController implements FxController {

    private final RoomBedOccupancyService occupancyService = new RoomBedOccupancyService();
    private final RoomWriteService roomWriteService = new RoomWriteService();
    private final SectionService sectionService = new SectionService();
    private final SqliteRoomDao roomDao = new SqliteRoomDao();
    private final SqliteSectionDao sectionDao = new SqliteSectionDao();
    private final SqliteAuditLogDao auditLogDao = new SqliteAuditLogDao();
    private final ObservableList<RoomBedOccupancyService.RoomRow> rows = FXCollections.observableArrayList();
    private final ObservableList<SqliteSectionDao.SectionRecord> sections = FXCollections.observableArrayList();
    private AppShell appShell;
    private boolean filterListenersConfigured;

    @FXML private VBox accessDeniedPane;
    @FXML private VBox occupancyContentPane;
    @FXML private ComboBox<String> sectionFilter;
    @FXML private TextField roomSearchField;
    @FXML private ComboBox<String> statusFilter;
    @FXML private ComboBox<String> priorityFilter;
    @FXML private Label totalRoomsLabel;
    @FXML private Label occupiedRoomsLabel;
    @FXML private Label occupiedBedsLabel;
    @FXML private Label availableCapacityLabel;
    @FXML private Label fallbackStatusLabel;
    @FXML private VBox activePatientsBySectionBox;
    @FXML private VBox criticalPatientsBySectionBox;
    @FXML private TableView<SqliteSectionDao.SectionRecord> sectionTable;
    @FXML private TableColumn<SqliteSectionDao.SectionRecord, String> sectionNameColumn;
    @FXML private TableColumn<SqliteSectionDao.SectionRecord, String> sectionStatusColumn;
    @FXML private TableColumn<SqliteSectionDao.SectionRecord, String> sectionUpdatedColumn;
    @FXML private TableColumn<SqliteSectionDao.SectionRecord, String> sectionNotesColumn;
    @FXML private TableView<RoomBedOccupancyService.RoomRow> roomTable;
    @FXML private TableColumn<RoomBedOccupancyService.RoomRow, String> sectionColumn;
    @FXML private TableColumn<RoomBedOccupancyService.RoomRow, String> roomColumn;
    @FXML private TableColumn<RoomBedOccupancyService.RoomRow, String> roomStatusColumn;
    @FXML private TableColumn<RoomBedOccupancyService.RoomRow, Integer> capacityColumn;
    @FXML private TableColumn<RoomBedOccupancyService.RoomRow, Integer> occupiedColumn;
    @FXML private TableColumn<RoomBedOccupancyService.RoomRow, Integer> availableColumn;
    @FXML private TableColumn<RoomBedOccupancyService.RoomRow, String> patientsColumn;
    @FXML private TableColumn<RoomBedOccupancyService.RoomRow, String> priorityColumn;
    @FXML private Button addRoomButton;
    @FXML private Button editRoomButton;
    @FXML private Button deactivateRoomButton;
    @FXML private Button addSectionButton;
    @FXML private Button editSectionButton;
    @FXML private Button deactivateSectionButton;
    @FXML private Button assignPatientButton;
    @FXML private Button movePatientButton;
    @FXML private Button removePatientRoomButton;
    @FXML private Label statusLabel;

    @Override
    public void setAppShell(AppShell appShell) {
        this.appShell = appShell;
        configureAccess();
        configureFilters();
        configureTable();
        configureSectionTable();
        if (isAuthorized()) {
            logAudit("JavaFX ROOM_OCCUPANCY opened overview");
            loadOccupancy();
        }
    }

    @FXML
    private void loadOccupancy() {
        if (!isAuthorized()) {
            statusLabel.setText("Access denied.");
            return;
        }

        try {
            RoomBedOccupancyService.OccupancyFilter filter = new RoomBedOccupancyService.OccupancyFilter(
                    sectionFilter.getValue(),
                    roomSearchField.getText(),
                    statusFilter.getValue(),
                    priorityFilter.getValue()
            );
            RoomBedOccupancyService.OccupancyOverview overview = occupancyService.loadOverview(filter);
            renderOverview(overview);
            loadSections();
            statusLabel.setText("Room/bed occupancy refreshed from the local database at "
                    + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        } catch (Exception e) {
            statusLabel.setText("Could not load room/bed occupancy: " + e.getMessage());
        }
    }

    @FXML
    private void clearFilters() {
        sectionFilter.getSelectionModel().select("All");
        roomSearchField.clear();
        statusFilter.getSelectionModel().select("All");
        priorityFilter.getSelectionModel().select("All");
        loadOccupancy();
    }

    @FXML
    private void showDashboard() {
        appShell.showDashboard(Session.getCurrentUser());
    }

    @FXML
    private void openSelectedPatient() {
        RoomBedOccupancyService.RoomRow selected = roomTable.getSelectionModel().getSelectedItem();
        if (selected == null || selected.getSelectedPatientId().isBlank()) {
            statusLabel.setText("Select an occupied room row to open the highest-priority assigned patient.");
            return;
        }
        openPatient(selected);
    }

    @FXML
    private void addRoom() {
        try {
            boolean saved = RoomFormController.showCreateDialog(roomTable.getScene().getWindow(), Session.getCurrentUser());
            if (saved) {
                refreshAfterWrite("Room created.");
            }
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
        }
    }

    @FXML
    private void addSection() {
        try {
            boolean saved = SectionFormController.showCreateDialog(roomTable.getScene().getWindow(), Session.getCurrentUser());
            if (saved) {
                reloadSectionChoices();
                refreshAfterWrite("Section created.");
            }
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
        }
    }

    @FXML
    private void editSection() {
        SqliteSectionDao.SectionRecord section = selectedSectionRecord();
        if (section == null) {
            return;
        }
        try {
            boolean saved = SectionFormController.showEditDialog(roomTable.getScene().getWindow(), Session.getCurrentUser(), section);
            if (saved) {
                reloadSectionChoices();
                refreshAfterWrite("Section updated.");
            }
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
        }
    }

    @FXML
    private void deactivateSection() {
        SqliteSectionDao.SectionRecord section = selectedSectionRecord();
        if (section == null) {
            return;
        }
        try {
            boolean confirmed = sectionService.confirmDeactivateWithActiveRecords(section.getName());
            sectionService.deactivateSection(Session.getCurrentUser(), section.getId(), confirmed);
            reloadSectionChoices();
            refreshAfterWrite("Section deactivated.");
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
        }
    }

    @FXML
    private void editRoom() {
        RoomBedOccupancyService.RoomRow selected = selectedRealRoom();
        if (selected == null) {
            return;
        }
        try {
            SqliteRoomDao.RoomDetail room = roomDao.findById(selected.getRoomId())
                    .orElseThrow(() -> new IllegalArgumentException("Room not found in SQLite."));
            boolean saved = RoomFormController.showEditDialog(roomTable.getScene().getWindow(), Session.getCurrentUser(), room);
            if (saved) {
                refreshAfterWrite("Room updated.");
            }
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
        }
    }

    @FXML
    private void deactivateRoom() {
        RoomBedOccupancyService.RoomRow selected = selectedRealRoom();
        if (selected == null) {
            return;
        }
        if (!DialogHelper.confirm("Deactivate room",
                "Deactivate " + selected.getSection() + " / Room " + selected.getRoomNumber() + "?")) {
            return;
        }
        try {
            roomWriteService.deactivateRoom(Session.getCurrentUser(), selected.getRoomId());
            refreshAfterWrite("Room deactivated.");
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
        }
    }

    @FXML
    private void assignPatientToRoom() {
        RoomBedOccupancyService.RoomRow selected = selectedRealRoom();
        if (selected == null) {
            return;
        }
        try {
            boolean saved = RoomAssignmentController.showAssignDialog(roomTable.getScene().getWindow(), Session.getCurrentUser(), selected);
            if (saved) {
                refreshAfterWrite("Patient assigned to room.");
            }
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
        }
    }

    @FXML
    private void movePatientToRoom() {
        RoomBedOccupancyService.RoomRow selected = roomTable.getSelectionModel().getSelectedItem();
        try {
            boolean saved = RoomAssignmentController.showMoveDialog(roomTable.getScene().getWindow(), Session.getCurrentUser(), selected);
            if (saved) {
                refreshAfterWrite("Patient moved to room.");
            }
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
        }
    }

    @FXML
    private void removePatientFromRoom() {
        RoomBedOccupancyService.RoomRow selected = roomTable.getSelectionModel().getSelectedItem();
        if (selected == null || selected.getSelectedPatientId().isBlank()) {
            NotificationHelper.showInfo(statusLabel, "Select an occupied room row first.");
            return;
        }
        try {
            boolean saved = RoomAssignmentController.showRemoveDialog(roomTable.getScene().getWindow(), Session.getCurrentUser(), selected);
            if (saved) {
                refreshAfterWrite("Patient removed from room.");
            }
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
        }
    }

    private void configureAccess() {
        boolean authorized = isAuthorized();
        accessDeniedPane.setVisible(!authorized);
        accessDeniedPane.setManaged(!authorized);
        occupancyContentPane.setVisible(authorized);
        occupancyContentPane.setManaged(authorized);
        boolean canManageRooms = PermissionHelper.canManageRooms(Session.getCurrentUser());
        setButtonVisible(addRoomButton, canManageRooms);
        setButtonVisible(editRoomButton, canManageRooms);
        setButtonVisible(deactivateRoomButton, canManageRooms);
        setButtonVisible(addSectionButton, canManageRooms);
        setButtonVisible(editSectionButton, canManageRooms);
        setButtonVisible(deactivateSectionButton, canManageRooms);
        boolean canAssign = PermissionHelper.canAssignPatientRoom(Session.getCurrentUser());
        setButtonVisible(assignPatientButton, canAssign);
        setButtonVisible(movePatientButton, canAssign);
        setButtonVisible(removePatientRoomButton, canAssign);
    }

    private void configureFilters() {
        ArrayList<String> sections = new ArrayList<>();
        sections.add("All");
        try {
            sections.addAll(sectionService.findActiveSectionNames());
            for (String section : occupancyService.findSections()) {
                if (!sections.contains(section)) {
                    sections.add(section);
                }
            }
        } catch (Exception e) {
            statusLabel.setText("Section filters unavailable: " + e.getMessage());
        }
        sectionFilter.setItems(FXCollections.observableArrayList(sections));
        statusFilter.setItems(FXCollections.observableArrayList("All", "Active", "DECEASED", "DISCHARGED", "Unknown"));
        priorityFilter.setItems(FXCollections.observableArrayList("All", "NORMAL", "HIGH", "CRITICAL", "EMERGENCY"));
        sectionFilter.getSelectionModel().select("All");
        statusFilter.getSelectionModel().select("All");
        priorityFilter.getSelectionModel().select("All");

        if (!filterListenersConfigured) {
            sectionFilter.valueProperty().addListener((observable, oldValue, newValue) -> loadOccupancy());
            roomSearchField.textProperty().addListener((observable, oldValue, newValue) -> loadOccupancy());
            statusFilter.valueProperty().addListener((observable, oldValue, newValue) -> loadOccupancy());
            priorityFilter.valueProperty().addListener((observable, oldValue, newValue) -> loadOccupancy());
            filterListenersConfigured = true;
        }
    }

    private void configureTable() {
        sectionColumn.setCellValueFactory(new PropertyValueFactory<>("section"));
        roomColumn.setCellValueFactory(new PropertyValueFactory<>("roomNumber"));
        roomStatusColumn.setCellValueFactory(new PropertyValueFactory<>("roomStatus"));
        capacityColumn.setCellValueFactory(new PropertyValueFactory<>("capacity"));
        occupiedColumn.setCellValueFactory(new PropertyValueFactory<>("occupiedCount"));
        availableColumn.setCellValueFactory(new PropertyValueFactory<>("availableCount"));
        patientsColumn.setCellValueFactory(new PropertyValueFactory<>("patientsInRoom"));
        priorityColumn.setCellValueFactory(new PropertyValueFactory<>("highestPatientPriority"));

        roomTable.setRowFactory(table -> {
            TableRow<RoomBedOccupancyService.RoomRow> row = new TableRow<>();
            row.itemProperty().addListener((observable, oldValue, newValue) -> {
                row.getStyleClass().remove("active-alert-row");
                if (newValue != null && isCritical(newValue.getHighestPatientPriority())) {
                    row.getStyleClass().add("active-alert-row");
                }
            });
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    openPatient(row.getItem());
                }
            });
            return row;
        });
    }

    private void configureSectionTable() {
        if (sectionTable == null) {
            return;
        }
        sectionNameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        sectionStatusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        sectionUpdatedColumn.setCellValueFactory(new PropertyValueFactory<>("updatedAt"));
        sectionNotesColumn.setCellValueFactory(new PropertyValueFactory<>("notes"));
        sectionTable.setItems(sections);
        sectionTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, selected) -> {
            if (selected != null && selected.getName() != null) {
                sectionFilter.getSelectionModel().select(selected.getName());
            }
        });
    }

    private void renderOverview(RoomBedOccupancyService.OccupancyOverview overview) {
        totalRoomsLabel.setText(String.valueOf(overview.getTotalRooms()));
        occupiedRoomsLabel.setText(String.valueOf(overview.getOccupiedRooms()));
        occupiedBedsLabel.setText(String.valueOf(overview.getOccupiedBeds()));
        availableCapacityLabel.setText(String.valueOf(overview.getAvailableCapacity()));
        fallbackStatusLabel.setText(overview.isFallbackMode()
                ? "Fallback mode: SQLite rooms table is empty, so rows are built from patient section/room assignments."
                : "Rooms mode: SQLite rooms table is available and patient assignments are overlaid.");

        renderSectionBox(activePatientsBySectionBox, overview.getActivePatientsBySection(), "No active patient section counts for this filter.", false);
        renderSectionBox(criticalPatientsBySectionBox, overview.getCriticalEmergencyBySection(), "No critical/emergency patients for this filter.", true);

        rows.setAll(overview.getRooms());
        roomTable.setItems(rows);
    }

    private void renderSectionBox(VBox target, Map<String, Integer> values, String emptyText, boolean critical) {
        target.getChildren().setAll();
        if (values.isEmpty()) {
            target.getChildren().add(emptyRow(emptyText));
            return;
        }
        for (Map.Entry<String, Integer> entry : values.entrySet()) {
            target.getChildren().add(sectionSummaryRow(entry.getKey(), entry.getValue(), critical));
        }
    }

    private HBox sectionSummaryRow(String section, int count, boolean critical) {
        Label badge = new Label(section == null || section.isBlank() ? "Unassigned" : section);
        badge.getStyleClass().addAll("dashboard-badge", critical ? "severity-critical" : "timeline-type-vital");
        Label value = new Label(String.valueOf(count));
        value.getStyleClass().add("dashboard-row-count");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(12, badge, spacer, value);
        row.getStyleClass().add("dashboard-summary-row");
        return row;
    }

    private HBox emptyRow(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("muted-text");
        label.setWrapText(true);
        HBox row = new HBox(label);
        row.getStyleClass().add("dashboard-list-row");
        return row;
    }

    private void openPatient(RoomBedOccupancyService.RoomRow row) {
        if (row == null || row.getSelectedPatientId().isBlank()) {
            statusLabel.setText("This room has no assigned patient to open.");
            return;
        }
        logAudit("JavaFX ROOM_OCCUPANCY opened patient detail for " + row.getSelectedPatientId());
        appShell.showPatientDetail(row.getSelectedPatientId());
    }

    private RoomBedOccupancyService.RoomRow selectedRealRoom() {
        RoomBedOccupancyService.RoomRow selected = roomTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            NotificationHelper.showInfo(statusLabel, "Select a room row first.");
            return null;
        }
        if (selected.getRoomId() <= 0) {
            NotificationHelper.showInfo(statusLabel, "This is a fallback row from patient location fields. Add a SQLite room before editing or assigning.");
            return null;
        }
        return selected;
    }

    private void refreshAfterWrite(String message) {
        loadOccupancy();
        NotificationHelper.showSuccess(statusLabel, message);
    }

    private void loadSections() {
        try {
            sections.setAll(sectionService.findSections());
        } catch (Exception e) {
            statusLabel.setText("Could not load sections: " + e.getMessage());
        }
    }

    private void reloadSectionChoices() {
        String selected = sectionFilter.getValue();
        configureFilters();
        if (selected != null && sectionFilter.getItems().contains(selected)) {
            sectionFilter.getSelectionModel().select(selected);
        }
        loadSections();
    }

    private SqliteSectionDao.SectionRecord selectedSectionRecord() {
        SqliteSectionDao.SectionRecord selected = sectionTable == null ? null : sectionTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            return selected;
        }
        String selectedName = sectionFilter.getValue();
        if (selectedName == null || selectedName.isBlank() || "All".equalsIgnoreCase(selectedName)) {
            NotificationHelper.showInfo(statusLabel, "Select a section row or choose a specific section filter first.");
            return null;
        }
        try {
            return sectionDao.findByName(selectedName)
                    .orElseThrow(() -> new IllegalArgumentException("Section not found: " + selectedName));
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
            return null;
        }
    }

    private void setButtonVisible(Button button, boolean visible) {
        if (button != null) {
            button.setVisible(visible);
            button.setManaged(visible);
        }
    }

    private void logAudit(String action) {
        try {
            auditLogDao.log(SessionContext.username(), action);
        } catch (Exception e) {
            System.out.println("SQLite room occupancy audit skipped: " + e.getMessage());
        }
    }

    private boolean isCritical(String priority) {
        return "CRITICAL".equalsIgnoreCase(priority) || "EMERGENCY".equalsIgnoreCase(priority);
    }

    private boolean isAuthorized() {
        return isAdmin() || isClinical();
    }

    private boolean isAdmin() {
        return "ADMIN".equals(roleGroup(SessionContext.role()));
    }

    private boolean isClinical() {
        String role = roleGroup(SessionContext.role());
        return "DOCTOR".equals(role) || "NURSE".equals(role);
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
}
