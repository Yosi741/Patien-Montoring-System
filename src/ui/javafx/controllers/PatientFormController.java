package ui.javafx.controllers;

import Data_Access_Object.SqlitePatientDao;
import Data_Access_Object.SqliteRoomDao;
import Data_Access_Object.SqliteUserDao;
import ui.javafx.services.SectionService;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Window;
import ui.javafx.services.PatientWriteService;
import ui.javafx.AppNavigator;
import ui.javafx.helpers.DatePickerHelper;
import ui.javafx.helpers.NotificationHelper;
import users.User;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class PatientFormController {

    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private final PatientWriteService patientWriteService = new PatientWriteService();
    private final SectionService sectionService = new SectionService();
    private final SqliteRoomDao roomDao = new SqliteRoomDao();
    private final SqliteUserDao userDao = new SqliteUserDao();
    private User currentUser;
    private SqlitePatientDao.PatientDetail existingPatient;
    private boolean saved;

    @FXML private Label titleLabel;
    @FXML private Label helpLabel;
    @FXML private TextField patientIdField;
    @FXML private TextField firstNameField;
    @FXML private TextField lastNameField;
    @FXML private DatePicker birthDatePicker;
    @FXML private ComboBox<String> genderBox;
    @FXML private ComboBox<String> sectionBox;
    @FXML private ComboBox<String> roomBox;
    @FXML private ComboBox<String> statusBox;
    @FXML private ComboBox<String> priorityBox;
    @FXML private ComboBox<String> bloodTypeBox;
    @FXML private ComboBox<String> assignedDoctorBox;
    @FXML private ComboBox<String> assignedStaffBox;
    @FXML private TextArea diagnosisArea;
    @FXML private Label statusLabel;

    public static boolean showCreateDialog(Window owner, User currentUser) {
        return showDialog(owner, currentUser, null);
    }

    public static boolean showEditDialog(Window owner, User currentUser, SqlitePatientDao.PatientDetail patient) {
        return showDialog(owner, currentUser, patient);
    }

    private static boolean showDialog(Window owner, User currentUser, SqlitePatientDao.PatientDetail patient) {
        try {
            FXMLLoader loader = new FXMLLoader(AppNavigator.resolve("/ui/javafx/views/PatientFormView.fxml"));
            Parent root = loader.load();
            PatientFormController controller = loader.getController();
            controller.prepare(currentUser, patient);

            ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setTitle(patient == null ? "Add Patient" : "Edit Patient");
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
            throw new IllegalStateException("Could not open patient form: " + e.getMessage(), e);
        }
    }

    @FXML
    private void initialize() {
        genderBox.getItems().setAll("Female", "Male", "Other", "Unknown");
        statusBox.getItems().setAll("ACTIVE", "DISCHARGED", "DECEASED");
        priorityBox.getItems().setAll("NORMAL", "HIGH", "CRITICAL", "EMERGENCY");
        bloodTypeBox.getItems().setAll("Unknown", "A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-");
        genderBox.getSelectionModel().select("Unknown");
        statusBox.getSelectionModel().select("ACTIVE");
        priorityBox.getSelectionModel().select("NORMAL");
        bloodTypeBox.getSelectionModel().select("Unknown");
        loadSections();
        configureInputFilters();
        DatePickerHelper.configureDdMmYyyy(birthDatePicker);
        sectionBox.valueProperty().addListener((observable, oldValue, newValue) -> {
            loadRoomsForSection(newValue);
            loadAssignedStaffForSection(newValue);
        });
        NotificationHelper.showInfo(statusLabel, "Patient record form. System data is stored in the local database.");
    }

    private void prepare(User currentUser, SqlitePatientDao.PatientDetail patient) {
        this.currentUser = currentUser;
        this.existingPatient = patient;
        if (patient == null) {
            titleLabel.setText("Add Patient");
            helpLabel.setText("Create a patient record in the local database.");
            return;
        }

        titleLabel.setText("Edit Patient");
        helpLabel.setText("Update this patient record only. System data is stored in the local database.");
        patientIdField.setText(patient.getPatientId());
        patientIdField.setDisable(true);
        firstNameField.setText(patient.getFirstName());
        lastNameField.setText(patient.getLastName());
        birthDatePicker.setValue(parseBirthDate(patient.getBirthDate()));
        genderBox.getSelectionModel().select(blankTo(patient.getGender(), "Unknown"));
        selectOrSet(sectionBox, patient.getSection());
        loadRoomsForSection(patient.getSection());
        selectOrSet(roomBox, patient.getRoom());
        loadAssignedStaffForSection(patient.getSection());
        selectOrSet(assignedDoctorBox, patient.getAssignedDoctorUsername());
        selectOrSet(assignedStaffBox, patient.getAssignedStaffUsername());
        statusBox.getSelectionModel().select(normalizeStatus(patient.getStatus()));
        priorityBox.getSelectionModel().select(normalizePriority(patient.getPriority()));
        bloodTypeBox.getSelectionModel().select(normalizeBloodType(patient.getBloodType()));
        diagnosisArea.setText(patient.getDiagnosis());
    }

    private boolean save() {
        try {
            DatePickerHelper.commitEditorText(birthDatePicker);
            SqlitePatientDao.PatientWriteRecord record = buildRecord();
            if (existingPatient == null) {
                patientWriteService.createPatient(currentUser, record);
            } else {
                patientWriteService.updatePatient(currentUser, record);
            }
            saved = true;
            return true;
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
            return false;
        }
    }

    private SqlitePatientDao.PatientWriteRecord buildRecord() {
        return new SqlitePatientDao.PatientWriteRecord(
                patientIdField.getText(),
                firstNameField.getText(),
                lastNameField.getText(),
                birthDatePicker.getValue() == null ? "" : birthDatePicker.getValue().format(DISPLAY_DATE),
                genderBox.getValue(),
                comboValue(sectionBox),
                comboValue(roomBox),
                statusBox.getValue(),
                priorityBox.getValue(),
                bloodTypeBox.getValue(),
                diagnosisArea.getText(),
                comboValue(assignedDoctorBox),
                comboValue(assignedStaffBox)
        );
    }

    private void configureInputFilters() {
        patientIdField.textProperty().addListener((observable, oldValue, newValue) -> {
            String clean = digitsOnly(newValue);
            if (clean.length() > 9) {
                clean = clean.substring(0, 9);
            }
            if (!clean.equals(newValue)) {
                patientIdField.setText(clean);
            }
        });
        installNameFilter(firstNameField);
        installNameFilter(lastNameField);
    }

    private void installNameFilter(TextField field) {
        field.textProperty().addListener((observable, oldValue, newValue) -> {
            String clean = cleanName(newValue);
            if (!clean.equals(newValue)) {
                field.setText(clean);
            }
        });
    }

    private String digitsOnly(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
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

    private void loadSections() {
        LinkedHashSet<String> sections = new LinkedHashSet<>();
        try {
            sections.addAll(sectionService.findActiveSectionNames());
        } catch (Exception e) {
            NotificationHelper.showInfo(statusLabel, "Active section list unavailable: " + e.getMessage());
        }
        try {
            sections.addAll(new SqlitePatientDao().findDistinctSections());
        } catch (Exception ignored) {
            // Patient sections supplement the configured section list.
        }
        sectionBox.getItems().setAll(sections);
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
        if (selected != null && !selected.isBlank()) {
            selectOrSet(roomBox, selected);
        }
    }

    private void loadAssignedStaffForSection(String section) {
        String selectedDoctor = comboValue(assignedDoctorBox);
        String selectedStaff = comboValue(assignedStaffBox);
        List<String> doctors = new ArrayList<>();
        List<String> nursesAndStaff = new ArrayList<>();
        if (section != null && !section.isBlank()) {
            try {
                doctors.addAll(userDao.findActiveUsernamesByRoleGroupAndSection("DOCTOR", section));
                nursesAndStaff.addAll(userDao.findActiveUsernamesByRoleGroupAndSection("NURSE", section));
                for (String username : userDao.findActiveUsernamesByRoleGroupAndSection("STAFF", section)) {
                    if (!nursesAndStaff.contains(username)) {
                        nursesAndStaff.add(username);
                    }
                }
            } catch (Exception e) {
                NotificationHelper.showInfo(statusLabel, "Assigned staff choices unavailable: " + e.getMessage());
            }
        }
        assignedDoctorBox.getItems().setAll(doctors);
        assignedStaffBox.getItems().setAll(nursesAndStaff);
        if (selectedDoctor != null && !selectedDoctor.isBlank()) {
            selectOrSet(assignedDoctorBox, selectedDoctor);
        }
        if (selectedStaff != null && !selectedStaff.isBlank()) {
            selectOrSet(assignedStaffBox, selectedStaff);
        }
        if (doctors.isEmpty() && nursesAndStaff.isEmpty() && section != null && !section.isBlank()) {
            NotificationHelper.showInfo(statusLabel, "No active doctor, nurse, or staff accounts found for " + section + ".");
        }
    }

    private void selectOrSet(ComboBox<String> comboBox, String value) {
        String safeValue = value == null ? "" : value.trim();
        if (safeValue.isBlank()) {
            comboBox.getSelectionModel().clearSelection();
            comboBox.getEditor().clear();
            return;
        }
        if (!comboBox.getItems().contains(safeValue)) {
            comboBox.getItems().add(safeValue);
        }
        comboBox.getSelectionModel().select(safeValue);
        if (comboBox.isEditable() && comboBox.getEditor() != null) {
            comboBox.getEditor().setText(safeValue);
        }
    }

    private String comboValue(ComboBox<String> comboBox) {
        if (comboBox == null) {
            return "";
        }
        String editorText = comboBox.getEditor() == null ? "" : comboBox.getEditor().getText();
        if (editorText != null && !editorText.isBlank()) {
            return editorText.trim();
        }
        return comboBox.getValue() == null ? "" : comboBox.getValue().trim();
    }

    private LocalDate parseBirthDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim(), DISPLAY_DATE);
        } catch (DateTimeParseException e) {
            try {
                return LocalDate.parse(value.trim());
            } catch (DateTimeParseException ignored) {
                return null;
            }
        }
    }

    private String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return "ACTIVE";
        }
        return "Active".equalsIgnoreCase(status) ? "ACTIVE" : status.toUpperCase();
    }

    private String normalizePriority(String priority) {
        if (priority == null || priority.isBlank()) {
            return "NORMAL";
        }
        return "WARNING".equalsIgnoreCase(priority) ? "HIGH" : priority.toUpperCase();
    }

    private String normalizeBloodType(String bloodType) {
        if (bloodType == null || bloodType.isBlank()) {
            return "Unknown";
        }
        String normalized = bloodType.trim();
        return "UNKNOWN".equalsIgnoreCase(normalized) ? "Unknown" : normalized.toUpperCase();
    }
}
