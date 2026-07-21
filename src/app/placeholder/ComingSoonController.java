package app.placeholder;

import app.contracts.AppController;
import app.core.AppShell;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

/**
 * Controls the reusable placeholder page shown when a routed clinic workflow is not yet active.
 */
public class ComingSoonController implements AppController {

    @FXML
    private Label titleLabel;

    @FXML
    private Label subtitleLabel;

    @FXML
    private Label bodyLabel;

    /**
     * Supplies the application shell used by this controller for navigation.
     */
    @Override
    public void setAppShell(AppShell appShell) {
    }

    /**
     * Updates content for the current object.
     */
    public void setContent(String title, String subtitle, String body) {
        titleLabel.setText(title);
        subtitleLabel.setText(subtitle);
        bodyLabel.setText(body);
    }
}
