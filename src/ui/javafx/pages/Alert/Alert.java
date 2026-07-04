package ui.javafx.pages.Alert;

public class Alert {

    private String message;
    private String level;

    public Alert(String message, String level) {
        this.message = message;
        this.level = level;
    }

    public String getMessage() {
        return message;
    }

    public String getLevel() {
        return level;
    }

    public void displayAlert() {
        System.out.println("ALERT [" + level + "]: " + message);
    }
}
