package pages.login;

import pages.user.dao.SqliteUserDao;
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
import javafx.scene.layout.StackPane;
import app.AppShell;
import app.FxController;
import pages.user.User;

import java.net.URL;

public class LoginController implements FxController {

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

    @FXML
    private StackPane fallbackLogoMark;

    @FXML
    private void initialize() {
        loadLogoImage();
    }

    @Override
    public void setAppShell(AppShell appShell) {
        this.appShell = appShell;
    }

    @FXML
    private void handleLogin() {
        String username = usernameField.getText().trim();
        char[] password = passwordField.getText().toCharArray();

        try {
            if (sqliteUserDao.verifyPassword(username, password)) {
                User user = sqliteUserDao.findByUsername(username).orElse(null);
                if (user == null) {
                    statusLabel.setText("Invalid username or password.");
                    return;
                }
                appShell.showDashboard(user, "Local database");
                return;
            }
        } catch (Exception e) {
            statusLabel.setText("Could not check local database login: " + e.getMessage());
            return;
        }

        statusLabel.setText("Invalid username or password.");
    }

    @FXML
    private void handleClearLoginForm() {
        usernameField.clear();
        passwordField.clear();
        statusLabel.setText("");
    }

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
        ButtonType submitType = new ButtonType("Request Reset", ButtonBar.ButtonData.OK_DONE);
        requestDialog.getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, submitType);

        if (requestDialog.showAndWait().orElse(ButtonType.CANCEL) != submitType) {
            return;
        }

        try {
            ForgotPasswordService.ForgotPasswordResult result = forgotPasswordService.requestReset(
                    resetUsername.getText(),
                    staffIdField.getText());
            showForgotPasswordResult(result);
        } catch (Exception e) {
            statusLabel.setText("Could not process password reset request: " + e.getMessage());
        }
    }

    private void showForgotPasswordResult(ForgotPasswordService.ForgotPasswordResult result) {
        if (result == null) {
            statusLabel.setText("Could not process password reset request.");
            return;
        }
        switch (result.status()) {
            case EMPTY_CREDENTIALS -> statusLabel.setText("Username and Staff ID are required.");
            case CREDENTIALS_MISMATCH -> statusLabel.setText("The username and staff ID do not match our records.");
            case NO_EMAIL_CONFIGURED -> statusLabel.setText("No email is configured for this account. Please contact an administrator.");
            case EMAIL_QUEUED -> {
                statusLabel.setText("Password reset email was queued for the registered email address.");
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Password Reset Requested");
                alert.setHeaderText("Reset request queued");
                String maskedEmail = result.maskedEmail();
                if (maskedEmail == null || maskedEmail.isBlank()) {
                    alert.setContentText("Password reset email was queued for the registered email address.");
                } else {
                    alert.setContentText("Password reset email was queued for " + maskedEmail + ".");
                }
                app.helpers.DialogThemeHelper.apply(alert);
                alert.initOwner(usernameField.getScene().getWindow());
                alert.showAndWait();
            }
        }
    }

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
            if (fallbackLogoMark != null) {
                fallbackLogoMark.setVisible(false);
                fallbackLogoMark.setManaged(false);
            }
        } catch (Exception e) {
            showFallbackLogo();
        }
    }

    private void showFallbackLogo() {
        if (loginLogoImage != null) {
            loginLogoImage.setVisible(false);
            loginLogoImage.setManaged(false);
        }
        if (fallbackLogoMark != null) {
            fallbackLogoMark.setVisible(true);
            fallbackLogoMark.setManaged(true);
        }
    }
}
