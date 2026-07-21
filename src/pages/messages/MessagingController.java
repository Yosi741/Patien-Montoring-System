package pages.messages;

import app.core.AppShell;
import app.contracts.AppController;
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
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import pages.notification.NotificationHelper;
import pages.user.User;
import pages.user.profile_settings.SqliteUserDao;
import pages.user.Session;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Controls MessagingView.fxml for inbox, sent messages, composition, and message actions.
 */
public class MessagingController implements AppController {

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
    private final ObservableList<SqliteUserDao.UserTarget> availableTargets = FXCollections.observableArrayList();
    private final ObservableList<SqliteUserDao.UserTarget> filteredTargets = FXCollections.observableArrayList();
    private final Map<String, String> displayNameCache = new HashMap<>();

    private AppShell appShell;
    private MailboxView currentView = MailboxView.INBOX;
    private SqliteUserDao.UserTarget selectedRecipientTarget;

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

    @FXML private StackPane composeOverlay;
    @FXML private TextField recipientSearchField;
    @FXML private Label recipientHelperLabel;
    @FXML private ListView<SqliteUserDao.UserTarget> recipientSuggestionList;
    @FXML private Label selectedRecipientLabel;
    @FXML private Label composeValidationLabel;
    @FXML private ComboBox<String> priorityBox;
    @FXML private TextField subjectField;
    @FXML private TextArea bodyArea;

    /**
     * Supplies the application shell used by this controller for navigation.
     */
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

    /**
     * Handles the load messages UI action.
     */
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

    /**
     * Handles the show inbox UI action.
     */
    @FXML
    private void showInbox() {
        setCurrentView(MailboxView.INBOX);
    }

    /**
     * Handles the show sent UI action.
     */
    @FXML
    private void showSent() {
        setCurrentView(MailboxView.SENT);
    }

    /**
     * Handles the show requests UI action.
     */
    @FXML
    private void showRequests() {
        setCurrentView(MailboxView.REQUESTS);
    }

    /**
     * Handles the show compose overlay UI action.
     */
    @FXML
    private void showComposeOverlay() {
        clearCompose();
        reloadUsers();
        composeOverlay.setManaged(true);
        composeOverlay.setVisible(true);
        Platform.runLater(() -> {
            if (recipientSearchField != null) {
                recipientSearchField.requestFocus();
            }
        });
    }

    /**
     * Handles the hide compose overlay UI action.
     */
    @FXML
    private void hideComposeOverlay() {
        composeOverlay.setVisible(false);
        composeOverlay.setManaged(false);
    }

    /**
     * Handles the hide detail overlay UI action.
     */
    @FXML
    private void hideDetailOverlay() {
        detailOverlay.setVisible(false);
        detailOverlay.setManaged(false);
        if (messageListView != null) {
            messageListView.getSelectionModel().clearSelection();
        }
    }

    /**
     * Handles the send message UI action.
     */
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

    /**
     * Handles the mark read UI action.
     */
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

    /**
     * Handles the archive message UI action.
     */
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

    /**
     * Handles the open linked patient UI action.
     */
    @FXML
    private void openLinkedPatient() {
        SqliteMessageDao.MessageRow row = selectedMessage();
        if (row != null && row.getPatientId() != null && !row.getPatientId().isBlank()) {
            appShell.showPatientDetail(row.getPatientId());
        }
    }

    /**
     * Handles the show dashboard UI action.
     */
    @FXML
    private void showDashboard() {
        appShell.showDashboard(Session.getCurrentUser());
    }

    /**
     * Configures access.
     */
    private void configureAccess() {
        boolean allowed = PermissionHelper.canViewMessages(Session.getCurrentUser());
        accessDeniedPane.setVisible(!allowed);
        accessDeniedPane.setManaged(!allowed);
        contentPane.setVisible(allowed);
        contentPane.setManaged(allowed);
    }

    /**
     * Configures filters.
     */
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

    /**
     * Configures message list.
     */
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

