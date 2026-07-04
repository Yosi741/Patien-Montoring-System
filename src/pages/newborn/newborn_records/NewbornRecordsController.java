package pages.newborn.newborn_records;

import pages.newborn.*;
import pages.newborn.newborn_form.NewbornFormController;
import pages.patient.dao.SqlitePatientDao;
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
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.VBox;
import pages.certificate.CertificateEventService;
import app.AppShell;
import app.FxController;
import pages.audit_log.AuditAction;
import pages.audit_log.AuditWriteHelper;
import pages.notification.NotificationHelper;
import app.helpers.PermissionHelper;
import app.helpers.SelectionHelper;
import users.Session;

import java.nio.file.Path;
import java.util.ArrayList;

public class NewbornRecordsController implements FxController {

    private final NewbornService newbornService = new NewbornService();
    private final CertificateEventService certificateEventService = new CertificateEventService();
    private final SqliteNewbornRecordDao newbornDao = new SqliteNewbornRecordDao();
    private final SqlitePatientDao patientDao = new SqlitePatientDao();
    private final ObservableList<SqliteNewbornRecordDao.NewbornRecord> records = FXCollections.observableArrayList();
    private AppShell appShell;
    private String motherFilter = "";

    @FXML private VBox accessDeniedPane;
    @FXML private VBox contentPane;
    @FXML private TextField searchField;
    @FXML private ComboBox<String> dateRangeFilter;
    @FXML private ComboBox<String> sectionFilter;
    @FXML private ComboBox<String> genderFilter;
    @FXML private Label motherFilterLabel;
    @FXML private Label totalNewbornsLabel;
    @FXML private Label birthsTodayLabel;
    @FXML private Label birthsThisMonthLabel;
    @FXML private Label certificatesGeneratedLabel;
    @FXML private Label pendingCertificatesLabel;
    @FXML private TableView<SqliteNewbornRecordDao.NewbornRecord> newbornTable;
    @FXML private TableColumn<SqliteNewbornRecordDao.NewbornRecord, Number> rowNumberColumn;
    @FXML private TableColumn<SqliteNewbornRecordDao.NewbornRecord, String> newbornIdColumn;
    @FXML private TableColumn<SqliteNewbornRecordDao.NewbornRecord, String> babyNameColumn;
    @FXML private TableColumn<SqliteNewbornRecordDao.NewbornRecord, String> genderColumn;
    @FXML private TableColumn<SqliteNewbornRecordDao.NewbornRecord, String> birthTimeColumn;
    @FXML private TableColumn<SqliteNewbornRecordDao.NewbornRecord, String> motherColumn;
    @FXML private TableColumn<SqliteNewbornRecordDao.NewbornRecord, String> roomColumn;
    @FXML private TableColumn<SqliteNewbornRecordDao.NewbornRecord, String> sectionColumn;
    @FXML private TableColumn<SqliteNewbornRecordDao.NewbornRecord, String> certificateColumn;
    @FXML private Label detailBabyLabel;
    @FXML private Label detailMotherLabel;
    @FXML private Label detailBirthLabel;
    @FXML private Label detailMeasurementsLabel;
    @FXML private Label detailDeliveryLabel;
    @FXML private Label detailCertificateLabel;
    @FXML private Label detailNotesLabel;
    @FXML private Label statusLabel;
    @FXML private Button addNewbornButton;
    @FXML private Button editNewbornButton;
    @FXML private Button generateCertificateButton;
    @FXML private Button openCertificateButton;
    @FXML private Button sendBirthNoticeButton;
    @FXML private Button copySummaryButton;
    @FXML private Button viewMotherFileButton;

    @Override
    public void setAppShell(AppShell appShell) {
        this.appShell = appShell;
        configureAccess();
        configureTable();
        configureFilters();
        if (PermissionHelper.canViewNewbornRecords(Session.getCurrentUser())) {
            loadRecords();
        }
    }

    public void openForMother(String patientId) {
        this.motherFilter = patientId == null ? "" : patientId;
        if (motherFilterLabel != null) {
            motherFilterLabel.setText(motherFilter.isBlank() ? "" : "Mother Patient ID = " + motherFilter);
        }
        loadRecords();
    }

    public void openWithRecord(long recordId) {
        if (!PermissionHelper.canViewNewbornRecords(Session.getCurrentUser())) {
            return;
        }
        clearFilterControls();
        loadRecords();
        for (SqliteNewbornRecordDao.NewbornRecord record : records) {
            if (record.getId() == recordId) {
                int index = newbornTable.getItems() == null ? -1 : newbornTable.getItems().indexOf(record);
                SelectionHelper.safeSelectIndex(newbornTable, index);
                renderDetail(record);
                NotificationHelper.showInfo(statusLabel, "Opened birth certificate source record: " + recordId);
                return;
            }
        }
        NotificationHelper.showError(statusLabel, "Newborn record not found in SQLite: " + recordId);
    }

