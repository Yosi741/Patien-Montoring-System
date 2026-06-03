package services;

import dao.SqliteReminderDao;
import ui.javafx.helpers.AuditAction;
import ui.javafx.helpers.AuditWriteHelper;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ReminderEngineService {

    private static final DateTimeFormatter DISPLAY_DATE_TIME = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
    private static final int UPCOMING_WINDOW_MINUTES = 120;
    private static final int NOTIFICATION_COOLDOWN_MINUTES = 10;
    private static final Map<Long, LocalDateTime> NOTIFICATION_COOLDOWNS = new HashMap<>();

    private final SqliteReminderDao reminderDao;

    public ReminderEngineService() {
        this(new SqliteReminderDao());
    }

    public ReminderEngineService(SqliteReminderDao reminderDao) {
        this.reminderDao = reminderDao;
    }

    public EngineResult evaluateReminders(String username) throws SQLException {
        List<SqliteReminderDao.ReminderRow> reminders = reminderDao.findReminders("", "All", "All", "");
        int overdueDetected = 0;
        LocalDateTime now = LocalDateTime.now();
        for (SqliteReminderDao.ReminderRow reminder : reminders) {
            String status = normalize(reminder.getStatus());
            if (!"PENDING".equals(status)) {
                continue;
            }
            Optional<LocalDateTime> due = parseDateTime(reminder.getDueTime());
            if (due.isPresent() && due.get().isBefore(now)) {
                boolean updated = reminderDao.updateStatusIfCurrent(reminder.getId(), "OVERDUE", "PENDING");
                if (updated) {
                    overdueDetected++;
                    AuditWriteHelper.write(usernameOrSystem(username), AuditAction.REMINDER_OVERDUE_DETECTED,
                            "reminder_id=" + reminder.getId() + ", patient_id=" + reminder.getPatientId()
                                    + ", type=" + reminder.getReminderType());
                    new NotificationCenterService().notifyOverdueReminder(
                            reminder.getPatientId(),
                            reminder.getTitle(),
                            reminder.getId()
                    );
                }
            }
        }
        return new EngineResult(overdueDetected);
    }

    public List<ReminderNotification> loadNotifications(String username) throws SQLException {
        evaluateReminders(username);
        ArrayList<ReminderNotification> notifications = new ArrayList<>();
        List<SqliteReminderDao.ReminderRow> reminders = reminderDao.findReminders("", "All", "All", "");
        LocalDateTime now = LocalDateTime.now();
        for (SqliteReminderDao.ReminderRow reminder : reminders) {
            String status = normalize(reminder.getStatus());
            if (!"PENDING".equals(status) && !"OVERDUE".equals(status)) {
                continue;
            }
            Optional<LocalDateTime> due = parseDateTime(reminder.getDueTime());
            if (due.isEmpty()) {
                continue;
            }
            if ("OVERDUE".equals(status) || due.get().isBefore(now)) {
                if (passesCooldown(reminder.getId(), now)) {
                    notifications.add(new ReminderNotification(
                            reminder.getId(),
                            reminder.getPatientId(),
                            reminder.getReminderType(),
                            reminder.getTitle(),
                            reminder.getDueTime(),
                            "OVERDUE",
                            "MEDICATION".equalsIgnoreCase(reminder.getReminderType())
                                    ? "Overdue medication reminder needs review."
                                    : "Overdue reminder needs review."
                    ));
                }
            } else if (!due.get().isAfter(now.plusMinutes(UPCOMING_WINDOW_MINUTES))) {
                if (passesCooldown(reminder.getId(), now)) {
                    notifications.add(new ReminderNotification(
                            reminder.getId(),
                            reminder.getPatientId(),
                            reminder.getReminderType(),
                            reminder.getTitle(),
                            reminder.getDueTime(),
                            "UPCOMING",
                            "Upcoming reminder due soon."
                    ));
                }
            }
        }
        return notifications;
    }

    private boolean passesCooldown(long reminderId, LocalDateTime now) {
        LocalDateTime lastShown = NOTIFICATION_COOLDOWNS.get(reminderId);
        if (lastShown != null && lastShown.plusMinutes(NOTIFICATION_COOLDOWN_MINUTES).isAfter(now)) {
            return false;
        }
        NOTIFICATION_COOLDOWNS.put(reminderId, now);
        return true;
    }

    public Optional<LocalDateTime> parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String trimmed = value.trim();
        try {
            return Optional.of(LocalDateTime.parse(trimmed, DISPLAY_DATE_TIME));
        } catch (DateTimeParseException ignored) {
            try {
                return Optional.of(LocalDateTime.parse(trimmed, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            } catch (DateTimeParseException ignoredAgain) {
                try {
                    return Optional.of(LocalDateTime.parse(trimmed.replace(" ", "T")));
                } catch (DateTimeParseException e) {
                    return Optional.empty();
                }
            }
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private String usernameOrSystem(String username) {
        return username == null || username.isBlank() ? "System" : username;
    }

    public static class EngineResult {
        private final int overdueDetected;

        public EngineResult(int overdueDetected) {
            this.overdueDetected = overdueDetected;
        }

        public int getOverdueDetected() {
            return overdueDetected;
        }
    }

    public static class ReminderNotification {
        private final long reminderId;
        private final String patientId;
        private final String reminderType;
        private final String title;
        private final String dueTime;
        private final String status;
        private final String message;

        public ReminderNotification(long reminderId, String patientId, String reminderType, String title,
                                    String dueTime, String status, String message) {
            this.reminderId = reminderId;
            this.patientId = patientId;
            this.reminderType = reminderType;
            this.title = title;
            this.dueTime = dueTime;
            this.status = status;
            this.message = message;
        }

        public long getReminderId() { return reminderId; }
        public String getPatientId() { return patientId; }
        public String getReminderType() { return reminderType; }
        public String getTitle() { return title; }
        public String getDueTime() { return dueTime; }
        public String getStatus() { return status; }
        public String getMessage() { return message; }
    }
}
