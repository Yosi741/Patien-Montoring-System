package ui.javafx;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

public class AppNavigator {

    private final AppShell appShell;

    public AppNavigator(AppShell appShell) {
        this.appShell = appShell;
    }

    public Parent load(String fxmlPath) {
        return loadView(fxmlPath).parent;
    }

    public LoadedView loadView(String fxmlPath) {
        try {
            FXMLLoader loader = new FXMLLoader(resolve(fxmlPath));
            Parent parent = loader.load();
            Object controller = loader.getController();
            if (controller instanceof FxController) {
                ((FxController) controller).setAppShell(appShell);
            }
            parent.getStylesheets().clear();
            parent.getStylesheets().add(resolve(AppShell.PRESENTATION_THEME).toExternalForm());
            appShell.applyThemeTo(parent);
            return new LoadedView(parent, controller);
        } catch (IOException e) {
            throw new IllegalStateException("Could not load JavaFX view: " + fxmlPath, e);
        }
    }

    public static URL resolve(String path) {
        URL resource = AppNavigator.class.getResource(path);
        if (resource != null) {
            return resource;
        }

        File sourceFile = new File("src" + path.replace("/", File.separator));
        if (sourceFile.exists()) {
            try {
                return sourceFile.toURI().toURL();
            } catch (MalformedURLException e) {
                throw new IllegalStateException("Invalid JavaFX resource path: " + path, e);
            }
        }

        throw new IllegalStateException("JavaFX resource not found: " + path);
    }

    public static class LoadedView {
        private final Parent parent;
        private final Object controller;

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
