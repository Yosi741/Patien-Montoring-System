package ui.javafx.pages.notifications;

public class AppNotification {
    private String id;
    private String username;
    private String severity;
    private String message;
    private String timestamp;
    private boolean read;

    public AppNotification(String id, String username, String severity, String message, String timestamp, boolean read) {
        this.id = id;
        this.username = username;
        this.severity = severity;
        this.message = message;
        this.timestamp = timestamp;
        this.read = read;
    }

    public String getId() { return id; }
    public String getUsername() { return username; }
    public String getSeverity() { return severity; }
    public String getMessage() { return message; }
    public String getTimestamp() { return timestamp; }
    public boolean isRead() { return read; }
}
