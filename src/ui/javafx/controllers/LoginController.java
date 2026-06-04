package ui.javafx.controllers;

import dao.SqliteAuditLogDao;
import dao.SqliteUserDao;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.fxml.FXML;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import services.PasswordResetService;
import ui.javafx.AppShell;
import ui.javafx.FxController;
import users.User;

public class LoginController implements FxController {

    private AppShell appShell;
    private final SqliteUserDao sqliteUserDao = new SqliteUserDao();
    private final SqliteAuditLogDao auditLogDao = new SqliteAuditLogDao();
    private final PasswordResetService passwordResetService = new PasswordResetService();

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private Label statusLabel;

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
                User user = sqliteUserDao.findByUsername(username).get();
                logLogin(user.getUsername(), "Local database");
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
    private void handleClear() {
        usernameField.clear();
        passwordField.clear();
        statusLabel.setText("");
    }

    @FXML
    private void forgotPassword() {
        Dialog<ButtonType> requestDialog = new Dialog<>();
        requestDialog.setTitle("Forgot Password");
        ui.javafx.helpers.DialogThemeHelper.apply(requestDialog);
        requestDialog.initOwner(usernameField.getScene().getWindow());
        TextField resetUsername = new TextField(usernameField.getText());
        resetUsername.setPromptText("Username");
        GridPane requestGrid = new GridPane();
        requestGrid.setHgap(12);
        requestGrid.setVgap(10);
        requestGrid.add(new Label("Username"), 0, 0);
        requestGrid.add(resetUsername, 1, 0);
        requestDialog.getDialogPane().setContent(requestGrid);
        ButtonType createTokenType = new ButtonType("Create Local Token", ButtonBar.ButtonData.OK_DONE);
        requestDialog.getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, createTokenType);

        if (requestDialog.showAndWait().orElse(ButtonType.CANCEL) != createTokenType) {
            return;
        }

        try {
            PasswordResetService.ResetTokenResult token = passwordResetService.createResetToken(resetUsername.getText());
            showResetPasswordDialog(token);
        } catch (Exception e) {
            statusLabel.setText("Could not create reset token: " + e.getMessage());
        }
    }

    private void showResetPasswordDialog(PasswordResetService.ResetTokenResult token) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Reset Password");
        ui.javafx.helpers.DialogThemeHelper.apply(dialog);
        dialog.initOwner(usernameField.getScene().getWindow());

        Label tokenLabel = new Label("Local demo token: " + token.getToken() + "\nExpires: " + token.getExpiresAt());
        tokenLabel.setWrapText(true);
        TextField tokenField = new TextField(token.getToken());
        PasswordField newPassword = new PasswordField();
        newPassword.setPromptText("New password");
        PasswordField confirmPassword = new PasswordField();
        confirmPassword.setPromptText("Confirm new password");

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.add(tokenLabel, 0, 0, 2, 1);
        grid.add(new Label("Token"), 0, 1);
        grid.add(tokenField, 1, 1);
        grid.add(new Label("New password"), 0, 2);
        grid.add(newPassword, 1, 2);
        grid.add(new Label("Confirm password"), 0, 3);
        grid.add(confirmPassword, 1, 3);
        dialog.getDialogPane().setContent(grid);

        ButtonType resetType = new ButtonType("Reset Password", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, resetType);
        dialog.getDialogPane().lookupButton(resetType).addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            if (!newPassword.getText().equals(confirmPassword.getText())) {
                statusLabel.setText("New password and confirmation do not match.");
                event.consume();
                return;
            }
            try {
                passwordResetService.resetPassword(token.getUsername(), tokenField.getText(), newPassword.getText().toCharArray());
                usernameField.setText(token.getUsername());
                passwordField.clear();
                statusLabel.setText("Password reset. Login with the new password.");
            } catch (Exception e) {
                statusLabel.setText("Could not reset password: " + e.getMessage());
                event.consume();
            }
        });
        dialog.showAndWait();
    }

    private void logLogin(String username, String source) {
        try {
            auditLogDao.log(username, "JavaFX login via " + source);
        } catch (Exception e) {
            System.out.println("SQLite login audit skipped: " + e.getMessage());
        }
    }
}
