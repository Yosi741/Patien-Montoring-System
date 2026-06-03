package ui.javafx.controllers;

import dao.SqliteMedicationCatalogDao;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Window;
import services.MedicationCatalogService;
import ui.javafx.AppNavigator;
import ui.javafx.helpers.NotificationHelper;
import ui.javafx.helpers.PermissionHelper;
import users.User;

import java.util.List;

public class MedicationCatalogController {

    private final MedicationCatalogService catalogService = new MedicationCatalogService();
    private final ObservableList<SqliteMedicationCatalogDao.MedicationCatalogRecord> catalogRows =
            FXCollections.observableArrayList();

    private User currentUser;
    private SqliteMedicationCatalogDao.MedicationCatalogRecord selectedRecord;
    private boolean changed;

    @FXML private TextField catalogSearchField;
    @FXML private TableView<SqliteMedicationCatalogDao.MedicationCatalogRecord> catalogTable;
    @FXML private TableColumn<SqliteMedicationCatalogDao.MedicationCatalogRecord, String> nameColumn;
    @FXML private TableColumn<SqliteMedicationCatalogDao.MedicationCatalogRecord, String> formTypeColumn;
    @FXML private TableColumn<SqliteMedicationCatalogDao.MedicationCatalogRecord, String> defaultUnitColumn;
    @FXML private TableColumn<SqliteMedicationCatalogDao.MedicationCatalogRecord, String> activeColumn;
    @FXML private Label formTitleLabel;
    @FXML private TextField nameField;
    @FXML private ComboBox<String> formTypeBox;
    @FXML private ComboBox<String> defaultUnitBox;
    @FXML private TextField allowedUnitsField;
    @FXML private TextField allowedRoutesField;
    @FXML private TextField minSingleDoseField;
    @FXML private TextField maxSingleDoseField;
    @FXML private TextField maxDailyDoseField;
    @FXML private TextField minimumIntervalMinutesField;
    @FXML private CheckBox requiresDoctorOverrideCheck;
    @FXML private TextArea dangerNotesArea;
    @FXML private CheckBox activeCheck;
    @FXML private Button deactivateButton;
    @FXML private Label statusLabel;

