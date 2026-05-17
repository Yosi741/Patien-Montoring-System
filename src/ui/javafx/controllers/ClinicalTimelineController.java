package ui.javafx.controllers;

import dao.ClinicalTimelineDao;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import ui.javafx.AppShell;
import ui.javafx.FxController;

import java.util.Map;

public class ClinicalTimelineController implements FxController {

    private final ClinicalTimelineDao timelineDao = new ClinicalTimelineDao();
    private final ObservableList<ClinicalTimelineDao.TimelineEvent> timelineEvents = FXCollections.observableArrayList();
    private AppShell appShell;
    private String patientId;
    private ClinicalTimelineDao.TimelineEventDetail selectedDetail;
    private ClinicalTimelineDao.TimelineEvent selectedEvent;

    @FXML private Label patientLabel;
    @FXML private Label statusLabel;
    @FXML private ComboBox<String> eventTypeFilter;
    @FXML private TextField searchField;
    @FXML private ListView<ClinicalTimelineDao.TimelineEvent> timelineList;
    @FXML private Label detailTitleLabel;
    @FXML private Label detailSubtitleLabel;
    @FXML private Label detailDescriptionLabel;
    @FXML private Label recommendedActionLabel;
    @FXML private VBox detailFieldsBox;
    @FXML private Button copySummaryButton;
    @FXML private Button openAlertButton;
    @FXML private Button openFileButton;

