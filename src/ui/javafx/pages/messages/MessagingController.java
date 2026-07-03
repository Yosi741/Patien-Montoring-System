package ui.javafx.pages.messages;

import Data_Access_Object.SqliteMessageDao;
import ui.javafx.users.dao.SqliteUserDao;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import ui.javafx.services.MessagingService;
import ui.javafx.AppShell;
import ui.javafx.FxController;
import ui.javafx.helpers.AuditAction;
import ui.javafx.helpers.AuditWriteHelper;
import ui.javafx.helpers.FxFileOpenHelper;
import ui.javafx.helpers.NotificationHelper;
import ui.javafx.helpers.PermissionHelper;
import ui.javafx.helpers.SelectionHelper;
import users.Session;
import users.User;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class MessagingController implements FxController {

    private static final Path DEATH_CERTIFICATE_DIR = Path.of("data", "generated", "death-certificates").toAbsolutePath().normalize();
    private static final Path BIRTH_CERTIFICATE_DIR = Path.of("data", "generated", "birth-certificates").toAbsolutePath().normalize();

    private final MessagingService messagingService = new MessagingService();
    private final SqliteUserDao userDao = new SqliteUserDao();
    private final ObservableList<SqliteMessageDao.MessageRow> inboxRows = FXCollections.observableArrayList();
    private final ObservableList<SqliteMessageDao.MessageRow> sentRows = FXCollections.observableArrayList();
    private final ObservableList<SqliteMessageDao.MessageRow> requestRows = FXCollections.observableArrayList();
    private AppShell appShell;

    @FXML private VBox accessDeniedPane;
    @FXML private VBox contentPane;
    @FXML private TextField searchField;
    @FXML private ComboBox<String> statusFilter;
    @FXML private ComboBox<String> requestFilter;
    @FXML private TableView<SqliteMessageDao.MessageRow> inboxTable;
    @FXML private TableView<SqliteMessageDao.MessageRow> sentTable;
    @FXML private TableView<SqliteMessageDao.MessageRow> requestTable;
    @FXML private TableColumn<SqliteMessageDao.MessageRow, Number> inboxRowNumberColumn;
    @FXML private TableColumn<SqliteMessageDao.MessageRow, Long> inboxIdColumn;
    @FXML private TableColumn<SqliteMessageDao.MessageRow, String> inboxSenderColumn;
    @FXML private TableColumn<SqliteMessageDao.MessageRow, String> inboxSubjectColumn;
    @FXML private TableColumn<SqliteMessageDao.MessageRow, String> inboxPriorityColumn;
    @FXML private TableColumn<SqliteMessageDao.MessageRow, String> inboxStatusColumn;
    @FXML private TableColumn<SqliteMessageDao.MessageRow, String> inboxPatientColumn;
    @FXML private TableColumn<SqliteMessageDao.MessageRow, String> inboxCreatedColumn;
    @FXML private TableColumn<SqliteMessageDao.MessageRow, Number> sentRowNumberColumn;
    @FXML private TableColumn<SqliteMessageDao.MessageRow, Long> sentIdColumn;
    @FXML private TableColumn<SqliteMessageDao.MessageRow, String> sentTargetColumn;
    @FXML private TableColumn<SqliteMessageDao.MessageRow, String> sentSubjectColumn;
    @FXML private TableColumn<SqliteMessageDao.MessageRow, String> sentPriorityColumn;
    @FXML private TableColumn<SqliteMessageDao.MessageRow, String> sentStatusColumn;
    @FXML private TableColumn<SqliteMessageDao.MessageRow, String> sentPatientColumn;
    @FXML private TableColumn<SqliteMessageDao.MessageRow, String> sentCreatedColumn;
    @FXML private TableColumn<SqliteMessageDao.MessageRow, Number> requestRowNumberColumn;
    @FXML private TableColumn<SqliteMessageDao.MessageRow, String> requestTypeColumn;
    @FXML private TableColumn<SqliteMessageDao.MessageRow, String> requestFromColumn;
    @FXML private TableColumn<SqliteMessageDao.MessageRow, String> requestToColumn;
    @FXML private TableColumn<SqliteMessageDao.MessageRow, String> requestPatientColumn;
    @FXML private TableColumn<SqliteMessageDao.MessageRow, String> requestSubjectColumn;
    @FXML private TableColumn<SqliteMessageDao.MessageRow, String> requestPriorityColumn;
    @FXML private TableColumn<SqliteMessageDao.MessageRow, String> requestStatusColumn;
    @FXML private TableColumn<SqliteMessageDao.MessageRow, String> requestCreatedColumn;
    @FXML private ComboBox<String> targetTypeBox;
    @FXML private ComboBox<SqliteUserDao.UserTarget> targetUserBox;
    @FXML private ComboBox<String> targetRoleBox;
    @FXML private TextField targetSectionField;
    @FXML private TextField patientIdField;
    @FXML private TextField subjectField;
    @FXML private ComboBox<String> priorityBox;
    @FXML private TextArea bodyArea;
    @FXML private Label detailTitleLabel;
    @FXML private Label detailMetaLabel;
    @FXML private Label detailSourceLabel;
    @FXML private TextArea detailBodyArea;
    @FXML private FlowPane certificateButtonPane;
    @FXML private Label statusLabel;

    @Override
    public void setAppShell(AppShell appShell) {
        this.appShell = appShell;
        configureAccess();
        configureFilters();
        configureTables();
        configureCompose();
        loadMessages();
    }

    @FXML
    private void loadMessages() {
        if (!PermissionHelper.canViewMessages(Session.getCurrentUser())) {
            return;
        }
        try {
            User user = Session.getCurrentUser();
            Long selectedInboxId = selectedId(inboxTable);
            Long selectedSentId = selectedId(sentTable);
            Long selectedRequestId = selectedId(requestTable);
            var loadedInbox = messagingService.inbox(user, searchField.getText(), statusFilter.getValue());
            var loadedSent = messagingService.sent(user, searchField.getText(), statusFilter.getValue());
            var loadedRequests = messagingService.requests(user, searchField.getText(), requestFilter.getValue());
            SelectionHelper.runWhenTablesStable(() -> {
                SelectionHelper.safeReplaceItems(inboxTable, inboxRows, loadedInbox);
                SelectionHelper.safeReplaceItems(sentTable, sentRows, loadedSent);
                SelectionHelper.safeReplaceItems(requestTable, requestRows, loadedRequests);
                boolean restored = SelectionHelper.safeRestoreSelectionById(inboxTable, selectedInboxId, SqliteMessageDao.MessageRow::getId)
                        || SelectionHelper.safeRestoreSelectionById(sentTable, selectedSentId, SqliteMessageDao.MessageRow::getId)
                        || SelectionHelper.safeRestoreSelectionById(requestTable, selectedRequestId, SqliteMessageDao.MessageRow::getId);
                if (!restored && inboxRows.isEmpty() && sentRows.isEmpty() && requestRows.isEmpty()) {
                    clearDetail();
                }
                NotificationHelper.showInfo(statusLabel, "Messages loaded. Inbox: " + inboxRows.size()
                        + " | Sent: " + sentRows.size() + " | Requests: " + requestRows.size());
            }, inboxTable, sentTable, requestTable);
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, "Could not load messages: " + e.getMessage());
        }
    }

    @FXML
    private void sendMessage() {
        try {
            if (!PermissionHelper.canComposeMessage(Session.getCurrentUser())) {
                throw new SecurityException("This role cannot compose messages.");
            }
            SqliteMessageDao.MessageWriteRecord record = buildRecord();
            long id = messagingService.sendMessage(Session.getCurrentUser(), record);
            clearCompose();
            loadMessages();
            NotificationHelper.showSuccess(statusLabel, "Message sent. ID: " + id);
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
        }
    }

    @FXML
    private void markRead() {
        SqliteMessageDao.MessageRow row = selectedMessage();
        if (row == null) {
            return;
        }
        try {
            messagingService.markRead(Session.getCurrentUser(), row.getId());
            loadMessages();
            NotificationHelper.showSuccess(statusLabel, "Message marked read.");
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, "Could not mark read: " + e.getMessage());
        }
    }

    @FXML
    private void archiveMessage() {
        SqliteMessageDao.MessageRow row = selectedMessage();
        if (row == null) {
            return;
        }
        try {
            messagingService.archive(Session.getCurrentUser(), row.getId());
            loadMessages();
            clearDetail();
            NotificationHelper.showSuccess(statusLabel, "Message archived.");
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, "Could not archive: " + e.getMessage());
        }
    }

    @FXML
    private void openLinkedPatient() {
        SqliteMessageDao.MessageRow row = selectedMessage();
        if (row != null && row.getPatientId() != null && !row.getPatientId().isBlank()) {
            appShell.showPatientDetail(row.getPatientId());
        }
    }

    @FXML
    private void openSourceRecord() {
        SqliteMessageDao.MessageRow row = selectedMessage();
        CertificateMetadata metadata = metadataFrom(row);
        if (metadata == null) {
            NotificationHelper.showInfo(statusLabel, "This message does not include certificate source metadata.");
            return;
        }
        appShell.showMessageCertificateSourceRecord(metadata.sourceType, metadata.sourceId);
    }

    @FXML
    private void openCertificate() {
        SqliteMessageDao.MessageRow row = selectedMessage();
        CertificateMetadata metadata = metadataFrom(row);
        if (metadata == null) {
            NotificationHelper.showInfo(statusLabel, "This message does not include certificate metadata.");
            return;
        }
        try {
            Path certificate = validateCertificatePath(metadata);
            FxFileOpenHelper.open(certificate);
            AuditWriteHelper.write(Session.getUsername(), AuditAction.OPEN_CERTIFICATE_FROM_MESSAGE,
                    "source=" + metadata.sourceType + ":" + metadata.sourceId + ", path=" + certificate);
            NotificationHelper.showSuccess(statusLabel, "Opening certificate with the local desktop handler.");
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, certificateError(e));
        }
    }

    @FXML
    private void showDashboard() {
        appShell.showDashboard(Session.getCurrentUser());
    }

    private void configureAccess() {
        boolean allowed = PermissionHelper.canViewMessages(Session.getCurrentUser());
        accessDeniedPane.setVisible(!allowed);
        accessDeniedPane.setManaged(!allowed);
        contentPane.setVisible(allowed);
        contentPane.setManaged(allowed);
    }

    private void configureFilters() {
        statusFilter.setItems(FXCollections.observableArrayList("All", "SENT", "READ", "ARCHIVED"));
        requestFilter.setItems(FXCollections.observableArrayList("All Requests", "Pending", "Read", "Archived", "High Priority"));
        statusFilter.getSelectionModel().select("All");
        requestFilter.getSelectionModel().select("All Requests");
        searchField.textProperty().addListener((obs, old, value) -> loadMessages());
        statusFilter.valueProperty().addListener((obs, old, value) -> loadMessages());
        requestFilter.valueProperty().addListener((obs, old, value) -> loadMessages());
    }

    private void configureTables() {
        if (inboxRowNumberColumn != null) {
            inboxRowNumberColumn.setCellValueFactory(cell -> {
                int index = inboxTable.getItems() == null ? -1 : inboxTable.getItems().indexOf(cell.getValue());
                Number rowNumber = index >= 0 ? index + 1 : null;
                return new ReadOnlyObjectWrapper<>(rowNumber);
            });
        }
        inboxIdColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        inboxSenderColumn.setCellValueFactory(new PropertyValueFactory<>("senderUsername"));
        inboxSubjectColumn.setCellValueFactory(new PropertyValueFactory<>("subject"));
        inboxPriorityColumn.setCellValueFactory(new PropertyValueFactory<>("priority"));
        inboxStatusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        inboxPatientColumn.setCellValueFactory(new PropertyValueFactory<>("patientId"));
        inboxCreatedColumn.setCellValueFactory(new PropertyValueFactory<>("createdAt"));
        sentIdColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        sentTargetColumn.setCellValueFactory(new PropertyValueFactory<>("targetSummary"));
        sentSubjectColumn.setCellValueFactory(new PropertyValueFactory<>("subject"));
        sentPriorityColumn.setCellValueFactory(new PropertyValueFactory<>("priority"));
        sentStatusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        sentPatientColumn.setCellValueFactory(new PropertyValueFactory<>("patientId"));
        if (sentRowNumberColumn != null) {
            sentRowNumberColumn.setCellValueFactory(cell -> {
                int index = sentTable.getItems() == null ? -1 : sentTable.getItems().indexOf(cell.getValue());
                Number rowNumber = index >= 0 ? index + 1 : null;
                return new ReadOnlyObjectWrapper<>(rowNumber);
            });
        }
        sentCreatedColumn.setCellValueFactory(new PropertyValueFactory<>("createdAt"));
        if (requestRowNumberColumn != null) {
            requestRowNumberColumn.setCellValueFactory(cell -> {
                int index = requestTable.getItems() == null ? -1 : requestTable.getItems().indexOf(cell.getValue());
                Number rowNumber = index >= 0 ? index + 1 : null;
                return new ReadOnlyObjectWrapper<>(rowNumber);
            });
        }
        requestTypeColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(messagingService.requestType(cell.getValue())));
        requestFromColumn.setCellValueFactory(new PropertyValueFactory<>("senderUsername"));
        requestToColumn.setCellValueFactory(new PropertyValueFactory<>("targetSummary"));
        requestPatientColumn.setCellValueFactory(new PropertyValueFactory<>("patientId"));
        requestSubjectColumn.setCellValueFactory(new PropertyValueFactory<>("subject"));
        requestPriorityColumn.setCellValueFactory(new PropertyValueFactory<>("priority"));
        requestStatusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        requestCreatedColumn.setCellValueFactory(new PropertyValueFactory<>("createdAt"));
        inboxTable.getSelectionModel().selectedItemProperty().addListener((obs, old, row) -> {
            if (row != null) {
                SelectionHelper.safeClearTableSelection(sentTable);
                SelectionHelper.safeClearTableSelection(requestTable);
                showDetail(row);
            }
        });
        sentTable.getSelectionModel().selectedItemProperty().addListener((obs, old, row) -> {
            if (row != null) {
                SelectionHelper.safeClearTableSelection(inboxTable);
                SelectionHelper.safeClearTableSelection(requestTable);
                showDetail(row);
            }
        });
        requestTable.getSelectionModel().selectedItemProperty().addListener((obs, old, row) -> {
            if (row != null) {
                SelectionHelper.safeClearTableSelection(inboxTable);
                SelectionHelper.safeClearTableSelection(sentTable);
                showDetail(row);
            }
        });
    }

    private void configureCompose() {
        targetTypeBox.setItems(FXCollections.observableArrayList("User", "Role", "Section"));
        targetRoleBox.setItems(FXCollections.observableArrayList("ADMIN", "DOCTOR", "NURSE", "STAFF"));
        priorityBox.setItems(FXCollections.observableArrayList("NORMAL", "HIGH", "URGENT"));
        targetTypeBox.getSelectionModel().select("User");
        targetRoleBox.getSelectionModel().select("NURSE");
        priorityBox.getSelectionModel().select("NORMAL");
        targetTypeBox.setVisible(false);
        targetTypeBox.setManaged(false);
        targetRoleBox.setVisible(false);
        targetRoleBox.setManaged(false);
        targetSectionField.setVisible(false);
        targetSectionField.setManaged(false);
        reloadUsers();
    }

    private void reloadUsers() {
        try {
            targetUserBox.setItems(FXCollections.observableArrayList(
                    userDao.findMessageTargetsExcept(Session.getUsername())));
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, "Could not load user targets: " + e.getMessage());
        }
        if (!targetUserBox.getItems().isEmpty()) {
            targetUserBox.getSelectionModel().selectFirst();
        } else {
            targetUserBox.getSelectionModel().clearSelection();
        }
    }

    private void updateTargetControls() {
        String type = targetTypeBox.getValue();
        targetUserBox.setDisable(!"User".equals(type));
        targetRoleBox.setDisable(!"Role".equals(type));
        targetSectionField.setDisable(!"Section".equals(type));
    }

    private SqliteMessageDao.MessageWriteRecord buildRecord() {
        SqliteUserDao.UserTarget selected = targetUserBox.getValue();
        if (selected == null || selected.getUsername().isBlank()) {
            throw new IllegalArgumentException("Select an exact user account recipient before sending.");
        }
        if (selected.getUsername().equalsIgnoreCase(Session.getUsername())) {
            throw new IllegalArgumentException("You cannot send a message to yourself.");
        }
        return new SqliteMessageDao.MessageWriteRecord(
                Session.getUsername(),
                selected.getUsername(),
                "",
                "",
                patientIdField.getText(),
                subjectField.getText(),
                bodyArea.getText(),
                priorityBox.getValue()
        );
    }

    private SqliteMessageDao.MessageRow selectedMessage() {
        SqliteMessageDao.MessageRow row = inboxTable.getSelectionModel().getSelectedItem();
        if (row != null) {
            return row;
        }
        row = sentTable.getSelectionModel().getSelectedItem();
        return row == null ? requestTable.getSelectionModel().getSelectedItem() : row;
    }

    private void showDetail(SqliteMessageDao.MessageRow row) {
        if (row == null) {
            return;
        }
        detailTitleLabel.setText(row.getSubject());
        detailMetaLabel.setText("From " + row.getSenderUsername() + " | " + row.getTargetSummary()
                + " | " + messageCategory(row) + " | " + row.getPriority()
                + " | " + row.getStatus() + " | " + row.getCreatedAt());
        detailBodyArea.setText(row.getBody());
        CertificateMetadata metadata = metadataFrom(row);
        if (metadata == null) {
            detailSourceLabel.setText("Certificate source: none");
            certificateButtonPane.setVisible(false);
            certificateButtonPane.setManaged(false);
        } else {
            detailSourceLabel.setText("Certificate source: " + metadata.certificateType
                    + " | Source ID: " + metadata.sourceId
                    + " | Patient: " + emptyTo(metadata.patientId, "-")
                    + " | Newborn: " + emptyTo(metadata.newbornId, "-"));
            certificateButtonPane.setVisible(true);
            certificateButtonPane.setManaged(true);
        }
    }

    private void clearDetail() {
        detailTitleLabel.setText("Select a message");
        detailMetaLabel.setText("-");
        detailSourceLabel.setText("Certificate source: none");
        detailBodyArea.clear();
        certificateButtonPane.setVisible(false);
        certificateButtonPane.setManaged(false);
    }

    private void clearCompose() {
        patientIdField.clear();
        subjectField.clear();
        bodyArea.clear();
        priorityBox.getSelectionModel().select("NORMAL");
    }

    private CertificateMetadata metadataFrom(SqliteMessageDao.MessageRow row) {
        if (row == null || row.getBody() == null || !row.getBody().contains("[SPMS_CERTIFICATE]")) {
            return null;
        }
        boolean inBlock = false;
        Map<String, String> values = new HashMap<>();
        for (String line : row.getBody().split("\\R")) {
            String clean = line.trim();
            if ("[SPMS_CERTIFICATE]".equals(clean)) {
                inBlock = true;
                continue;
            }
            if ("[/SPMS_CERTIFICATE]".equals(clean)) {
                break;
            }
            if (inBlock) {
                int equals = clean.indexOf('=');
                if (equals > 0) {
                    values.put(clean.substring(0, equals).trim(), clean.substring(equals + 1).trim());
                }
            }
        }
        String sourceType = emptyTo(values.get("source_type"), "");
        String sourceId = emptyTo(values.get("source_id"), "");
        if (sourceType.isBlank() || sourceId.isBlank() || "-".equals(sourceId)) {
            return null;
        }
        return new CertificateMetadata(
                emptyTo(values.get("certificate_type"), "CERTIFICATE"),
                sourceType,
                sourceId,
                emptyTo(values.get("patient_id"), ""),
                emptyTo(values.get("newborn_id"), ""),
                emptyTo(values.get("certificate_path"), "")
        );
    }

    private String emptyTo(String value, String fallback) {
        return value == null || value.isBlank() || "-".equals(value) ? fallback : value;
    }

    private String messageCategory(SqliteMessageDao.MessageRow row) {
        return messagingService.isRequestMessage(row) ? messagingService.requestType(row) : "MESSAGE";
    }

    private Long selectedId(TableView<SqliteMessageDao.MessageRow> table) {
        SqliteMessageDao.MessageRow row = table == null ? null : table.getSelectionModel().getSelectedItem();
        return row == null ? null : row.getId();
    }

    private Path validateCertificatePath(CertificateMetadata metadata) {
        String rawPath = metadata == null ? "" : metadata.certificatePath;
        if (rawPath == null || rawPath.isBlank() || "-".equals(rawPath.trim())) {
            throw new IllegalArgumentException("Certificate file was not found.");
        }
        Path path = Path.of(rawPath.trim()).toAbsolutePath().normalize();
        if (!path.startsWith(DEATH_CERTIFICATE_DIR) && !path.startsWith(BIRTH_CERTIFICATE_DIR)) {
            throw new SecurityException("Certificate path is outside the generated certificate folders.");
        }
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("Certificate file was not found.");
        }
        return path;
    }

    private String certificateError(Exception e) {
        String message = e == null ? "" : e.getMessage();
        if (message == null || message.isBlank() || message.toLowerCase().contains("does not exist")) {
            return "Certificate file was not found.";
        }
        return message;
    }

    private static class CertificateMetadata {
        private final String certificateType;
        private final String sourceType;
        private final String sourceId;
        private final String patientId;
        private final String newbornId;
        private final String certificatePath;

        private CertificateMetadata(String certificateType, String sourceType, String sourceId,
                                    String patientId, String newbornId, String certificatePath) {
            this.certificateType = certificateType;
            this.sourceType = sourceType;
            this.sourceId = sourceId;
            this.patientId = patientId;
            this.newbornId = newbornId;
            this.certificatePath = certificatePath;
        }
    }
}
