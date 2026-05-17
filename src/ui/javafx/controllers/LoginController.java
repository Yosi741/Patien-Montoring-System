package ui.javafx.controllers;

import database.UserStorage;
import dao.SqliteAuditLogDao;
import dao.SqliteUserDao;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import security.PasswordHasher;
import ui.javafx.AppShell;
import ui.javafx.FxController;
import users.User;

public class LoginController implements FxController {

    private AppShell appShell;
    private final SqliteUserDao sqliteUserDao = new SqliteUserDao();
    private final SqliteAuditLogDao auditLogDao = new SqliteAuditLogDao();

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private Label statusLabel;

    @FXML
    private Label databaseStatusLabel;

    @Override
    public void setAppShell(AppShell appShell) {
        this.appShell = appShell;
        databaseStatusLabel.setText(appShell.getDatabaseStatus());
    }

    @FXML
    private void handleLogin() {
        String username = usernameField.getText().trim();
        char[] password = passwordField.getText().toCharArray();

        try {
            if (sqliteUserDao.verifyPassword(username, password)) {
                User user = sqliteUserDao.findByUsername(username).get();
                logLogin(user.getUsername(), "SQLite");
                appShell.showDashboard(user, "SQLite");
                return;
            }
        } catch (Exception e) {
            statusLabel.setText("SQLite login unavailable, checking legacy users.");
        }

        for (User user : UserStorage.loadUsers()) {
            if (user.getUsername().equals(username) && passwordMatches(password, user.getPassword())) {
                logLogin(user.getUsername(), "Legacy text-file fallback");
                appShell.showDashboard(user, "Legacy text-file fallback");
                return;
            }
        }

        statusLabel.setText("Invalid username or password.");
    }

    @FXML
    private void handleClear() {
        usernameField.clear();
        passwordField.clear();
        statusLabel.setText("");
    }

    private boolean passwordMatches(char[] inputPassword, String storedPassword) {
        if (PasswordHasher.isHashed(storedPassword)) {
            return PasswordHasher.verify(inputPassword, storedPassword);
        }
        return new String(inputPassword).equals(storedPassword);
    }

    private void logLogin(String username, String source) {
        try {
            auditLogDao.log(username, "JavaFX login via " + source);
        } catch (Exception e) {
            System.out.println("SQLite login audit skipped: " + e.getMessage());
        }
    }
}