    @FXML
    private void loadRecords() {
        if (!PermissionHelper.canViewNewbornRecords(Session.getCurrentUser())) {
            NotificationHelper.showError(statusLabel, "Access denied.");
            return;
        }
        try {
            var loadedRecords = newbornService.getNewbornRecords(buildFilter());
            loadSummaryCards();
            SelectionHelper.runWhenTableStable(newbornTable, () -> {
                SelectionHelper.safeReplaceItems(newbornTable, records, loadedRecords);
                statusLabel.setText("Newborn records loaded from the local database: " + records.size());
                renderDetail(null);
            });
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, "Could not load newborn records: " + e.getMessage());
        }
    }

    @FXML
    private void clearFilters() {
        clearFilterControls();
        loadRecords();
    }

    @FXML
    private void addNewborn() {
        try {
            boolean saved = NewbornFormController.showCreateDialog(newbornTable.getScene().getWindow(), Session.getCurrentUser(), motherFilter);
            if (saved) {
                loadRecords();
                NotificationHelper.showSuccess(statusLabel, "Newborn record created.");
            }
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
        }
    }

    @FXML
    private void editNewborn() {
        SqliteNewbornRecordDao.NewbornRecord selected = selectedRecord();
        if (selected == null) {
            return;
        }
        try {
            boolean saved = NewbornFormController.showEditDialog(newbornTable.getScene().getWindow(), Session.getCurrentUser(), selected);
            if (saved) {
                loadRecords();
                NotificationHelper.showSuccess(statusLabel, "Newborn record updated.");
            }
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
        }
    }

    @FXML
    private void generateCertificate() {
        SqliteNewbornRecordDao.NewbornRecord selected = selectedRecord();
        if (selected == null) {
            return;
        }
        try {
            Path path = newbornService.generateBirthCertificate(Session.getCurrentUser(), selected.getNewbornId());
            loadRecords();
            NotificationHelper.showSuccess(statusLabel, "Birth certificate generated: " + path);
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
        }
    }

    @FXML
    private void openCertificate() {
        SqliteNewbornRecordDao.NewbornRecord selected = selectedRecord();
        if (selected == null) {
            return;
        }
        try {
            newbornService.openBirthCertificate(Session.getCurrentUser(), selected.getNewbornId());
            NotificationHelper.showSuccess(statusLabel, "Opening certificate with the local desktop handler.");
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
        }
    }

    @FXML
    private void sendBirthNotice() {
        SqliteNewbornRecordDao.NewbornRecord selected = selectedRecord();
        if (selected == null) {
            return;
        }
        try {
            long id = certificateEventService.sendBirthCertificateNotice(Session.getCurrentUser(), selected);
            NotificationHelper.showSuccess(statusLabel, "Birth notice sent through SQLite messages. Message ID: " + id);
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
        }
    }

    @FXML
    private void copySummary() {
        SqliteNewbornRecordDao.NewbornRecord selected = selectedRecord();
        if (selected == null) {
            return;
        }
        try {
            ClipboardContent content = new ClipboardContent();
            content.putString(certificateEventService.birthSummary(selected));
            Clipboard.getSystemClipboard().setContent(content);
            AuditWriteHelper.write(username(), AuditAction.COPY_CERTIFICATE_SUMMARY,
                    "type=birth, newborn_id=" + selected.getNewbornId());
            NotificationHelper.showSuccess(statusLabel, "Certificate summary copied.");
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
        }
    }

    @FXML
    private void viewMotherFile() {
        SqliteNewbornRecordDao.NewbornRecord selected = selectedRecord();
        if (selected == null) {
            return;
        }
        if (selected.getMotherPatientId() == null || selected.getMotherPatientId().isBlank()) {
            NotificationHelper.showInfo(statusLabel, "This newborn record is not linked to a mother patient file.");
            return;
        }
        try {
            AuditWriteHelper.write(username(), AuditAction.OPEN_MOTHER_FROM_NEWBORN,
                    "newborn_id=" + selected.getNewbornId() + ", mother_patient_id=" + selected.getMotherPatientId());
            appShell.showPatientDetail(selected.getMotherPatientId());
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
        }
    }

    @FXML
    private void backToDashboard() {
        appShell.showDashboard(Session.getCurrentUser());
    }

    private void configureAccess() {
        boolean authorized = PermissionHelper.canViewNewbornRecords(Session.getCurrentUser());
        accessDeniedPane.setVisible(!authorized);
        accessDeniedPane.setManaged(!authorized);
        contentPane.setVisible(authorized);
        contentPane.setManaged(authorized);
        setButtonVisible(addNewbornButton, PermissionHelper.canManageNewbornRecords(Session.getCurrentUser()));
        setButtonVisible(editNewbornButton, PermissionHelper.canManageNewbornRecords(Session.getCurrentUser()));
        setButtonVisible(generateCertificateButton, PermissionHelper.canGenerateBirthCertificate(Session.getCurrentUser()));
        setButtonVisible(openCertificateButton, authorized);
        setButtonVisible(sendBirthNoticeButton, PermissionHelper.canSendBirthCertificateNotice(Session.getCurrentUser()));
        setButtonVisible(copySummaryButton, authorized);
    }

    private void configureTable() {
        if (rowNumberColumn != null) {
            rowNumberColumn.setCellValueFactory(cell -> {
                int index = newbornTable.getItems() == null ? -1 : newbornTable.getItems().indexOf(cell.getValue());
                Number rowNumber = index >= 0 ? index + 1 : null;
                return new javafx.beans.property.ReadOnlyObjectWrapper<>(rowNumber);
            });
        }
        newbornIdColumn.setCellValueFactory(new PropertyValueFactory<>("newbornId"));
        babyNameColumn.setCellValueFactory(new PropertyValueFactory<>("babyName"));
        genderColumn.setCellValueFactory(new PropertyValueFactory<>("gender"));
        birthTimeColumn.setCellValueFactory(new PropertyValueFactory<>("birthTime"));
        motherColumn.setCellValueFactory(new PropertyValueFactory<>("motherDisplay"));
        roomColumn.setCellValueFactory(new PropertyValueFactory<>("room"));
        sectionColumn.setCellValueFactory(new PropertyValueFactory<>("section"));
        certificateColumn.setCellValueFactory(new PropertyValueFactory<>("certificateStatus"));
        newbornTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> renderDetail(newValue));
    }

    private void configureFilters() {
        dateRangeFilter.setItems(FXCollections.observableArrayList("All", "Today", "Last 7 days", "Last 30 days"));
        genderFilter.setItems(FXCollections.observableArrayList("All", "MALE", "FEMALE", "UNKNOWN"));
        dateRangeFilter.getSelectionModel().select("All");
        genderFilter.getSelectionModel().select("All");
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
        genderFilter.valueProperty().addListener((observable, oldValue, newValue) -> loadRecords());
    }

    private void clearFilterControls() {
        searchField.clear();
        motherFilter = "";
        motherFilterLabel.setText("");
        dateRangeFilter.getSelectionModel().select("All");
        sectionFilter.getSelectionModel().select("All");
        genderFilter.getSelectionModel().select("All");
    }

    private void loadSummaryCards() {
        try {
            totalNewbornsLabel.setText(String.valueOf(newbornDao.count()));
            birthsTodayLabel.setText(String.valueOf(newbornDao.countBirthsToday()));
            birthsThisMonthLabel.setText(String.valueOf(newbornDao.countBirthsThisMonth()));
            certificatesGeneratedLabel.setText(String.valueOf(newbornDao.countCertificatesGenerated()));
            pendingCertificatesLabel.setText(String.valueOf(newbornDao.countPendingCertificates()));
        } catch (Exception e) {
            statusLabel.setText("Newborn report counters unavailable: " + e.getMessage());
        }
    }

    private SqliteNewbornRecordDao.RecordFilter buildFilter() {
        SqliteNewbornRecordDao.RecordFilter filter = new SqliteNewbornRecordDao.RecordFilter();
        filter.setSearch(searchField.getText());
        filter.setDateRange(dateRangeFilter.getValue());
        filter.setSection(sectionFilter.getValue());
        filter.setGender(genderFilter.getValue());
        filter.setMotherPatientId(motherFilter);
        return filter;
    }

    private void renderDetail(SqliteNewbornRecordDao.NewbornRecord record) {
        if (record == null) {
            detailBabyLabel.setText("No newborn selected");
            detailMotherLabel.setText("-");
            detailBirthLabel.setText("-");
            detailMeasurementsLabel.setText("-");
            detailDeliveryLabel.setText("-");
            detailCertificateLabel.setText("-");
            detailNotesLabel.setText("Select a newborn record to view details.");
            setButtonVisible(viewMotherFileButton, false);
            return;
        }
        detailBabyLabel.setText(record.getNewbornId() + " | " + record.getBabyName() + " | " + record.getGender());
        detailMotherLabel.setText(record.getMotherDisplay() + " | Father: " + fallback(record.getFatherName()));
        detailBirthLabel.setText(record.getBirthTime());
        detailMeasurementsLabel.setText(record.getBirthWeight() + " kg"
                + (record.getBirthLength() == null ? "" : " | " + record.getBirthLength() + " cm"));
        detailDeliveryLabel.setText(record.getDeliveryType() + " | " + fallback(record.getRoom()) + " / " + fallback(record.getSection())
                + " | " + fallback(record.getDoctorOrMidwife()));
        detailCertificateLabel.setText(record.getCertificatePath() == null || record.getCertificatePath().isBlank()
                ? "Pending certificate generation"
                : "Generated: " + record.getCertificatePath());
        detailNotesLabel.setText(fallback(record.getNotes()));
        setButtonVisible(viewMotherFileButton, record.getMotherPatientId() != null && !record.getMotherPatientId().isBlank());
    }

    private SqliteNewbornRecordDao.NewbornRecord selectedRecord() {
        SqliteNewbornRecordDao.NewbornRecord selected = newbornTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            NotificationHelper.showInfo(statusLabel, "Select a newborn record first.");
        }
        return selected;
    }

    private String fallback(String value) {
        return value == null || value.isBlank() ? "-" : value;
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
