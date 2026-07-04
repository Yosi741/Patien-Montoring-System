package ui.javafx.pages.medications;

import javafx.event.ActionEvent;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Window;
import javafx.util.StringConverter;
import app.AppNavigator;
import ui.javafx.pages.notifications.NotificationHelper;
import app.helpers.SelectionHelper;
import users.User;

import java.util.ArrayList;
import java.util.List;

public class MedicationFormController {

    private static final List<String> DEFAULT_UNITS = List.of("mg", "g", "mcg", "mL", "units", "tablet", "capsule", "puff", "drop", "%");
    private static final List<String> DEFAULT_ROUTES = List.of("Oral", "IV", "IM", "SC", "Inhalation", "Topical", "Eye drops", "Ear drops", "Other");
    private static final List<String> DEFAULT_FREQUENCIES = List.of("Once daily", "Twice daily", "Three times daily",
            "Every 6 hours", "Every 8 hours", "Every 12 hours", "Weekly", "As needed", "Other");

    private final MedicationWriteService medicationWriteService = new MedicationWriteService();
    private final MedicationCatalogService medicationCatalogService = new MedicationCatalogService();

    private User currentUser;
    private String patientId;
    private SqliteMedicationDao.MedicationRecord existingMedication;
    private boolean saved;
    private boolean loadingCatalog;

    @FXML private Label titleLabel;
    @FXML private Label patientIdLabel;
    @FXML private ComboBox<SqliteMedicationCatalogDao.MedicationCatalogRecord> catalogMedicationBox;
    @FXML private TextField doseAmountField;
    @FXML private ComboBox<String> doseUnitField;
    @FXML private ComboBox<String> routeField;
    @FXML private ComboBox<String> frequencyField;
    @FXML private CheckBox activeCheckBox;
    @FXML private Label safetyInfoLabel;
    @FXML private Label statusLabel;

    public static boolean showCreateDialog(Window owner, User currentUser, String patientId) {
        return showDialog(owner, currentUser, patientId, null);
    }

    public static boolean showEditDialog(Window owner, User currentUser, SqliteMedicationDao.MedicationRecord medication) {
        return showDialog(owner, currentUser, medication == null ? "" : medication.getPatientId(), medication);
    }

    private static boolean showDialog(Window owner, User currentUser, String patientId, SqliteMedicationDao.MedicationRecord medication) {
        try {
            FXMLLoader loader = new FXMLLoader(AppNavigator.resolve("/ui/javafx/pages/medications/MedicationFormView.fxml"));
            Parent root = loader.load();
            MedicationFormController controller = loader.getController();
            controller.prepare(currentUser, patientId, medication);

            ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setTitle(medication == null ? "Add Medication" : "Edit Medication");
            app.helpers.DialogThemeHelper.apply(dialog);
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
            throw new IllegalStateException("Could not open medication form: " + e.getMessage(), e);
        }
    }

