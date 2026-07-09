package pages.notification;

import app.core.AppShell;
import app.contracts.AppController;
import app.helpers.PermissionHelper;
import app.helpers.SelectionHelper;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import users.Session;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class NotificationCenterController implements AppController {

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
    @FXML private Label systemCountLabel;
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
            long selectedId = currentSelectedRow() == null ? -1 : currentSelectedRow().getId();
            List<SqliteNotificationDao.NotificationRow> loaded = notificationService.findForCurrentUser(
                    Session.getCurrentUser(),
                    valueOf(severityFilter),
                    valueOf(statusFilter),
                    patientSearchField == null ? "" : patientSearchField.getText(),
                    valueOf(dateRangeFilter)
            );
            List<SqliteNotificationDao.NotificationRow> visibleRows = applyQuickFilter(filterAlertScope(loaded));
            SelectionHelper.safeClearSelection(notificationList);
            rows.setAll(visibleRows);
            renderCounters(visibleRows);
            updateStatusLabel(visibleRows.size());
            if (selectedId > -1) {
                selectById(selectedId);
            }
            appShell.refreshNotificationCount();
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, "Could not load alerts: " + e.getMessage());
        }
    }

    @FXML
    private void showAll() {
        quickFilter = "All";
        loadNotifications();
    }

    @FXML
    private void showUnread() {
        quickFilter = "Unread";
        loadNotifications();
    }

    @FXML
    private void showCritical() {
        quickFilter = "Critical";
        loadNotifications();
    }

    @FXML
    private void showSystem() {
        quickFilter = "System";
        loadNotifications();
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
        severityFilter.valueProperty().addListener((obs, old, value) -> reloadCustom());
        statusFilter.valueProperty().addListener((obs, old, value) -> reloadCustom());
        dateRangeFilter.valueProperty().addListener((obs, old, value) -> reloadCustom());
        patientSearchField.textProperty().addListener((obs, old, value) -> reloadCustom());
    }

    private void configureList() {
        notificationList.setItems(rows);
        notificationList.setCellFactory(list -> new AlertRowCell());
    }

    private void reloadCustom() {
        if (!suppressFilterEvents) {
            quickFilter = "Custom";
            loadNotifications();
        }
    }

    private List<SqliteNotificationDao.NotificationRow> filterAlertScope(List<SqliteNotificationDao.NotificationRow> loaded) {
        ArrayList<SqliteNotificationDao.NotificationRow> filtered = new ArrayList<>();
        for (SqliteNotificationDao.NotificationRow row : loaded) {
            if (!isMessageRow(row) && !isReminderRow(row)) {
                filtered.add(row);
            }
        }
        return filtered;
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
            return isUnread(row);
        }
        if ("Critical".equals(quickFilter)) {
            return "CRITICAL".equalsIgnoreCase(row.getSeverity());
        }
        if ("System".equals(quickFilter)) {
            return isSystemRow(row);
        }
        return true;
    }

    private void openAlertDetails(SqliteNotificationDao.NotificationRow row) {
        if (row == null) {
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Alert Details");
        dialog.setHeaderText(null);
        if (notificationList != null && notificationList.getScene() != null) {
            dialog.initOwner(notificationList.getScene().getWindow());
            dialog.getDialogPane().getStylesheets().addAll(notificationList.getScene().getStylesheets());
        }
        dialog.getDialogPane().getStyleClass().add("alert-detail-dialog");

        VBox content = new VBox(14.0);
        content.setPadding(new Insets(4, 4, 4, 4));

        Label titleLabel = new Label(row.getTitle());
        titleLabel.getStyleClass().addAll("notification-section-title", "alert-detail-title");
        titleLabel.setWrapText(true);

        HBox badgeRow = new HBox(10.0);
        badgeRow.setAlignment(Pos.CENTER_LEFT);

        Label typeBadge = new Label(alertTypeText(row));
        typeBadge.getStyleClass().addAll("alert-type-badge", isSystemRow(row) ? "alert-type-system" : "alert-type-alert");

        Label severityBadge = new Label(nullTo(row.getSeverity(), "INFO"));
        severityBadge.getStyleClass().addAll("alert-severity-badge", severityStyleClass(row.getSeverity()));

        Label statusBadge = new Label(isUnread(row) ? "Unread" : "Read");
        statusBadge.getStyleClass().addAll("alert-severity-badge", isUnread(row) ? "alert-status-unread" : "alert-status-read");

        badgeRow.getChildren().addAll(typeBadge, severityBadge, statusBadge);

        VBox detailStack = new VBox(12.0,
                buildDetailBlock("Patient ID", nullTo(row.getPatientId(), "No linked patient")),
                buildDetailBlock("Source", nullTo(row.getSourceType(), "-")),
                buildDetailBlock("Source ID", nullTo(row.getSourceId(), "-")),
                buildDetailBlock("Date / Time", nullTo(row.getCreatedAt(), "-")),
                buildDetailBlock("Full Message", nullTo(row.getMessage(), "No message text available."))
        );

        content.getChildren().addAll(titleLabel, badgeRow, detailStack);

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefViewportWidth(680);
        scrollPane.setPrefViewportHeight(420);
        scrollPane.getStyleClass().add("alert-detail-scroll");

        dialog.getDialogPane().setContent(scrollPane);

        ButtonType closeType = new ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().add(closeType);

        ButtonType openPatientType = null;
        if (row.getPatientId() != null && !row.getPatientId().isBlank()) {
            openPatientType = new ButtonType("Open Patient File", ButtonBar.ButtonData.LEFT);
            dialog.getDialogPane().getButtonTypes().add(openPatientType);
        }

        ButtonType markReadType = null;
        if (isUnread(row)) {
            markReadType = new ButtonType("Mark Read", ButtonBar.ButtonData.APPLY);
            dialog.getDialogPane().getButtonTypes().add(markReadType);
        }

        styleDialogButton(dialog, closeType, "notification-action-secondary");
        if (openPatientType != null) {
            styleDialogButton(dialog, openPatientType, "notification-action-primary");
        }
        if (markReadType != null) {
            styleDialogButton(dialog, markReadType, "notification-action-secondary");
        }

        dialog.setResultConverter(buttonType -> buttonType);
        ButtonType result = dialog.showAndWait().orElse(closeType);
        if (result == openPatientType) {
            appShell.showPatientDetail(row.getPatientId());
        } else if (result == markReadType) {
            markRead(row, false);
        }
    }

    private VBox buildDetailBlock(String label, String value) {
        Label labelNode = new Label(label);
        labelNode.getStyleClass().add("notification-detail-label");

        Label valueNode = new Label(value);
        valueNode.getStyleClass().add("notification-detail-value");
        valueNode.setWrapText(true);

        VBox box = new VBox(6.0, labelNode, valueNode);
        box.getStyleClass().add("notification-detail-soft-card");
        return box;
    }

    private void styleDialogButton(Dialog<ButtonType> dialog, ButtonType buttonType, String styleClass) {
        Button button = (Button) dialog.getDialogPane().lookupButton(buttonType);
        if (button != null) {
            button.getStyleClass().add(styleClass);
            button.setDefaultButton(false);
        }
    }

    private void markRead(SqliteNotificationDao.NotificationRow row, boolean showFeedback) {
        if (row == null || !isUnread(row)) {
            return;
        }
        try {
            notificationService.markRead(Session.getCurrentUser(), row);
            loadNotifications();
            if (showFeedback) {
                NotificationHelper.showSuccess(statusLabel, "Alert marked read.");
            }
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, "Could not mark read: " + e.getMessage());
        }
    }

    private void renderCounters(List<SqliteNotificationDao.NotificationRow> notifications) {
        int unread = 0;
        int critical = 0;
        int activeAlerts = 0;
        int systemUpdates = 0;
        for (SqliteNotificationDao.NotificationRow row : notifications) {
            if (isUnread(row)) {
                unread++;
            }
            if ("CRITICAL".equalsIgnoreCase(row.getSeverity())) {
                critical++;
            }
            if (isAlertRow(row) && !"DISMISSED".equalsIgnoreCase(row.getStatus())) {
                activeAlerts++;
            }
            if (isSystemRow(row)) {
                systemUpdates++;
            }
        }
        unreadCountLabel.setText(String.valueOf(unread));
        criticalCountLabel.setText(String.valueOf(critical));
        alertsCountLabel.setText(String.valueOf(activeAlerts));
        systemCountLabel.setText(String.valueOf(systemUpdates));
    }

    private void updateStatusLabel(int count) {
        if (statusLabel == null) {
            return;
        }
        String suffix;
        switch (quickFilter) {
            case "Unread":
                suffix = "unread alerts";
                break;
            case "Critical":
                suffix = "critical alerts";
                break;
            case "System":
                suffix = "system updates";
                break;
            default:
                suffix = "alerts";
                break;
        }
        statusLabel.setText("Showing " + count + " " + suffix + ".");
    }

    private boolean selectById(long id) {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).getId() == id) {
                return SelectionHelper.safeSelectIndex(notificationList, i);
            }
        }
        return false;
    }

    private SqliteNotificationDao.NotificationRow currentSelectedRow() {
        return notificationList == null ? null : notificationList.getSelectionModel().getSelectedItem();
    }

    private void startAutoRefresh() {
        if (refreshTimeline != null) {
            refreshTimeline.stop();
        }
        refreshTimeline = new Timeline(new KeyFrame(Duration.seconds(25), event -> loadNotifications()));
        refreshTimeline.setCycleCount(Timeline.INDEFINITE);
        refreshTimeline.play();
    }

    @Override
    public void dispose() {
        if (refreshTimeline != null) {
            refreshTimeline.stop();
            refreshTimeline = null;
        }
    }

    private String valueOf(ComboBox<String> comboBox) {
        return comboBox == null || comboBox.getValue() == null ? "All" : comboBox.getValue();
    }

    private boolean isUnread(SqliteNotificationDao.NotificationRow row) {
        return row != null && "UNREAD".equalsIgnoreCase(row.getStatus());
    }

    private boolean isMessageRow(SqliteNotificationDao.NotificationRow row) {
        return sourceContains(row, "MESSAGE");
    }

    private boolean isReminderRow(SqliteNotificationDao.NotificationRow row) {
        return sourceContains(row, "REMINDER")
                || sourceContains(row, "SCHEDULING")
                || sourceContains(row, "CHECKUP");
    }

    private boolean isAlertRow(SqliteNotificationDao.NotificationRow row) {
        return sourceContains(row, "ALERT");
    }

    private boolean isSystemRow(SqliteNotificationDao.NotificationRow row) {
        return row != null && !isAlertRow(row) && !isMessageRow(row) && !isReminderRow(row);
    }

    private boolean sourceContains(SqliteNotificationDao.NotificationRow row, String token) {
        String source = nullTo(row == null ? null : row.getSourceType(), "").toUpperCase(Locale.ROOT);
        String title = nullTo(row == null ? null : row.getTitle(), "").toUpperCase(Locale.ROOT);
        return source.contains(token) || title.contains(token);
    }

    private String alertTypeText(SqliteNotificationDao.NotificationRow row) {
        return isSystemRow(row) ? "SYSTEM" : "ALERT";
    }

    private String severityStyleClass(String severity) {
        if ("CRITICAL".equalsIgnoreCase(severity)) {
            return "alert-severity-critical";
        }
        if ("WARNING".equalsIgnoreCase(severity)) {
            return "alert-severity-warning";
        }
        return "alert-severity-info";
    }

    private String nullTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String groupedDate(SqliteNotificationDao.NotificationRow row) {
        LocalDate date = parseDate(row == null ? null : row.getCreatedAt());
        if (date == null) {
            return "Earlier";
        }
        LocalDate today = LocalDate.now();
        if (date.equals(today)) {
            return "Today";
        }
        if (date.equals(today.minusDays(1))) {
            return "Yesterday";
        }
        return "Earlier";
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

    private class AlertRowCell extends ListCell<SqliteNotificationDao.NotificationRow> {
        @Override
        protected void updateItem(SqliteNotificationDao.NotificationRow row, boolean empty) {
            super.updateItem(row, empty);
            if (empty || row == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            setText(null);
            setGraphic(createRow(row));
        }

        private VBox createRow(SqliteNotificationDao.NotificationRow row) {
            VBox wrapper = new VBox(8.0);
            String group = groupedDate(row);
            int index = getIndex();
            if (index <= 0 || index >= rows.size() || !group.equals(groupedDate(rows.get(index - 1)))) {
                Label groupLabel = new Label(group);
                groupLabel.getStyleClass().add("notification-group-label");
                wrapper.getChildren().add(groupLabel);
            }

            HBox card = new HBox(14.0);
            card.setAlignment(Pos.CENTER_LEFT);
            card.getStyleClass().addAll("notification-card-row", "alert-row");
            if (isUnread(row)) {
                card.getStyleClass().add("notification-card-unread");
            }

            VBox leading = new VBox(8.0);
            leading.setAlignment(Pos.TOP_LEFT);

            HBox badgeLine = new HBox(8.0);
            badgeLine.setAlignment(Pos.CENTER_LEFT);

            Label typeBadge = new Label(alertTypeText(row));
            typeBadge.getStyleClass().addAll("notification-type-pill", "alert-type-badge",
                    isSystemRow(row) ? "alert-type-system" : "alert-type-alert");

            Label unreadBadge = new Label("Unread");
            unreadBadge.getStyleClass().addAll("notification-unread-dot", "unread-badge");
            unreadBadge.setVisible(isUnread(row));
            unreadBadge.setManaged(isUnread(row));

            badgeLine.getChildren().addAll(typeBadge, unreadBadge);

            VBox textBox = new VBox(5.0);
            Label title = new Label(row.getTitle());
            title.getStyleClass().add("notification-card-title");
            title.setWrapText(true);

            Label preview = new Label(nullTo(row.getMessage(), ""));
            preview.getStyleClass().add("notification-card-preview");
            preview.setWrapText(true);

            Label meta = new Label("Patient ID: " + nullTo(row.getPatientId(), "-") + "   |   " + nullTo(row.getCreatedAt(), "-"));
            meta.getStyleClass().add("notification-card-meta");

            textBox.getChildren().addAll(title, preview, meta);
            HBox.setHgrow(textBox, Priority.ALWAYS);

            leading.getChildren().addAll(badgeLine, textBox);
            HBox.setHgrow(leading, Priority.ALWAYS);

            VBox trailing = new VBox(10.0);
            trailing.setAlignment(Pos.CENTER_RIGHT);

            Label severityBadge = new Label(nullTo(row.getSeverity(), "INFO"));
            severityBadge.getStyleClass().addAll("notification-card-badge", "alert-severity-badge", severityStyleClass(row.getSeverity()));

            HBox actions = new HBox(8.0);
            actions.setAlignment(Pos.CENTER_RIGHT);

            Button viewButton = new Button("\uD83D\uDC41");
            viewButton.getStyleClass().addAll("alert-action-button", "notification-action-secondary");
            viewButton.setOnAction(event -> {
                event.consume();
                openAlertDetails(row);
            });

            Button markReadButton = new Button("\u2713");
            markReadButton.getStyleClass().addAll("alert-action-button", "notification-action-primary");
            markReadButton.setDisable(!isUnread(row));
            markReadButton.setOnAction(event -> {
                event.consume();
                markRead(row, true);
            });

            actions.getChildren().addAll(viewButton, markReadButton);
            trailing.getChildren().addAll(severityBadge, actions);

            card.getChildren().addAll(leading, trailing);
            card.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2) {
                    openAlertDetails(row);
                }
            });

            wrapper.getChildren().add(card);
            return wrapper;
        }
    }
}
