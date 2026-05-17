package ui.javafx.controllers;

import dao.SqliteDeceasedRecordDao;
import dao.SqlitePatientDao;
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
import javafx.scene.layout.VBox;
import services.DeceasedPatientService;
import ui.javafx.AppShell;
import ui.javafx.FxController;
import ui.javafx.helpers.NotificationHelper;
import ui.javafx.helpers.PermissionHelper;
import users.Session;

import java.nio.file.Path;
import java.util.ArrayList;

public class DeceasedRecordsController implements FxController {

    private final DeceasedPatientService deceasedPatientService = new DeceasedPatientService();
    private final SqlitePatientDao patientDao = new SqlitePatientDao();
    private final ObservableList<SqliteDeceasedRecordDao.DeathRecord> records = FXCollections.observableArrayList();
    private AppShell appShell;

    @FXML private VBox accessDeniedPane;
    @FXML private VBox contentPane;
    @FXML private TextField searchField;
    @FXML private ComboBox<String> dateRangeFilter;
    @FXML private ComboBox<String> sectionFilter;
    @FXML private TableView<SqliteDeceasedRecordDao.DeathRecord> deceasedTable;
    @FXML private TableColumn<SqliteDeceasedRecordDao.DeathRecord, String> patientIdColumn;
    @FXML private TableColumn<SqliteDeceasedRecordDao.DeathRecord, String> patientNameColumn;
    @FXML private TableColumn<SqliteDeceasedRecordDao.DeathRecord, String> deathTimeColumn;
    @FXML private TableColumn<SqliteDeceasedRecordDao.DeathRecord, String> pronouncedByColumn;
    @FXML private TableColumn<SqliteDeceasedRecordDao.DeathRecord, String> causeColumn;
    @FXML private TableColumn<SqliteDeceasedRecordDao.DeathRecord, String> certificateColumn;
    @FXML private Label detailPatientLabel;
    @FXML private Label detailDeathTimeLabel;
    @FXML private Label detailPronouncedByLabel;
    @FXML private Label detailCauseLabel;
    @FXML private Label detailNotesLabel;
    @FXML private Label detailCertificateLabel;
    @FXML private Label statusLabel;
    @FXML private Button updateRecordButton;
    @FXML private Button generateCertificateButton;
    @FXML private Button openCertificateButton;

    @Override
    public void setAppShell(AppShell appShell) {
        this.appShell = appShell;
        configureAccess();
        configureTable();
        configureFilters();
        if (PermissionHelper.canViewDeceasedRecords(Session.getCurrentUser())) {
            loadRecords();
        }
    }

