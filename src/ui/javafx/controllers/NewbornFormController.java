package ui.javafx.controllers;

import Data_Access_Object.SqliteNewbornRecordDao;
import Data_Access_Object.SqlitePatientDao;
import Data_Access_Object.SqliteRoomDao;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Window;
import ui.javafx.services.NewbornService;
import ui.javafx.services.SectionService;
import ui.javafx.AppNavigator;
import ui.javafx.helpers.NotificationHelper;
import users.User;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public class NewbornFormController {

    private static final DateTimeFormatter SQLITE_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final NewbornService newbornService = new NewbornService();
    private final SqlitePatientDao patientDao = new SqlitePatientDao();
    private final SectionService sectionService = new SectionService();
    private final SqliteRoomDao roomDao = new SqliteRoomDao();
    private User currentUser;
    private SqliteNewbornRecordDao.NewbornRecord existingRecord;
    private String motherPatientId;
    private boolean saved;

    @FXML private Label titleLabel;
    @FXML private TextField newbornIdField;
    @FXML private TextField babyNameField;
    @FXML private ComboBox<String> genderBox;
    @FXML private TextField birthTimeField;
    @FXML private TextField birthWeightField;
    @FXML private TextField birthLengthField;
    @FXML private TextField motherPatientIdField;
    @FXML private TextField motherNameField;
    @FXML private TextField fatherNameField;
    @FXML private ComboBox<String> deliveryTypeBox;
    @FXML private ComboBox<String> roomBox;
    @FXML private ComboBox<String> sectionBox;
    @FXML private TextField doctorField;
    @FXML private TextArea notesArea;
    @FXML private Label statusLabel;

    public static boolean showCreateDialog(Window owner, User currentUser, String motherPatientId) {
        return showDialog(owner, currentUser, motherPatientId, null);
    }

    public static boolean showEditDialog(Window owner, User currentUser, SqliteNewbornRecordDao.NewbornRecord record) {
        return showDialog(owner, currentUser, record == null ? "" : record.getMotherPatientId(), record);
    }

    private static boolean showDialog(Window owner, User currentUser, String motherPatientId, SqliteNewbornRecordDao.NewbornRecord record) {
        try {
            FXMLLoader loader = new FXMLLoader(AppNavigator.resolve("/ui/javafx/views/NewbornFormView.fxml"));
            Parent root = loader.load();
            NewbornFormController controller = loader.getController();
            controller.prepare(currentUser, motherPatientId, record);

            ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setTitle(record == null ? "Create Newborn Record" : "Update Newborn Record");
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
            throw new IllegalStateException("Could not open newborn form: " + e.getMessage(), e);
        }
    }

    @FXML
    private void initialize() {
        genderBox.getItems().setAll("MALE", "FEMALE", "UNKNOWN");
        genderBox.getSelectionModel().select("UNKNOWN");
        deliveryTypeBox.getItems().setAll("NATURAL", "C_SECTION", "ASSISTED", "UNKNOWN");
        deliveryTypeBox.getSelectionModel().select("UNKNOWN");
        birthTimeField.setText(LocalDateTime.now().format(SQLITE_DATE_TIME));
        configureInputFilters();
        loadSections();
        sectionBox.valueProperty().addListener((observable, oldValue, newValue) -> loadRoomsForSection(newValue));
        loadRoomsForSection(sectionBox.getValue());
        NotificationHelper.showInfo(statusLabel, "Newborn record workflow. System data is stored in the local database.");
    }

    private void configureInputFilters() {
        installNineDigitFilter(newbornIdField);
        installNineDigitFilter(motherPatientIdField);
        installNameFilter(babyNameField);
        installNameFilter(motherNameField);
        installNameFilter(fatherNameField);
    }

    private void installNineDigitFilter(TextField field) {
        field.textProperty().addListener((observable, oldValue, newValue) -> {
            String clean = newValue == null ? "" : newValue.replaceAll("\\D", "");
            if (clean.length() > 9) {
                clean = clean.substring(0, 9);
            }
            if (!clean.equals(newValue)) {
                field.setText(clean);
            }
        });
    }

    private void installNameFilter(TextField field) {
        field.textProperty().addListener((observable, oldValue, newValue) -> {
            String clean = cleanName(newValue);
            if (!clean.equals(newValue)) {
                field.setText(clean);
            }
        });
    }

    private String cleanName(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (Character.isLetter(ch) || ch == ' ' || ch == '-' || ch == '\'' || ch == '\u2019') {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    private void prepare(User currentUser, String motherPatientId, SqliteNewbornRecordDao.NewbornRecord record) {
        this.currentUser = currentUser;
        this.existingRecord = record;
        this.motherPatientId = motherPatientId == null ? "" : motherPatientId;
        motherPatientIdField.setText(this.motherPatientId);
        if (!this.motherPatientId.isBlank()) {
            findMother();
        }
        if (record == null) {
            titleLabel.setText("Create Newborn Record");
            doctorField.setText(currentUser == null ? "" : currentUser.getUsername());
            selectPreferredSection();
            return;
        }
        titleLabel.setText("Update Newborn Record");
        newbornIdField.setText(record.getNewbornId());
        newbornIdField.setDisable(true);
        babyNameField.setText(record.getBabyName());
        genderBox.getSelectionModel().select(record.getGender());
        birthTimeField.setText(record.getBirthTime());
        birthWeightField.setText(String.valueOf(record.getBirthWeight()));
        birthLengthField.setText(record.getBirthLength() == null ? "" : String.valueOf(record.getBirthLength()));
        motherPatientIdField.setText(record.getMotherPatientId());
        motherNameField.setText(record.getMotherName());
        fatherNameField.setText(record.getFatherName());
        deliveryTypeBox.getSelectionModel().select(record.getDeliveryType());
        selectOrSet(sectionBox, record.getSection());
        loadRoomsForSection(record.getSection());
        selectOrSet(roomBox, record.getRoom());
        doctorField.setText(record.getDoctorOrMidwife());
        notesArea.setText(record.getNotes());
    }

    private boolean save() {
        try {
            NewbornService.NewbornRecordRequest request = new NewbornService.NewbornRecordRequest(
                    newbornIdField.getText(),
                    babyNameField.getText(),
                    genderBox.getValue(),
                    birthTimeField.getText(),
                    birthWeightField.getText(),
                    birthLengthField.getText(),
                    motherPatientIdField.getText(),
                    motherNameField.getText(),
                    fatherNameField.getText(),
                    deliveryTypeBox.getValue(),
                    comboValue(roomBox),
                    comboValue(sectionBox),
                    doctorField.getText(),
                    notesArea.getText()
            );
            if (existingRecord == null) {
                newbornService.createNewbornRecord(currentUser, request);
            } else {
                newbornService.updateNewbornRecord(currentUser, request);
            }
            saved = true;
            return true;
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
            return false;
        }
    }

    @FXML
    private void findMother() {
        String motherId = motherPatientIdField.getText() == null ? "" : motherPatientIdField.getText().trim();
        if (motherId.isBlank()) {
            NotificationHelper.showInfo(statusLabel, "Enter a mother patient ID to look up.");
            return;
        }
        try {
            SqlitePatientDao.PatientDetail mother = patientDao.findDetailById(motherId)
                    .orElseThrow(() -> new IllegalArgumentException("Mother patient ID not found in SQLite: " + motherId));
            motherNameField.setText(mother.getName());
            NotificationHelper.showSuccess(statusLabel, "Mother linked: " + mother.getName()
                    + ". Newborn section remains your selected birth section.");
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
        }
    }

    private void loadSections() {
        LinkedHashSet<String> sections = new LinkedHashSet<>();
        try {
            sections.addAll(sectionService.findActiveSectionNames());
        } catch (Exception e) {
            NotificationHelper.showInfo(statusLabel, "Active section list unavailable: " + e.getMessage());
        }
        sectionBox.getItems().setAll(sections);
        selectPreferredSection();
    }

    private void selectPreferredSection() {
        if (sectionBox == null || sectionBox.getItems().isEmpty()) {
            return;
        }
        if (sectionBox.getItems().contains("Maternity")) {
            sectionBox.getSelectionModel().select("Maternity");
        } else {
            sectionBox.getSelectionModel().selectFirst();
        }
    }

    private void loadRoomsForSection(String section) {
        String selected = comboValue(roomBox);
        List<String> rooms = new ArrayList<>();
        if (section != null && !section.isBlank()) {
            try {
                rooms.addAll(roomDao.findActiveRoomsForSection(section));
            } catch (Exception e) {
                NotificationHelper.showInfo(statusLabel, "Room choices unavailable for section: " + e.getMessage());
            }
        }
        roomBox.getItems().setAll(rooms);
        if (rooms.isEmpty()) {
            NotificationHelper.showInfo(statusLabel, "No active rooms found for the selected section.");
        } else if (selected != null && !selected.isBlank() && rooms.contains(selected)) {
            roomBox.getSelectionModel().select(selected);
        } else {
            roomBox.getSelectionModel().selectFirst();
        }
    }

    private void selectOrSet(ComboBox<String> comboBox, String value) {
        String safeValue = value == null ? "" : value.trim();
        if (safeValue.isBlank()) {
            comboBox.getSelectionModel().clearSelection();
            return;
        }
        if (!comboBox.getItems().contains(safeValue)) {
            comboBox.getItems().add(safeValue);
        }
        comboBox.getSelectionModel().select(safeValue);
    }

    private String comboValue(ComboBox<String> comboBox) {
        if (comboBox == null || comboBox.getValue() == null) {
            return "";
        }
        return comboBox.getValue().trim();
    }
}
