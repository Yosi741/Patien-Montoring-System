package ui.javafx.controllers;

import dao.SqliteDeceasedRecordDao;
import dao.SqliteMessageDao;
import dao.SqliteNewbornRecordDao;
import dao.SqlitePatientDao;
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
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import services.CertificateEventService;
import services.CertificateRegistryService;
import services.CertificateReviewService;
import services.DeceasedPatientService;
import services.MessagingService;
import services.NewbornService;
import ui.javafx.AppShell;
import ui.javafx.FxController;
import ui.javafx.SessionContext;
import ui.javafx.helpers.AuditAction;
import ui.javafx.helpers.AuditWriteHelper;
import ui.javafx.helpers.NotificationHelper;
import ui.javafx.helpers.PermissionHelper;
import users.Session;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Optional;

public class CertificateRegistryController implements FxController {

    private final CertificateRegistryService registryService = new CertificateRegistryService();
    private final CertificateReviewService reviewService = new CertificateReviewService();
    private final DeceasedPatientService deceasedPatientService = new DeceasedPatientService();
    private final NewbornService newbornService = new NewbornService();
    private final CertificateEventService certificateEventService = new CertificateEventService();
    private final MessagingService messagingService = new MessagingService();
    private final SqliteDeceasedRecordDao deceasedRecordDao = new SqliteDeceasedRecordDao();
    private final SqliteNewbornRecordDao newbornRecordDao = new SqliteNewbornRecordDao();
    private final SqlitePatientDao patientDao = new SqlitePatientDao();
    private final ObservableList<CertificateRegistryService.CertificateRow> rows = FXCollections.observableArrayList();
    private AppShell appShell;

    @FXML private Label accessDeniedPane;
    @FXML private javafx.scene.layout.VBox contentPane;
    @FXML private ComboBox<String> typeFilter;
    @FXML private ComboBox<String> statusFilter;
    @FXML private ComboBox<String> reviewStatusFilter;
    @FXML private ComboBox<String> dateRangeFilter;
    @FXML private ComboBox<String> sectionFilter;
    @FXML private TextField searchField;
    @FXML private Label totalCertificatesLabel;
    @FXML private Label generatedCertificatesLabel;
    @FXML private Label pendingCertificatesLabel;
    @FXML private Label birthCertificatesLabel;
    @FXML private Label deathCertificatesLabel;
    @FXML private TableView<CertificateRegistryService.CertificateRow> certificateTable;
    @FXML private TableColumn<CertificateRegistryService.CertificateRow, Number> rowNumberColumn;
    @FXML private TableColumn<CertificateRegistryService.CertificateRow, String> typeColumn;
    @FXML private TableColumn<CertificateRegistryService.CertificateRow, Long> sourceRecordColumn;
    @FXML private TableColumn<CertificateRegistryService.CertificateRow, String> subjectIdColumn;
    @FXML private TableColumn<CertificateRegistryService.CertificateRow, String> nameColumn;
    @FXML private TableColumn<CertificateRegistryService.CertificateRow, String> eventTimeColumn;
    @FXML private TableColumn<CertificateRegistryService.CertificateRow, String> statusColumn;
    @FXML private TableColumn<CertificateRegistryService.CertificateRow, String> reviewStatusColumn;
    @FXML private TableColumn<CertificateRegistryService.CertificateRow, String> sectionColumn;
    @FXML private TableColumn<CertificateRegistryService.CertificateRow, String> generatedAtColumn;
    @FXML private Label detailTitleLabel;
    @FXML private Label detailMetaLabel;
    @FXML private Label detailPathLabel;
    @FXML private Label detailStatusLabel;
    @FXML private Label detailReviewLabel;
    @FXML private Label detailWarningLabel;
    @FXML private Button generateButton;
    @FXML private Button openCertificateButton;
    @FXML private Button sendNoticeButton;
    @FXML private Button submitReviewButton;
    @FXML private Button approveButton;
    @FXML private Button rejectButton;
    @FXML private Button resetDraftButton;
    @FXML private Button sendReviewNoteButton;
    @FXML private Label statusLabel;

    @Override
    public void setAppShell(AppShell appShell) {
        this.appShell = appShell;
        configureAccess();
        configureFilters();
        configureTable();
        if (canViewRegistry()) {
            audit(AuditAction.OPEN_CERTIFICATE_REGISTRY, "opened");
            loadCertificates();
        }
    }

