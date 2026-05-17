package ui.javafx.controllers;

import dao.SqliteRoomDao;
import javafx.collections.FXCollections;
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
import services.RoomBedOccupancyService;
import services.RoomWriteService;
import ui.javafx.AppNavigator;
import ui.javafx.helpers.NotificationHelper;
import users.User;

import java.util.List;
import java.util.stream.Collectors;

public class RoomAssignmentController {

    private enum AssignmentAction {
        ASSIGN,
        MOVE,
        REMOVE
    }

    private final RoomWriteService roomWriteService = new RoomWriteService();
    private final SqliteRoomDao roomDao = new SqliteRoomDao();
    private User currentUser;
    private AssignmentAction action;
    private Long fixedRoomId;
    private boolean saved;

    @FXML private Label titleLabel;
    @FXML private TextField patientIdField;
    @FXML private ComboBox<RoomChoice> roomBox;
    @FXML private TextArea notesArea;
    @FXML private Label statusLabel;

    public static boolean showAssignDialog(Window owner, User currentUser, RoomBedOccupancyService.RoomRow room) {
        return showDialog(owner, currentUser, AssignmentAction.ASSIGN, room == null ? 0 : room.getRoomId(),
                room == null ? "" : room.getSelectedPatientId());
    }

    public static boolean showMoveDialog(Window owner, User currentUser, RoomBedOccupancyService.RoomRow room) {
        return showDialog(owner, currentUser, AssignmentAction.MOVE, room == null ? 0 : room.getRoomId(), "");
    }

    public static boolean showMovePatientDialog(Window owner, User currentUser, String patientId) {
        return showDialog(owner, currentUser, AssignmentAction.MOVE, 0, patientId);
    }

    public static boolean showRemoveDialog(Window owner, User currentUser, RoomBedOccupancyService.RoomRow room) {
        return showDialog(owner, currentUser, AssignmentAction.REMOVE, 0,
                room == null ? "" : room.getSelectedPatientId());
    }

    private static boolean showDialog(Window owner, User currentUser, AssignmentAction action, long roomId, String patientId) {
        try {
            FXMLLoader loader = new FXMLLoader(AppNavigator.resolve("/ui/javafx/views/RoomAssignmentView.fxml"));
            Parent root = loader.load();
            RoomAssignmentController controller = loader.getController();
            controller.prepare(currentUser, action, roomId, patientId);

            ButtonType saveButtonType = new ButtonType(action == AssignmentAction.REMOVE ? "Remove" : "Save", ButtonBar.ButtonData.OK_DONE);
            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setTitle(titleFor(action));
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
            throw new IllegalStateException("Could not open room assignment form: " + e.getMessage(), e);
        }
    }

    private static String titleFor(AssignmentAction action) {
        if (action == AssignmentAction.REMOVE) {
            return "Remove Patient From Room";
        }
        if (action == AssignmentAction.MOVE) {
            return "Move Patient";
        }
        return "Assign Patient";
    }

    @FXML
    private void initialize() {
        NotificationHelper.showInfo(statusLabel, "SQLite-only patient location update. Legacy text files are unchanged.");
    }

    private void prepare(User currentUser, AssignmentAction action, long roomId, String patientId) throws Exception {
        this.currentUser = currentUser;
        this.action = action;
        this.fixedRoomId = roomId > 0 ? roomId : null;
        titleLabel.setText(titleFor(action));
        patientIdField.setText(patientId == null ? "" : patientId);

        if (action == AssignmentAction.REMOVE) {
            roomBox.setDisable(true);
            roomBox.setPromptText("No destination room for remove action");
            return;
        }

        List<RoomChoice> choices = roomDao.findAssignableRooms().stream()
                .map(RoomChoice::new)
                .collect(Collectors.toList());
        roomBox.setItems(FXCollections.observableArrayList(choices));
        if (fixedRoomId != null) {
            choices.stream()
                    .filter(choice -> choice.getRoomId() == fixedRoomId)
                    .findFirst()
                    .ifPresent(choice -> {
                        roomBox.getSelectionModel().select(choice);
                        roomBox.setDisable(true);
                    });
        }
        if (roomBox.getSelectionModel().isEmpty() && !choices.isEmpty()) {
            roomBox.getSelectionModel().selectFirst();
        }
    }

    private boolean save() {
        try {
            String patientId = patientIdField.getText();
            if (action == AssignmentAction.REMOVE) {
                roomWriteService.removePatientFromRoom(currentUser, patientId);
            } else {
                RoomChoice room = roomBox.getValue();
                if (room == null) {
                    throw new IllegalArgumentException("Select an active destination room.");
                }
                if (action == AssignmentAction.MOVE) {
                    roomWriteService.movePatientToRoom(currentUser, patientId, room.getRoomId());
                } else {
                    roomWriteService.assignPatientToRoom(currentUser, patientId, room.getRoomId());
                }
            }
            saved = true;
            return true;
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
            return false;
        }
    }

    public static class RoomChoice {
        private final SqliteRoomDao.RoomDetail room;

        private RoomChoice(SqliteRoomDao.RoomDetail room) {
            this.room = room;
        }

        public long getRoomId() {
            return room.getId();
        }

        @Override
        public String toString() {
            return room.getDisplayName();
        }
    }
}