    @FXML
    private void initialize() {
        catalogMedicationBox.setEditable(true);
        catalogMedicationBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(SqliteMedicationCatalogDao.MedicationCatalogRecord record) {
                return record == null ? "" : record.getName();
            }

            @Override
            public SqliteMedicationCatalogDao.MedicationCatalogRecord fromString(String value) {
                return catalogMedicationBox.getItems().stream()
                        .filter(row -> row.getName() != null && row.getName().equalsIgnoreCase(value == null ? "" : value.trim()))
                        .findFirst()
                        .orElse(null);
            }
        });

        replaceComboItemsSafely(doseUnitField, DEFAULT_UNITS);
        replaceComboItemsSafely(routeField, DEFAULT_ROUTES);
        replaceComboItemsSafely(frequencyField, DEFAULT_FREQUENCIES);
        safeSelectValue(doseUnitField, "mg");
        safeSelectValue(routeField, "Oral");
        safeSelectValue(frequencyField, "Once daily");
        activeCheckBox.setSelected(true);

        catalogMedicationBox.getSelectionModel().selectedItemProperty()
                .addListener((observable, oldValue, newValue) -> applyCatalogSelection(newValue));
        catalogMedicationBox.getEditor().textProperty().addListener((observable, oldValue, newValue) -> {
            if (!loadingCatalog) {
                loadCatalogMatches(newValue);
            }
        });
        NotificationHelper.showInfo(statusLabel, "Medication order form ready.");
    }

    private void prepare(User currentUser, String patientId, SqliteMedicationDao.MedicationRecord medication) {
        this.currentUser = currentUser;
        this.patientId = patientId == null ? "" : patientId;
        this.existingMedication = medication;
        patientIdLabel.setText(this.patientId);
        loadCatalogMatches("");
        if (medication == null) {
            titleLabel.setText("Add Medication");
            safetyInfoLabel.setText("Select a catalog medication to show dose and route guidance.");
            return;
        }

        titleLabel.setText("Edit Medication");
        if (medication.getCatalogMedicationId() != null && medication.getCatalogMedicationId() > 0) {
            try {
                SqliteMedicationCatalogDao.MedicationCatalogRecord catalogItem =
                        medicationCatalogService.getMedicationCatalogItem(medication.getCatalogMedicationId());
                selectCatalogItem(catalogItem);
            } catch (Exception e) {
                setCatalogEditorTextOnly(medication.getName());
                safetyInfoLabel.setText("Original catalog item is unavailable. The saved medication name is shown.");
            }
        } else {
            setCatalogEditorTextOnly(medication.getName());
        }
        doseAmountField.setText(formatNumber(medication.getDoseAmount()));
        selectOrFallback(doseUnitField, medication.getDoseUnit(), DEFAULT_UNITS);
        selectOrFallback(routeField, medication.getRoute(), DEFAULT_ROUTES);
        selectOrFallback(frequencyField, medication.getFrequency(), DEFAULT_FREQUENCIES);
        activeCheckBox.setSelected(medication.isActive());
    }

    @FXML
    private void registerNewMedication() {
        try {
            boolean changed = MedicationCatalogController.showDialog(catalogMedicationBox.getScene().getWindow(), currentUser);
            if (changed) {
                loadCatalogMatches(catalogMedicationBox.getEditor().getText());
                NotificationHelper.showSuccess(statusLabel, "Medication catalog updated.");
            }
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
        }
    }

    private boolean save() {
        try {
            SqliteMedicationCatalogDao.MedicationCatalogRecord selectedCatalog = catalogMedicationBox.getSelectionModel().getSelectedItem();
            String typedName = catalogMedicationBox.getEditor().getText();
            MedicationWriteService.MedicationRequest request = new MedicationWriteService.MedicationRequest(
                    existingMedication == null ? 0 : existingMedication.getId(),
                    patientId,
                    selectedCatalog == null ? null : selectedCatalog.getId(),
                    selectedCatalog == null ? typedName : selectedCatalog.getName(),
                    "",
                    doseAmountField.getText(),
                    doseUnitField.getValue(),
                    routeField.getValue(),
                    frequencyField.getValue(),
                    activeCheckBox.isSelected()
            );
            if (existingMedication == null) {
                medicationWriteService.addMedication(currentUser, request);
            } else {
                medicationWriteService.updateMedication(currentUser, request);
            }
            saved = true;
            return true;
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
            return false;
        }
    }

    private void loadCatalogMatches(String searchText) {
        try {
            loadingCatalog = true;
            String typed = searchText == null ? "" : searchText;
            List<SqliteMedicationCatalogDao.MedicationCatalogRecord> matches =
                    medicationCatalogService.searchMedicationsByName(typed);
            replaceComboItemsSafely(catalogMedicationBox, matches);
            if (matches.isEmpty()) {
                safetyInfoLabel.setText("No catalog medication selected.");
            }
            catalogMedicationBox.getEditor().setText(typed);
            catalogMedicationBox.getEditor().positionCaret(typed.length());
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, "Could not load medication catalog: " + e.getMessage());
        } finally {
            loadingCatalog = false;
        }
    }

    private void applyCatalogSelection(SqliteMedicationCatalogDao.MedicationCatalogRecord catalogItem) {
        if (catalogItem == null) {
            replaceComboItemsSafely(doseUnitField, DEFAULT_UNITS);
            replaceComboItemsSafely(routeField, DEFAULT_ROUTES);
            safeSelectValue(doseUnitField, "mg");
            safeSelectValue(routeField, "Oral");
            safetyInfoLabel.setText("Select a catalog medication to show dose and route guidance.");
            return;
        }
        List<String> allowedUnits = csvValues(catalogItem.getAllowedUnits(), DEFAULT_UNITS);
        List<String> allowedRoutes = csvValues(catalogItem.getAllowedRoutes(), DEFAULT_ROUTES);
        replaceComboItemsSafely(doseUnitField, allowedUnits);
        replaceComboItemsSafely(routeField, allowedRoutes);
        selectOrFallback(doseUnitField, catalogItem.getDefaultUnit(), allowedUnits);
        if (catalogItem.getDefaultRoute() != null && !catalogItem.getDefaultRoute().isBlank()) {
            selectOrFallback(routeField, catalogItem.getDefaultRoute(), allowedRoutes);
        } else if (!allowedRoutes.isEmpty()) {
            SelectionHelper.safeSelectFirst(routeField);
        }
        safetyInfoLabel.setText(safetySummary(catalogItem));
    }

    private void selectCatalogItem(SqliteMedicationCatalogDao.MedicationCatalogRecord catalogItem) {
        if (catalogItem == null) {
            setCatalogEditorTextOnly("");
            return;
        }
        loadingCatalog = true;
        List<SqliteMedicationCatalogDao.MedicationCatalogRecord> items = new ArrayList<>(catalogMedicationBox.getItems());
        if (!items.contains(catalogItem)) {
            items.add(catalogItem);
        }
        replaceComboItemsSafely(catalogMedicationBox, items);
        int index = catalogMedicationBox.getItems().indexOf(catalogItem);
        if (index >= 0) {
            catalogMedicationBox.getSelectionModel().select(index);
        }
        catalogMedicationBox.getEditor().setText(catalogItem.getName());
        loadingCatalog = false;
        applyCatalogSelection(catalogItem);
    }

    private void setCatalogEditorTextOnly(String text) {
        loadingCatalog = true;
        clearComboSelectionSafely(catalogMedicationBox);
        catalogMedicationBox.getEditor().setText(text == null ? "" : text);
        catalogMedicationBox.getEditor().positionCaret(catalogMedicationBox.getEditor().getText().length());
        loadingCatalog = false;
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

    private String safetySummary(SqliteMedicationCatalogDao.MedicationCatalogRecord item) {
        String maxSingle = formatNumber(item.getMaxSingleDose());
        String maxDaily = formatNumber(item.getMaxDailyDose());
        String minInterval = formatNumber(item.getMinIntervalMinutes());
        StringBuilder text = new StringBuilder("Catalog safety: ");
        text.append("max single dose ").append(maxSingle.isBlank() ? "not set" : maxSingle + " " + item.getDefaultUnit());
        text.append(" | max daily ").append(maxDaily.isBlank() ? "not set" : maxDaily + " " + item.getDefaultUnit());
        text.append(" | min interval ").append(minInterval.isBlank() ? "not set" : minInterval + " minutes");
        if (item.isRequiresDoctorOverride()) {
            text.append(" | doctor override required");
        }
        if (item.getDangerNotes() != null && !item.getDangerNotes().isBlank()) {
            text.append(" | ").append(item.getDangerNotes());
        }
        return text.toString();
    }

    private void selectOrFallback(ComboBox<String> comboBox, String value, List<String> fallback) {
        if (value != null && comboBox.getItems().contains(value)) {
            comboBox.getSelectionModel().select(value);
        } else {
            replaceComboItemsSafely(comboBox, fallback);
            SelectionHelper.safeSelectFirst(comboBox);
        }
    }

    private void safeSelectValue(ComboBox<String> comboBox, String value) {
        if (comboBox == null || comboBox.getItems() == null || comboBox.getItems().isEmpty()) {
            SelectionHelper.safeClearSelection(comboBox);
            return;
        }
        if (value != null && comboBox.getItems().contains(value)) {
            comboBox.getSelectionModel().select(value);
        } else {
            SelectionHelper.safeSelectFirst(comboBox);
        }
    }

    private <T> void clearComboSelectionSafely(ComboBox<T> comboBox) {
        if (comboBox == null) {
            return;
        }
        comboBox.hide();
        if (comboBox.getSelectionModel() != null) {
            comboBox.getSelectionModel().clearSelection();
        }
        comboBox.setValue(null);
    }

    private <T> void replaceComboItemsSafely(ComboBox<T> comboBox, List<T> newItems) {
        if (comboBox == null) {
            return;
        }
        clearComboSelectionSafely(comboBox);
        comboBox.setItems(FXCollections.observableArrayList(newItems == null ? List.of() : newItems));
    }

    private String formatNumber(Double value) {
        if (value == null) {
            return "";
        }
        return value == Math.rint(value) ? String.valueOf(value.longValue()) : String.valueOf(value);
    }
}
