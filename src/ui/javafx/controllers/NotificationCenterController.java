package ui.javafx.controllers;

import dao.SqliteNotificationDao;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import services.NotificationCenterService;
import ui.javafx.AppShell;
import ui.javafx.FxController;
import ui.javafx.helpers.NotificationHelper;
import ui.javafx.helpers.PermissionHelper;
import users.Session;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

public class NotificationCenterController implements FxController {

    private final NotificationCenterService notificationService = new NotificationCenterService();
    private final ObservableList<SqliteNotificationDao.NotificationRow> rows = FXCollections.observableArrayList();
    private AppShell appShell;
    private Timeline refreshTimeline;
    private String quickFilter = "All";
    private boolean suppressFilterEvents;

    @FXML private VBox accessDeniedPane;
    @FXML private VBox contentPane;
    @FXML private ComboBox<String> severityFilter;
    @FXML private ComboBox<String> statusFilter;
    @FXML private ComboBox<String> dateRangeFilter;
    @FXML private TextField patientSearchField;
    @FXML private ListView<SqliteNotificationDao.NotificationRow> notificationList;
    @FXML private Label unreadCountLabel;
    @FXML private Label criticalCountLabel;
    @FXML private Label alertsCountLabel;
    @FXML private Label workflowCountLabel;
    @FXML private Label detailTitleLabel;
    @FXML private Label detailMetaLabel;
    @FXML private Label detailSourceLabel;
    @FXML private Label detailSeverityLabel;
    @FXML private Label detailStatusLabel;
    @FXML private Label detailPatientLabel;
    @FXML private TextArea detailMessageArea;
    @FXML private TextArea recommendedActionArea;
    @FXML private FlowPane sourceButtonPane;
    @FXML private Label statusLabel;

    @Override
    public void setAppShell(AppShell appShell) {
        this.appShell = appShell;
        configureAccess();
        configureFilters();
        configureList();
        loadNotifications();
        startAutoRefresh();
    }

