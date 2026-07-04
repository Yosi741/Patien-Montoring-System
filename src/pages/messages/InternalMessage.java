package pages.messages;

public class InternalMessage {
    private String id;
    private String sender;
    private String targetType;
    private String target;
    private String subject;
    private String body;
    private String timestamp;

    public InternalMessage(String id, String sender, String targetType, String target, String subject, String body, String timestamp) {
        this.id = id;
        this.sender = sender;
        this.targetType = targetType;
        this.target = target;
        this.subject = subject;
        this.body = body;
        this.timestamp = timestamp;
    }

    public String getId() { return id; }
    public String getSender() { return sender; }
    public String getTargetType() { return targetType; }
    public String getTarget() { return target; }
    public String getSubject() { return subject; }
    public String getBody() { return body; }
    public String getTimestamp() { return timestamp; }
}
