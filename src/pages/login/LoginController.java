package pages.login;

import pages.user.profile_settings.SqliteUserDao;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Alert;
import javafx.fxml.FXML;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import app.core.AppShell;
import app.contracts.AppController;
import pages.user.User;

import java.net.URL;

/**
 * Controls LoginView.fxml, including authentication, form clearing, branding, and password recovery.
 */
public class LoginController implements AppController {

    private AppShell appShell;
    private final SqliteUserDao sqliteUserDao = new SqliteUserDao();
    private final ForgotPasswordService forgotPasswordService = new ForgotPasswordService();

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private Label statusLabel;

    @FXML
    private ImageView loginLogoImage;

    /**
     * Initializes the FXML controls after the JavaFX view has been loaded.
     */
    @FXML
    private void initialize() {
        loadLogoImage();
    }

    @Override
    public void setAppShell(AppShell appShell) {
        this.appShell = appShell;
    }

    /**
     * Handles the login UI action.
     */
    @FXML
    private void handleLogin() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText() == null ? "" : passwordField.getText();

        try {
            if (sqliteUserDao.verifyPassword(username, password)) {
                User user = sqliteUserDao.findByUsername(username).orElse(null);
                if (user == null) {
                    showStatus("Invalid username or password.");
                    return;
                }
                appShell.showDashboard(user, "Local database");
                return;
            }
        } catch (Exception e) {
            showStatus("Could not check local database login: " + e.getMessage());
            return;
        }

        showStatus("Invalid username or password.");
    }

    /**
     * Handles the clear login form UI action.
     */
    @FXML
    private void handleClearLoginForm() {
        usernameField.clear();
        passwordField.clear();
        showStatus("");
    }

    /**
     * Handles the forgot password request UI action.
     */
    @FXML
    private void handleForgotPasswordRequest() {
        Dialog<ButtonType> requestDialog = new Dialog<>();
        requestDialog.setTitle("Forgot Password");
        app.helpers.DialogThemeHelper.apply(requestDialog);
        requestDialog.initOwner(usernameField.getScene().getWindow());
        TextField resetUsername = new TextField(usernameField.getText());
        resetUsername.setPromptText("Username");
        TextField staffIdField = new TextField();
        staffIdField.setPromptText("Staff ID");
        GridPane requestGrid = new GridPane();
        requestGrid.setHgap(12);
        requestGrid.setVgap(10);
        requestGrid.add(new Label("Enter your username"), 0, 0);
        requestGrid.add(resetUsername, 1, 0);
        requestGrid.add(new Label("Enter your Staff ID"), 0, 1);
        requestGrid.add(staffIdField, 1, 1);
        requestDialog.getDialogPane().setContent(requestGrid);
        ButtonType submitType = new ButtonType("Continue", ButtonBar.ButtonData.OK_DONE);
        requestDialog.getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, submitType);

        if (requestDialog.showAndWait().orElse(ButtonType.CANCEL) != submitType) {
            return;
        }

        try {
            ForgotPasswordService.ForgotPasswordResult result = forgotPasswordService.requestReset(
                    resetUsername.getText(),
                    staffIdField.getText());
            if (result.status() == ForgotPasswordService.Status.READY_FOR_RESET) {
                showPasswordResetDialog(result.username(), result.staffId());
            } else {
                showForgotPasswordResult(result);
            }
        } catch (Exception e) {
            showStatus("Could not process password reset request: " + e.getMessage());
        }
    }

    /**
     * Displays forgot password result to the user.
     */
    private void showForgotPasswordResult(ForgotPasswordService.ForgotPasswordResult result) {
        if (result == null) {
            showStatus("Could not process password reset request.");
            return;
        }
        switch (result.status()) {
            case EMPTY_CREDENTIALS -> showStatus("Username and Staff ID are required.");
            case CREDENTIALS_MISMATCH -> showStatus("The username and staff ID do not match our records.");
            case READY_FOR_RESET -> showStatus("Enter a new password to finish the reset.");
        }
    }

    /**
     * Displays password reset dialog to the user.
     */
    private void showPasswordResetDialog(String username, String staffId) {
        Dialog<ButtonType> resetDialog = new Dialog<>();
        resetDialog.setTitle("Update Password");
        app.helpers.DialogThemeHelper.apply(resetDialog);
        resetDialog.initOwner(usernameField.getScene().getWindow());

        PasswordField newPasswordField = new PasswordField();
        newPasswordField.setPromptText("New Password");
        PasswordField confirmPasswordField = new PasswordField();
        confirmPasswordField.setPromptText("Confirm Password");

        GridPane resetGrid = new GridPane();
        resetGrid.setHgap(12);
        resetGrid.setVgap(10);
        resetGrid.add(new Label("Username"), 0, 0);
        resetGrid.add(new Label(username), 1, 0);
        resetGrid.add(new Label("Staff ID"), 0, 1);
        resetGrid.add(new Label(staffId), 1, 1);
        resetGrid.add(new Label("New Password"), 0, 2);
        resetGrid.add(newPasswordField, 1, 2);
        resetGrid.add(new Label("Confirm Password"), 0, 3);
        resetGrid.add(confirmPasswordField, 1, 3);
        resetDialog.getDialogPane().setContent(resetGrid);

        ButtonType updateType = new ButtonType("Update Password", ButtonBar.ButtonData.OK_DONE);
        resetDialog.getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, updateType);

        if (resetDialog.showAndWait().orElse(ButtonType.CANCEL) != updateType) {
            return;
        }

        try {
            forgotPasswordService.updatePassword(username, staffId, newPasswordField.getText(), confirmPasswordField.getText());
            showStatus("Password was updated. You can now log in with the new password.");
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Password Updated");
            alert.setHeaderText("Password reset complete");
            alert.setContentText("Password was updated. You can now log in with the new password.");
            app.helpers.DialogThemeHelper.apply(alert);
            alert.initOwner(usernameField.getScene().getWindow());
            alert.showAndWait();
        } catch (Exception e) {
            showStatus(e.getMessage());
        }
    }

    /**
     * Displays status to the user.
     */
    private void showStatus(String message) {
        if (statusLabel == null) {
            System.err.println("LoginController: statusLabel was not injected from FXML.");
            return;
        }

        boolean hasMessage = message != null && !message.isBlank();
        statusLabel.setText(hasMessage ? message : "");
        statusLabel.setVisible(hasMessage);
        statusLabel.setManaged(hasMessage);
    }

    /**
     * Loads logo image for the login workflow.
     */
    private void loadLogoImage() {
        if (loginLogoImage == null) {
            return;
        }
        try {
            URL logoUrl = getClass().getResource("/photo/ICON-Logo.png");
            if (logoUrl == null) {
                showFallbackLogo();
                return;
            }
            loginLogoImage.setImage(new Image(logoUrl.toExternalForm()));
            loginLogoImage.setVisible(true);
            loginLogoImage.setManaged(true);
        } catch (Exception e) {
            showFallbackLogo();
        }
    }

    /**
     * Displays fallback logo to the user.
     */
    private void showFallbackLogo() {
        if (loginLogoImage != null) {
            loginLogoImage.setVisible(false);
            loginLogoImage.setManaged(false);
        }
    }
}
