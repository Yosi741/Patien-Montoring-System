package ui.javafx.pages.medications;

import Data_Access_Object.SqliteMedicationCatalogDao;
import Data_Access_Object.SqliteMedicationDao;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import ui.javafx.services.MedicationWriteService;
import ui.javafx.services.MedicationCatalogService;
import ui.javafx.AppNavigator;
import ui.javafx.SessionContext;
import ui.javafx.helpers.NotificationHelper;
import ui.javafx.helpers.PermissionHelper;
import users.User;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class MedicationGivenController {

    private static final DateTimeFormatter DISPLAY_DATE_TIME = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
    private static final List<String> DEFAULT_UNITS = List.of("mg", "g", "mcg", "mL", "units", "tablet", "capsule", "puff", "drop", "%");
    private static final List<String> DEFAULT_ROUTES = List.of("Oral", "IV", "IM", "SC", "Inhalation", "Topical", "Eye drops", "Ear drops", "Other");

    private final MedicationWriteService medicationWriteService = new MedicationWriteService();
    private final MedicationCatalogService medicationCatalogService = new MedicationCatalogService();
    private User currentUser;
    private String patientId;
    private boolean saved;

    @FXML private Label patientIdLabel;
    @FXML private ComboBox<SqliteMedicationDao.MedicationRecord> medicationBox;
    @FXML private TextField givenAmountField;
    @FXML private ComboBox<String> givenUnitBox;
    @FXML private ComboBox<String> routeBox;
    @FXML private TextField givenByField;
    @FXML private TextField givenAtField;
    @FXML private ComboBox<String> statusBox;
    @FXML private TextArea notesArea;
    @FXML private VBox overridePane;
    @FXML private CheckBox overrideCheckBox;
    @FXML private TextArea overrideReasonArea;
    @FXML private Label safetyInfoLabel;
    @FXML private Label statusLabel;

    public static boolean showDialog(Window owner, User currentUser, String patientId, SqliteMedicationDao.MedicationRecord selectedMedication) {
        try {
            FXMLLoader loader = new FXMLLoader(AppNavigator.resolve("/ui/javafx/pages/medications/MedicationGivenView.fxml"));
            Parent root = loader.load();
            MedicationGivenController controller = loader.getController();
            controller.prepare(currentUser, patientId, selectedMedication);

            ButtonType saveButtonType = new ButtonType("Record", ButtonBar.ButtonData.OK_DONE);
            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setTitle("Record Medication Given");
            ui.javafx.helpers.DialogThemeHelper.apply(dialog);
            dialog.initOwner(owner);
            dialog.getDialogPane().setContent(root);
            dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, saveButtonType);
            dialog.getDialogPane().lookupButton(saveButtonType).addEventFilter(ActionEvent.ACTION, event -> {
                if (!controller.save()) {
                    event.consume();
                }
            });
            dialog.showAndWait();
            return controller.saved;
        } catch (Exception e) {
            throw new IllegalStateException("Could not open medication administration form: " + e.getMessage(), e);
        }
    }

    @FXML
    private void initialize() {
        statusBox.setItems(FXCollections.observableArrayList("GIVEN", "MISSED", "DELAYED"));
        statusBox.getSelectionModel().select("GIVEN");
        givenUnitBox.getItems().setAll(DEFAULT_UNITS);
        routeBox.getItems().setAll(DEFAULT_ROUTES);
        givenUnitBox.getSelectionModel().select("mg");
        routeBox.getSelectionModel().select("Oral");
        givenByField.setText(SessionContext.username());
        givenByField.setEditable(false);
        givenAtField.setText(LocalDateTime.now().format(DISPLAY_DATE_TIME));
        medicationBox.valueProperty().addListener((observable, oldValue, newValue) -> loadSafetyForMedication(newValue));
        NotificationHelper.showInfo(statusLabel, "Medication administration safety checks are rule-based decision support only.");
    }

    private void prepare(User currentUser, String patientId, SqliteMedicationDao.MedicationRecord selectedMedication) throws Exception {
        this.currentUser = currentUser;
        this.patientId = patientId == null ? "" : patientId;
        patientIdLabel.setText(this.patientId);
        givenByField.setText(currentUser == null ? SessionContext.username() : currentUser.getUsername());

        boolean canOverride = PermissionHelper.canAddMedication(currentUser);
        overridePane.setVisible(canOverride);
        overridePane.setManaged(canOverride);
        overrideCheckBox.setSelected(false);

        List<SqliteMedicationDao.MedicationRecord> activeMedications = medicationWriteService.findActiveMedicationsForPatient(this.patientId);
        medicationBox.setItems(FXCollections.observableArrayList(activeMedications));
        if (selectedMedication != null && selectedMedication.isActive()) {
            medicationBox.getSelectionModel().select(selectedMedication);
        } else if (!activeMedications.isEmpty()) {
            medicationBox.getSelectionModel().selectFirst();
        }
        if (activeMedications.isEmpty()) {
            NotificationHelper.showError(statusLabel, "No active medications found for this patient.");
        }
    }

    private boolean save() {
        try {
            SqliteMedicationDao.MedicationRecord medication = medicationBox.getValue();
            if (medication == null) {
                throw new IllegalArgumentException("Select an active medication first.");
            }
            medicationWriteService.recordMedicationGiven(currentUser, new MedicationWriteService.MedicationEventRequest(
                    medication.getId(),
                    givenAtField.getText(),
                    statusBox.getValue(),
                    notesArea.getText(),
                    givenAmountField.getText(),
                    givenUnitBox.getValue(),
                    routeBox.getValue(),
                    overrideCheckBox.isSelected(),
                    overrideReasonArea.getText()
            ));
            saved = true;
            return true;
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
            return false;
        }
    }

    private void loadSafetyForMedication(SqliteMedicationDao.MedicationRecord medication) {
        if (medication == null) {
            safetyInfoLabel.setText("Select a medication to show safety guidance.");
            return;
        }
        givenAmountField.setText(formatNumber(medication.getDoseAmount()));
        try {
            MedicationWriteService.MedicationSafetyContext context = medicationWriteService.getMedicationSafetyContext(medication.getId());
            SqliteMedicationCatalogDao.MedicationCatalogRecord catalog = context.getCatalog();
            if (catalog != null) {
                List<String> units = csvValues(catalog.getAllowedUnits(), DEFAULT_UNITS);
                List<String> routes = csvValues(catalog.getAllowedRoutes(), DEFAULT_ROUTES);
                givenUnitBox.getItems().setAll(units);
                routeBox.getItems().setAll(routes);
                selectOrFallback(givenUnitBox, medication.getDoseUnit(), units);
                selectOrFallback(routeBox, medication.getRoute(), routes);
            } else {
                givenUnitBox.getItems().setAll(DEFAULT_UNITS);
                routeBox.getItems().setAll(DEFAULT_ROUTES);
                selectOrFallback(givenUnitBox, medication.getDoseUnit(), DEFAULT_UNITS);
                selectOrFallback(routeBox, medication.getRoute(), DEFAULT_ROUTES);
            }
            safetyInfoLabel.setText(safetySummary(context));
        } catch (Exception e) {
            safetyInfoLabel.setText("Could not load medication safety info: " + e.getMessage());
        }
    }

    private String safetySummary(MedicationWriteService.MedicationSafetyContext context) {
        SqliteMedicationDao.MedicationRecord medication = context.getMedication();
        String unit = medication.getDoseUnit() == null || medication.getDoseUnit().isBlank() ? "" : medication.getDoseUnit();
        String maxSingle = context.getMaxSingleDose() == null ? "not set" : formatNumber(context.getMaxSingleDose()) + " " + unit;
        String maxDaily = context.getMaxDailyDose() == null ? "not set" : formatNumber(context.getMaxDailyDose()) + " " + unit;
        String minInterval = context.getMinimumIntervalMinutes() == null ? "not set" : formatNumber(context.getMinimumIntervalMinutes()) + " minutes";
        String lastGiven = context.getLastGivenAt() == null || context.getLastGivenAt().isBlank() ? "none recorded" : context.getLastGivenAt();
        String interactionText = "";
        if (context.getCatalog() != null) {
            try {
                int interactionCount = medicationCatalogService.getInteractionsForMedication(context.getCatalog().getId()).size();
                if (interactionCount > 0) {
                    interactionText = " | active interaction rules: " + interactionCount;
                }
            } catch (Exception ignored) {
                interactionText = "";
            }
        }
        return "Safety guidance: max single dose " + maxSingle
                + " | max daily dose " + maxDaily
                + " | minimum interval " + minInterval
                + " | last given " + lastGiven
                + interactionText;
    }

    private List<String> csvValues(String csv, List<String> fallback) {
        if (csv == null || csv.isBlank()) {
            return fallback;
        }
        ArrayList<String> values = new ArrayList<>();
        for (String part : csv.split(",")) {
            String value = part.trim();
            if (!value.isEmpty()) {
                values.add(value);
            }
        }
        return values.isEmpty() ? fallback : values;
    }

    private void selectOrFallback(ComboBox<String> comboBox, String value, List<String> fallback) {
        if (value != null && comboBox.getItems().contains(value)) {
            comboBox.getSelectionModel().select(value);
            return;
        }
        comboBox.getItems().setAll(fallback);
        if (!comboBox.getItems().isEmpty()) {
            comboBox.getSelectionModel().selectFirst();
        }
    }

    private String formatNumber(Double value) {
        if (value == null) {
            return "";
        }
        return value == Math.rint(value) ? String.valueOf(value.longValue()) : String.valueOf(value);
    }
}
