package ui.javafx.controllers;

import dao.SqliteSectionDao;
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
import services.RoomWriteService;
import services.SectionService;
import ui.javafx.AppNavigator;
import ui.javafx.helpers.NotificationHelper;
import users.User;

public class SectionFormController {

    private final SectionService sectionService = new SectionService();
    private final RoomWriteService roomWriteService = new RoomWriteService();
    private User currentUser;
    private SqliteSectionDao.SectionRecord existingSection;
    private boolean saved;
    private String lastSuggestedPrefix = "";

    @FXML private Label titleLabel;
    @FXML private TextField nameField;
    @FXML private ComboBox<String> statusBox;
    @FXML private TextArea notesArea;
    @FXML private TextField roomPrefixField;
    @FXML private ComboBox<String> floorNumberBox;
    @FXML private Label roomCountLabel;
    @FXML private TextField roomCountField;
    @FXML private TextField capacityField;
    @FXML private Label currentRoomsCountLabel;
    @FXML private Label roomEstimateLabel;
    @FXML private Label statusLabel;

    public static boolean showCreateDialog(Window owner, User currentUser) {
        return showDialog(owner, currentUser, null);
    }

    public static boolean showEditDialog(Window owner, User currentUser, SqliteSectionDao.SectionRecord section) {
        return showDialog(owner, currentUser, section);
    }

    private static boolean showDialog(Window owner, User currentUser, SqliteSectionDao.SectionRecord section) {
        try {
            FXMLLoader loader = new FXMLLoader(AppNavigator.resolve("/ui/javafx/views/SectionFormView.fxml"));
            Parent root = loader.load();
            SectionFormController controller = loader.getController();
            controller.prepare(currentUser, section);

            ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setTitle(section == null ? "Add Section" : "Edit Section");
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
            throw new IllegalStateException("Could not open section form: " + e.getMessage(), e);
        }
    }

    @FXML
    private void initialize() {
        statusBox.getItems().setAll("ACTIVE", "INACTIVE");
        statusBox.getSelectionModel().select("ACTIVE");
        floorNumberBox.getItems().setAll("-1", "0", "1", "2", "3", "4");
        floorNumberBox.getSelectionModel().select("1");

        nameField.textProperty().addListener((observable, oldValue, newValue) -> updateSuggestedPrefix(newValue));
        roomPrefixField.textProperty().addListener((observable, oldValue, newValue) -> updateRoomPreview());
        floorNumberBox.valueProperty().addListener((observable, oldValue, newValue) -> refreshCurrentCountAndPreview());
        roomCountField.textProperty().addListener((observable, oldValue, newValue) -> updateRoomPreview());
        capacityField.textProperty().addListener((observable, oldValue, newValue) -> updateRoomPreview());
        NotificationHelper.showInfo(statusLabel, "Local database section management. System data is stored in the local database.");
    }

    private void prepare(User currentUser, SqliteSectionDao.SectionRecord section) {
        this.currentUser = currentUser;
        this.existingSection = section;
        if (section == null) {
            titleLabel.setText("Add Section");
            roomCountLabel.setText("Number of Rooms (0-500)");
            currentRoomsCountLabel.setText("New section");
            roomCountField.setText("");
            capacityField.setText("2");
            updateRoomPreview();
            return;
        }

        titleLabel.setText("Edit Section");
        roomCountLabel.setText("Target Rooms Count (0-500)");
        nameField.setText(section.getName());
        updateSuggestedPrefix(section.getName());
        statusBox.getSelectionModel().select(section.getStatus());
        notesArea.setText(section.getNotes());
        capacityField.setText("2");
        refreshCurrentCountAndPreview();
    }

    private boolean save() {
        try {
            SectionService.SectionRequest request = new SectionService.SectionRequest(
                    nameField.getText(),
                    statusBox.getValue(),
                    notesArea.getText());
            if (existingSection == null) {
                sectionService.createSection(currentUser, request);
                RoomWriteService.GenerateRoomsResult result =
                        roomWriteService.createAutomaticRooms(currentUser, autoRoomsRequest());
                NotificationHelper.showSuccess(statusLabel, createMessage(result));
            } else {
                boolean renamed = !existingSection.getName().equalsIgnoreCase(nameField.getText().trim());
                boolean confirmed = !renamed || sectionService.confirmRelatedUpdate(existingSection.getName());
                sectionService.updateSection(currentUser, existingSection.getId(), request, confirmed);
                RoomWriteService.GenerateRoomsResult result =
                        roomWriteService.syncSectionRoomTarget(currentUser, autoRoomsRequest());
                NotificationHelper.showSuccess(statusLabel, updateMessage(result));
            }
            saved = true;
            return true;
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
            return false;
        }
    }

