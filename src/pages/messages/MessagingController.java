package pages.messages;

import app.core.AppShell;
import app.contracts.AppController;
import app.helpers.FxFileOpenHelper;
import app.helpers.PermissionHelper;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import pages.notification.NotificationHelper;
import pages.user.User;
import pages.user.dao.SqliteUserDao;
import users.Session;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class MessagingController implements AppController {

    private static final Path DEATH_CERTIFICATE_DIR = Path.of("data", "generated", "death-certificates")
            .toAbsolutePath()
            .normalize();
    private static final Path BIRTH_CERTIFICATE_DIR = Path.of("data", "generated", "birth-certificates")
            .toAbsolutePath()
            .normalize();
    private static final DateTimeFormatter STORAGE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter SAME_DAY_TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);
    private static final DateTimeFormatter OTHER_DAY_TIME = DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH);

    private enum MailboxView {
        INBOX,
        SENT,
        REQUESTS
    }

    private final MessagingService messagingService = new MessagingService();
    private final SqliteUserDao userDao = new SqliteUserDao();
    private final ObservableList<SqliteMessageDao.MessageRow> inboxRows = FXCollections.observableArrayList();
    private final ObservableList<SqliteMessageDao.MessageRow> sentRows = FXCollections.observableArrayList();
    private final ObservableList<SqliteMessageDao.MessageRow> requestRows = FXCollections.observableArrayList();
    private final ObservableList<SqliteMessageDao.MessageRow> visibleRows = FXCollections.observableArrayList();
    private final Map<String, String> displayNameCache = new HashMap<>();

    private AppShell appShell;
    private MailboxView currentView = MailboxView.INBOX;

    @FXML private VBox accessDeniedPane;
    @FXML private VBox contentPane;
    @FXML private TextField searchField;
    @FXML private ComboBox<String> statusFilter;
    @FXML private ComboBox<String> requestStatusFilter;
    @FXML private Button inboxFilterButton;
    @FXML private Button sentFilterButton;
    @FXML private Button requestFilterButton;
    @FXML private ListView<SqliteMessageDao.MessageRow> messageListView;
    @FXML private Label statusLabel;

    @FXML private StackPane detailOverlay;
    @FXML private Label detailDialogTitleLabel;
    @FXML private Label detailSenderValueLabel;
    @FXML private Label detailRecipientValueLabel;
    @FXML private Label detailCreatedValueLabel;
    @FXML private Label detailPatientValueLabel;
    @FXML private Label detailPriorityValueLabel;
    @FXML private Label detailStatusValueLabel;
    @FXML private Label detailLinkedItemLabel;
    @FXML private TextArea detailBodyArea;
    @FXML private Button detailOpenPatientButton;
    @FXML private Button detailMarkReadButton;
    @FXML private Button detailArchiveButton;
    @FXML private Button detailOpenSourceButton;
    @FXML private Button detailOpenFileButton;
    @FXML private javafx.scene.layout.FlowPane detailLinkedActionsPane;

    @FXML private StackPane composeOverlay;
    @FXML private ComboBox<SqliteUserDao.UserTarget> targetUserBox;
    @FXML private TextField patientIdField;
    @FXML private ComboBox<String> priorityBox;
    @FXML private TextField subjectField;
    @FXML private TextArea bodyArea;

    @Override
    public void setAppShell(AppShell appShell) {
        this.appShell = appShell;
        configureAccess();
        configureFilters();
        configureMessageList();
        configureCompose();
        updateFilterPills();
        loadMessages();
    }

    @FXML
    private void loadMessages() {
        if (!PermissionHelper.canViewMessages(Session.getCurrentUser())) {
            return;
        }
        try {
            User user = Session.getCurrentUser();
            Long selectedId = selectedMessageId();
            inboxRows.setAll(messagingService.inbox(user, searchField.getText(), normalizedStatusFilter()));
            sentRows.setAll(messagingService.sent(user, searchField.getText(), normalizedStatusFilter()));
            requestRows.setAll(messagingService.requests(user, searchField.getText(), requestStatusFilter.getValue()));
            refreshVisibleMessages(selectedId);
            NotificationHelper.showInfo(statusLabel, "Messages loaded. Inbox: " + inboxRows.size()
                    + " | Sent: " + sentRows.size() + " | Requests: " + requestRows.size());
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, "Could not load messages: " + e.getMessage());
        }
    }

    @FXML
    private void showInbox() {
        setCurrentView(MailboxView.INBOX);
    }

    @FXML
    private void showSent() {
        setCurrentView(MailboxView.SENT);
    }

    @FXML
    private void showRequests() {
        setCurrentView(MailboxView.REQUESTS);
    }

    @FXML
    private void showComposeOverlay() {
        clearCompose();
        reloadUsers();
        composeOverlay.setManaged(true);
        composeOverlay.setVisible(true);
        Platform.runLater(() -> {
            if (targetUserBox != null) {
                targetUserBox.requestFocus();
            }
        });
    }

    @FXML
    private void hideComposeOverlay() {
        composeOverlay.setVisible(false);
        composeOverlay.setManaged(false);
    }

    @FXML
    private void hideDetailOverlay() {
        detailOverlay.setVisible(false);
        detailOverlay.setManaged(false);
        if (messageListView != null) {
            messageListView.getSelectionModel().clearSelection();
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
            hideComposeOverlay();
            loadMessages();
            setCurrentView(MailboxView.SENT);
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
            SqliteMessageDao.MessageRow refreshed = findVisibleById(row.getId());
            if (refreshed != null) {
                messageListView.getSelectionModel().select(refreshed);
                showDetail(refreshed);
            } else {
                hideDetailOverlay();
            }
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
            hideDetailOverlay();
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
            NotificationHelper.showInfo(statusLabel, "This message does not include a linked item.");
            return;
        }
        appShell.showMessageCertificateSourceRecord(metadata.sourceType, metadata.sourceId);
    }

    @FXML
    private void openCertificate() {
        SqliteMessageDao.MessageRow row = selectedMessage();
        CertificateMetadata metadata = metadataFrom(row);
        if (metadata == null) {
            NotificationHelper.showInfo(statusLabel, "This message does not include a linked file.");
            return;
        }
        try {
            Path certificate = validateCertificatePath(metadata);
            FxFileOpenHelper.open(certificate);
            NotificationHelper.showSuccess(statusLabel, "Opening linked file with the local desktop handler.");
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
        statusFilter.setItems(FXCollections.observableArrayList("All", "Unread", "Read", "Archived"));
        requestStatusFilter.setItems(FXCollections.observableArrayList(
                "All Requests", "Pending", "Read", "Archived", "High Priority"));
        statusFilter.getSelectionModel().select("All");
        requestStatusFilter.getSelectionModel().select("All Requests");
        searchField.textProperty().addListener((obs, old, value) -> loadMessages());
        statusFilter.valueProperty().addListener((obs, old, value) -> loadMessages());
        requestStatusFilter.valueProperty().addListener((obs, old, value) -> loadMessages());
    }

    private void configureMessageList() {
        messageListView.setItems(visibleRows);
        Label placeholder = new Label("No messages to show.");
        placeholder.getStyleClass().add("muted-text");
        messageListView.setPlaceholder(placeholder);
        messageListView.setCellFactory(list -> new MessageRowCell());
        messageListView.getSelectionModel().selectedItemProperty().addListener((obs, old, row) -> {
            if (row != null) {
                showDetail(row);
            }
        });
    }

    private void configureCompose() {
        priorityBox.setItems(FXCollections.observableArrayList("NORMAL", "HIGH", "URGENT"));
        priorityBox.getSelectionModel().select("NORMAL");
        reloadUsers();
    }

    private void reloadUsers() {
        try {
            targetUserBox.setItems(FXCollections.observableArrayList(
                    userDao.findMessageTargetsExcept(Session.getUsername())));
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, "Could not load user targets: " + e.getMessage());
        }
        if (targetUserBox.getItems() != null && !targetUserBox.getItems().isEmpty()) {
            targetUserBox.getSelectionModel().selectFirst();
        } else {
            targetUserBox.getSelectionModel().clearSelection();
        }
    }

    private void setCurrentView(MailboxView view) {
        currentView = view == null ? MailboxView.INBOX : view;
        updateFilterPills();
        boolean requestsVisible = currentView == MailboxView.REQUESTS;
        requestStatusFilter.setVisible(requestsVisible);
        requestStatusFilter.setManaged(requestsVisible);
        refreshVisibleMessages(selectedMessageId());
    }

    private void updateFilterPills() {
        applyFilterState(inboxFilterButton, currentView == MailboxView.INBOX);
        applyFilterState(sentFilterButton, currentView == MailboxView.SENT);
        applyFilterState(requestFilterButton, currentView == MailboxView.REQUESTS);
    }

    private void applyFilterState(Button button, boolean active) {
        if (button == null) {
            return;
        }
        button.getStyleClass().remove("message-filter-pill-active");
        if (active) {
            button.getStyleClass().add("message-filter-pill-active");
        }
    }

    private void refreshVisibleMessages(Long preferredId) {
        switch (currentView) {
            case SENT -> visibleRows.setAll(sentRows);
            case REQUESTS -> visibleRows.setAll(requestRows);
            case INBOX -> visibleRows.setAll(inboxRows);
        }
        restoreSelection(preferredId);
    }

    private void restoreSelection(Long preferredId) {
        if (messageListView == null) {
            return;
        }
        if (preferredId != null) {
            SqliteMessageDao.MessageRow match = findVisibleById(preferredId);
            if (match != null) {
                messageListView.getSelectionModel().select(match);
                return;
            }
        }
        messageListView.getSelectionModel().clearSelection();
        if (detailOverlay != null) {
            detailOverlay.setVisible(false);
            detailOverlay.setManaged(false);
        }
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
        return messageListView == null ? null : messageListView.getSelectionModel().getSelectedItem();
    }

    private Long selectedMessageId() {
        SqliteMessageDao.MessageRow row = selectedMessage();
        return row == null ? null : row.getId();
    }

    private SqliteMessageDao.MessageRow findVisibleById(Long id) {
        if (id == null) {
            return null;
        }
        for (SqliteMessageDao.MessageRow row : visibleRows) {
            if (row != null && id.equals(row.getId())) {
                return row;
            }
        }
        return null;
    }

    private void showDetail(SqliteMessageDao.MessageRow row) {
        if (row == null) {
            return;
        }
        CertificateMetadata metadata = metadataFrom(row);
        String senderName = displayNameForUsername(row.getSenderUsername());
        String recipientName = recipientDisplay(row);

        detailDialogTitleLabel.setText(blankTo(row.getSubject(), "No subject"));
        detailSenderValueLabel.setText(senderName);
        detailRecipientValueLabel.setText(recipientName);
        detailCreatedValueLabel.setText(blankTo(row.getCreatedAt(), "-"));
        detailPatientValueLabel.setText(blankTo(row.getPatientId(), "No linked patient"));
        detailBodyArea.setText(blankTo(cleanMessageBody(row.getBody()), "No message body"));
        detailLinkedItemLabel.setText(metadata == null
                ? "Linked item: none"
                : "Linked item: " + metadata.certificateType + " | Source ID: " + metadata.sourceId);

        applyPillStyle(detailPriorityValueLabel, priorityStyle(row.getPriority()), blankTo(row.getPriority(), "NORMAL"));
        applyPillStyle(detailStatusValueLabel, statusStyle(row.getStatus()), friendlyStatus(row.getStatus()));

        boolean unread = isUnread(row);
        detailMarkReadButton.setVisible(unread && currentView != MailboxView.SENT);
        detailMarkReadButton.setManaged(unread && currentView != MailboxView.SENT);
        boolean hasPatient = row.getPatientId() != null && !row.getPatientId().isBlank();
        detailOpenPatientButton.setVisible(hasPatient);
        detailOpenPatientButton.setManaged(hasPatient);
        detailArchiveButton.setDisable("ARCHIVED".equalsIgnoreCase(row.getStatus()));

        boolean hasMetadata = metadata != null;
        detailLinkedActionsPane.setVisible(hasMetadata);
        detailLinkedActionsPane.setManaged(hasMetadata);
        detailOpenSourceButton.setVisible(hasMetadata);
        detailOpenSourceButton.setManaged(hasMetadata);
        detailOpenFileButton.setVisible(hasMetadata);
        detailOpenFileButton.setManaged(hasMetadata);

        detailOverlay.setManaged(true);
        detailOverlay.setVisible(true);
    }

    private void clearCompose() {
        patientIdField.clear();
        subjectField.clear();
        bodyArea.clear();
        if (priorityBox != null) {
            priorityBox.getSelectionModel().select("NORMAL");
        }
    }

    private String normalizedStatusFilter() {
        String status = statusFilter == null ? null : statusFilter.getValue();
        if (status == null || status.isBlank() || "All".equalsIgnoreCase(status)) {
            return "All";
        }
        if ("Unread".equalsIgnoreCase(status)) {
            return "SENT";
        }
        return status.toUpperCase(Locale.ROOT);
    }

    private String displayNameForUsername(String username) {
        String key = blankTo(username, "").trim().toLowerCase(Locale.ROOT);
        if (key.isBlank()) {
            return "Unknown user";
        }
        return displayNameCache.computeIfAbsent(key, ignored -> {
            try {
                return userDao.findDirectoryRowByUsername(username)
                        .map(SqliteUserDao.UserDirectoryRow::getDisplayName)
                        .filter(value -> value != null && !value.isBlank())
                        .orElse(username);
            } catch (Exception ignoredException) {
                // Keep username fallback for UI stability.
            }
            return username;
        });
    }

    private String recipientDisplay(SqliteMessageDao.MessageRow row) {
        if (row == null) {
            return "-";
        }
        String usernameTarget = usernameFromTargetSummary(row.getTargetSummary());
        if (!usernameTarget.isBlank()) {
            return displayNameForUsername(usernameTarget);
        }
        return blankTo(row.getTargetSummary(), "-");
    }

    private String usernameFromTargetSummary(String targetSummary) {
        if (targetSummary == null) {
            return "";
        }
        String value = targetSummary.trim();
        if (value.regionMatches(true, 0, "User:", 0, 5)) {
            return value.substring(5).trim();
        }
        return "";
    }

    private String displayNameForRow(SqliteMessageDao.MessageRow row) {
        if (row == null) {
            return "Unknown user";
        }
        if (currentView == MailboxView.SENT) {
            return "To " + recipientDisplay(row);
        }
        return displayNameForUsername(row.getSenderUsername());
    }

    private String subjectText(SqliteMessageDao.MessageRow row) {
        if (currentView == MailboxView.REQUESTS) {
            return blankTo(messagingService.requestType(row), "Request");
        }
        return blankTo(row == null ? null : row.getSubject(), "No subject");
    }

    private String previewText(SqliteMessageDao.MessageRow row) {
        String text = cleanMessageBody(row == null ? null : row.getBody());
        if (text.isBlank()) {
            text = currentView == MailboxView.SENT
                    ? "Sent to " + recipientDisplay(row)
                    : "No additional message text.";
        }
        text = text.replace('\r', ' ').replace('\n', ' ').trim();
        return text.length() > 96 ? text.substring(0, 93) + "..." : text;
    }

    private String cleanMessageBody(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        StringBuilder cleaned = new StringBuilder();
        boolean inMetadata = false;
        for (String line : body.split("\\R")) {
            String trimmed = line.trim();
            if ("[SPMS_CERTIFICATE]".equals(trimmed)) {
                inMetadata = true;
                continue;
            }
            if ("[/SPMS_CERTIFICATE]".equals(trimmed)) {
                inMetadata = false;
                continue;
            }
            if (!inMetadata) {
                if (!cleaned.isEmpty()) {
                    cleaned.append(System.lineSeparator());
                }
                cleaned.append(line);
            }
        }
        return cleaned.toString().trim();
    }

    private String initialsFor(String displayName) {
        String fallback = blankTo(displayName, "?").trim();
        if (fallback.isBlank()) {
            return "?";
        }
        String[] tokens = fallback.split("\\s+");
        if (tokens.length == 1) {
            String token = tokens[0];
            return token.substring(0, Math.min(2, token.length())).toUpperCase(Locale.ROOT);
        }
        String first = tokens[0].substring(0, 1);
        String last = tokens[tokens.length - 1].substring(0, 1);
        return (first + last).toUpperCase(Locale.ROOT);
    }

    private String friendlyTimestamp(String createdAt) {
        if (createdAt == null || createdAt.isBlank()) {
            return "";
        }
        try {
            LocalDateTime timestamp = LocalDateTime.parse(createdAt.trim(), STORAGE_TIME);
            if (timestamp.toLocalDate().equals(LocalDate.now())) {
                return timestamp.format(SAME_DAY_TIME);
            }
            return timestamp.format(OTHER_DAY_TIME);
        } catch (DateTimeParseException ignored) {
            return createdAt;
        }
    }

    private boolean isUnread(SqliteMessageDao.MessageRow row) {
        return row != null && row.getStatus() != null && "SENT".equalsIgnoreCase(row.getStatus());
    }

    private String friendlyStatus(String status) {
        if (status == null || status.isBlank()) {
            return "Unknown";
        }
        if ("SENT".equalsIgnoreCase(status)) {
            return "Unread";
        }
        return status.substring(0, 1).toUpperCase(Locale.ROOT)
                + status.substring(1).toLowerCase(Locale.ROOT);
    }

    private String priorityStyle(String priority) {
        if (priority == null) {
            return "muted-pill";
        }
        String value = priority.trim().toUpperCase(Locale.ROOT);
        return switch (value) {
            case "URGENT" -> "danger-pill";
            case "HIGH" -> "warning-pill";
            default -> "muted-pill";
        };
    }

    private String statusStyle(String status) {
        if (status == null) {
            return "muted-pill";
        }
        String value = status.trim().toUpperCase(Locale.ROOT);
        return switch (value) {
            case "SENT" -> "info-pill";
            case "READ" -> "success-pill";
            case "ARCHIVED" -> "muted-pill";
            default -> "muted-pill";
        };
    }

    private void applyPillStyle(Label label, String styleClass, String text) {
        if (label == null) {
            return;
        }
        label.setText(text);
        label.getStyleClass().setAll("badge-pill", styleClass);
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

    private String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private Path validateCertificatePath(CertificateMetadata metadata) {
        String rawPath = metadata == null ? "" : metadata.certificatePath;
        if (rawPath == null || rawPath.isBlank() || "-".equals(rawPath.trim())) {
            throw new IllegalArgumentException("Linked file was not found.");
        }
        Path path = Path.of(rawPath.trim()).toAbsolutePath().normalize();
        if (!path.startsWith(DEATH_CERTIFICATE_DIR) && !path.startsWith(BIRTH_CERTIFICATE_DIR)) {
            throw new SecurityException("Linked file path is outside the generated certificate folders.");
        }
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("Linked file was not found.");
        }
        return path;
    }

    private String certificateError(Exception e) {
        String message = e == null ? "" : e.getMessage();
        if (message == null || message.isBlank() || message.toLowerCase(Locale.ROOT).contains("does not exist")) {
            return "Linked file was not found.";
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

        private CertificateMetadata(String certificateType,
                                    String sourceType,
                                    String sourceId,
                                    String patientId,
                                    String newbornId,
                                    String certificatePath) {
            this.certificateType = certificateType;
            this.sourceType = sourceType;
            this.sourceId = sourceId;
            this.patientId = patientId;
            this.newbornId = newbornId;
            this.certificatePath = certificatePath;
        }
    }

    private class MessageRowCell extends ListCell<SqliteMessageDao.MessageRow> {

        private final HBox row = new HBox(14);
        private final StackPane avatarPane = new StackPane();
        private final Label initialsLabel = new Label();
        private final VBox textBox = new VBox(4);
        private final Label senderLabel = new Label();
        private final Label subjectLabel = new Label();
        private final Label previewLabel = new Label();
        private final VBox rightBox = new VBox(8);
        private final Label timeLabel = new Label();
        private final HBox badgesRow = new HBox(8);
        private final Label priorityBadge = new Label();
        private final Region unreadDot = new Region();

        private MessageRowCell() {
            avatarPane.getStyleClass().add("message-avatar");
            initialsLabel.getStyleClass().add("message-initials");
            avatarPane.getChildren().add(initialsLabel);

            senderLabel.getStyleClass().add("message-sender");
            subjectLabel.getStyleClass().add("message-subject");
            previewLabel.getStyleClass().add("message-preview");
            previewLabel.setWrapText(true);
            textBox.getChildren().addAll(senderLabel, subjectLabel, previewLabel);
            HBox.setHgrow(textBox, Priority.ALWAYS);

            timeLabel.getStyleClass().add("message-time");
            priorityBadge.getStyleClass().addAll("badge-pill", "muted-pill");
            unreadDot.getStyleClass().add("unread-dot");
            unreadDot.setMinSize(10, 10);
            unreadDot.setPrefSize(10, 10);
            unreadDot.setMaxSize(10, 10);
            badgesRow.setAlignment(Pos.CENTER_RIGHT);
            rightBox.setAlignment(Pos.CENTER_RIGHT);
            rightBox.getChildren().addAll(timeLabel, badgesRow);

            row.getStyleClass().add("message-row");
            row.setAlignment(Pos.CENTER_LEFT);
            row.getChildren().addAll(avatarPane, textBox, rightBox);
        }

        @Override
        protected void updateItem(SqliteMessageDao.MessageRow item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                return;
            }

            String displayName = displayNameForRow(item);
            initialsLabel.setText(initialsFor(displayName));
            senderLabel.setText(displayName);
            subjectLabel.setText(subjectText(item));
            previewLabel.setText(previewText(item));
            timeLabel.setText(friendlyTimestamp(item.getCreatedAt()));

            badgesRow.getChildren().clear();
            if (item.getPriority() != null && !"NORMAL".equalsIgnoreCase(item.getPriority())) {
                priorityBadge.setText(item.getPriority().toUpperCase(Locale.ROOT));
                priorityBadge.getStyleClass().setAll("badge-pill", priorityStyle(item.getPriority()));
                badgesRow.getChildren().add(priorityBadge);
            }
            boolean unread = isUnread(item) && currentView != MailboxView.SENT;
            unreadDot.setVisible(unread);
            unreadDot.setManaged(unread);
            if (unread) {
                badgesRow.getChildren().add(unreadDot);
            }

            row.getStyleClass().setAll("message-row");
            if (isSelected()) {
                row.getStyleClass().add("message-row-selected");
            }
            setText(null);
            setGraphic(row);
        }

        @Override
        public void updateSelected(boolean selected) {
            super.updateSelected(selected);
            row.getStyleClass().setAll("message-row");
            if (selected) {
                row.getStyleClass().add("message-row-selected");
            }
        }
    }
}
