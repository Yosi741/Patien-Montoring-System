package ui.javafx.controllers;

import dao.SqliteAuditLogDao;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import services.AiRecommendationService;
import ui.javafx.AppShell;
import ui.javafx.FxController;
import ui.javafx.SessionContext;
import users.Session;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

public class AiRecommendationsController implements FxController {

    private final AiRecommendationService recommendationService = new AiRecommendationService();
    private final SqliteAuditLogDao auditLogDao = new SqliteAuditLogDao();
    private final ObservableList<AiRecommendationService.RecommendationBoardRow> rows = FXCollections.observableArrayList();
    private AppShell appShell;

    @FXML private VBox accessDeniedPane;
    @FXML private VBox aiContentPane;
    @FXML private ComboBox<String> sectionFilter;
    @FXML private ComboBox<String> riskLevelFilter;
    @FXML private TableView<AiRecommendationService.RecommendationBoardRow> recommendationTable;
    @FXML private TableColumn<AiRecommendationService.RecommendationBoardRow, String> patientIdColumn;
    @FXML private TableColumn<AiRecommendationService.RecommendationBoardRow, String> patientNameColumn;
    @FXML private TableColumn<AiRecommendationService.RecommendationBoardRow, String> sectionColumn;
    @FXML private TableColumn<AiRecommendationService.RecommendationBoardRow, String> riskScoreColumn;
    @FXML private TableColumn<AiRecommendationService.RecommendationBoardRow, String> riskLevelColumn;
    @FXML private TableColumn<AiRecommendationService.RecommendationBoardRow, String> recommendationColumn;
    @FXML private TableColumn<AiRecommendationService.RecommendationBoardRow, String> reasonsColumn;
    @FXML private TableColumn<AiRecommendationService.RecommendationBoardRow, String> createdAtColumn;
    @FXML private Label statusLabel;

    @Override
    public void setAppShell(AppShell appShell) {
        this.appShell = appShell;
        configureAccess();
        configureFilters();
        configureTable();
        if (isAuthorized()) {
            logAudit("JavaFX AI_RECOMMENDATIONS opened overview");
            loadRecommendations();
        }
    }

    @FXML
    private void loadRecommendations() {
        if (!isAuthorized()) {
            statusLabel.setText("Access denied.");
            return;
        }
        try {
            rows.setAll(recommendationService.loadBoardRows(sectionFilter.getValue(), riskLevelFilter.getValue()));
            recommendationTable.setItems(rows);
            statusLabel.setText("AI recommendations refreshed from SQLite at "
                    + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        } catch (Exception e) {
            statusLabel.setText("Could not load AI recommendations: " + e.getMessage());
        }
    }

    @FXML
    private void clearFilters() {
        sectionFilter.getSelectionModel().select("All");
        riskLevelFilter.getSelectionModel().select("All");
        loadRecommendations();
    }

    @FXML
    private void openSelectedPatient() {
        AiRecommendationService.RecommendationBoardRow selected = recommendationTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            statusLabel.setText("Select a patient row first.");
            return;
        }
        appShell.showPatientDetail(selected.getPatientId());
    }

    @FXML
    private void showDashboard() {
        appShell.showDashboard(Session.getCurrentUser());
    }

    private void configureAccess() {
        boolean authorized = isAuthorized();
        accessDeniedPane.setVisible(!authorized);
        accessDeniedPane.setManaged(!authorized);
        aiContentPane.setVisible(authorized);
        aiContentPane.setManaged(authorized);
    }

    private void configureFilters() {
        ArrayList<String> sections = new ArrayList<>();
        sections.add("All");
        try {
            sections.addAll(recommendationService.findSections());
        } catch (Exception e) {
            statusLabel.setText("Section filters unavailable: " + e.getMessage());
        }
        sectionFilter.setItems(FXCollections.observableArrayList(sections));
        riskLevelFilter.setItems(FXCollections.observableArrayList("All", "CRITICAL", "HIGH", "MODERATE", "LOW", "UNSCORED"));
        sectionFilter.getSelectionModel().select("All");
        riskLevelFilter.getSelectionModel().select("All");
        sectionFilter.valueProperty().addListener((observable, oldValue, newValue) -> loadRecommendations());
        riskLevelFilter.valueProperty().addListener((observable, oldValue, newValue) -> loadRecommendations());
    }

    private void configureTable() {
        patientIdColumn.setCellValueFactory(new PropertyValueFactory<>("patientId"));
        patientNameColumn.setCellValueFactory(new PropertyValueFactory<>("patientName"));
        sectionColumn.setCellValueFactory(new PropertyValueFactory<>("section"));
        riskScoreColumn.setCellValueFactory(new PropertyValueFactory<>("riskScoreText"));
        riskLevelColumn.setCellValueFactory(new PropertyValueFactory<>("riskLevel"));
        recommendationColumn.setCellValueFactory(new PropertyValueFactory<>("latestRecommendation"));
        reasonsColumn.setCellValueFactory(new PropertyValueFactory<>("reasonIndicators"));
        createdAtColumn.setCellValueFactory(new PropertyValueFactory<>("createdAt"));

        recommendationTable.setRowFactory(table -> {
            TableRow<AiRecommendationService.RecommendationBoardRow> row = new TableRow<>();
            row.itemProperty().addListener((observable, oldValue, newValue) -> {
                row.getStyleClass().remove("active-alert-row");
                if (newValue != null && ("CRITICAL".equals(newValue.getRiskLevel()) || "HIGH".equals(newValue.getRiskLevel()))) {
                    row.getStyleClass().add("active-alert-row");
                }
            });
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    appShell.showPatientDetail(row.getItem().getPatientId());
                }
            });
            return row;
        });
    }

    private void logAudit(String action) {
        try {
            auditLogDao.log(SessionContext.username(), action);
        } catch (Exception e) {
            System.out.println("SQLite AI recommendation audit skipped: " + e.getMessage());
        }
    }

    private boolean isAuthorized() {
        String role = roleGroup(SessionContext.role());
        return "ADMIN".equals(role) || "DOCTOR".equals(role) || "NURSE".equals(role);
    }

    private String roleGroup(String role) {
        if (role == null) {
            return "UNKNOWN";
        }
        String upper = role.toUpperCase();
        if (upper.contains("ADMIN")) {
            return "ADMIN";
        }
        if (upper.contains("DOCTOR") || upper.contains("MEDICAL") || upper.contains("DEPARTMENT HEAD")) {
            return "DOCTOR";
        }
        if (upper.contains("NURSE") || upper.contains("NURSING")) {
            return "NURSE";
        }
        return "STAFF";
    }
}
