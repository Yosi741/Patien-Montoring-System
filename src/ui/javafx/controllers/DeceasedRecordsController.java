package ui.javafx.controllers;

import Data_Access_Object.SqliteDeceasedRecordDao;
import Data_Access_Object.SqlitePatientDao;
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
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.VBox;
import ui.javafx.services.CertificateEventService;
import ui.javafx.services.DeceasedPatientService;
import ui.javafx.AppShell;
import ui.javafx.FxController;
import ui.javafx.helpers.AuditAction;
import ui.javafx.helpers.AuditWriteHelper;
import ui.javafx.helpers.NotificationHelper;
import ui.javafx.helpers.PermissionHelper;
import ui.javafx.helpers.SelectionHelper;
import users.Session;

import java.nio.file.Path;
import java.util.ArrayList;

public class DeceasedRecordsController implements FxController {

    private final DeceasedPatientService deceasedPatientService = new DeceasedPatientService();
    private final CertificateEventService certificateEventService = new CertificateEventService();
    private final SqliteDeceasedRecordDao deceasedRecordDao = new SqliteDeceasedRecordDao();
    private final SqlitePatientDao patientDao = new SqlitePatientDao();
    private final ObservableList<SqliteDeceasedRecordDao.DeathRecord> records = FXCollections.observableArrayList();
    private AppShell appShell;

    @FXML private VBox accessDeniedPane;
    @FXML private VBox contentPane;
    @FXML private TextField searchField;
    @FXML private ComboBox<String> dateRangeFilter;
    @FXML private ComboBox<String> sectionFilter;
    @FXML private Label totalRecordsLabel;
    @FXML private Label certificatesGeneratedLabel;
    @FXML private Label pendingCertificatesLabel;
    @FXML private Label deathsThisMonthLabel;
    @FXML private TableView<SqliteDeceasedRecordDao.DeathRecord> deceasedTable;
    @FXML private TableColumn<SqliteDeceasedRecordDao.DeathRecord, Number> rowNumberColumn;
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
    @FXML private Button sendNoticeButton;
    @FXML private Button copySummaryButton;

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

    public void openWithRecord(long recordId) {
        if (!PermissionHelper.canViewDeceasedRecords(Session.getCurrentUser())) {
            return;
        }
        clearFilterControls();
        loadRecords();
        for (SqliteDeceasedRecordDao.DeathRecord record : records) {
            if (record.getId() == recordId) {
                int index = deceasedTable.getItems() == null ? -1 : deceasedTable.getItems().indexOf(record);
                if (index >= 0) {
                    deceasedTable.getSelectionModel().select(index);
                    deceasedTable.scrollTo(index);
                }
                renderDetail(record);
                NotificationHelper.showInfo(statusLabel, "Opened death certificate source record: " + recordId);
                return;
            }
        }
        NotificationHelper.showError(statusLabel, "Death record not found in SQLite: " + recordId);
    }

    @FXML
    private void loadRecords() {
        if (!PermissionHelper.canViewDeceasedRecords(Session.getCurrentUser())) {
            NotificationHelper.showError(statusLabel, "Access denied.");
            return;
        }
        try {
            SelectionHelper.safeClearSelection(deceasedTable);
            records.setAll(deceasedPatientService.getDeceasedRecords(buildFilter()));
            deceasedTable.setItems(records);
            loadSummaryCards();
            statusLabel.setText("Deceased records loaded from the local database: " + records.size());
            renderDetail(null);
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, "Could not load deceased records: " + e.getMessage());
        }
    }

    @FXML
    private void clearFilters() {
        clearFilterControls();
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
                NotificationHelper.showSuccess(statusLabel, "Death record updated.");
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

    @FXML
    private void sendCertificateNotice() {
        SqliteDeceasedRecordDao.DeathRecord selected = selectedRecord();
        if (selected == null) {
            return;
        }
        try {
            long id = certificateEventService.sendDeathCertificateNotice(Session.getCurrentUser(), selected);
            NotificationHelper.showSuccess(statusLabel, "Death certificate notice sent through SQLite messages. Message ID: " + id);
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
        }
    }

    @FXML
    private void copySummary() {
        SqliteDeceasedRecordDao.DeathRecord selected = selectedRecord();
        if (selected == null) {
            return;
        }
        try {
            ClipboardContent content = new ClipboardContent();
            content.putString(certificateEventService.deathSummary(selected));
            Clipboard.getSystemClipboard().setContent(content);
            AuditWriteHelper.write(username(), AuditAction.COPY_CERTIFICATE_SUMMARY,
                    "type=death, patient_id=" + selected.getPatientId());
            NotificationHelper.showSuccess(statusLabel, "Certificate summary copied.");
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
        setButtonVisible(sendNoticeButton, PermissionHelper.canSendDeathCertificateNotice(Session.getCurrentUser()));
        setButtonVisible(copySummaryButton, authorized);
    }

    private void configureTable() {
        if (rowNumberColumn != null) {
            rowNumberColumn.setCellValueFactory(cell -> {
                int index = deceasedTable.getItems() == null ? -1 : deceasedTable.getItems().indexOf(cell.getValue());
                Number rowNumber = index >= 0 ? index + 1 : null;
                return new javafx.beans.property.ReadOnlyObjectWrapper<>(rowNumber);
            });
        }
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
                if (event.getClickCount() == 2 && !row.isEmpty() && row.getItem() != null) {
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

    private void clearFilterControls() {
        searchField.clear();
        dateRangeFilter.getSelectionModel().select("All");
        sectionFilter.getSelectionModel().select("All");
    }

    private void loadSummaryCards() {
        try {
            totalRecordsLabel.setText(String.valueOf(deceasedRecordDao.count()));
            certificatesGeneratedLabel.setText(String.valueOf(deceasedRecordDao.countCertificatesGenerated()));
            pendingCertificatesLabel.setText(String.valueOf(deceasedRecordDao.countPendingCertificates()));
            deathsThisMonthLabel.setText(String.valueOf(deceasedRecordDao.countDeathsThisMonth()));
        } catch (Exception e) {
            statusLabel.setText("Deceased report counters unavailable: " + e.getMessage());
        }
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
                ? "Pending certificate generation"
                : "Generated: " + record.getCertificatePath());
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

    private String username() {
        return Session.getCurrentUser() == null ? "Unknown" : Session.getCurrentUser().getUsername();
    }
}
