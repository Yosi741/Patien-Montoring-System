package ui.javafx.controllers;

import dao.SqliteMessageDao;
import dao.SqliteUserDao;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import services.MessagingService;
import ui.javafx.AppShell;
import ui.javafx.FxController;
import ui.javafx.helpers.NotificationHelper;
import ui.javafx.helpers.PermissionHelper;
import users.Session;
import users.User;

import java.util.ArrayList;

public class MessagingController implements FxController {

    private final MessagingService messagingService = new MessagingService();
    private final SqliteUserDao userDao = new SqliteUserDao();
    private final ObservableList<SqliteMessageDao.MessageRow> inboxRows = FXCollections.observableArrayList();
    private final ObservableList<SqliteMessageDao.MessageRow> sentRows = FXCollections.observableArrayList();
    private AppShell appShell;

    @FXML private VBox accessDeniedPane;
    @FXML private VBox contentPane;
    @FXML private TextField searchField;
    @FXML private ComboBox<String> statusFilter;
    @FXML private TableView<SqliteMessageDao.MessageRow> inboxTable;
    @FXML private TableView<SqliteMessageDao.MessageRow> sentTable;
    @FXML private TableColumn<SqliteMessageDao.MessageRow, Long> inboxIdColumn;
    @FXML private TableColumn<SqliteMessageDao.MessageRow, String> inboxSenderColumn;
    @FXML private TableColumn<SqliteMessageDao.MessageRow, String> inboxSubjectColumn;
    @FXML private TableColumn<SqliteMessageDao.MessageRow, String> inboxPriorityColumn;
    @FXML private TableColumn<SqliteMessageDao.MessageRow, String> inboxStatusColumn;
    @FXML private TableColumn<SqliteMessageDao.MessageRow, String> inboxPatientColumn;
    @FXML private TableColumn<SqliteMessageDao.MessageRow, String> inboxCreatedColumn;
    @FXML private TableColumn<SqliteMessageDao.MessageRow, Long> sentIdColumn;
    @FXML private TableColumn<SqliteMessageDao.MessageRow, String> sentTargetColumn;
    @FXML private TableColumn<SqliteMessageDao.MessageRow, String> sentSubjectColumn;
    @FXML private TableColumn<SqliteMessageDao.MessageRow, String> sentPriorityColumn;
    @FXML private TableColumn<SqliteMessageDao.MessageRow, String> sentStatusColumn;
    @FXML private TableColumn<SqliteMessageDao.MessageRow, String> sentCreatedColumn;
    @FXML private ComboBox<String> targetTypeBox;
    @FXML private ComboBox<String> targetUserBox;
    @FXML private ComboBox<String> targetRoleBox;
    @FXML private TextField targetSectionField;
    @FXML private TextField patientIdField;
    @FXML private TextField subjectField;
    @FXML private ComboBox<String> priorityBox;
    @FXML private TextArea bodyArea;
    @FXML private Label detailTitleLabel;
    @FXML private Label detailMetaLabel;
    @FXML private TextArea detailBodyArea;
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
            inboxRows.setAll(messagingService.inbox(user, searchField.getText(), statusFilter.getValue()));
            sentRows.setAll(messagingService.sent(user, searchField.getText(), statusFilter.getValue()));
            inboxTable.setItems(inboxRows);
            sentTable.setItems(sentRows);
            NotificationHelper.showInfo(statusLabel, "Messages loaded. Inbox: " + inboxRows.size() + " | Sent: " + sentRows.size());
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
            NotificationHelper.showSuccess(statusLabel, "Message sent in SQLite. ID: " + id);
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
        statusFilter.getSelectionModel().select("All");
        searchField.textProperty().addListener((obs, old, value) -> loadMessages());
        statusFilter.valueProperty().addListener((obs, old, value) -> loadMessages());
    }

    private void configureTables() {
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
        sentCreatedColumn.setCellValueFactory(new PropertyValueFactory<>("createdAt"));
        inboxTable.getSelectionModel().selectedItemProperty().addListener((obs, old, row) -> showDetail(row));
        sentTable.getSelectionModel().selectedItemProperty().addListener((obs, old, row) -> showDetail(row));
    }

    private void configureCompose() {
        targetTypeBox.setItems(FXCollections.observableArrayList("User", "Role", "Section"));
        targetRoleBox.setItems(FXCollections.observableArrayList("ADMIN", "DOCTOR", "NURSE", "STAFF"));
        priorityBox.setItems(FXCollections.observableArrayList("NORMAL", "HIGH", "URGENT"));
        targetTypeBox.getSelectionModel().select("User");
        targetRoleBox.getSelectionModel().select("NURSE");
        priorityBox.getSelectionModel().select("NORMAL");
        reloadUsers();
        targetTypeBox.valueProperty().addListener((obs, old, value) -> updateTargetControls());
        updateTargetControls();
    }

    private void reloadUsers() {
        ArrayList<String> users = new ArrayList<>();
        try {
            for (User user : userDao.findAll()) {
                users.add(user.getUsername());
            }
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, "Could not load user targets: " + e.getMessage());
        }
        targetUserBox.setItems(FXCollections.observableArrayList(users));
        if (!users.isEmpty()) {
            targetUserBox.getSelectionModel().selectFirst();
        }
    }

    private void updateTargetControls() {
        String type = targetTypeBox.getValue();
        targetUserBox.setDisable(!"User".equals(type));
        targetRoleBox.setDisable(!"Role".equals(type));
        targetSectionField.setDisable(!"Section".equals(type));
    }

    private SqliteMessageDao.MessageWriteRecord buildRecord() {
        String type = targetTypeBox.getValue();
        return new SqliteMessageDao.MessageWriteRecord(
                Session.getUsername(),
                "User".equals(type) ? targetUserBox.getValue() : "",
                "Role".equals(type) ? targetRoleBox.getValue() : "",
                "Section".equals(type) ? targetSectionField.getText() : "",
                patientIdField.getText(),
                subjectField.getText(),
                bodyArea.getText(),
                priorityBox.getValue()
        );
    }

    private SqliteMessageDao.MessageRow selectedMessage() {
        SqliteMessageDao.MessageRow row = inboxTable.getSelectionModel().getSelectedItem();
        return row == null ? sentTable.getSelectionModel().getSelectedItem() : row;
    }

    private void showDetail(SqliteMessageDao.MessageRow row) {
        if (row == null) {
            return;
        }
        detailTitleLabel.setText(row.getSubject());
        detailMetaLabel.setText("From " + row.getSenderUsername() + " | " + row.getTargetSummary()
                + " | " + row.getPriority() + " | " + row.getStatus() + " | " + row.getCreatedAt());
        detailBodyArea.setText(row.getBody());
    }

    private void clearDetail() {
        detailTitleLabel.setText("Select a message");
        detailMetaLabel.setText("-");
        detailBodyArea.clear();
    }

    private void clearCompose() {
        patientIdField.clear();
        subjectField.clear();
        bodyArea.clear();
        priorityBox.getSelectionModel().select("NORMAL");
    }
}
