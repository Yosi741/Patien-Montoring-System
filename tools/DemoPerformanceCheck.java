import app.database.DatabaseManager;
import app.database.SchemaInitializer;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

public class DemoPerformanceCheck {

    public static void main(String[] args) throws Exception {
        SchemaInitializer.initialize();
        Map<String, String> checks = new LinkedHashMap<>();
        checks.put("dashboardCounts", "SELECT COUNT(*) FROM patients");
        checks.put("patientBoard", "SELECT patient_id, first_name, last_name FROM patients ORDER BY updated_at DESC LIMIT 20");
        checks.put("latestVitals", "SELECT patient_id, vital_type, value FROM vital_readings ORDER BY id DESC LIMIT 20");
        checks.put("recentAlerts", "SELECT patient_id, severity, message FROM alerts ORDER BY id DESC LIMIT 20");
        checks.put("appointments", "SELECT patient_id, appointment_type, status FROM appointments ORDER BY id DESC LIMIT 20");
        checks.put("billing", "SELECT invoice_no, payment_status, amount FROM billing_records ORDER BY id DESC LIMIT 20");

        boolean ok = true;
        try (Connection connection = DatabaseManager.getConnection()) {
            for (Map.Entry<String, String> check : checks.entrySet()) {
                long start = System.nanoTime();
                int rows = runQuery(connection, check.getValue());
                long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
                boolean fastEnough = elapsedMs < 500;
                System.out.printf("%-18s %4d rows %4d ms %s%n", check.getKey(), rows, elapsedMs, fastEnough ? "OK" : "SLOW");
                ok &= fastEnough;
            }
        }
        if (!ok) {
            throw new IllegalStateException("Demo performance check exceeded the local threshold.");
        }
        System.out.println("Demo performance check passed.");
    }

    private static int runQuery(Connection connection, String sql) throws Exception {
        int rows = 0;
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                rows++;
            }
        }
        return rows;
    }
}
