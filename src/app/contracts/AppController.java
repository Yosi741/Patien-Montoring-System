package app.contracts;

import app.core.AppShell;

/**
 * Defines the lifecycle contract shared by JavaFX page controllers.
 * The application shell uses it to inject navigation access and release page resources.
 */
public interface AppController {
    /**
     * Supplies the application shell used by this controller for navigation.
     */
    void setAppShell(AppShell appShell);

    /**
     * Releases timers or other page resources when the current view is replaced.
     */
    default void dispose() {
        // Optional lifecycle hook for controllers with timers/listeners that
        // should stop when the user navigates away or logs out.
    }
}
