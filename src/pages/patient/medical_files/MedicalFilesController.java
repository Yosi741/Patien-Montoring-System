package pages.patient.medical_files;

import app.core.AppShell;
import app.contracts.AppController;
import app.helpers.DialogHelper;
import app.helpers.PermissionHelper;
import app.helpers.SelectionHelper;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ContentDisplay;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import javafx.stage.Window;
import pages.notification.NotificationHelper;
import pages.patient.dao.SqliteMedicalFileDao;
import pages.patient.services.MedicalFilePreviewService;
import users.Session;

import java.io.File;
import java.util.Locale;

public class MedicalFilesController implements AppController {

    private final SqliteMedicalFileDao fileDao = new SqliteMedicalFileDao();
    private final MedicalFilePreviewService previewService = new MedicalFilePreviewService();
    private final javafx.collections.ObservableList<SqliteMedicalFileDao.MedicalFileRecord> files =
            javafx.collections.FXCollections.observableArrayList();

    private AppShell appShell;
    private String patientIdFilter = "";
    private String pendingFileId = "";

    @FXML private VBox accessDeniedPane;
    @FXML private VBox contentPane;
    @FXML private Label patientFilterChip;
    @FXML private Button clearPatientFilterButton;
    @FXML private Button uploadButton;
    @FXML private TextField searchField;
    @FXML private ComboBox<String> dateRangeFilter;
    @FXML private TextField uploadedByFilter;
    @FXML private TableView<SqliteMedicalFileDao.MedicalFileRecord> filesTable;
    @FXML private TableColumn<SqliteMedicalFileDao.MedicalFileRecord, String> patientIdColumn;
    @FXML private TableColumn<SqliteMedicalFileDao.MedicalFileRecord, String> patientNameColumn;
    @FXML private TableColumn<SqliteMedicalFileDao.MedicalFileRecord, String> categoryColumn;
    @FXML private TableColumn<SqliteMedicalFileDao.MedicalFileRecord, String> uploadedByColumn;
    @FXML private TableColumn<SqliteMedicalFileDao.MedicalFileRecord, String> uploadedAtColumn;
    @FXML private TableColumn<SqliteMedicalFileDao.MedicalFileRecord, SqliteMedicalFileDao.MedicalFileRecord> actionsColumn;
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