    /**
     * Configures compose.
     */
    private void configureCompose() {
        priorityBox.setItems(FXCollections.observableArrayList("NORMAL", "HIGH", "URGENT"));
        priorityBox.getSelectionModel().select("NORMAL");
        recipientSuggestionList.setItems(filteredTargets);
        recipientSuggestionList.setPlaceholder(new Label("No staff account found."));
        recipientSuggestionList.setCellFactory(list -> new RecipientSuggestionCell());
        recipientSuggestionList.setOnMouseClicked(event -> {
            if (event.getClickCount() >= 1) {
                selectRecipientTarget(recipientSuggestionList.getSelectionModel().getSelectedItem());
            }
        });
        recipientSuggestionList.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                selectRecipientTarget(recipientSuggestionList.getSelectionModel().getSelectedItem());
                event.consume();
            }
        });
        recipientSearchField.textProperty().addListener((obs, old, value) -> onRecipientQueryChanged(value));
        recipientSearchField.focusedProperty().addListener((obs, old, focused) -> {
            if (!focused) {
                Platform.runLater(() -> {
                    if (!recipientSearchField.isFocused() && !recipientSuggestionList.isFocused()) {
                        hideSuggestionList();
                    }
                });
            } else if (focused) {
                updateRecipientSuggestions(recipientSearchField.getText());
            }
        });
        reloadUsers();
    }

    /**
     * Reloads users from SQLite.
     */
    private void reloadUsers() {
        try {
            availableTargets.setAll(userDao.findMessageTargetsExcept(Session.getUsername()));
            updateRecipientSuggestions(recipientSearchField == null ? "" : recipientSearchField.getText());
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, "Could not load user targets: " + e.getMessage());
            availableTargets.clear();
            filteredTargets.clear();
            hideSuggestionList();
        }
    }

    /**
     * Updates current view for the current object.
     */
    private void setCurrentView(MailboxView view) {
        currentView = view == null ? MailboxView.INBOX : view;
        updateFilterPills();
        boolean requestsVisible = currentView == MailboxView.REQUESTS;
        requestStatusFilter.setVisible(requestsVisible);
        requestStatusFilter.setManaged(requestsVisible);
        refreshVisibleMessages(selectedMessageId());
    }

    /**
     * Updates filter pills.
     */
    private void updateFilterPills() {
        applyFilterState(inboxFilterButton, currentView == MailboxView.INBOX);
        applyFilterState(sentFilterButton, currentView == MailboxView.SENT);
        applyFilterState(requestFilterButton, currentView == MailboxView.REQUESTS);
    }

    /**
     * Applies filter state to the current control or record.
     */
    private void applyFilterState(Button button, boolean active) {
        if (button == null) {
            return;
        }
        button.getStyleClass().remove("message-filter-pill-active");
        if (active) {
            button.getStyleClass().add("message-filter-pill-active");
        }
    }

    /**
     * Refreshes visible messages from the current application state.
     */
    private void refreshVisibleMessages(Long preferredId) {
        switch (currentView) {
            case SENT -> visibleRows.setAll(sentRows);
            case REQUESTS -> visibleRows.setAll(requestRows);
            case INBOX -> visibleRows.setAll(inboxRows);
        }
        restoreSelection(preferredId);
    }

    /**
     * Restores selection after the backing items change.
     */
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

    /**
     * Builds record used by the messaging view.
     */
    private SqliteMessageDao.MessageWriteRecord buildRecord() {
        SqliteUserDao.UserTarget selected = resolveExactRecipient(recipientSearchField.getText());
        if (selected == null || selected.getUsername().isBlank()) {
            showComposeValidation("Recipient not found. Check the email or choose a staff member from the suggestions.");
            throw new IllegalArgumentException("Recipient not found. Check the email or choose a staff member from the suggestions.");
        }
        if (selected.getUsername().equalsIgnoreCase(Session.getUsername())) {
            showComposeValidation("You cannot send a message to yourself.");
            throw new IllegalArgumentException("You cannot send a message to yourself.");
        }
        return new SqliteMessageDao.MessageWriteRecord(
                Session.getUsername(),
                selected.getUsername(),
                "",
                "",
                "",
                subjectField.getText(),
                bodyArea.getText(),
                priorityBox.getValue()
        );
    }

    /**
     * Selects selected message without using an invalid index.
     */
    private SqliteMessageDao.MessageRow selectedMessage() {
        return messageListView == null ? null : messageListView.getSelectionModel().getSelectedItem();
    }

    /**
     * Selects selected message ID without using an invalid index.
     */
    private Long selectedMessageId() {
        SqliteMessageDao.MessageRow row = selectedMessage();
        return row == null ? null : row.getId();
    }

    /**
     * Finds visible by ID.
     */
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

    /**
     * Displays detail to the user.
     */
    private void showDetail(SqliteMessageDao.MessageRow row) {
        if (row == null) {
            return;
        }
        String senderName = displayNameForUsername(row.getSenderUsername());
        String recipientName = recipientDisplay(row);

        detailDialogTitleLabel.setText(blankTo(row.getSubject(), "No subject"));
        detailSenderValueLabel.setText(senderName);
        detailRecipientValueLabel.setText(recipientName);
        detailCreatedValueLabel.setText(blankTo(row.getCreatedAt(), "-"));
        detailPatientValueLabel.setText(blankTo(row.getPatientId(), "No linked patient"));
        detailBodyArea.setText(blankTo(cleanMessageBody(row.getBody()), "No message body"));
        detailLinkedItemLabel.setText("Linked item: none");

        applyPillStyle(detailPriorityValueLabel, priorityStyle(row.getPriority()), blankTo(row.getPriority(), "NORMAL"));
        applyPillStyle(detailStatusValueLabel, statusStyle(row.getStatus()), friendlyStatus(row.getStatus()));

        boolean unread = isUnread(row);
        detailMarkReadButton.setVisible(unread && currentView != MailboxView.SENT);
        detailMarkReadButton.setManaged(unread && currentView != MailboxView.SENT);
        boolean hasPatient = row.getPatientId() != null && !row.getPatientId().isBlank();
        detailOpenPatientButton.setVisible(hasPatient);
        detailOpenPatientButton.setManaged(hasPatient);
        detailArchiveButton.setDisable("ARCHIVED".equalsIgnoreCase(row.getStatus()));

        detailOverlay.setManaged(true);
        detailOverlay.setVisible(true);
    }

    /**
     * Clears compose and restores its default state.
     */
    private void clearCompose() {
        selectedRecipientTarget = null;
        recipientSearchField.clear();
        subjectField.clear();
        bodyArea.clear();
        if (priorityBox != null) {
            priorityBox.getSelectionModel().select("NORMAL");
        }
        recipientSuggestionList.getSelectionModel().clearSelection();
        filteredTargets.clear();
        hideSuggestionList();
        hideHelperLabel();
        hideComposeValidation();
        hideSelectedRecipient();
    }

    /**
     * Responds when recipient query changed changes in the UI.
     */
    private void onRecipientQueryChanged(String query) {
        clearComposeValidation();
        if (!matchesSelectedRecipient(query)) {
            selectedRecipientTarget = null;
            hideSelectedRecipient();
        }
        updateRecipientSuggestions(query);
    }

    /**
     * Determines whether the current value matches selected recipient.
     */
    private boolean matchesSelectedRecipient(String query) {
        if (selectedRecipientTarget == null) {
            return false;
        }
        return normalizeRecipientText(query).equals(normalizeRecipientText(recipientFieldValue(selectedRecipientTarget)));
    }

    /**
     * Updates recipient suggestions.
     */
    private void updateRecipientSuggestions(String query) {
        filteredTargets.clear();
        String normalizedQuery = normalizeRecipientText(query);
        if (normalizedQuery.isBlank()) {
            hideSuggestionList();
            hideHelperLabel();
            return;
        }
        for (SqliteUserDao.UserTarget target : availableTargets) {
            if (matchesTarget(normalizedQuery, target)) {
                filteredTargets.add(target);
            }
        }
        if (filteredTargets.isEmpty()) {
            hideSuggestionList();
            showHelperLabel("No staff account found.");
            return;
        }
        hideHelperLabel();
        recipientSuggestionList.setManaged(true);
        recipientSuggestionList.setVisible(true);
    }

    /**
     * Determines whether the current value matches target.
     */
    private boolean matchesTarget(String normalizedQuery, SqliteUserDao.UserTarget target) {
        if (target == null || normalizedQuery.isBlank()) {
            return false;
        }
        return containsNormalized(target.getDisplayName(), normalizedQuery)
                || containsNormalized(target.getUsername(), normalizedQuery)
                || containsNormalized(target.getEmail(), normalizedQuery)
                || containsNormalized(target.getRole(), normalizedQuery);
    }

    /**
     * Resolves exact recipient for the current workflow.
     */
    private SqliteUserDao.UserTarget resolveExactRecipient(String query) {
        if (selectedRecipientTarget != null && matchesSelectedRecipient(query)) {
            return selectedRecipientTarget;
        }
        String normalizedQuery = normalizeRecipientText(query);
        if (normalizedQuery.isBlank()) {
            return null;
        }
        SqliteUserDao.UserTarget exactMatch = null;
        for (SqliteUserDao.UserTarget target : availableTargets) {
            if (isExactRecipientMatch(normalizedQuery, target)) {
                if (exactMatch != null) {
                    return null;
                }
                exactMatch = target;
            }
        }
        if (exactMatch != null) {
            selectRecipientTarget(exactMatch);
        }
        return exactMatch;
    }

    private boolean isExactRecipientMatch(String normalizedQuery, SqliteUserDao.UserTarget target) {
        return normalizeRecipientText(target.getEmail()).equals(normalizedQuery)
                || normalizeRecipientText(target.getUsername()).equals(normalizedQuery)
                || normalizeRecipientText(target.getDisplayName()).equals(normalizedQuery);
    }

    /**
     * Selects recipient target without using an invalid index.
     */
    private void selectRecipientTarget(SqliteUserDao.UserTarget target) {
        if (target == null) {
            return;
        }
        selectedRecipientTarget = target;
        recipientSearchField.setText(recipientFieldValue(target));
        recipientSuggestionList.getSelectionModel().select(target);
        hideSuggestionList();
        hideHelperLabel();
        hideComposeValidation();
        selectedRecipientLabel.setText("Selected: " + recipientSummary(target));
        selectedRecipientLabel.setManaged(true);
        selectedRecipientLabel.setVisible(true);
    }

    /**
     * Returns the text shown in the recipient field for the current selection.
     */
    private String recipientFieldValue(SqliteUserDao.UserTarget target) {
        if (target == null) {
            return "";
        }
        return target.getEmail().isBlank() ? target.getUsername() : target.getEmail();
    }

    /**
     * Builds the concise recipient summary shown by the compose form.
     */
    private String recipientSummary(SqliteUserDao.UserTarget target) {
        if (target == null) {
            return "";
        }
        StringBuilder summary = new StringBuilder(target.getDisplayName());
        if (!target.getUsername().isBlank() && !target.getUsername().equalsIgnoreCase(target.getDisplayName())) {
            summary.append(" (@").append(target.getUsername()).append(')');
        }
        if (!target.getRole().isBlank()) {
            summary.append(" | ").append(target.getRole());
        }
        if (!target.getEmail().isBlank()) {
            summary.append(" | ").append(target.getEmail());
        }
        return summary.toString();
    }

    /**
     * Clears compose validation and restores its default state.
     */
    private void clearComposeValidation() {
        hideComposeValidation();
    }

    /**
     * Displays compose validation to the user.
     */
    private void showComposeValidation(String message) {
        composeValidationLabel.setText(blankTo(message, "Recipient not found. Check the email or choose a staff member from the suggestions."));
        composeValidationLabel.setManaged(true);
        composeValidationLabel.setVisible(true);
    }

    /**
     * Hides compose validation and removes its layout space.
     */
    private void hideComposeValidation() {
        composeValidationLabel.setManaged(false);
        composeValidationLabel.setVisible(false);
    }

    /**
     * Builds the JavaFX control used for show helper label.
     */
    private void showHelperLabel(String message) {
        recipientHelperLabel.setText(blankTo(message, "No staff account found."));
        recipientHelperLabel.setManaged(true);
        recipientHelperLabel.setVisible(true);
    }

    /**
     * Hides helper label and removes its layout space.
     */
    private void hideHelperLabel() {
        recipientHelperLabel.setManaged(false);
        recipientHelperLabel.setVisible(false);
    }

    /**
     * Hides selected recipient and removes its layout space.
     */
    private void hideSelectedRecipient() {
        selectedRecipientLabel.setManaged(false);
        selectedRecipientLabel.setVisible(false);
    }

    /**
     * Hides suggestion list and removes its layout space.
     */
    private void hideSuggestionList() {
        recipientSuggestionList.setManaged(false);
        recipientSuggestionList.setVisible(false);
    }

    /**
     * Determines whether the normalized content contains normalized.
     */
    private boolean containsNormalized(String value, String normalizedQuery) {
        return normalizeRecipientText(value).contains(normalizedQuery);
    }

    /**
     * Returns formatted display text for normalize recipient text.
     */
    private String normalizeRecipientText(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Normalizes normalized status filter to the stored application format.
     */
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

    /**
     * Formats name for username for display in the JavaFX UI.
     */
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

    /**
     * Formats a staff recipient for the message suggestion list.
     */
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

    /**
     * Extracts the username from a displayed message-recipient summary.
     */
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

    /**
     * Formats name for row for display in the JavaFX UI.
     */
    private String displayNameForRow(SqliteMessageDao.MessageRow row) {
        if (row == null) {
            return "Unknown user";
        }
        if (currentView == MailboxView.SENT) {
            return "To " + recipientDisplay(row);
        }
        return displayNameForUsername(row.getSenderUsername());
    }

    /**
     * Returns formatted display text for subject text.
     */
    private String subjectText(SqliteMessageDao.MessageRow row) {
        if (currentView == MailboxView.REQUESTS) {
            return blankTo(messagingService.requestType(row), "Request");
        }
        return blankTo(row == null ? null : row.getSubject(), "No subject");
    }

    /**
     * Returns formatted display text for preview text.
     */
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

    /**
     * Trims and normalizes message body before storage or comparison.
     */
    private String cleanMessageBody(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        return body.trim();
    }

    /**
     * Builds initials from for for an avatar fallback.
     */
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

    /**
     * Converts timestamp to user-friendly display text.
     */
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

    /**
     * Converts status to user-friendly display text.
     */
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

    /**
     * Resolves priority style for the current clinical state.
     */
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

    /**
     * Resolves status style for workflow display or ordering.
     */
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

    /**
     * Returns the CSS style class for apply pill style.
     */
    private void applyPillStyle(Label label, String styleClass, String text) {
        if (label == null) {
            return;
        }
        label.setText(text);
        label.getStyleClass().setAll("badge-pill", styleClass);
    }

    /**
     * Normalizes blank to to the workflow fallback value.
     */
    private String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
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

        /**
         * Creates a message row cell from the supplied record values.
         */
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

        /**
         * Updates item.
         */
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

        /**
         * Updates selected.
         */
        @Override
        public void updateSelected(boolean selected) {
            super.updateSelected(selected);
            row.getStyleClass().setAll("message-row");
            if (selected) {
                row.getStyleClass().add("message-row-selected");
            }
        }
    }

    private static class RecipientSuggestionCell extends ListCell<SqliteUserDao.UserTarget> {

        private final VBox content = new VBox(4);
        private final Label nameLabel = new Label();
        private final Label metaLabel = new Label();

        /**
         * Creates a recipient suggestion cell from the supplied record values.
         */
        private RecipientSuggestionCell() {
            content.getStyleClass().add("message-suggestion-row");
            nameLabel.getStyleClass().add("message-suggestion-name");
            metaLabel.getStyleClass().add("message-suggestion-meta");
            metaLabel.setWrapText(true);
            content.getChildren().addAll(nameLabel, metaLabel);
        }

        /**
         * Updates item.
         */
        @Override
        protected void updateItem(SqliteUserDao.UserTarget item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            nameLabel.setText(item.getDisplayName());
            String email = item.getEmail().isBlank() ? "No email" : item.getEmail();
            metaLabel.setText(item.getUsername() + " | " + item.getRole() + " | " + email);
            setText(null);
            setGraphic(content);
        }
    }
}