    @FXML
    private void loadRecords() {
        if (!PermissionHelper.canViewDeceasedRecords(Session.getCurrentUser())) {
            NotificationHelper.showError(statusLabel, "Access denied.");
            return;
        }
        try {
            records.setAll(deceasedPatientService.getDeceasedRecords(buildFilter()));
            deceasedTable.setItems(records);
            statusLabel.setText("Deceased records loaded from SQLite: " + records.size());
            renderDetail(null);
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, "Could not load deceased records: " + e.getMessage());
        }
    }

    @FXML
    private void clearFilters() {
        searchField.clear();
        dateRangeFilter.getSelectionModel().select("All");
        sectionFilter.getSelectionModel().select("All");
        loadRecords();
    }

    @FXML
    private void backToDashboard() {
        appShell.showDashboard(Session.getCurrentUser());
    }

    @FXML
    private void openPatientDetail() {
        SqliteDeceasedRecordDao.DeathRecord selected = selectedRecord();
        if (selected != null) {
            appShell.showPatientDetail(selected.getPatientId());
        }
    }

    @FXML
    private void updateRecord() {
        SqliteDeceasedRecordDao.DeathRecord selected = selectedRecord();
        if (selected == null) {
            return;
        }
        try {
            boolean saved = DeathRecordFormController.showEditDialog(deceasedTable.getScene().getWindow(), Session.getCurrentUser(), selected);
            if (saved) {
                loadRecords();
                NotificationHelper.showSuccess(statusLabel, "Death record updated in SQLite.");
            }
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
        }
    }

    @FXML
    private void generateCertificate() {
        SqliteDeceasedRecordDao.DeathRecord selected = selectedRecord();
        if (selected == null) {
            return;
        }
        try {
            Path path = deceasedPatientService.generateDeathCertificate(Session.getCurrentUser(), selected.getId());
            loadRecords();
            NotificationHelper.showSuccess(statusLabel, "Death certificate generated: " + path);
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
        }
    }

    @FXML
    private void openCertificate() {
        SqliteDeceasedRecordDao.DeathRecord selected = selectedRecord();
        if (selected == null) {
            return;
        }
        try {
            deceasedPatientService.openDeathCertificate(Session.getCurrentUser(), selected.getId());
            NotificationHelper.showSuccess(statusLabel, "Opening certificate with the local desktop handler.");
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
        }
    }

    private void configureAccess() {
        boolean authorized = PermissionHelper.canViewDeceasedRecords(Session.getCurrentUser());
        accessDeniedPane.setVisible(!authorized);
        accessDeniedPane.setManaged(!authorized);
        contentPane.setVisible(authorized);
        contentPane.setManaged(authorized);
        boolean canWrite = PermissionHelper.canMarkPatientDeceased(Session.getCurrentUser());
        setButtonVisible(updateRecordButton, canWrite);
        setButtonVisible(generateCertificateButton, PermissionHelper.canGenerateDeathCertificate(Session.getCurrentUser()));
        setButtonVisible(openCertificateButton, authorized);
    }

    private void configureTable() {
        patientIdColumn.setCellValueFactory(new PropertyValueFactory<>("patientId"));
        patientNameColumn.setCellValueFactory(new PropertyValueFactory<>("patientName"));
        deathTimeColumn.setCellValueFactory(new PropertyValueFactory<>("deathTime"));
        pronouncedByColumn.setCellValueFactory(new PropertyValueFactory<>("pronouncedBy"));
        causeColumn.setCellValueFactory(new PropertyValueFactory<>("causeOfDeath"));
        certificateColumn.setCellValueFactory(new PropertyValueFactory<>("certificateStatus"));
        deceasedTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> renderDetail(newValue));
        deceasedTable.setRowFactory(table -> {
            TableRow<SqliteDeceasedRecordDao.DeathRecord> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    appShell.showPatientDetail(row.getItem().getPatientId());
                }
            });
            return row;
        });
    }

    private void configureFilters() {
        dateRangeFilter.setItems(FXCollections.observableArrayList("All", "Today", "Last 7 days", "Last 30 days"));
        dateRangeFilter.getSelectionModel().select("All");
        ArrayList<String> sections = new ArrayList<>();
        sections.add("All");
        try {
            sections.addAll(patientDao.findDistinctSections());
        } catch (Exception e) {
            statusLabel.setText("Section filter unavailable: " + e.getMessage());
        }
        sectionFilter.setItems(FXCollections.observableArrayList(sections));
        sectionFilter.getSelectionModel().select("All");
        searchField.textProperty().addListener((observable, oldValue, newValue) -> loadRecords());
        dateRangeFilter.valueProperty().addListener((observable, oldValue, newValue) -> loadRecords());
        sectionFilter.valueProperty().addListener((observable, oldValue, newValue) -> loadRecords());
    }

    private SqliteDeceasedRecordDao.RecordFilter buildFilter() {
        SqliteDeceasedRecordDao.RecordFilter filter = new SqliteDeceasedRecordDao.RecordFilter();
        filter.setSearch(searchField.getText());
        filter.setDateRange(dateRangeFilter.getValue());
        filter.setSection(sectionFilter.getValue());
        return filter;
    }

    private void renderDetail(SqliteDeceasedRecordDao.DeathRecord record) {
        if (record == null) {
            detailPatientLabel.setText("No record selected");
            detailDeathTimeLabel.setText("-");
            detailPronouncedByLabel.setText("-");
            detailCauseLabel.setText("-");
            detailNotesLabel.setText("Select a deceased record to view details.");
            detailCertificateLabel.setText("-");
            return;
        }
        detailPatientLabel.setText(record.getPatientId() + " | " + record.getPatientName() + " | " + record.getSection());
        detailDeathTimeLabel.setText(record.getDeathTime());
        detailPronouncedByLabel.setText(record.getPronouncedBy());
        detailCauseLabel.setText(record.getCauseOfDeath());
        detailNotesLabel.setText(record.getNotes() == null || record.getNotes().isBlank() ? "-" : record.getNotes());
        detailCertificateLabel.setText(record.getCertificatePath() == null || record.getCertificatePath().isBlank()
                ? "Not generated"
                : record.getCertificatePath());
    }

    private SqliteDeceasedRecordDao.DeathRecord selectedRecord() {
        SqliteDeceasedRecordDao.DeathRecord selected = deceasedTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            NotificationHelper.showInfo(statusLabel, "Select a deceased record first.");
        }
        return selected;
    }

    private void setButtonVisible(Button button, boolean visible) {
        if (button != null) {
            button.setVisible(visible);
            button.setManaged(visible);
        }
    }
}
