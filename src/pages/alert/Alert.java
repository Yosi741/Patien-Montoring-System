package pages.alert;

/**
 * Represents a patient alert with its severity and clinical message.
 */
public class Alert {

    private String message;
    private String level;

    /**
     * Creates a alert from the supplied record values.
     */
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

}