    @FXML
    private void loadNotifications() {
        if (!PermissionHelper.canViewNotifications(Session.getCurrentUser())) {
            return;
        }
        try {
            SqliteNotificationDao.NotificationRow currentSelection = currentSelectedRow();
            long selectedId = currentSelection == null ? -1 : currentSelection.getId();
            List<SqliteNotificationDao.NotificationRow> loaded = notificationService.findForCurrentUser(
                    Session.getCurrentUser(),
                    severityFilter.getValue(),
                    statusFilter.getValue(),
                    patientSearchField.getText(),
                    dateRangeFilter.getValue()
            );
            rows.setAll(applyQuickFilter(loaded));
            notificationList.setItems(rows);
            renderCounters(rows);
            NotificationHelper.showInfo(statusLabel, "Notifications loaded: " + rows.size());
            if (selectedId > -1 && selectById(selectedId)) {
                return;
            }
            if (!rows.isEmpty() && notificationList.getSelectionModel().isEmpty()) {
                notificationList.getSelectionModel().selectFirst();
                showDetail(rows.get(0));
            } else if (rows.isEmpty()) {
                clearDetail();
            }
            appShell.refreshNotificationCount();
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, "Could not load notifications: " + e.getMessage());
        }
    }

    @FXML private void showAll() {
        quickFilter = "All";
        selectFilters("All", "All");
    }

    @FXML private void showUnread() {
        quickFilter = "Unread";
        selectFilters("All", "UNREAD");
    }

    @FXML private void showCritical() {
        quickFilter = "Critical";
        selectFilters("CRITICAL", "All");
    }

    @FXML private void showAlerts() {
        quickFilter = "Alerts";
        selectFilters("All", "All");
    }

    @FXML private void showReminders() {
        quickFilter = "Reminders";
        selectFilters("All", "All");
    }

    @FXML private void showMessages() {
        quickFilter = "Messages";
        selectFilters("All", "All");
    }

    @FXML private void showSystem() {
        quickFilter = "System";
        selectFilters("All", "All");
    }

    @FXML
    private void markRead() {
        SqliteNotificationDao.NotificationRow row = selectedRow();
        if (row == null) {
            return;
        }
        try {
            notificationService.markRead(Session.getCurrentUser(), row.getId());
            loadNotifications();
            NotificationHelper.showSuccess(statusLabel, "Notification marked read.");
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, "Could not mark read: " + e.getMessage());
        }
    }

    @FXML
    private void dismissNotification() {
        SqliteNotificationDao.NotificationRow row = selectedRow();
        if (row == null) {
            return;
        }
        try {
            notificationService.dismiss(Session.getCurrentUser(), row.getId());
            loadNotifications();
            NotificationHelper.showSuccess(statusLabel, "Notification dismissed.");
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, "Could not dismiss: " + e.getMessage());
        }
    }

    @FXML
    private void openLinkedItem() {
        SqliteNotificationDao.NotificationRow row = selectedRow();
        if (row == null) {
            return;
        }
        if (isCertificateSource(row)) {
            appShell.showCertificateSourceRecord(row.getSourceType(), row.getSourceId());
            return;
        }
        if (row.getPatientId() != null && !row.getPatientId().isBlank()) {
            if ("ALERT".equalsIgnoreCase(row.getSourceType()) && row.getSourceId() != null && row.getSourceId().matches("\\d+")) {
                appShell.showAlertCenterForAlert(Long.parseLong(row.getSourceId()));
            } else if ("REMINDER".equalsIgnoreCase(row.getSourceType()) || "SCHEDULING".equalsIgnoreCase(row.getSourceType())) {
                appShell.showSchedulingForPatient(row.getPatientId());
            } else {
                appShell.showPatientDetail(row.getPatientId());
            }
        }
    }

    @FXML
    private void openRelatedPatient() {
        SqliteNotificationDao.NotificationRow row = selectedRow();
        if (row != null && row.getPatientId() != null && !row.getPatientId().isBlank()) {
            appShell.showPatientDetail(row.getPatientId());
        }
    }

    @FXML
    private void openSourceRecord() {
        SqliteNotificationDao.NotificationRow row = selectedRow();
        if (row == null) {
            return;
        }
        if (isCertificateSource(row)) {
            appShell.showCertificateSourceRecord(row.getSourceType(), row.getSourceId());
        } else {
            openLinkedItem();
        }
    }

    @FXML
    private void openCertificate() {
        SqliteNotificationDao.NotificationRow row = selectedRow();
        if (row == null) {
            return;
        }
        if (!isCertificateSource(row)) {
            NotificationHelper.showInfo(statusLabel, "This notification is not linked to a certificate.");
            return;
        }
        appShell.showCertificateFromNotification(row.getSourceType(), row.getSourceId());
    }

    @FXML
    private void showDashboard() {
        appShell.showDashboard(Session.getCurrentUser());
    }

    private void configureAccess() {
        boolean allowed = PermissionHelper.canViewNotifications(Session.getCurrentUser());
        accessDeniedPane.setVisible(!allowed);
        accessDeniedPane.setManaged(!allowed);
        contentPane.setVisible(allowed);
        contentPane.setManaged(allowed);
    }

    private void configureFilters() {
        severityFilter.setItems(FXCollections.observableArrayList("All", "INFO", "WARNING", "CRITICAL"));
        statusFilter.setItems(FXCollections.observableArrayList("All", "UNREAD", "READ", "DISMISSED"));
        dateRangeFilter.setItems(FXCollections.observableArrayList("All", "Today", "Last 7 days", "Last 30 days"));
        severityFilter.getSelectionModel().select("All");
        statusFilter.getSelectionModel().select("All");
        dateRangeFilter.getSelectionModel().select("All");
        severityFilter.valueProperty().addListener((obs, old, value) -> {
            if (!suppressFilterEvents) {
                quickFilter = "Custom";
                loadNotifications();
            }
        });
        statusFilter.valueProperty().addListener((obs, old, value) -> {
            if (!suppressFilterEvents) {
                quickFilter = "Custom";
                loadNotifications();
            }
        });
        dateRangeFilter.valueProperty().addListener((obs, old, value) -> loadNotifications());
        patientSearchField.textProperty().addListener((obs, old, value) -> loadNotifications());
    }

    private void configureList() {
        notificationList.setCellFactory(list -> new NotificationCardCell());
        notificationList.getSelectionModel().selectedItemProperty().addListener((obs, old, row) -> showDetail(row));
    }

    private void showDetail(SqliteNotificationDao.NotificationRow row) {
        if (row == null) {
            clearDetail();
            return;
        }
        detailTitleLabel.setText(row.getTitle());
        detailMetaLabel.setText(row.getTargetSummary() + " | " + row.getCreatedAt());
        detailSeverityLabel.setText(row.getSeverity());
        detailStatusLabel.setText(row.getStatus());
        detailPatientLabel.setText(nullTo(row.getPatientId(), "No linked patient"));
        detailSourceLabel.setText("Source: " + nullTo(row.getSourceType(), "-") + " | Source ID: " + nullTo(row.getSourceId(), "-"));
        detailMessageArea.setText(row.getMessage());
        recommendedActionArea.setText(recommendedAction(row));
        sourceButtonPane.setVisible(isCertificateSource(row));
        sourceButtonPane.setManaged(isCertificateSource(row));
        setBadgeStyle(detailSeverityLabel, severityStyle(row.getSeverity()), "notification-badge-info", "notification-badge-warning", "notification-badge-critical");
        setBadgeStyle(detailStatusLabel, statusStyle(row.getStatus()), "notification-badge-status", "notification-badge-unread", "notification-badge-dismissed");
    }

    private void clearDetail() {
        detailTitleLabel.setText("Select a notification");
        detailMetaLabel.setText("-");
        detailSeverityLabel.setText("-");
        detailStatusLabel.setText("-");
        detailPatientLabel.setText("-");
        detailSourceLabel.setText("Source: -");
        detailMessageArea.clear();
        recommendedActionArea.clear();
        sourceButtonPane.setVisible(false);
        sourceButtonPane.setManaged(false);
    }

    private SqliteNotificationDao.NotificationRow selectedRow() {
        SqliteNotificationDao.NotificationRow row = notificationList == null ? null : notificationList.getSelectionModel().getSelectedItem();
        if (row == null) {
            NotificationHelper.showError(statusLabel, "Select a notification first.");
        }
        return row;
    }

    private SqliteNotificationDao.NotificationRow currentSelectedRow() {
        return notificationList == null ? null : notificationList.getSelectionModel().getSelectedItem();
    }

    private void startAutoRefresh() {
        refreshTimeline = new Timeline(new KeyFrame(Duration.seconds(25), event -> loadNotifications()));
        refreshTimeline.setCycleCount(Timeline.INDEFINITE);
        refreshTimeline.play();
    }

    private String nullTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private boolean isCertificateSource(SqliteNotificationDao.NotificationRow row) {
        return row != null && ("DEATH_CERTIFICATE".equalsIgnoreCase(row.getSourceType())
                || "BIRTH_CERTIFICATE".equalsIgnoreCase(row.getSourceType()));
    }

    private void selectFilters(String severity, String status) {
        suppressFilterEvents = true;
        severityFilter.getSelectionModel().select(severity);
        statusFilter.getSelectionModel().select(status);
        suppressFilterEvents = false;
        loadNotifications();
    }

    private List<SqliteNotificationDao.NotificationRow> applyQuickFilter(List<SqliteNotificationDao.NotificationRow> loaded) {
        ArrayList<SqliteNotificationDao.NotificationRow> filtered = new ArrayList<>();
        for (SqliteNotificationDao.NotificationRow row : loaded) {
            if (matchesQuickFilter(row)) {
                filtered.add(row);
            }
        }
        return filtered;
    }

    private boolean matchesQuickFilter(SqliteNotificationDao.NotificationRow row) {
        if ("Unread".equals(quickFilter)) {
            return "UNREAD".equalsIgnoreCase(row.getStatus());
        }
        if ("Critical".equals(quickFilter)) {
            return "CRITICAL".equalsIgnoreCase(row.getSeverity());
        }
        if ("Alerts".equals(quickFilter)) {
            return sourceContains(row, "ALERT");
        }
        if ("Reminders".equals(quickFilter)) {
            return sourceContains(row, "REMINDER") || sourceContains(row, "SCHEDULING") || sourceContains(row, "CHECKUP");
        }
        if ("Messages".equals(quickFilter)) {
            return sourceContains(row, "MESSAGE");
        }
        if ("System".equals(quickFilter)) {
            return sourceContains(row, "SYSTEM") || sourceContains(row, "AI") || isCertificateSource(row);
        }
        return true;
    }

    private boolean sourceContains(SqliteNotificationDao.NotificationRow row, String token) {
        String source = nullTo(row.getSourceType(), "").toUpperCase();
        String title = nullTo(row.getTitle(), "").toUpperCase();
        return source.contains(token) || title.contains(token);
    }

    private void renderCounters(List<SqliteNotificationDao.NotificationRow> notifications) {
        int unread = 0;
        int critical = 0;
        int alerts = 0;
        int workflow = 0;
        for (SqliteNotificationDao.NotificationRow row : notifications) {
            if ("UNREAD".equalsIgnoreCase(row.getStatus())) {
                unread++;
            }
            if ("CRITICAL".equalsIgnoreCase(row.getSeverity())) {
                critical++;
            }
            if (sourceContains(row, "ALERT")) {
                alerts++;
            }
            if (sourceContains(row, "REMINDER") || sourceContains(row, "MESSAGE") || sourceContains(row, "SCHEDULING")) {
                workflow++;
            }
        }
        unreadCountLabel.setText(String.valueOf(unread));
        criticalCountLabel.setText(String.valueOf(critical));
        alertsCountLabel.setText(String.valueOf(alerts));
        workflowCountLabel.setText(String.valueOf(workflow));
    }

    private boolean selectById(long id) {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).getId() == id) {
                notificationList.getSelectionModel().select(i);
                notificationList.scrollTo(i);
                showDetail(rows.get(i));
                return true;
            }
        }
        return false;
    }

    private String recommendedAction(SqliteNotificationDao.NotificationRow row) {
        if (row == null) {
            return "";
        }
        if ("CRITICAL".equalsIgnoreCase(row.getSeverity())) {
            return "Review immediately, open the linked clinical item, and notify responsible staff if follow-up is still pending.";
        }
        if ("WARNING".equalsIgnoreCase(row.getSeverity())) {
            return "Review during the current round and mark read once the item is understood.";
        }
        return "Review the notification, open the linked item if needed, then mark read or dismiss.";
    }

    private String severityStyle(String severity) {
        if ("CRITICAL".equalsIgnoreCase(severity)) {
            return "notification-badge-critical";
        }
        if ("WARNING".equalsIgnoreCase(severity)) {
            return "notification-badge-warning";
        }
        return "notification-badge-info";
    }

    private String statusStyle(String status) {
        if ("UNREAD".equalsIgnoreCase(status)) {
            return "notification-badge-unread";
        }
        if ("DISMISSED".equalsIgnoreCase(status)) {
            return "notification-badge-dismissed";
        }
        return "notification-badge-status";
    }

    private void setBadgeStyle(Label label, String selected, String... styles) {
        label.getStyleClass().removeAll(styles);
        label.getStyleClass().add(selected);
    }

    private String notificationType(SqliteNotificationDao.NotificationRow row) {
        String source = nullTo(row.getSourceType(), "").toUpperCase();
        if (source.contains("ALERT")) {
            return "ALERT";
        }
        if (source.contains("REMINDER") || source.contains("SCHEDULING")) {
            return "TASK";
        }
        if (source.contains("MESSAGE")) {
            return "MSG";
        }
        if (source.contains("CERTIFICATE")) {
            return "CERT";
        }
        if (source.contains("AI")) {
            return "AI";
        }
        return "SYS";
    }

    private String dateGroup(SqliteNotificationDao.NotificationRow row) {
        LocalDate date = parseDate(row.getCreatedAt());
        if (date == null) {
            return "Older";
        }
        LocalDate today = LocalDate.now();
        if (date.equals(today)) {
            return "Today";
        }
        if (date.equals(today.minusDays(1))) {
            return "Yesterday";
        }
        return "Older";
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")).toLocalDate();
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDateTime.parse(value, DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss")).toLocalDate();
            } catch (DateTimeParseException ignoredAgain) {
                try {
                    return LocalDate.parse(value.substring(0, Math.min(10, value.length())));
                } catch (Exception ignoredThird) {
                    return null;
                }
            }
        }
    }

    private class NotificationCardCell extends ListCell<SqliteNotificationDao.NotificationRow> {
        @Override
        protected void updateItem(SqliteNotificationDao.NotificationRow row, boolean empty) {
            super.updateItem(row, empty);
            if (empty || row == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            setText(null);
            setGraphic(createCard(row));
        }

        private VBox createCard(SqliteNotificationDao.NotificationRow row) {
            VBox wrapper = new VBox(8);
            int index = getIndex();
            String group = dateGroup(row);
            if (index <= 0 || index >= rows.size() || !group.equals(dateGroup(rows.get(index - 1)))) {
                Label groupLabel = new Label(group);
                groupLabel.getStyleClass().add("notification-group-label");
                wrapper.getChildren().add(groupLabel);
            }

            HBox card = new HBox(12);
            card.getStyleClass().add("notification-card-row");
            if ("UNREAD".equalsIgnoreCase(row.getStatus())) {
                card.getStyleClass().add("notification-card-unread");
            }
            if ("CRITICAL".equalsIgnoreCase(row.getSeverity())) {
                card.getStyleClass().add("notification-card-critical");
            }

            Label type = new Label(notificationType(row));
            type.getStyleClass().add("notification-type-pill");

            VBox text = new VBox(5);
            text.setMaxWidth(Double.MAX_VALUE);
            HBox titleRow = new HBox(8);
            Label unreadDot = new Label("NEW");
            unreadDot.getStyleClass().add("notification-unread-dot");
            unreadDot.setVisible("UNREAD".equalsIgnoreCase(row.getStatus()));
            unreadDot.setManaged("UNREAD".equalsIgnoreCase(row.getStatus()));
            Label title = new Label(row.getTitle());
            title.setWrapText(true);
            title.getStyleClass().add("notification-card-title");
            Label severity = new Label(row.getSeverity());
            severity.getStyleClass().addAll(severityStyle(row.getSeverity()), "notification-card-badge");
            titleRow.getChildren().addAll(unreadDot, title, severity);

            Label preview = new Label(nullTo(row.getMessage(), ""));
            preview.setWrapText(true);
            preview.setMaxHeight(38);
            preview.getStyleClass().add("notification-card-preview");

            Label meta = new Label("Patient " + nullTo(row.getPatientId(), "-") + " | " + nullTo(row.getCreatedAt(), "-"));
            meta.getStyleClass().add("notification-card-meta");
            text.getChildren().addAll(titleRow, preview, meta);
            HBox.setHgrow(text, javafx.scene.layout.Priority.ALWAYS);

            card.getChildren().addAll(type, text);
            wrapper.getChildren().add(card);
            return wrapper;
        }
    }
}
