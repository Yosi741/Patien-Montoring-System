package ui.javafx.controllers;

import dao.SqliteRoomDao;
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
import ui.javafx.AppNavigator;
import ui.javafx.helpers.NotificationHelper;
import users.User;

public class RoomFormController {

    private final RoomWriteService roomWriteService = new RoomWriteService();
    private User currentUser;
    private SqliteRoomDao.RoomDetail existingRoom;
    private boolean saved;

    @FXML private Label titleLabel;
    @FXML private TextField sectionField;
    @FXML private TextField roomNumberField;
    @FXML private TextField capacityField;
    @FXML private ComboBox<String> statusBox;
    @FXML private TextArea notesArea;
    @FXML private Label statusLabel;

    public static boolean showCreateDialog(Window owner, User currentUser) {
        return showDialog(owner, currentUser, null);
    }

    public static boolean showEditDialog(Window owner, User currentUser, SqliteRoomDao.RoomDetail room) {
        return showDialog(owner, currentUser, room);
    }

    private static boolean showDialog(Window owner, User currentUser, SqliteRoomDao.RoomDetail room) {
        try {
            FXMLLoader loader = new FXMLLoader(AppNavigator.resolve("/ui/javafx/views/RoomFormView.fxml"));
            Parent root = loader.load();
            RoomFormController controller = loader.getController();
            controller.prepare(currentUser, room);

            ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setTitle(room == null ? "Add Room" : "Edit Room");
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
            throw new IllegalStateException("Could not open room form: " + e.getMessage(), e);
        }
    }

    @FXML
    private void initialize() {
        statusBox.getItems().setAll("ACTIVE", "MAINTENANCE", "INACTIVE");
        statusBox.getSelectionModel().select("ACTIVE");
        capacityField.setText("1");
        NotificationHelper.showInfo(statusLabel, "SQLite-only room management. Legacy room text storage is unchanged.");
    }

    private void prepare(User currentUser, SqliteRoomDao.RoomDetail room) {
        this.currentUser = currentUser;
        this.existingRoom = room;
        if (room == null) {
            titleLabel.setText("Add Room");
            return;
        }
        titleLabel.setText("Edit Room");
        sectionField.setText(room.getSection());
        roomNumberField.setText(room.getRoomNumber());
        capacityField.setText(String.valueOf(room.getCapacity()));
        statusBox.getSelectionModel().select(room.getStatus());
        notesArea.setText(room.getNotes());
    }

    private boolean save() {
        try {
            int capacity = Integer.parseInt(capacityField.getText().trim());
            RoomWriteService.RoomRequest request = new RoomWriteService.RoomRequest(
                    sectionField.getText(),
                    roomNumberField.getText(),
                    capacity,
                    statusBox.getValue(),
                    notesArea.getText()
            );
            if (existingRoom == null) {
                roomWriteService.createRoom(currentUser, request);
            } else {
                roomWriteService.updateRoom(currentUser, existingRoom.getId(), request);
            }
            saved = true;
            return true;
        } catch (NumberFormatException e) {
            NotificationHelper.showError(statusLabel, "Capacity must be a whole number.");
            return false;
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
            return false;
        }
    }
}