    private String createMessage(RoomWriteService.GenerateRoomsResult result) {
        if (result.getCreatedCount() <= 0) {
            return "Section created without rooms.";
        }
        String message = "Section created. " + result.getCreatedCount()
                + " rooms generated from " + result.getFirstRoom() + " to " + result.getLastRoom() + ".";
        if (result.getSkippedCount() > 0) {
            message += " " + result.getSkippedCount() + " skipped because they already exist.";
        }
        return message;
    }

    private String updateMessage(RoomWriteService.GenerateRoomsResult result) {
        String message = "Section updated.";
        if (result.getCreatedCount() > 0) {
            message += " " + result.getCreatedCount()
                    + " rooms generated from " + result.getFirstRoom() + " to " + result.getLastRoom() + ".";
        }
        if (result.getDeactivatedCount() > 0) {
            message += " " + result.getDeactivatedCount() + " empty rooms marked inactive.";
        }
        if (result.getKeptOccupiedCount() > 0) {
            message += " Some occupied rooms were kept active.";
        }
        return message;
    }

    private void refreshCurrentCountAndPreview() {
        if (existingSection != null) {
            try {
                int current = roomWriteService.countMatchingGeneratedRooms(
                        existingSection.getName(),
                        roomPrefixField.getText(),
                        floorNumberBox.getValue());
                currentRoomsCountLabel.setText(String.valueOf(current));
                if (roomCountField.getText() == null || roomCountField.getText().isBlank()) {
                    roomCountField.setText(String.valueOf(current));
                }
            } catch (Exception e) {
                currentRoomsCountLabel.setText("Unknown");
            }
        }
        updateRoomPreview();
    }

    private void updateRoomPreview() {
        if (roomEstimateLabel == null) {
            return;
        }
        try {
            RoomWriteService.RoomPlanPreview preview = roomWriteService.previewAutomaticRooms(autoRoomsRequest());
            if (existingSection != null) {
                currentRoomsCountLabel.setText(String.valueOf(preview.getCurrentCount()));
            }
            roomEstimateLabel.setText(preview.getMessage());
        } catch (Exception e) {
            roomEstimateLabel.setText("Preview: enter floor, number of rooms, and beds.");
        }
    }

    private RoomWriteService.AutoRoomsRequest autoRoomsRequest() {
        return new RoomWriteService.AutoRoomsRequest(
                nameField.getText(),
                roomPrefixField.getText(),
                floorNumberBox.getValue(),
                parseOptionalInt(roomCountField, "Number of rooms"),
                parseRequiredInt(capacityField, "Default beds per room"));
    }

    private int parseOptionalInt(TextField field, String label) {
        String value = field.getText() == null ? "" : field.getText().trim();
        if (value.isBlank()) {
            return 0;
        }
        return parseRequiredInt(field, label);
    }

    private int parseRequiredInt(TextField field, String label) {
        try {
            return Integer.parseInt(field.getText().trim());
        } catch (Exception e) {
            throw new IllegalArgumentException(label + " must be a whole number.");
        }
    }

    private void updateSuggestedPrefix(String sectionName) {
        if (roomPrefixField == null) {
            return;
        }
        String current = roomPrefixField.getText() == null ? "" : roomPrefixField.getText().trim();
        if (!current.isBlank() && !current.equalsIgnoreCase(lastSuggestedPrefix)) {
            return;
        }
        lastSuggestedPrefix = suggestedPrefix(sectionName);
        roomPrefixField.setText(lastSuggestedPrefix);
    }

    private String suggestedPrefix(String sectionName) {
        String clean = sectionName == null ? "" : sectionName.trim().toUpperCase();
        if (clean.isBlank()) {
            return "";
        }
        if (clean.startsWith("EMERGENCY") || clean.equals("ER")) {
            return "ER";
        }
        if (clean.startsWith("SURGERY")) {
            return "SUR";
        }
        if (clean.startsWith("CARDIOLOGY")) {
            return "CAR";
        }
        if (clean.startsWith("HEART")) {
            return "HEART";
        }
        StringBuilder prefix = new StringBuilder();
        for (int i = 0; i < clean.length() && prefix.length() < 5; i++) {
            char ch = clean.charAt(i);
            if (Character.isLetterOrDigit(ch)) {
                prefix.append(ch);
            }
        }
        return prefix.toString();
    }
}
