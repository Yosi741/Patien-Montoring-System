package ui.javafx.pages.nurse_work_queue;

import ui.javafx.pages.Alert.SqliteAlertDao;
import ui.javafx.pages.patients.dao.SqlitePatientDao;
import ui.javafx.pages.scheduling.SqliteReminderDao;
import app.DatabaseManager;
import ui.javafx.pages.scheduling.ReminderEngineService;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class NurseWorkQueueService {

    private final ReminderEngineService reminderEngineService;
    private final SqliteReminderDao reminderDao;
    private final SqliteAlertDao alertDao;

    public NurseWorkQueueService() {
        this(new ReminderEngineService(), new SqliteReminderDao(), new SqliteAlertDao());
    }

    public NurseWorkQueueService(ReminderEngineService reminderEngineService, SqliteReminderDao reminderDao, SqliteAlertDao alertDao) {
        this.reminderEngineService = reminderEngineService;
        this.reminderDao = reminderDao;
        this.alertDao = alertDao;
    }

    public WorkQueueOverview loadQueue(String username) throws SQLException {
        reminderEngineService.evaluateReminders(username);
        ArrayList<WorkQueueTask> tasks = new ArrayList<>();
        addReminderTasks(tasks);
        addAlertTasks(tasks);
        addMissingVitalsTasks(tasks);
        tasks.sort(Comparator.comparingInt(WorkQueueTask::getSortWeight)
                .thenComparing(WorkQueueTask::getDueTime));
        return new WorkQueueOverview(
                countByStatus(tasks, "OVERDUE"),
                countByStatus(tasks, "UPCOMING"),
                tasks.size(),
                countByType(tasks, "ALERT"),
                countByType(tasks, "MISSING_VITALS"),
                tasks
        );
    }

    private void addReminderTasks(List<WorkQueueTask> tasks) throws SQLException {
        LocalDateTime now = LocalDateTime.now();
        List<SqliteReminderDao.ReminderRow> reminders = reminderDao.findReminders("", "All", "All", "");
        for (SqliteReminderDao.ReminderRow reminder : reminders) {
            String status = upper(reminder.getStatus());
            if (!"PENDING".equals(status) && !"OVERDUE".equals(status)) {
                continue;
            }
            Optional<LocalDateTime> due = reminderEngineService.parseDateTime(reminder.getDueTime());
            String actionStatus;
            int weight;
            if ("OVERDUE".equals(status) || due.map(value -> value.isBefore(now)).orElse(false)) {
                actionStatus = "OVERDUE";
                weight = "MEDICATION".equalsIgnoreCase(reminder.getReminderType()) ? 10 : 20;
            } else if (due.map(value -> !value.isAfter(now.plusHours(4))).orElse(false)) {
                actionStatus = "UPCOMING";
                weight = "MEDICATION".equalsIgnoreCase(reminder.getReminderType()) ? 30 : 45;
            } else {
                actionStatus = "PENDING";
                weight = 60;
            }
            PatientLocation location = loadPatientLocation(reminder.getPatientId());
            tasks.add(new WorkQueueTask(
                    "reminders",
                    reminder.getId(),
                    reminder.getPatientId(),
                    reminder.getPatientName(),
                    location.room,
                    location.section,
                    reminder.getReminderType(),
                    reminder.getTitle(),
                    reminder.getDueTime(),
                    reminder.getReminderType(),
                    actionStatus,
                    reminder.getStatus(),
                    "Reminder " + reminder.getTitle() + " due at " + reminder.getDueTime(),
                    weight
            ));
        }
    }

    private void addAlertTasks(List<WorkQueueTask> tasks) throws SQLException {
        for (SqliteAlertDao.AlertRow alert : alertDao.findAlertRows("All", "ACTIVE", "", null)) {
            String severity = upper(alert.getSeverity());
            if (!"CRITICAL".equals(severity) && !"EMERGENCY".equals(severity)) {
                continue;
            }
            PatientLocation location = loadPatientLocation(alert.getPatientId());
            tasks.add(new WorkQueueTask(
                    "alerts",
                    alert.getId(),
                    alert.getPatientId(),
                    alert.getPatientName(),
                    location.room,
                    location.section,
                    "ALERT",
                    alert.getSeverity() + " active alert",
                    alert.getCreatedAt(),
                    "ALERT",
                    alert.getSeverity(),
                    alert.getStatus(),
                    alert.getMessage(),
                    "EMERGENCY".equals(severity) ? 5 : 15
            ));
        }
    }

    private void addMissingVitalsTasks(List<WorkQueueTask> tasks) throws SQLException {
        String sql = "SELECT p.patient_id, COALESCE(TRIM(p.first_name || ' ' || p.last_name), '') AS patient_name, "
                + "p.section, p.room, MAX(v.recorded_at) AS latest_vital "
                + "FROM patients p LEFT JOIN vital_readings v ON v.patient_id = p.patient_id "
                + "WHERE UPPER(p.status) NOT IN ('DECEASED', 'DISCHARGED', 'INACTIVE') "
                + "GROUP BY p.patient_id, p.first_name, p.last_name, p.section, p.room";
        LocalDateTime now = LocalDateTime.now();
        try (Connection connection = DatabaseManager.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                String latest = resultSet.getString("latest_vital");
                Optional<LocalDateTime> latestTime = reminderEngineService.parseDateTime(latest);
                if (latestTime.isPresent() && latestTime.get().plusHours(24).isAfter(now)) {
                    continue;
                }
                tasks.add(new WorkQueueTask(
                        "vital_readings",
                        0,
                        value(resultSet.getString("patient_id")),
                        value(resultSet.getString("patient_name")),
                        value(resultSet.getString("room")),
                        value(resultSet.getString("section")),
                        "MISSING_VITALS",
                        "Missing recent vitals",
                        latest == null || latest.isBlank() ? "No vitals recorded" : latest,
                        "MISSING_VITALS",
                        "MISSING_VITALS",
                        "OPEN",
                        "No SQLite vital reading in the last 24 hours.",
                        70
                ));
            }
        }
    }

    private PatientLocation loadPatientLocation(String patientId) throws SQLException {
        if (patientId == null || patientId.isBlank()) {
            return new PatientLocation("", "");
        }
        return new SqlitePatientDao().findDetailById(patientId)
                .map(detail -> new PatientLocation(detail.getRoom(), detail.getSection()))
                .orElse(new PatientLocation("", ""));
    }

    private int countByStatus(List<WorkQueueTask> tasks, String status) {
        int count = 0;
        for (WorkQueueTask task : tasks) {
            if (status.equalsIgnoreCase(task.getActionStatus())) {
                count++;
            }
        }
        return count;
    }

    private int countByType(List<WorkQueueTask> tasks, String type) {
        int count = 0;
        for (WorkQueueTask task : tasks) {
            if (type.equalsIgnoreCase(task.getTaskType())) {
                count++;
            }
        }
        return count;
    }

    private String upper(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private static class PatientLocation {
        private final String room;
        private final String section;

        private PatientLocation(String room, String section) {
            this.room = room == null ? "" : room;
            this.section = section == null ? "" : section;
        }
    }

    public static class WorkQueueOverview {
        private final int overdueReminders;
        private final int upcomingReminders;
        private final int totalTasks;
        private final int criticalAlerts;
        private final int missingVitals;
        private final List<WorkQueueTask> tasks;

        public WorkQueueOverview(int overdueReminders, int upcomingReminders, int totalTasks, int criticalAlerts,
                                 int missingVitals, List<WorkQueueTask> tasks) {
            this.overdueReminders = overdueReminders;
            this.upcomingReminders = upcomingReminders;
            this.totalTasks = totalTasks;
            this.criticalAlerts = criticalAlerts;
            this.missingVitals = missingVitals;
            this.tasks = tasks;
        }

        public int getOverdueReminders() { return overdueReminders; }
        public int getUpcomingReminders() { return upcomingReminders; }
        public int getTotalTasks() { return totalTasks; }
        public int getCriticalAlerts() { return criticalAlerts; }
        public int getMissingVitals() { return missingVitals; }
        public List<WorkQueueTask> getTasks() { return tasks; }
    }

    public static class WorkQueueTask {
        private final String sourceTable;
        private final long sourceId;
        private final String patientId;
        private final String patientName;
        private final String room;
        private final String section;
        private final String taskType;
        private final String title;
        private final String dueTime;
        private final String severity;
        private final String actionStatus;
        private final String recordStatus;
        private final String description;
        private final int sortWeight;

        public WorkQueueTask(String sourceTable, long sourceId, String patientId, String patientName, String room,
                             String section, String taskType, String title, String dueTime, String severity,
                             String actionStatus, String recordStatus, String description, int sortWeight) {
            this.sourceTable = sourceTable;
            this.sourceId = sourceId;
            this.patientId = patientId;
            this.patientName = patientName;
            this.room = room;
            this.section = section;
            this.taskType = taskType;
            this.title = title;
            this.dueTime = dueTime;
            this.severity = severity;
            this.actionStatus = actionStatus;
            this.recordStatus = recordStatus;
            this.description = description;
            this.sortWeight = sortWeight;
        }

        public String getSourceTable() { return sourceTable; }
        public long getSourceId() { return sourceId; }
        public String getPatientId() { return patientId; }
        public String getPatientName() { return patientName == null || patientName.isBlank() ? "Unknown patient" : patientName; }
        public String getRoom() { return room; }
        public String getSection() { return section; }
        public String getTaskType() { return taskType; }
        public String getTitle() { return title; }
        public String getDueTime() { return dueTime; }
        public String getSeverity() { return severity; }
        public String getActionStatus() { return actionStatus; }
        public String getRecordStatus() { return recordStatus; }
        public String getDescription() { return description; }
        public int getSortWeight() { return sortWeight; }
        public boolean isReminderTask() { return "reminders".equalsIgnoreCase(sourceTable); }
        public boolean isAlertTask() { return "alerts".equalsIgnoreCase(sourceTable); }
    }
}
