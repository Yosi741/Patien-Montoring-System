package pages.patient.medical_files;

import pages.patient.dao.SqliteMedicalFileDao;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.VBox;
import pages.patient.services.MedicalFilePreviewService;
import pages.patient.services.MedicalFileUploadService;
import app.AppShell;
import app.FxController;
import app.SessionContext;
import pages.audit_log.AuditAction;
import pages.audit_log.AuditWriteHelper;
import pages.notification.NotificationHelper;
import app.helpers.PermissionHelper;
import app.helpers.SelectionHelper;
import users.Session;

public class MedicalFilesController implements FxController {

    private final SqliteMedicalFileDao fileDao = new SqliteMedicalFileDao();
    private final MedicalFileUploadService uploadService = new MedicalFileUploadService();
    private final MedicalFilePreviewService previewService = new MedicalFilePreviewService();
    private final ObservableList<SqliteMedicalFileDao.MedicalFileRecord> files = FXCollections.observableArrayList();
    private AppShell appShell;
    private String patientIdFilter = "";
    private String pendingFileId = "";
    private String lastViewedFileId = "";

    @FXML private VBox accessDeniedPane;
    @FXML private VBox contentPane;
    @FXML private Label patientFilterChip;
    @FXML private Button clearPatientFilterButton;
    @FXML private Button uploadButton;
    @FXML private Button copySummaryButton;
    @FXML private Button openFileButton;
    @FXML private TextField searchField;
    @FXML private ComboBox<String> categoryFilter;
    @FXML private ComboBox<String> dateRangeFilter;
    @FXML private TextField uploadedByFilter;
    @FXML private TableView<SqliteMedicalFileDao.MedicalFileRecord> filesTable;
    @FXML private TableColumn<SqliteMedicalFileDao.MedicalFileRecord, Number> rowNumberColumn;
    @FXML private TableColumn<SqliteMedicalFileDao.MedicalFileRecord, String> patientIdColumn;
    @FXML private TableColumn<SqliteMedicalFileDao.MedicalFileRecord, String> patientNameColumn;
    @FXML private TableColumn<SqliteMedicalFileDao.MedicalFileRecord, String> originalNameColumn;
    @FXML private TableColumn<SqliteMedicalFileDao.MedicalFileRecord, String> categoryColumn;
    @FXML private TableColumn<SqliteMedicalFileDao.MedicalFileRecord, String> uploadedByColumn;
    @FXML private TableColumn<SqliteMedicalFileDao.MedicalFileRecord, String> uploadedAtColumn;
    @FXML private TableColumn<SqliteMedicalFileDao.MedicalFileRecord, String> fileSizeColumn;
    @FXML private Label detailTitleLabel;
    @FXML private Label fileIdLabel;
    @FXML private Label patientLabel;
    @FXML private Label originalNameLabel;
    @FXML private Label storedPathLabel;
    @FXML private Label categoryLabel;
    @FXML private Label uploadedByLabel;
    @FXML private Label uploadedAtLabel;
    @FXML private Label fileSizeLabel;
    @FXML private TextArea summaryArea;
    @FXML private TextArea notesArea;
    @FXML private Label previewTypeLabel;
    @FXML private TextArea previewArea;
    @FXML private ImageView previewImageView;
    @FXML private Label statusLabel;

    @Override
    public void setAppShell(AppShell appShell) {
        this.appShell = appShell;
        configureAccess();
        configureFilters();
        configureTable();
        configureButtons();
        if (isAuthorized()) {
            loadFiles();
        }
    }

    public void openForPatient(String patientId) {
        patientIdFilter = patientId == null ? "" : patientId;
        updatePatientFilterChip();
        if (isAuthorized()) {
            loadFiles();
        }
    }

    public void openForFile(String patientId, String fileId) {
        patientIdFilter = patientId == null ? "" : patientId;
        pendingFileId = fileId == null ? "" : fileId;
        if (categoryFilter != null) {
            categoryFilter.getSelectionModel().select("All");
            dateRangeFilter.getSelectionModel().select("All");
            searchField.clear();
            uploadedByFilter.clear();
        }
        updatePatientFilterChip();
        if (isAuthorized()) {
            loadFiles();
            selectPendingFile();
        }
    }

