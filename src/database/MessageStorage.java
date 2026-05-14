package database;

import models.InternalMessage;
import users.User;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.UUID;

public class MessageStorage {
    private static final String FILE_PATH = "data/messages.txt";
    private static final String DELIMITER = "\\|";

    public static void sendMessage(InternalMessage message) {
        ArrayList<InternalMessage> messages = loadMessages();
        messages.add(message);
        saveMessages(messages);
        notifyTargets(message);
    }

    public static InternalMessage create(String sender, String targetType, String target, String subject, String body) {
        return new InternalMessage(UUID.randomUUID().toString(), sender, targetType, target, subject, body, now());
    }

    public static ArrayList<InternalMessage> inboxFor(User user) {
        ArrayList<InternalMessage> inbox = new ArrayList<>();
        for (InternalMessage message : loadMessages()) {
            if (message.getTargetType().equals("User") && message.getTarget().equals(user.getUsername())) inbox.add(message);
            if (message.getTargetType().equals("Section") && message.getTarget().equals(user.getSection())) inbox.add(message);
            if (message.getTargetType().equals("All")) inbox.add(message);
        }
        return inbox;
    }

    private static void notifyTargets(InternalMessage message) {
        if (message.getTargetType().equals("User")) {
            NotificationStorage.addNotification(message.getTarget(), "INFO", "New message from " + message.getSender() + ": " + message.getSubject());
        } else if (message.getTargetType().equals("All")) {
            NotificationStorage.addNotification("ALL", "INFO", "Announcement from " + message.getSender() + ": " + message.getSubject());
        } else {
            for (User user : UserStorage.loadUsers()) {
                if (user.getSection().equals(message.getTarget())) {
                    NotificationStorage.addNotification(user.getUsername(), "INFO", "Section message from " + message.getSender() + ": " + message.getSubject());
                }
            }
        }
    }

    private static ArrayList<InternalMessage> loadMessages() {
        ArrayList<InternalMessage> messages = new ArrayList<>();
        try {
            File file = new File(FILE_PATH);
            if (!file.exists()) return messages;
            BufferedReader reader = new BufferedReader(new FileReader(file));
            String line;
            while ((line = reader.readLine()) != null) {
                String[] data = line.split(DELIMITER, -1);
                if (data.length == 7) messages.add(new InternalMessage(data[0], data[1], data[2], data[3], data[4], data[5], data[6]));
            }
            reader.close();
        } catch (Exception e) {
            System.out.println("Error loading messages: " + e.getMessage());
        }
        return messages;
    }

    private static void saveMessages(ArrayList<InternalMessage> messages) {
        try {
            new File("data").mkdirs();
            PrintWriter writer = new PrintWriter(new FileWriter(FILE_PATH));
            for (InternalMessage m : messages) {
                writer.println(clean(m.getId()) + "|" + clean(m.getSender()) + "|" + clean(m.getTargetType()) + "|" + clean(m.getTarget()) + "|" + clean(m.getSubject()) + "|" + clean(m.getBody()) + "|" + clean(m.getTimestamp()));
            }
            writer.close();
        } catch (Exception e) {
            System.out.println("Error saving messages: " + e.getMessage());
        }
    }

    private static String now() { return LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss")); }
    private static String clean(String value) { return value == null ? "" : value.replace("|", " ").replace("\n", " / "); }
}
