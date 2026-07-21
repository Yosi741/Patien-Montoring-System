package app.navigation;

import app.contracts.AppController;
import app.core.AppShell;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Resolves FXML resources and loads JavaFX views with their controllers for the application shell.
 */
public class AppNavigator {

    private final AppShell appShell;

    /**
     * Creates a app navigator from the supplied record values.
     */
    public AppNavigator(AppShell appShell) {
        this.appShell = appShell;
    }

    /**
     * Loads load for the application workflow.
     */
    public Parent load(String fxmlPath) {
        return loadView(fxmlPath).parent;
    }

    /**
     * Loads view for the application workflow.
     */
    public LoadedView loadView(String fxmlPath) {
        try {
            FXMLLoader loader = new FXMLLoader(resolve(fxmlPath));
            Parent parent = loader.load();
            Object controller = loader.getController();
            if (controller instanceof AppController) {
                ((AppController) controller).setAppShell(appShell);
            }
            appShell.applyThemeTo(parent);
            return new LoadedView(parent, controller);
        } catch (IOException e) {
            throw new IllegalStateException("Could not load JavaFX view: " + fxmlPath, e);
        }
    }

    /**
     * Resolves resolve for the current workflow.
     */
    public static URL resolve(String path) {
        File sourceFile = new File("src" + path.replace("/", File.separator));
        if (sourceFile.exists()) {
            try {
                return sourceFile.toURI().toURL();
            } catch (MalformedURLException e) {
                throw new IllegalStateException("Invalid JavaFX resource path: " + path, e);
            }
        }

        URL resource = AppNavigator.class.getResource(path);
        if (resource != null) {
            return resource;
        }

        throw new IllegalStateException("JavaFX resource not found: " + path);
    }

    public static class LoadedView {
        private final Parent parent;
        private final Object controller;

        /**
         * Creates a loaded view from the supplied record values.
         */
        public LoadedView(Parent parent, Object controller) {
            this.parent = parent;
            this.controller = controller;
        }

        public Parent getParent() {
            return parent;
        }

        public Object getController() {
            return controller;
        }
    }
}