    @FXML
    private void loadFiles() {
        if (!isAuthorized()) {
            NotificationHelper.showError(statusLabel, "Access denied.");
            return;
        }
        try {
            var loadedFiles = fileDao.findFiles(
                    searchField.getText(),
                    categoryFilter.getValue(),
                    dateRangeFilter.getValue(),
                    uploadedByFilter.getText(),
                    patientIdFilter);
            SelectionHelper.runWhenTableStable(filesTable, () -> {
                SelectionHelper.safeReplaceItems(filesTable, files, loadedFiles);
                selectPendingFile();
                NotificationHelper.showInfo(statusLabel, "Medical files loaded from the local database: " + files.size());
            });
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, "Could not load medical files: " + e.getMessage());
        }
    }

    @FXML
    private void uploadFile() {
        if (!PermissionHelper.canUploadMedicalFile(Session.getCurrentUser())) {
            NotificationHelper.showError(statusLabel, "Access denied. Admin, Doctor, or Nurse role is required.");
            return;
        }
        try {
            boolean saved = MedicalFileUploadController.showDialog(filesTable.getScene().getWindow(), Session.getCurrentUser(), patientIdFilter);
            if (saved) {
                loadFiles();
                NotificationHelper.showSuccess(statusLabel, "Medical file uploaded and stored in the patient file list.");
            }
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
        }
    }

    @FXML
    private void copySummary() {
        SqliteMedicalFileDao.MedicalFileRecord selected = selectedFile();
        if (selected == null) {
            return;
        }
        String summary = selected.getExtractedSummary();
        if (summary == null || summary.isBlank()) {
            summary = previewArea.getText();
        }
        if (summary == null || summary.isBlank()) {
            NotificationHelper.showError(statusLabel, "No summary or preview text is available to copy.");
            return;
        }
        ClipboardContent content = new ClipboardContent();
        content.putString(summary);
        Clipboard.getSystemClipboard().setContent(content);
        try {
            AuditWriteHelper.write(SessionContext.username(), AuditAction.COPY_FILE_SUMMARY,
                    "patient_id=" + selected.getPatientId() + ", file_id=" + selected.getFileId());
        } catch (Exception e) {
            System.out.println("SQLite copy file summary audit skipped: " + e.getMessage());
        }
        NotificationHelper.showSuccess(statusLabel, "Copied file summary/preview to clipboard.");
    }

    @FXML
    private void openSelectedFile() {
        SqliteMedicalFileDao.MedicalFileRecord selected = selectedFile();
        if (selected == null) {
            return;
        }
        try {
            String message = previewService.openFile(Session.getCurrentUser(), selected.getFileId());
            NotificationHelper.showSuccess(statusLabel, message);
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, "Open blocked: " + e.getMessage());
        }
    }

    @FXML
    private void clearFilters() {
        searchField.clear();
        uploadedByFilter.clear();
        categoryFilter.getSelectionModel().select("All");
        dateRangeFilter.getSelectionModel().select("Last 30 days");
        loadFiles();
    }

    @FXML
    private void clearPatientFilter() {
        patientIdFilter = "";
        updatePatientFilterChip();
        loadFiles();
    }

    @FXML
    private void showDashboard() {
        appShell.showDashboard(Session.getCurrentUser());
    }

    private void configureAccess() {
        boolean authorized = isAuthorized();
        accessDeniedPane.setVisible(!authorized);
        accessDeniedPane.setManaged(!authorized);
        contentPane.setVisible(authorized);
        contentPane.setManaged(authorized);
    }

    private void configureFilters() {
        categoryFilter.setItems(FXCollections.observableArrayList("All", "LAB_RESULT", "DISCHARGE_SUMMARY", "IMAGING", "PRESCRIPTION", "OTHER"));
        dateRangeFilter.setItems(FXCollections.observableArrayList("Today", "Last 7 days", "Last 30 days", "All"));
        categoryFilter.getSelectionModel().select("All");
        dateRangeFilter.getSelectionModel().select("Last 30 days");
        searchField.textProperty().addListener((observable, oldValue, newValue) -> loadFiles());
        uploadedByFilter.textProperty().addListener((observable, oldValue, newValue) -> loadFiles());
        categoryFilter.valueProperty().addListener((observable, oldValue, newValue) -> loadFiles());
        dateRangeFilter.valueProperty().addListener((observable, oldValue, newValue) -> loadFiles());
    }

    private void configureTable() {
        if (rowNumberColumn != null) {
            rowNumberColumn.setCellValueFactory(cell -> {
                int index = filesTable.getItems() == null ? -1 : filesTable.getItems().indexOf(cell.getValue());
                Number rowNumber = index >= 0 ? index + 1 : null;
                return new ReadOnlyObjectWrapper<>(rowNumber);
            });
        }
        patientIdColumn.setCellValueFactory(new PropertyValueFactory<>("patientId"));
        patientNameColumn.setCellValueFactory(new PropertyValueFactory<>("patientName"));
        originalNameColumn.setCellValueFactory(new PropertyValueFactory<>("originalName"));
        categoryColumn.setCellValueFactory(new PropertyValueFactory<>("fileType"));
        uploadedByColumn.setCellValueFactory(new PropertyValueFactory<>("uploadedBy"));
        uploadedAtColumn.setCellValueFactory(new PropertyValueFactory<>("uploadedAt"));
        fileSizeColumn.setCellValueFactory(new PropertyValueFactory<>("fileSizeText"));
        filesTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> renderDetail(newValue));
    }

    private void configureButtons() {
        boolean canUpload = PermissionHelper.canUploadMedicalFile(Session.getCurrentUser());
        uploadButton.setVisible(canUpload);
        uploadButton.setManaged(canUpload);
        updatePatientFilterChip();
    }

    private void renderDetail(SqliteMedicalFileDao.MedicalFileRecord file) {
        if (file == null) {
            detailTitleLabel.setText("Select a file");
            fileIdLabel.setText("-");
            patientLabel.setText("-");
            originalNameLabel.setText("-");
            storedPathLabel.setText("-");
            categoryLabel.setText("-");
            uploadedByLabel.setText("-");
            uploadedAtLabel.setText("-");
            fileSizeLabel.setText("-");
            summaryArea.setText("");
            notesArea.setText("");
            copySummaryButton.setDisable(true);
            openFileButton.setDisable(true);
            previewTypeLabel.setText("-");
            previewArea.setText("Select a medical file to load a safe local preview.");
            previewImageView.setImage(null);
            previewImageView.setVisible(false);
            previewImageView.setManaged(false);
            return;
        }
        detailTitleLabel.setText(file.getOriginalName());
        fileIdLabel.setText(file.getFileId());
        patientLabel.setText(file.getPatientId() + " | " + file.getPatientName());
        originalNameLabel.setText(file.getOriginalName());
        storedPathLabel.setText(file.getStoredPath());
        categoryLabel.setText(file.getFileType());
        uploadedByLabel.setText(file.getUploadedBy());
        uploadedAtLabel.setText(file.getUploadedAt());
        fileSizeLabel.setText(file.getFileSizeText());
        summaryArea.setText(file.getExtractedSummary() == null ? "" : file.getExtractedSummary());
        notesArea.setText(file.getNotes() == null ? "" : file.getNotes());
        copySummaryButton.setDisable(false);
        openFileButton.setDisable(false);
        loadPreview(file);
        auditView(file);
    }

    private void loadPreview(SqliteMedicalFileDao.MedicalFileRecord file) {
        previewImageView.setImage(null);
        previewImageView.setVisible(false);
        previewImageView.setManaged(false);
        try {
            MedicalFilePreviewService.PreviewResult preview = previewService.loadPreview(Session.getCurrentUser(), file.getFileId());
            previewTypeLabel.setText(preview.getPreviewType());
            previewArea.setText(preview.getPreviewText());
            if (preview.isImage()) {
                Image image = new Image(new java.io.File(preview.getSafePath()).toURI().toString(), 520, 320, true, true);
                previewImageView.setImage(image);
                previewImageView.setVisible(true);
                previewImageView.setManaged(true);
            }
        } catch (Exception e) {
            previewTypeLabel.setText("Unavailable");
            previewArea.setText("Preview unavailable: " + e.getMessage());
            openFileButton.setDisable(true);
        }
    }

    private void auditView(SqliteMedicalFileDao.MedicalFileRecord file) {
        if (file.getFileId().equals(lastViewedFileId)) {
            return;
        }
        lastViewedFileId = file.getFileId();
        try {
            AuditWriteHelper.write(SessionContext.username(), AuditAction.VIEW_MEDICAL_FILE,
                    "patient_id=" + file.getPatientId() + ", file_id=" + file.getFileId());
        } catch (Exception e) {
            System.out.println("SQLite medical file view audit skipped: " + e.getMessage());
        }
    }

    private SqliteMedicalFileDao.MedicalFileRecord selectedFile() {
        SqliteMedicalFileDao.MedicalFileRecord selected = filesTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            NotificationHelper.showError(statusLabel, "Select a medical file first.");
            return null;
        }
        return selected;
    }

    private void updatePatientFilterChip() {
        boolean filtered = patientIdFilter != null && !patientIdFilter.isBlank();
        patientFilterChip.setVisible(filtered);
        patientFilterChip.setManaged(filtered);
        clearPatientFilterButton.setVisible(filtered);
        clearPatientFilterButton.setManaged(filtered);
        patientFilterChip.setText(filtered ? "Patient ID = " + patientIdFilter : "");
    }

    private void selectPendingFile() {
        if (pendingFileId == null || pendingFileId.isBlank() || filesTable == null) {
            return;
        }
        for (SqliteMedicalFileDao.MedicalFileRecord file : files) {
            if (pendingFileId.equals(file.getFileId())) {
                int index = filesTable.getItems() == null ? -1 : filesTable.getItems().indexOf(file);
                SelectionHelper.safeSelectIndex(filesTable, index);
                pendingFileId = "";
                return;
            }
        }
    }

    private boolean isAuthorized() {
        return PermissionHelper.canViewMedicalFiles(Session.getCurrentUser());
    }
}
