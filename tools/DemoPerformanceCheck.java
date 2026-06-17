import Data_Access_Object.SqliteAuditLogDao;
import Data_Access_Object.SqliteDeceasedRecordDao;
import Data_Access_Object.SqliteNewbornRecordDao;
import Data_Access_Object.SqlitePatientDao;
import Data_Access_Object.SqliteVitalReadingDao;
import database.DatabaseManager;
import database.SchemaInitializer;
import ui.javafx.services.DashboardMetricsService;
import ui.javafx.services.MedicationOverviewService;
import ui.javafx.services.NotificationCenterService;
import ui.javafx.services.SchedulingService;
import users.User;

import java.sql.Connection;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class DemoPerformanceCheck {

    private static final long PASS_THRESHOLD_MS = 1000;
    private static final long WARNING_THRESHOLD_MS = 3000;

    public static void main(String[] args) throws Exception {
        SchemaInitializer.initialize();

        SqlitePatientDao patientDao = new SqlitePatientDao();
        SqliteVitalReadingDao vitalDao = new SqliteVitalReadingDao();
        SqliteNewbornRecordDao newbornDao = new SqliteNewbornRecordDao();
        SqliteDeceasedRecordDao deceasedDao = new SqliteDeceasedRecordDao();
        MedicationOverviewService medicationOverviewService = new MedicationOverviewService();
        SchedulingService schedulingService = new SchedulingService();
        SqliteAuditLogDao auditLogDao = new SqliteAuditLogDao();
        NotificationCenterService notificationCenterService = new NotificationCenterService();
        DashboardMetricsService dashboardMetricsService = new DashboardMetricsService();

        SqlitePatientDao.PatientListRow samplePatient = firstPatient(patientDao);
        User adminUser = new User("admin", "", "ADMIN", "All");

        ArrayList<OperationResult> results = new ArrayList<>();
        results.add(check("Database connection time", () -> {
            try (Connection ignored = DatabaseManager.getConnection()) {
                return "Opened SQLite connection to " + DatabaseManager.getDatabasePath();
            }
        }));
        results.add(check("Dashboard metrics load", () -> {
            DashboardMetricsService.DashboardMetrics metrics = dashboardMetricsService.loadMetrics();
            return "Dashboard patients=" + metrics.getTotalPatients() + ", alerts=" + metrics.getActiveAlerts();
        }));
        results.add(check("Patient list load", () -> {
            List<SqlitePatientDao.PatientListRow> rows = patientDao.findPatientListRows(new SqlitePatientDao.PatientFilter());
            return "Patient rows=" + rows.size();
        }));
        results.add(check("Patient search by ID", () -> {
            requireSample(samplePatient, "No patient found for ID search.");
            SqlitePatientDao.PatientFilter filter = new SqlitePatientDao.PatientFilter();
            filter.setSearch(samplePatient.getPatientId());
            List<SqlitePatientDao.PatientListRow> rows = patientDao.findPatientListRows(filter);
            return "Matches for " + samplePatient.getPatientId() + "=" + rows.size();
        }));
        results.add(check("Patient search by name", () -> {
            requireSample(samplePatient, "No patient found for name search.");
            SqlitePatientDao.PatientFilter filter = new SqlitePatientDao.PatientFilter();
            filter.setSearch(samplePatient.getName());
            List<SqlitePatientDao.PatientListRow> rows = patientDao.findPatientListRows(filter);
            return "Matches for \"" + samplePatient.getName() + "\"=" + rows.size();
        }));
        results.add(check("Patient filter active", () -> {
            SqlitePatientDao.PatientFilter filter = new SqlitePatientDao.PatientFilter();
            filter.setStatus("ACTIVE");
            List<SqlitePatientDao.PatientListRow> rows = patientDao.findPatientListRows(filter);
            return "Active patient rows=" + rows.size();
        }));
        results.add(check("Patient filter deceased", () -> {
            SqlitePatientDao.PatientFilter filter = new SqlitePatientDao.PatientFilter();
            filter.setStatus("DECEASED");
            List<SqlitePatientDao.PatientListRow> rows = patientDao.findPatientListRows(filter);
            return "Deceased patient rows=" + rows.size();
        }));
        results.add(check("Newborn records load", () -> {
            List<SqliteNewbornRecordDao.NewbornRecord> rows = newbornDao.findRecords(new SqliteNewbornRecordDao.RecordFilter());
            return "Newborn rows=" + rows.size();
        }));
        results.add(check("Patient filter critical/emergency", () -> {
            SqlitePatientDao.PatientFilter filter = new SqlitePatientDao.PatientFilter();
            filter.setCriticalEmergencyOnly(true);
            List<SqlitePatientDao.PatientListRow> rows = patientDao.findPatientListRows(filter);
            return "Critical/emergency rows=" + rows.size();
        }));
        results.add(check("Patient filter high priority", () -> {
            SqlitePatientDao.PatientFilter filter = new SqlitePatientDao.PatientFilter();
            filter.setPriority("HIGH");
            List<SqlitePatientDao.PatientListRow> rows = patientDao.findPatientListRows(filter);
            return "High priority rows=" + rows.size();
        }));
        results.add(check("Vitals history load for one patient", () -> {
            requireSample(samplePatient, "No patient found for vitals history.");
            return "Vitals rows for " + samplePatient.getPatientId() + "="
                    + vitalDao.findByPatientId(samplePatient.getPatientId()).size();
        }));
        results.add(check("Medication overview load", () -> {
            MedicationOverviewService.MedicationOverview overview = medicationOverviewService.loadOverview(
                    new MedicationOverviewService.MedicationFilter("", "All", "All", "All", ""));
            return "Medication rows=" + overview.getMedications().size() + ", event rows=" + overview.getEvents().size();
        }));
        results.add(check("Scheduling/reminders load", () -> {
            SchedulingService.SchedulingOverview overview = schedulingService.loadOverview("", "All", "All", "All", "All", "");
            return "Appointments=" + overview.getAppointments().size() + ", reminders=" + overview.getReminders().size();
        }));
        results.add(check("Audit logs load", () -> {
            List<SqliteAuditLogDao.AuditLogRow> rows = auditLogDao.findRows("", "All", "All");
            return "Audit rows=" + rows.size();
        }));
        results.add(check("Notifications load", () -> {
            return "Notification rows=" + notificationCenterService
                    .findForCurrentUser(adminUser, "All", "All", "", "All").size();
        }));
        results.add(check("Deceased records load", () -> {
            return "Deceased rows=" + deceasedDao.findRecords(new SqliteDeceasedRecordDao.RecordFilter()).size();
        }));

        int passCount = 0;
        int warningCount = 0;
        int failCount = 0;
        for (OperationResult result : results) {
            if ("PASS".equals(result.status)) {
                passCount++;
            } else if ("WARNING".equals(result.status)) {
                warningCount++;
            } else {
                failCount++;
            }
            System.out.println(result.status + ": " + result.name + " took " + result.elapsedMs + " ms | " + result.detail);
        }

        OperationResult slowest = results.stream().max(Comparator.comparingLong(r -> r.elapsedMs)).orElse(null);
        System.out.println("total checks=" + results.size());
        System.out.println("PASS count=" + passCount);
        System.out.println("WARNING count=" + warningCount);
        System.out.println("FAIL count=" + failCount);
        System.out.println("slowest operation=" + (slowest == null ? "-" : slowest.name + " (" + slowest.elapsedMs + " ms)"));
    }

    private static SqlitePatientDao.PatientListRow firstPatient(SqlitePatientDao patientDao) throws Exception {
        List<SqlitePatientDao.PatientListRow> rows = patientDao.findPatientListRows(new SqlitePatientDao.PatientFilter());
        return rows.isEmpty() ? null : rows.get(0);
    }

    private static void requireSample(Object sample, String message) {
        if (sample == null) {
            throw new IllegalStateException(message);
        }
    }

    private static OperationResult check(String name, CheckedOperation operation) {
        long start = System.nanoTime();
        try {
            String detail = operation.run();
            long elapsedMs = Duration.ofNanos(System.nanoTime() - start).toMillis();
            return new OperationResult(name, classify(elapsedMs), elapsedMs, detail);
        } catch (Exception e) {
            long elapsedMs = Duration.ofNanos(System.nanoTime() - start).toMillis();
            return new OperationResult(name, "FAIL", elapsedMs, e.getClass().getSimpleName() + ": " + safeMessage(e));
        }
    }

    private static String classify(long elapsedMs) {
        if (elapsedMs < PASS_THRESHOLD_MS) {
            return "PASS";
        }
        if (elapsedMs <= WARNING_THRESHOLD_MS) {
            return "WARNING";
        }
        return "FAIL";
    }

    private static String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? "No detail" : message;
    }

    @FunctionalInterface
    private interface CheckedOperation {
        String run() throws Exception;
    }

    private static class OperationResult {
        private final String name;
        private final String status;
        private final long elapsedMs;
        private final String detail;

        private OperationResult(String name, String status, long elapsedMs, String detail) {
            this.name = name;
            this.status = status;
            this.elapsedMs = elapsedMs;
            this.detail = detail == null ? "-" : detail;
        }
    }
}
