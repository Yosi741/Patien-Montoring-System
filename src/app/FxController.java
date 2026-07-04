package app;

public interface FxController {
    void setAppShell(AppShell appShell);

    default void dispose() {
        // Optional lifecycle hook for controllers with timers/listeners that
        // should stop when the user navigates away or logs out.
    }
}