    @FXML
    private void loadCertificates() {
        if (!canViewRegistry()) {
            NotificationHelper.showError(statusLabel, "Access denied.");
            return;
        }
        try {
            rows.setAll(registryService.findCertificates(buildFilter()));
            certificateTable.setItems(rows);
            renderSummary();
            renderDetail(null);
            NotificationHelper.showInfo(statusLabel, "Certificate registry loaded: " + rows.size());
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, "Could not load certificate registry: " + e.getMessage());
        }
    }

    @FXML
    private void clearFilters() {
        typeFilter.getSelectionModel().select("All");
        statusFilter.getSelectionModel().select("All");
        reviewStatusFilter.getSelectionModel().select("All");
        dateRangeFilter.getSelectionModel().select("All");
        sectionFilter.getSelectionModel().select("All");
        searchField.clear();
        loadCertificates();
    }

    @FXML
    private void showDashboard() {
        appShell.showDashboard(Session.getCurrentUser());
    }

    @FXML
    private void openRelatedRecord() {
        CertificateRegistryService.CertificateRow selected = selectedRow();
        if (selected == null) {
            return;
        }
        appShell.showCertificateSourceRecord(selected.getSourceType(), String.valueOf(selected.getSourceRecordId()));
    }

    @FXML
    private void generateCertificate() {
        CertificateRegistryService.CertificateRow selected = selectedRow();
        if (selected == null) {
            return;
        }
        if ("GENERATED".equals(selected.getCertificateStatus())) {
            NotificationHelper.showInfo(statusLabel, "Certificate is already generated.");
            return;
        }
        try {
            Path path;
            if ("DEATH".equals(selected.getCertificateType())) {
                path = deceasedPatientService.generateDeathCertificate(Session.getCurrentUser(), selected.getSourceRecordId());
            } else {
                SqliteNewbornRecordDao.NewbornRecord newborn = newbornRecordDao.findById(selected.getSourceRecordId())
                        .orElseThrow(() -> new IllegalArgumentException("Newborn record not found in SQLite."));
                path = newbornService.generateBirthCertificate(Session.getCurrentUser(), newborn.getNewbornId());
            }
            audit(AuditAction.GENERATE_CERTIFICATE_FROM_REGISTRY,
                    "type=" + selected.getCertificateType() + ", source_id=" + selected.getSourceRecordId());
            loadCertificates();
            NotificationHelper.showSuccess(statusLabel, "Certificate generated: " + path);
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
        }
    }

    @FXML
    private void openCertificate() {
        CertificateRegistryService.CertificateRow selected = selectedRow();
        if (selected == null) {
            return;
        }
        if (!"GENERATED".equals(selected.getCertificateStatus())) {
            NotificationHelper.showInfo(statusLabel, "Generate the certificate before opening it.");
            return;
        }
        if ("REJECTED".equals(selected.getReviewStatus())) {
            NotificationHelper.showError(statusLabel, "Rejected certificates cannot be opened from the registry.");
            return;
        }
        appShell.showCertificateFromRegistry(selected.getSourceType(), String.valueOf(selected.getSourceRecordId()));
    }

    @FXML
    private void copySummary() {
        CertificateRegistryService.CertificateRow selected = selectedRow();
        if (selected == null) {
            return;
        }
        try {
            ClipboardContent content = new ClipboardContent();
            content.putString(registryService.summaryText(selected));
            Clipboard.getSystemClipboard().setContent(content);
            audit(AuditAction.COPY_CERTIFICATE_REGISTRY_SUMMARY,
                    "type=" + selected.getCertificateType() + ", source_id=" + selected.getSourceRecordId());
            NotificationHelper.showSuccess(statusLabel, "Certificate summary copied.");
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
        }
    }

    @FXML
    private void sendNotice() {
        CertificateRegistryService.CertificateRow selected = selectedRow();
        if (selected == null) {
            return;
        }
        if (!"GENERATED".equals(selected.getCertificateStatus())) {
            NotificationHelper.showInfo(statusLabel, "Generate the certificate before sending a notice.");
            return;
        }
        if ("REJECTED".equals(selected.getReviewStatus())) {
            NotificationHelper.showError(statusLabel, "Rejected certificates cannot be sent as final notices.");
            return;
        }
        try {
            long messageId;
            if ("DEATH".equals(selected.getCertificateType())) {
                SqliteDeceasedRecordDao.DeathRecord record = deceasedRecordDao.findById(selected.getSourceRecordId())
                        .orElseThrow(() -> new IllegalArgumentException("Death record not found in SQLite."));
                messageId = certificateEventService.sendDeathCertificateNotice(Session.getCurrentUser(), record);
            } else {
                SqliteNewbornRecordDao.NewbornRecord record = newbornRecordDao.findById(selected.getSourceRecordId())
                        .orElseThrow(() -> new IllegalArgumentException("Newborn record not found in SQLite."));
                messageId = certificateEventService.sendBirthCertificateNotice(Session.getCurrentUser(), record);
            }
            audit(AuditAction.SEND_CERTIFICATE_NOTICE_FROM_REGISTRY,
                    "message_id=" + messageId + ", type=" + selected.getCertificateType()
                            + ", source_id=" + selected.getSourceRecordId());
            NotificationHelper.showSuccess(statusLabel, "Certificate notice sent. Message ID: " + messageId);
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
        }
    }

    @FXML
    private void submitForReview() {
        CertificateRegistryService.CertificateRow selected = selectedRow();
        if (selected == null) {
            return;
        }
        try {
            reviewService.submitForReview(Session.getCurrentUser(), selected.getCertificateType(), selected.getSourceRecordId());
            loadCertificates();
            NotificationHelper.showSuccess(statusLabel, "Certificate submitted for review.");
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
        }
    }

    @FXML
    private void approveCertificate() {
        CertificateRegistryService.CertificateRow selected = selectedRow();
        if (selected == null) {
            return;
        }
        try {
            reviewService.approveCertificate(Session.getCurrentUser(), selected.getCertificateType(), selected.getSourceRecordId());
            loadCertificates();
            NotificationHelper.showSuccess(statusLabel, "Certificate approved.");
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
        }
    }

    @FXML
    private void rejectCertificate() {
        CertificateRegistryService.CertificateRow selected = selectedRow();
        if (selected == null) {
            return;
        }
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Reject Certificate");
        dialog.setHeaderText("Rejection reason required");
        dialog.setContentText("Reason:");
        Optional<String> result = dialog.showAndWait();
        if (result.isEmpty()) {
            return;
        }
        try {
            reviewService.rejectCertificate(Session.getCurrentUser(), selected.getCertificateType(), selected.getSourceRecordId(), result.get());
            loadCertificates();
            NotificationHelper.showSuccess(statusLabel, "Certificate rejected.");
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
        }
    }

    @FXML
    private void resetToDraft() {
        CertificateRegistryService.CertificateRow selected = selectedRow();
        if (selected == null) {
            return;
        }
        try {
            reviewService.resetToDraft(Session.getCurrentUser(), selected.getCertificateType(), selected.getSourceRecordId());
            loadCertificates();
            NotificationHelper.showSuccess(statusLabel, "Certificate review status reset to draft.");
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
        }
    }

    @FXML
    private void sendReviewNote() {
        CertificateRegistryService.CertificateRow selected = selectedRow();
        if (selected == null) {
            return;
        }
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Send Review Note");
        dialog.setHeaderText("Internal review note");
        dialog.setContentText("Note:");
        Optional<String> result = dialog.showAndWait();
        if (result.isEmpty() || result.get().trim().isBlank()) {
            return;
        }
        try {
            String targetRole = "DEATH".equals(selected.getCertificateType()) ? "DOCTOR" : "NURSE";
            long id = messagingService.sendMessage(Session.getCurrentUser(), new SqliteMessageDao.MessageWriteRecord(
                    Session.getUsername(), "", targetRole, "",
                    "DEATH".equals(selected.getCertificateType()) ? selected.getSubjectId() : "",
                    "Certificate review note: " + selected.getCertificateType() + " " + selected.getSourceRecordId(),
                    registryService.summaryText(selected) + "\n\nReview note:\n" + result.get().trim(),
                    "NORMAL"
            ));
            audit(AuditAction.SEND_CERTIFICATE_REVIEW_NOTE,
                    "message_id=" + id + ", type=" + selected.getCertificateType()
                            + ", source_id=" + selected.getSourceRecordId());
            NotificationHelper.showSuccess(statusLabel, "Review note sent. Message ID: " + id);
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
        }
    }

    private void configureAccess() {
        boolean allowed = canViewRegistry();
        accessDeniedPane.setVisible(!allowed);
        accessDeniedPane.setManaged(!allowed);
        contentPane.setVisible(allowed);
        contentPane.setManaged(allowed);
        setButtonVisible(generateButton, PermissionHelper.canGenerateBirthCertificate(Session.getCurrentUser())
                || PermissionHelper.canGenerateDeathCertificate(Session.getCurrentUser()));
        setButtonVisible(openCertificateButton, allowed);
        setButtonVisible(sendNoticeButton, PermissionHelper.canSendBirthCertificateNotice(Session.getCurrentUser())
                || PermissionHelper.canSendDeathCertificateNotice(Session.getCurrentUser()));
        setButtonVisible(submitReviewButton, PermissionHelper.canSubmitCertificateReview(Session.getCurrentUser()));
        setButtonVisible(approveButton, PermissionHelper.canApproveCertificateReview(Session.getCurrentUser()));
        setButtonVisible(rejectButton, PermissionHelper.canApproveCertificateReview(Session.getCurrentUser()));
        setButtonVisible(resetDraftButton, PermissionHelper.canSubmitCertificateReview(Session.getCurrentUser()));
        setButtonVisible(sendReviewNoteButton, PermissionHelper.canComposeMessage(Session.getCurrentUser()));
    }

    private void configureFilters() {
        typeFilter.setItems(FXCollections.observableArrayList("All", "BIRTH", "DEATH"));
        statusFilter.setItems(FXCollections.observableArrayList("All", "GENERATED", "PENDING"));
        reviewStatusFilter.setItems(FXCollections.observableArrayList("All", "DRAFT", "PENDING_REVIEW", "APPROVED", "REJECTED"));
        dateRangeFilter.setItems(FXCollections.observableArrayList("All", "Today", "Last 7 days", "Last 30 days"));
        ArrayList<String> sections = new ArrayList<>();
        sections.add("All");
        try {
            sections.addAll(patientDao.findDistinctSections());
        } catch (Exception e) {
            statusLabel.setText("Section filter unavailable: " + e.getMessage());
        }
        sectionFilter.setItems(FXCollections.observableArrayList(sections));
        typeFilter.getSelectionModel().select("All");
        statusFilter.getSelectionModel().select("All");
        reviewStatusFilter.getSelectionModel().select("All");
        dateRangeFilter.getSelectionModel().select("All");
        sectionFilter.getSelectionModel().select("All");
        typeFilter.valueProperty().addListener((observable, oldValue, newValue) -> loadCertificates());
        statusFilter.valueProperty().addListener((observable, oldValue, newValue) -> loadCertificates());
        reviewStatusFilter.valueProperty().addListener((observable, oldValue, newValue) -> loadCertificates());
        dateRangeFilter.valueProperty().addListener((observable, oldValue, newValue) -> loadCertificates());
        sectionFilter.valueProperty().addListener((observable, oldValue, newValue) -> loadCertificates());
        searchField.textProperty().addListener((observable, oldValue, newValue) -> loadCertificates());
    }

    private void configureTable() {
        if (rowNumberColumn != null) {
            rowNumberColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(
                    certificateTable.getItems().indexOf(cell.getValue()) + 1));
        }
        typeColumn.setCellValueFactory(new PropertyValueFactory<>("certificateType"));
        sourceRecordColumn.setCellValueFactory(new PropertyValueFactory<>("sourceRecordId"));
        subjectIdColumn.setCellValueFactory(new PropertyValueFactory<>("subjectId"));
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("personName"));
        eventTimeColumn.setCellValueFactory(new PropertyValueFactory<>("eventDateTime"));
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("certificateStatus"));
        reviewStatusColumn.setCellValueFactory(new PropertyValueFactory<>("reviewStatus"));
        sectionColumn.setCellValueFactory(new PropertyValueFactory<>("section"));
        generatedAtColumn.setCellValueFactory(new PropertyValueFactory<>("generatedOrUpdatedAt"));
        certificateTable.getSelectionModel().selectedItemProperty().addListener((obs, old, row) -> renderDetail(row));
    }

    private CertificateRegistryService.CertificateFilter buildFilter() {
        CertificateRegistryService.CertificateFilter filter = new CertificateRegistryService.CertificateFilter();
        filter.setCertificateType(typeFilter.getValue());
        filter.setStatus(statusFilter.getValue());
        filter.setReviewStatus(reviewStatusFilter.getValue());
        filter.setDateRange(dateRangeFilter.getValue());
        filter.setSection(sectionFilter.getValue());
        filter.setSearch(searchField.getText());
        return filter;
    }

    private void renderSummary() throws Exception {
        CertificateRegistryService.RegistrySummary summary = registryService.loadSummary();
        totalCertificatesLabel.setText(String.valueOf(summary.getTotalCertificates()));
        generatedCertificatesLabel.setText(String.valueOf(summary.getGeneratedCertificates()));
        pendingCertificatesLabel.setText(String.valueOf(summary.getPendingCertificates()));
        birthCertificatesLabel.setText(String.valueOf(summary.getBirthCertificates()));
        deathCertificatesLabel.setText(String.valueOf(summary.getDeathCertificates()));
    }

    private void renderDetail(CertificateRegistryService.CertificateRow row) {
        if (row == null) {
        detailTitleLabel.setText("Select a certificate");
        detailMetaLabel.setText("-");
        detailPathLabel.setText("-");
        detailStatusLabel.setText("No certificate selected.");
        detailReviewLabel.setText("-");
        detailWarningLabel.setText("");
        updateActionButtons(null);
        return;
        }
        detailTitleLabel.setText(row.getCertificateType() + " certificate | " + row.getPersonName());
        detailMetaLabel.setText("Source ID: " + row.getSourceRecordId()
                + " | Identifier: " + row.getSubjectId()
                + " | Event: " + row.getEventDateTime()
                + " | Section/Room: " + row.getSection() + " / " + row.getRoom());
        detailPathLabel.setText(row.getSafeCertificatePath().isBlank() ? "Pending certificate generation" : row.getSafeCertificatePath());
        detailStatusLabel.setText(row.getCertificateStatus());
        detailReviewLabel.setText(row.getReviewStatus()
                + " | Reviewed by: " + row.getReviewedBy()
                + " | Reviewed at: " + row.getReviewedAt()
                + " | Reason: " + row.getRejectionReason());
        detailWarningLabel.setText("PENDING".equals(row.getCertificateStatus())
                ? "Warning: certificate record exists, but no HTML certificate has been generated yet."
                : "");
        updateActionButtons(row);
    }

    private CertificateRegistryService.CertificateRow selectedRow() {
        CertificateRegistryService.CertificateRow selected = certificateTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            NotificationHelper.showInfo(statusLabel, "Select a certificate first.");
        }
        return selected;
    }

    private boolean canViewRegistry() {
        return PermissionHelper.canViewCertificateRegistry(Session.getCurrentUser());
    }

    private void updateActionButtons(CertificateRegistryService.CertificateRow row) {
        boolean generated = row != null && "GENERATED".equals(row.getCertificateStatus());
        boolean pending = row != null && "PENDING".equals(row.getCertificateStatus());
        boolean canGenerate = row != null && ("DEATH".equals(row.getCertificateType())
                ? PermissionHelper.canGenerateDeathCertificate(Session.getCurrentUser())
                : PermissionHelper.canGenerateBirthCertificate(Session.getCurrentUser()));
        boolean canSend = row != null && ("DEATH".equals(row.getCertificateType())
                ? PermissionHelper.canSendDeathCertificateNotice(Session.getCurrentUser())
                : PermissionHelper.canSendBirthCertificateNotice(Session.getCurrentUser()));
        boolean canSubmit = row != null && PermissionHelper.canSubmitCertificateReview(Session.getCurrentUser());
        boolean canApprove = row != null && PermissionHelper.canApproveCertificateReview(Session.getCurrentUser());
        boolean pendingReview = row != null && "PENDING_REVIEW".equals(row.getReviewStatus());
        boolean terminalReview = row != null && ("APPROVED".equals(row.getReviewStatus()) || "REJECTED".equals(row.getReviewStatus()));
        boolean rejected = row != null && "REJECTED".equals(row.getReviewStatus());
        if (generateButton != null) {
            generateButton.setDisable(!pending || !canGenerate);
        }
        if (openCertificateButton != null) {
            openCertificateButton.setDisable(!generated || rejected);
        }
        if (sendNoticeButton != null) {
            sendNoticeButton.setDisable(!generated || !canSend || rejected);
        }
        if (submitReviewButton != null) {
            submitReviewButton.setDisable(!canSubmit || pendingReview || terminalReview);
        }
        if (approveButton != null) {
            approveButton.setDisable(!canApprove || !pendingReview || !generated);
        }
        if (rejectButton != null) {
            rejectButton.setDisable(!canApprove || !pendingReview);
        }
        if (resetDraftButton != null) {
            resetDraftButton.setDisable(!canSubmit || row == null || "DRAFT".equals(row.getReviewStatus()));
        }
        if (sendReviewNoteButton != null) {
            sendReviewNoteButton.setDisable(row == null || !PermissionHelper.canComposeMessage(Session.getCurrentUser()));
        }
    }

    private void setButtonVisible(Button button, boolean visible) {
        if (button != null) {
            button.setVisible(visible);
            button.setManaged(visible);
        }
    }

    private void audit(String action, String detail) {
        try {
            AuditWriteHelper.write(SessionContext.username(), action, detail);
        } catch (Exception e) {
            System.out.println("SQLite certificate registry audit skipped: " + e.getMessage());
        }
    }
}
