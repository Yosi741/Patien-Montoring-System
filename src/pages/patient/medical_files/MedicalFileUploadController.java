package pages.patient.medical_files;

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
import javafx.stage.FileChooser;
import javafx.stage.Window;
import app.navigation.AppNavigator;
import pages.notification.NotificationHelper;
import pages.user.User;

import java.io.File;

/**
 * Controls MedicalFileUploadView.fxml and collects metadata for a patient file upload.
 */
public class MedicalFileUploadController {

    private final MedicalFileUploadService uploadService = new MedicalFileUploadService();
    private User currentUser;
    private boolean saved;
    private boolean lockedPatientContext;

    @FXML private TextField patientIdField;
    @FXML private TextField filePathField;
    @FXML private ComboBox<String> categoryBox;
    @FXML private TextArea notesArea;
    @FXML private Label uploadedByLabel;
    @FXML private Label statusLabel;

    /**
     * Displays dialog to the user.
     */
    public static boolean showDialog(Window owner, User currentUser, String patientId) {
        try {
            FXMLLoader loader = new FXMLLoader(AppNavigator.resolve("/pages/patient/medical_files/MedicalFileUploadView.fxml"));
            Parent root = loader.load();
            MedicalFileUploadController controller = loader.getController();
            controller.prepare(currentUser, patientId);

            ButtonType uploadButtonType = new ButtonType("Upload", ButtonBar.ButtonData.OK_DONE);
            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setTitle("Upload Medical File");
            app.helpers.DialogThemeHelper.apply(dialog);
            dialog.initOwner(owner);
            dialog.getDialogPane().setContent(root);
            dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, uploadButtonType);
            dialog.getDialogPane().lookupButton(uploadButtonType).addEventFilter(ActionEvent.ACTION, event -> {
                if (!controller.upload()) {
                    event.consume();
                }
            });
            dialog.showAndWait();
            return controller.saved;
        } catch (Exception e) {
            throw new IllegalStateException("Could not open medical file upload form: " + e.getMessage(), e);
        }
    }

    /**
     * Initializes the FXML controls after the JavaFX view has been loaded.
     */
    @FXML
    private void initialize() {
        categoryBox.getItems().setAll("LAB_RESULT", "DISCHARGE_SUMMARY", "IMAGING", "PRESCRIPTION", "OTHER");
        categoryBox.getSelectionModel().select("OTHER");
        NotificationHelper.showInfo(statusLabel, "TXT/CSV/PDF text extraction only. Image OCR is not implemented.");
    }

    /**
     * Handles the browse file UI action.
     */
    @FXML
    private void browseFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Medical File");
        chooser.getExtensionFilters().setAll(
                new FileChooser.ExtensionFilter("Supported Medical Files", "*.txt", "*.csv", "*.pdf", "*.png", "*.jpg", "*.jpeg"),
                new FileChooser.ExtensionFilter("Text", "*.txt"),
                new FileChooser.ExtensionFilter("CSV", "*.csv"),
                new FileChooser.ExtensionFilter("PDF", "*.pdf"),
                new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg")
        );
        File selected = chooser.showOpenDialog(filePathField.getScene().getWindow());
        if (selected != null) {
            filePathField.setText(selected.getAbsolutePath());
        }
    }

    /**
     * Prepares the form with the selected patient record and mode.
     */
    private void prepare(User currentUser, String patientId) {
        this.currentUser = currentUser;
        this.lockedPatientContext = patientId != null && !patientId.isBlank();
        patientIdField.setText(patientId == null ? "" : patientId);
        patientIdField.setEditable(!lockedPatientContext);
        patientIdField.setFocusTraversable(!lockedPatientContext);
        if (lockedPatientContext) {
            patientIdField.getStyleClass().add("locked-context-field");
            patientIdField.setPromptText("Selected patient");
        }
        uploadedByLabel.setText(currentUser == null ? "Unknown" : currentUser.getUsername());
    }

    /**
     * Uploads upload and stores its metadata in SQLite.
     */
    private boolean upload() {
        try {
            if (lockedPatientContext && !patientIdField.getText().trim().matches("\\d{9}")) {
                throw new IllegalArgumentException("This patient uses an old demo ID format. Please update the patient ID to 9 digits or use the cleaned demo database.");
            }
            MedicalFileUploadService.UploadResult result = uploadService.uploadMedicalFile(currentUser,
                    new MedicalFileUploadService.UploadRequest(
                            patientIdField.getText(),
                            filePathField.getText(),
                            categoryBox.getValue(),
                            notesArea.getText()
                    ));
            saved = true;
            NotificationHelper.showSuccess(statusLabel, "Uploaded " + result.getFileId() + " to SQLite. " + result.getExtractedSummary());
            return true;
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
            return false;
        }
    }
}
