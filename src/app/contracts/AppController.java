package app.contracts;

import app.core.AppShell;

public interface AppController {
    void setAppShell(AppShell appShell);

    default void dispose() {
        // Optional lifecycle hook for controllers with timers/listeners that
        // should stop when the user navigates away or logs out.
    }
}