    @FXML
    private void loadFiles() {
        if (!isAuthorized()) {
            NotificationHelper.showError(statusLabel, "Access denied.");
            return;
        }
        try {
            var loadedFiles = fileDao.findFiles(
                    safeText(searchField),
                    normalizedCategory(),
                    normalizedDateRange(),
                    safeText(uploadedByFilter),
                    patientIdFilter);
            SelectionHelper.runWhenTableStable(filesTable, () -> {
                SelectionHelper.safeReplaceItems(filesTable, files, loadedFiles);
                selectPendingFile();
                NotificationHelper.showInfo(statusLabel, "Medical records loaded from the local database: " + files.size());
            });
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, "Could not load medical records: " + e.getMessage());
        }
    }

    @FXML
    private void uploadFile() {
        if (!PermissionHelper.canUploadMedicalFile(Session.getCurrentUser())) {
            NotificationHelper.showError(statusLabel, "Access denied. Admin or Doctor role is required.");
            return;
        }
        try {
            boolean saved = MedicalFileUploadController.showDialog(
                    filesTable == null ? null : filesTable.getScene().getWindow(),
                    Session.getCurrentUser(),
                    patientIdFilter);
            if (saved) {
                loadFiles();
                NotificationHelper.showSuccess(statusLabel, "Medical record uploaded and added to the list.");
            }
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
        }
    }

    @FXML
    private void clearFilters() {
        if (searchField != null) {
            searchField.clear();
        }
        if (uploadedByFilter != null) {
            uploadedByFilter.clear();
        }
        if (dateRangeFilter != null) {
            dateRangeFilter.getSelectionModel().select("Last 30 days");
        }
        loadFiles();
    }

    @FXML
    private void clearPatientFilter() {
        patientIdFilter = "";
        updatePatientFilterChip();
        loadFiles();
    }

    private void configureAccess() {
        boolean authorized = isAuthorized();
        if (accessDeniedPane != null) {
            accessDeniedPane.setVisible(!authorized);
            accessDeniedPane.setManaged(!authorized);
        }
        if (contentPane != null) {
            contentPane.setVisible(authorized);
            contentPane.setManaged(authorized);
        }
    }

    private void configureFilters() {
        if (dateRangeFilter != null) {
            dateRangeFilter.setItems(javafx.collections.FXCollections.observableArrayList(
                    "Last 30 days", "Today", "Last 7 days", "All"));
            dateRangeFilter.getSelectionModel().select("Last 30 days");
            dateRangeFilter.valueProperty().addListener((observable, oldValue, newValue) -> loadFiles());
        }
        if (searchField != null) {
            searchField.textProperty().addListener((observable, oldValue, newValue) -> loadFiles());
        }
        if (uploadedByFilter != null) {
            uploadedByFilter.textProperty().addListener((observable, oldValue, newValue) -> loadFiles());
        }
    }

    private void configureTable() {
        if (filesTable != null) {
            filesTable.setItems(files);
            filesTable.setFixedCellSize(58);
        }
        if (patientNameColumn != null) {
            patientNameColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue() == null
                    ? ""
                    : cell.getValue().getPatientName()));
            patientNameColumn.setCellFactory(column -> centeredTextCell("medical-records-centered-cell"));
        }
        if (patientIdColumn != null) {
            patientIdColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue() == null
                    ? ""
                    : cell.getValue().getPatientId()));
            patientIdColumn.setCellFactory(column -> centeredTextCell("medical-records-centered-cell"));
        }
        if (categoryColumn != null) {
            categoryColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue() == null
                    ? ""
                    : cell.getValue().getFileType()));
            categoryColumn.setCellFactory(column -> new TableCell<>() {
                private final Label badge = new Label();

                {
                    badge.getStyleClass().addAll("badge-pill", "record-type-badge");
                }

                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    getStyleClass().remove("medical-records-type-cell");
                    if (empty || item == null || item.isBlank()) {
                        setGraphic(null);
                        setText(null);
                        return;
                    }
                    getStyleClass().add("medical-records-type-cell");
                    setAlignment(Pos.CENTER);
                    setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                    badge.setText(formatType(item));
                    setGraphic(badge);
                    setText(null);
                }
            });
        }
        if (uploadedByColumn != null) {
            uploadedByColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue() == null
                    ? ""
                    : cell.getValue().getUploadedBy()));
            uploadedByColumn.setCellFactory(column -> centeredTextCell("medical-records-centered-cell"));
        }
        if (uploadedAtColumn != null) {
            uploadedAtColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue() == null
                    ? ""
                    : cell.getValue().getUploadedAt()));
            uploadedAtColumn.setCellFactory(column -> centeredTextCell("medical-records-date-cell"));
        }
        if (actionsColumn != null) {
            actionsColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
            actionsColumn.setCellFactory(column -> new TableCell<>() {
                private final Button viewButton = createActionButton(
                        "record-action-view",
                        "M1 8s2.5-4 7-4 7 4 7 4-2.5 4-7 4-7-4-7-4zm7 3a3 3 0 1 0 0-6a3 3 0 0 0 0 6z",
                        "View medical record");
                private final Button downloadButton = createActionButton(
                        "record-action-download",
                        "M7 1h2v7.2l2.5-2.5l1.5 1.5L8 13L2.9 7.2l1.5-1.5L7 8.2V1zm-4 12h10v2H3z",
                        "Open stored medical record");
                private final Button deleteButton = createActionButton(
                        "record-action-delete",
                        "M5 1h6l1 2h3v2H1V3h3l1-2zm-1 5h2v7H4V6zm5 0h2v7H9V6z",
                        "Delete medical record");
                private final HBox actionsBox = new HBox(8, viewButton, downloadButton, deleteButton);

                {
                    actionsBox.setAlignment(Pos.CENTER);
                    viewButton.setOnAction(event -> {
                        SqliteMedicalFileDao.MedicalFileRecord record = getCurrentTableRow();
                        if (record != null) {
                            showRecordDetails(record);
                        }
                    });
                    downloadButton.setOnAction(event -> {
                        SqliteMedicalFileDao.MedicalFileRecord record = getCurrentTableRow();
                        if (record != null) {
                            openRecordFile(record);
                        }
                    });
                    deleteButton.setOnAction(event -> {
                        SqliteMedicalFileDao.MedicalFileRecord record = getCurrentTableRow();
                        if (record != null) {
                            deleteRecord(record);
                        }
                    });
                }

                @Override
                protected void updateItem(SqliteMedicalFileDao.MedicalFileRecord item, boolean empty) {
                    super.updateItem(item, empty);
                    getStyleClass().remove("table-centered-cell");
                    if (empty || item == null) {
                        setGraphic(null);
                        setText(null);
                    } else {
                        deleteButton.setDisable(!PermissionHelper.canDeleteMedicalFile(Session.getCurrentUser()));
                        getStyleClass().add("table-centered-cell");
                        setAlignment(Pos.CENTER);
                        setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                        setGraphic(actionsBox);
                        setText(null);
                    }
                }

                private SqliteMedicalFileDao.MedicalFileRecord getCurrentTableRow() {
                    return getIndex() >= 0 && getIndex() < filesTable.getItems().size()
                            ? filesTable.getItems().get(getIndex())
                            : null;
                }
            });
        }
        if (filesTable != null) {
            Label placeholder = new Label("No medical records found.");
            placeholder.getStyleClass().add("records-placeholder");
            filesTable.setPlaceholder(placeholder);
            filesTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
            filesTable.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2) {
                    SqliteMedicalFileDao.MedicalFileRecord selected = filesTable.getSelectionModel().getSelectedItem();
                    if (selected != null) {
                        showRecordDetails(selected);
                    }
                }
            });
        }
    }

    private TableCell<SqliteMedicalFileDao.MedicalFileRecord, String> centeredTextCell(String styleClass) {
        return new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().remove(styleClass);
                setAlignment(Pos.CENTER);
                if (empty) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                getStyleClass().add(styleClass);
                setText(blankTo(item, "\u2014"));
                setGraphic(null);
            }
        };
    }

    private void configureButtons() {
        boolean canUpload = PermissionHelper.canUploadMedicalFile(Session.getCurrentUser());
        if (uploadButton != null) {
            uploadButton.setVisible(canUpload);
            uploadButton.setManaged(canUpload);
        }
        updatePatientFilterChip();
    }

    private void showRecordDetails(SqliteMedicalFileDao.MedicalFileRecord file) {
        if (file == null || filesTable == null || filesTable.getScene() == null) {
            return;
        }
        try {
            MedicalFilePreviewService.PreviewResult preview = previewService.loadPreview(Session.getCurrentUser(), file.getFileId());
            Dialog<Void> dialog = new Dialog<>();
            dialog.setTitle("Medical Record Details");
            app.helpers.DialogThemeHelper.apply(dialog);
            Window owner = filesTable.getScene().getWindow();
            if (owner != null) {
                dialog.initOwner(owner);
            }

            dialog.setResizable(true);
            dialog.getDialogPane().setPrefSize(700, 720);
            dialog.getDialogPane().setMinSize(620, 560);

            VBox detailsContent = new VBox(14);
            detailsContent.setPadding(new Insets(8, 4, 8, 4));
            detailsContent.getStyleClass().add("record-detail-dialog");

            Label title = new Label(file.getOriginalName());
            title.getStyleClass().add("section-title");

            Label patient = detailLabel("Patient", file.getPatientName());
            Label patientId = detailLabel("Patient ID", file.getPatientId());
            Label originalName = detailLabel("Original Filename", file.getOriginalName());
            Label category = detailLabel("Type / Category", formatType(file.getFileType()));
            Label uploadedBy = detailLabel("Uploaded By", file.getUploadedBy());
            Label uploadedAt = detailLabel("Uploaded Date", file.getUploadedAt());

            TextArea summaryArea = detailArea(file.getExtractedSummary(), 5);
            TextArea notesArea = detailArea(file.getNotes(), 3);
            TextArea previewArea = detailArea(preview.getPreviewText(), 8);

            detailsContent.getChildren().addAll(
                    title,
                    patient,
                    patientId,
                    originalName,
                    category,
                    uploadedBy,
                    uploadedAt,
                    sectionLabel("Extracted Summary"),
                    summaryArea,
                    sectionLabel("Notes"),
                    notesArea,
                    sectionLabel("Safe Preview"),
                    previewArea
            );

            if (preview.isImage()) {
                ImageView previewImageView = new ImageView();
                previewImageView.setFitWidth(520);
                previewImageView.setFitHeight(320);
                previewImageView.setPreserveRatio(true);
                previewImageView.setSmooth(true);
                previewImageView.setImage(new Image(new File(preview.getSafePath()).toURI().toString(), 520, 300, true, true));
                detailsContent.getChildren().add(previewImageView);
            }

            ScrollPane scrollPane = new ScrollPane(detailsContent);
            scrollPane.setFitToWidth(true);
            scrollPane.setPannable(true);
            scrollPane.getStyleClass().add("scroll-clear");

            Button copyButton = new Button("Copy Summary");
            copyButton.getStyleClass().add("secondary-button");
            copyButton.setOnAction(event -> copySummaryToClipboard(file, preview.getPreviewText()));

            Button openButton = new Button("Open File");
            openButton.getStyleClass().add("primary-button");
            openButton.setOnAction(event -> openRecordFile(file));

            Button closeButton = new Button("Close");
            closeButton.getStyleClass().add("secondary-button");
            closeButton.setCancelButton(true);
            closeButton.setOnAction(event -> dialog.close());

            HBox footer = new HBox(10, copyButton, new Region(), openButton, closeButton);
            footer.setAlignment(Pos.CENTER_RIGHT);
            HBox.setHgrow(footer.getChildren().get(1), Priority.ALWAYS);
            footer.setPadding(new Insets(14, 4, 4, 4));

            BorderPane layout = new BorderPane();
            layout.setCenter(scrollPane);
            layout.setBottom(footer);
            layout.getStyleClass().add("record-detail-dialog");

            dialog.getDialogPane().setContent(layout);
            dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CLOSE);
            Node hiddenCloseButton = dialog.getDialogPane().lookupButton(ButtonType.CLOSE);
            if (hiddenCloseButton != null) {
                hiddenCloseButton.setVisible(false);
                hiddenCloseButton.setManaged(false);
            }
            closeButton.setOnAction(event -> {
                dialog.setResult(null);
                dialog.close();
            });
            dialog.setOnCloseRequest(event -> dialog.setResult(null));
            dialog.getDialogPane().setOnKeyPressed(event -> {
                if (event.getCode() == KeyCode.ESCAPE) {
                    dialog.setResult(null);
                    dialog.close();
                    event.consume();
                }
            });

            dialog.showAndWait();
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, "Could not open medical record details: " + e.getMessage());
        }
    }

    private void openRecordFile(SqliteMedicalFileDao.MedicalFileRecord file) {
        try {
            String message = previewService.openFile(Session.getCurrentUser(), file.getFileId());
            NotificationHelper.showSuccess(statusLabel, message);
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, "The stored file could not be opened.");
        }
    }

    private void deleteRecord(SqliteMedicalFileDao.MedicalFileRecord file) {
        if (file == null) {
            return;
        }
        if (!PermissionHelper.canDeleteMedicalFile(Session.getCurrentUser())) {
            NotificationHelper.showError(statusLabel, "Only Admin users can delete medical records.");
            return;
        }
        String summary = file.getPatientName() + " | " + formatType(file.getFileType()) + " | " + blankTo(file.getUploadedAt(), "-");
        String message = "Delete medical record?\n\n" + summary;
        if (!DialogHelper.confirm("Delete medical record?", message)) {
            return;
        }
        try {
            if (!fileDao.deleteByFileId(file.getFileId())) {
                throw new IllegalArgumentException("Medical record could not be deleted.");
            }
            loadFiles();
            NotificationHelper.showSuccess(statusLabel, "Medical record deleted.");
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, "Could not delete medical record: " + e.getMessage());
        }
    }

    private void copySummaryToClipboard(SqliteMedicalFileDao.MedicalFileRecord file, String previewText) {
        String summary = file == null ? "" : file.getExtractedSummary();
        if (summary == null || summary.isBlank()) {
            summary = previewText;
        }
        if (summary == null || summary.isBlank()) {
            NotificationHelper.showError(statusLabel, "No summary or preview text is available to copy.");
            return;
        }
        ClipboardContent content = new ClipboardContent();
        content.putString(summary);
        Clipboard.getSystemClipboard().setContent(content);
        NotificationHelper.showSuccess(statusLabel, "Copied file summary/preview to clipboard.");
    }

    private Label detailLabel(String name, String value) {
        Label label = new Label(name + ": " + blankTo(value, "-"));
        label.getStyleClass().add("body-text");
        label.setWrapText(true);
        return label;
    }

    private Label sectionLabel(String title) {
        Label label = new Label(title);
        label.getStyleClass().add("field-label");
        return label;
    }

    private TextArea detailArea(String text, int rows) {
        TextArea area = new TextArea(blankTo(text, ""));
        area.setEditable(false);
        area.setWrapText(true);
        area.setPrefRowCount(rows);
        area.getStyleClass().add("control-input");
        return area;
    }

    private void updatePatientFilterChip() {
        boolean filtered = patientIdFilter != null && !patientIdFilter.isBlank();
        if (patientFilterChip != null) {
            patientFilterChip.setVisible(filtered);
            patientFilterChip.setManaged(filtered);
            patientFilterChip.setText(filtered ? "Patient ID = " + patientIdFilter : "");
        }
        if (clearPatientFilterButton != null) {
            clearPatientFilterButton.setVisible(filtered);
            clearPatientFilterButton.setManaged(filtered);
        }
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

    private String safeText(TextField field) {
        return field == null || field.getText() == null ? "" : field.getText().trim();
    }

    private String normalizedCategory() {
        return "All";
    }

    private String normalizedDateRange() {
        String value = dateRangeFilter == null ? "" : dateRangeFilter.getValue();
        return value == null || value.isBlank() ? "Last 30 days" : value;
    }

    private String formatType(String value) {
        if (value == null || value.isBlank()) {
            return "Other";
        }
        String[] tokens = value.toLowerCase(Locale.ROOT).split("_");
        StringBuilder builder = new StringBuilder();
        for (String token : tokens) {
            if (token.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(token.charAt(0))).append(token.substring(1));
        }
        return builder.isEmpty() ? value : builder.toString();
    }

    private String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private Button createActionButton(String variantClass, String svgPath, String accessibleText) {
        Button button = new Button();
        button.getStyleClass().addAll("record-action-button", variantClass);
        button.setFocusTraversable(false);
        button.setAccessibleText(accessibleText);

        SVGPath icon = new SVGPath();
        icon.setContent(svgPath);
        icon.getStyleClass().add("record-action-icon");

        button.setGraphic(icon);
        button.setText(null);
        return button;
    }
}