    public static boolean showDialog(Window owner, User currentUser) {
        try {
            FXMLLoader loader = new FXMLLoader(AppNavigator.resolve("/ui/javafx/views/MedicationCatalogView.fxml"));
            Parent root = loader.load();
            MedicationCatalogController controller = loader.getController();
            controller.prepare(currentUser);

            ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setTitle("Medication Catalog");
            dialog.initOwner(owner);
            dialog.getDialogPane().setContent(root);
            dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CLOSE, saveButtonType);
            dialog.getDialogPane().lookupButton(saveButtonType).addEventFilter(ActionEvent.ACTION, event -> {
                if (!controller.save()) {
                    event.consume();
                }
            });
            dialog.showAndWait();
            return controller.changed;
        } catch (Exception e) {
            throw new IllegalStateException("Could not open medication catalog form: " + e.getMessage(), e);
        }
    }

    @FXML
    private void initialize() {
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        formTypeColumn.setCellValueFactory(new PropertyValueFactory<>("formType"));
        defaultUnitColumn.setCellValueFactory(new PropertyValueFactory<>("defaultUnit"));
        activeColumn.setCellValueFactory(new PropertyValueFactory<>("active"));
        catalogTable.setItems(catalogRows);

        formTypeBox.getItems().setAll("TABLET", "CAPSULE", "LIQUID", "INJECTION", "INHALER", "CREAM", "DROPS", "OTHER");
        formTypeBox.getSelectionModel().select("TABLET");
        activeCheck.setSelected(true);
        refreshSuggestedUnitsAndRoutes("TABLET");

        formTypeBox.valueProperty().addListener((observable, oldValue, newValue) -> refreshSuggestedUnitsAndRoutes(newValue));
        catalogSearchField.textProperty().addListener((observable, oldValue, newValue) -> loadCatalog());
        catalogTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> populateForm(newValue));
    }

    private void prepare(User currentUser) {
        this.currentUser = currentUser;
        boolean canManage = PermissionHelper.canManageMedicationCatalog(currentUser);
        deactivateButton.setVisible(canManage);
        deactivateButton.setManaged(canManage);
        loadCatalog();
        resetForm();
        NotificationHelper.showInfo(statusLabel, canManage
                ? "Register or update medication catalog entries for ordering safety checks."
                : "Medication catalog is read-only for your role.");
    }

    @FXML
    private void resetForm() {
        selectedRecord = null;
        catalogTable.getSelectionModel().clearSelection();
        formTitleLabel.setText("Register New Medication");
        nameField.clear();
        formTypeBox.getSelectionModel().select("TABLET");
        refreshSuggestedUnitsAndRoutes("TABLET");
        minSingleDoseField.clear();
        maxSingleDoseField.clear();
        maxDailyDoseField.clear();
        minimumIntervalMinutesField.clear();
        requiresDoctorOverrideCheck.setSelected(false);
        dangerNotesArea.clear();
        activeCheck.setSelected(true);
    }

    @FXML
    private void deactivateSelected() {
        if (!PermissionHelper.canManageMedicationCatalog(currentUser)) {
            NotificationHelper.showError(statusLabel, "Access denied. Admin or Doctor role is required.");
            return;
        }
        if (selectedRecord == null) {
            NotificationHelper.showError(statusLabel, "Select a catalog medication first.");
            return;
        }
        try {
            catalogService.deactivateCatalogMedication(currentUser, selectedRecord.getId());
            changed = true;
            loadCatalog();
            resetForm();
            NotificationHelper.showSuccess(statusLabel, "Catalog medication deactivated.");
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
        }
    }

    private boolean save() {
        if (!PermissionHelper.canManageMedicationCatalog(currentUser)) {
            NotificationHelper.showError(statusLabel, "Access denied. Admin or Doctor role is required.");
            return false;
        }
        try {
            MedicationCatalogService.CatalogMedicationRequest request = new MedicationCatalogService.CatalogMedicationRequest(
                    nameField.getText(),
                    formTypeBox.getValue(),
                    defaultUnitBox.getValue(),
                    allowedUnitsField.getText(),
                    allowedRoutesField.getText(),
                    minSingleDoseField.getText(),
                    maxSingleDoseField.getText(),
                    maxDailyDoseField.getText(),
                    minimumIntervalMinutesField.getText(),
                    requiresDoctorOverrideCheck.isSelected(),
                    dangerNotesArea.getText(),
                    activeCheck.isSelected()
            );
            if (selectedRecord == null) {
                catalogService.createCatalogMedication(currentUser, request);
                NotificationHelper.showSuccess(statusLabel, "Catalog medication registered.");
            } else {
                catalogService.updateCatalogMedication(currentUser, selectedRecord.getId(), request);
                NotificationHelper.showSuccess(statusLabel, "Catalog medication updated.");
            }
            changed = true;
            loadCatalog();
            resetForm();
            return true;
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
            return false;
        }
    }

    private void loadCatalog() {
        try {
            catalogRows.setAll(catalogService.searchMedicationsByName(catalogSearchField == null ? "" : catalogSearchField.getText()));
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, "Could not load catalog: " + e.getMessage());
        }
    }

    private void populateForm(SqliteMedicationCatalogDao.MedicationCatalogRecord record) {
        selectedRecord = record;
        if (record == null) {
            return;
        }
        formTitleLabel.setText("Update Catalog Medication");
        nameField.setText(record.getName());
        selectOrFallback(formTypeBox, record.getFormType());
        refreshSuggestedUnitsAndRoutes(record.getFormType());
        selectOrFallback(defaultUnitBox, record.getDefaultUnit());
        allowedUnitsField.setText(record.getAllowedUnits());
        allowedRoutesField.setText(record.getAllowedRoutes());
        minSingleDoseField.setText(formatNumber(record.getMinSingleDose()));
        maxSingleDoseField.setText(formatNumber(record.getMaxSingleDose()));
        maxDailyDoseField.setText(formatNumber(record.getMaxDailyDose()));
        minimumIntervalMinutesField.setText(formatNumber(record.getMinIntervalMinutes()));
        requiresDoctorOverrideCheck.setSelected(record.isRequiresDoctorOverride());
        dangerNotesArea.setText(record.getDangerNotes());
        activeCheck.setSelected(record.isActive());
    }

    private void refreshSuggestedUnitsAndRoutes(String formType) {
        List<String> units = catalogService.getAllowedUnitsForFormType(formType);
        List<String> routes = catalogService.getAllowedRoutesForFormType(formType);
        defaultUnitBox.getItems().setAll(units);
        if (!units.isEmpty()) {
            defaultUnitBox.getSelectionModel().selectFirst();
        }
        allowedUnitsField.setText(String.join(", ", units));
        allowedRoutesField.setText(String.join(", ", routes));
    }

    private void selectOrFallback(ComboBox<String> comboBox, String value) {
        if (value != null && comboBox.getItems().contains(value)) {
            comboBox.getSelectionModel().select(value);
        } else if (!comboBox.getItems().isEmpty()) {
            comboBox.getSelectionModel().selectFirst();
        }
    }

    private String formatNumber(Double value) {
        if (value == null) {
            return "";
        }
        if (value == Math.rint(value)) {
            return String.valueOf(value.longValue());
        }
        return String.valueOf(value);
    }
}
