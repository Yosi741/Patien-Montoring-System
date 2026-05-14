package database;

import models.AppNotification;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.UUID;

public class NotificationStorage {
    private static final String FILE_PATH = "data/notifications.txt";
    private static final String DELIMITER = "\\|";

    public static void addNotification(String username, String severity, String message) {
        ArrayList<AppNotification> notifications = loadNotifications();
        notifications.add(new AppNotification(UUID.randomUUID().toString(), username, severity, message, now(), false));
        saveNotifications(notifications);
    }

    public static ArrayList<AppNotification> getForUser(String username) {
        ArrayList<AppNotification> result = new ArrayList<>();
        for (AppNotification n : loadNotifications()) {
            if (n.getUsername().equals(username) || n.getUsername().equals("ALL")) result.add(n);
        }
        return result;
    }

    public static int unreadCount(String username) {
        int count = 0;
        for (AppNotification n : getForUser(username)) if (!n.isRead()) count++;
        return count;
    }

    public static void markReadForUser(String username) {
        ArrayList<AppNotification> notifications = loadNotifications();
        ArrayList<AppNotification> updated = new ArrayList<>();
        for (AppNotification n : notifications) {
            if (n.getUsername().equals(username) || n.getUsername().equals("ALL")) {
                updated.add(new AppNotification(n.getId(), n.getUsername(), n.getSeverity(), n.getMessage(), n.getTimestamp(), true));
            } else {
                updated.add(n);
            }
        }
        saveNotifications(updated);
    }

    public static void clearForUser(String username) {
        ArrayList<AppNotification> remaining = new ArrayList<>();
        for (AppNotification n : loadNotifications()) {
            if (!n.getUsername().equals(username) && !n.getUsername().equals("ALL")) remaining.add(n);
        }
        saveNotifications(remaining);
    }

    private static ArrayList<AppNotification> loadNotifications() {
        ArrayList<AppNotification> notifications = new ArrayList<>();
        try {
            File file = new File(FILE_PATH);
            if (!file.exists()) return notifications;
            BufferedReader reader = new BufferedReader(new FileReader(file));
            String line;
            while ((line = reader.readLine()) != null) {
                String[] data = line.split(DELIMITER, -1);
                if (data.length == 6) notifications.add(new AppNotification(data[0], data[1], data[2], data[3], data[4], Boolean.parseBoolean(data[5])));
            }
            reader.close();
        } catch (Exception e) {
            System.out.println("Error loading notifications: " + e.getMessage());
        }
        return notifications;
    }

    private static void saveNotifications(ArrayList<AppNotification> notifications) {
        try {
            new File("data").mkdirs();
            PrintWriter writer = new PrintWriter(new FileWriter(FILE_PATH));
            for (AppNotification n : notifications) {
                writer.println(clean(n.getId()) + "|" + clean(n.getUsername()) + "|" + clean(n.getSeverity()) + "|" + clean(n.getMessage()) + "|" + clean(n.getTimestamp()) + "|" + n.isRead());
            }
            writer.close();
        } catch (Exception e) {
            System.out.println("Error saving notifications: " + e.getMessage());
        }
    }

    private static String now() { return LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss")); }
    private static String clean(String value) { return value == null ? "" : value.replace("|", " ").replace("\n", " "); }
}