    @Override
    public void setAppShell(AppShell appShell) {
        this.appShell = appShell;
        eventTypeFilter.setItems(FXCollections.observableArrayList(
                "All Events", "Vitals", "Alerts", "AI Notes", "Files", "Medical History", "Medications", "Shift Handover"));
        eventTypeFilter.getSelectionModel().select("All Events");
        eventTypeFilter.valueProperty().addListener((observable, oldValue, newValue) -> loadTimeline());
        searchField.textProperty().addListener((observable, oldValue, newValue) -> loadTimeline());
        timelineList.setItems(timelineEvents);
        timelineList.setCellFactory(list -> new TimelineEventCell());
        timelineList.setPlaceholder(new Label("No clinical events found for this patient."));
        timelineList.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> showEventDetail(newValue));
        clearDetailPanel();
    }

    public void loadPatient(String patientId) {
        this.patientId = patientId;
        patientLabel.setText("Patient ID: " + patientId);
        loadTimeline();
    }

    @FXML
    private void backToPatientDetail() {
        if (patientId == null || patientId.isBlank()) {
            appShell.showPatientList();
            return;
        }
        appShell.showPatientDetail(patientId);
    }

    @FXML
    private void loadTimeline() {
        if (patientId == null || patientId.isBlank()) {
            return;
        }
        try {
            timelineEvents.setAll(timelineDao.findEvents(patientId, eventTypeFilter.getValue(), searchField.getText()));
            statusLabel.setText("Clinical events loaded from SQLite: " + timelineEvents.size());
            clearDetailPanel();
        } catch (Exception e) {
            statusLabel.setText("Could not load clinical timeline: " + e.getMessage());
            timelineEvents.clear();
            clearDetailPanel();
        }
    }

    @FXML
    private void copySummary() {
        if (selectedDetail == null) {
            statusLabel.setText("Select a timeline event first.");
            return;
        }
        ClipboardContent content = new ClipboardContent();
        content.putString(selectedDetail.toSummary());
        Clipboard.getSystemClipboard().setContent(content);
        statusLabel.setText("Copied selected event summary to clipboard.");
    }

    @FXML
    private void openSelectedAlert() {
        if (selectedEvent == null || !"alerts".equals(selectedEvent.getSourceTable())) {
            statusLabel.setText("Select an alert event first.");
            return;
        }
        try {
            appShell.showAlertCenterForAlert(Long.parseLong(selectedEvent.getSourceId()));
        } catch (NumberFormatException e) {
            statusLabel.setText("Could not open alert: invalid alert ID " + selectedEvent.getSourceId());
        }
    }

    @FXML
    private void openSelectedFileDetails() {
        if (selectedEvent == null || !"medical_files".equals(selectedEvent.getSourceTable())) {
            statusLabel.setText("Select a file event first.");
            return;
        }
        appShell.showMedicalFileDetails(patientId, selectedEvent.getSourceId());
    }

    private void showEventDetail(ClinicalTimelineDao.TimelineEvent event) {
        if (event == null) {
            clearDetailPanel();
            return;
        }
        try {
            selectedEvent = event;
            selectedDetail = timelineDao.findEventDetail(event).orElse(null);
            if (selectedDetail == null) {
                clearDetailPanel();
                detailTitleLabel.setText("Event details unavailable");
                detailSubtitleLabel.setText(event.getSourceTable() + " #" + event.getSourceId());
                detailDescriptionLabel.setText("The source row could not be found in SQLite.");
                return;
            }
            renderDetail(selectedDetail);
        } catch (Exception e) {
            clearDetailPanel();
            detailTitleLabel.setText("Could not load event details");
            detailDescriptionLabel.setText(e.getMessage());
        }
    }

    private void renderDetail(ClinicalTimelineDao.TimelineEventDetail detail) {
        detailTitleLabel.setText(detail.getTitle());
        detailSubtitleLabel.setText(detail.getEventType() + "  |  " + detail.getSourceTable() + " #" + detail.getSourceId());
        detailDescriptionLabel.setText(emptyToDash(detail.getDescription()));
        recommendedActionLabel.setText(detail.getRecommendedAction() == null || detail.getRecommendedAction().isBlank()
                ? "No recommended action recorded for this event type."
                : detail.getRecommendedAction());
        detailFieldsBox.getChildren().clear();
        for (Map.Entry<String, String> entry : detail.getFields().entrySet()) {
            detailFieldsBox.getChildren().add(detailRow(entry.getKey(), entry.getValue()));
        }
        copySummaryButton.setDisable(false);
        openAlertButton.setDisable(selectedEvent == null || !"alerts".equals(selectedEvent.getSourceTable()));
        openFileButton.setDisable(selectedEvent == null || !"medical_files".equals(selectedEvent.getSourceTable()));
    }

    private void clearDetailPanel() {
        selectedDetail = null;
        selectedEvent = null;
        detailTitleLabel.setText("Select an event");
        detailSubtitleLabel.setText("Full read-only event details will appear here.");
        detailDescriptionLabel.setText("No event selected.");
        recommendedActionLabel.setText("No recommended action selected.");
        detailFieldsBox.getChildren().clear();
        copySummaryButton.setDisable(true);
        openAlertButton.setDisable(true);
        openFileButton.setDisable(true);
    }

    private HBox detailRow(String label, String value) {
        Label nameLabel = new Label(label);
        nameLabel.getStyleClass().add("detail-field-name");
        nameLabel.setMinWidth(130);
        nameLabel.setPrefWidth(130);

        Label valueLabel = new Label(emptyToDash(value));
        valueLabel.getStyleClass().add("detail-field-value");
        valueLabel.setWrapText(true);
        HBox.setHgrow(valueLabel, Priority.ALWAYS);

        HBox row = new HBox(10, nameLabel, valueLabel);
        row.getStyleClass().add("detail-field-row");
        return row;
    }

    private String emptyToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private static class TimelineEventCell extends ListCell<ClinicalTimelineDao.TimelineEvent> {
        @Override
        protected void updateItem(ClinicalTimelineDao.TimelineEvent event, boolean empty) {
            super.updateItem(event, empty);
            if (empty || event == null) {
                setText(null);
                setGraphic(null);
                return;
            }

            Label typeBadge = new Label(event.getEventType());
            typeBadge.getStyleClass().addAll("timeline-type-badge", typeStyle(event));

            Label titleLabel = new Label(event.getTitle());
            titleLabel.getStyleClass().add("timeline-title");
            titleLabel.setWrapText(true);

            Label timeLabel = new Label(event.getEventTime());
            timeLabel.getStyleClass().add("timeline-time");

            HBox header = new HBox(10, typeBadge, titleLabel, new Region(), timeLabel);
            HBox.setHgrow(header.getChildren().get(2), javafx.scene.layout.Priority.ALWAYS);
            header.getStyleClass().add("timeline-card-header");

            Label descriptionLabel = new Label(event.getDescription());
            descriptionLabel.getStyleClass().add("timeline-description");
            descriptionLabel.setWrapText(true);

            Label sourceLabel = new Label(event.getSourceTable() + " #" + event.getSourceId());
            sourceLabel.getStyleClass().add("timeline-source");

            VBox card = new VBox(8, header, descriptionLabel, sourceLabel);
            card.getStyleClass().addAll("timeline-card", severityStyle(event));
            card.setPadding(new Insets(14, 16, 14, 16));

            setText(null);
            setGraphic(card);
        }

        private static String typeStyle(ClinicalTimelineDao.TimelineEvent event) {
            switch (event.getEventType()) {
                case "Alerts":
                    return "timeline-type-alert";
                case "AI Notes":
                    return "timeline-type-ai";
                case "Files":
                    return "timeline-type-file";
                case "Medical History":
                    return "timeline-type-history";
                case "Medications":
                    return "timeline-type-medication";
                case "Shift Handover":
                    return "timeline-type-handover";
                default:
                    return "timeline-type-vital";
            }
        }

        private static String severityStyle(ClinicalTimelineDao.TimelineEvent event) {
            String severity = event.getSeverity() == null ? "" : event.getSeverity().toUpperCase();
            if ("EMERGENCY".equals(severity)) {
                return "timeline-emergency";
            }
            if ("CRITICAL".equals(severity)) {
                return "timeline-critical";
            }
            if ("WARNING".equals(severity)) {
                return "timeline-warning";
            }
            return "timeline-normal";
        }
    }
}
