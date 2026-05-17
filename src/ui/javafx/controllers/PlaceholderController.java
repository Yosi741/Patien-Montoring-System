package ui.javafx.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import ui.javafx.AppShell;
import ui.javafx.FxController;

public class PlaceholderController implements FxController {

    @FXML
    private Label titleLabel;

    @FXML
    private Label subtitleLabel;

    @FXML
    private Label bodyLabel;

    @Override
    public void setAppShell(AppShell appShell) {
    }

    public void setContent(String title, String subtitle, String body) {
        titleLabel.setText(title);
        subtitleLabel.setText(subtitle);
        bodyLabel.setText(body);
    }
}
