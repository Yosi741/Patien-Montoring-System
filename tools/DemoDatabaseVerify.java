import app.database.DatabaseManager;
import app.database.SchemaInitializer;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

public class DemoDatabaseVerify {

    private static final Map<String, Integer> MIN_COUNTS = new LinkedHashMap<>();

    static {
        MIN_COUNTS.put("users", 4);
        MIN_COUNTS.put("user_profiles", 4);
        MIN_COUNTS.put("patients", 8);
        MIN_COUNTS.put("patient_visits", 8);
        MIN_COUNTS.put("vital_readings", 48);
        MIN_COUNTS.put("alerts", 5);
        MIN_COUNTS.put("notifications", 5);
        MIN_COUNTS.put("appointments", 6);
        MIN_COUNTS.put("medical_files", 5);
        MIN_COUNTS.put("billing_records", 4);
        MIN_COUNTS.put("messages", 4);
    }

    public static void main(String[] args) throws Exception {
        SchemaInitializer.initialize();
        boolean ok = true;
        try (Connection connection = DatabaseManager.getConnection()) {
            for (Map.Entry<String, Integer> entry : MIN_COUNTS.entrySet()) {
                int count = count(connection, entry.getKey());
                String marker = count >= entry.getValue() ? "OK" : "FAIL";
                System.out.printf("%-18s %4d minimum %4d %s%n", entry.getKey(), count, entry.getValue(), marker);
                ok &= count >= entry.getValue();
            }
            ok &= verifyCriticalPatients(connection);
            ok &= verifyDemoUsers(connection);
            ok &= verifyCurrentAppointments(connection);
        }
        if (!ok) {
            throw new IllegalStateException("Demo database verification failed.");
        }
        System.out.println("Demo database verification passed.");
    }

    private static int count(Connection connection, String table) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            return resultSet.next() ? resultSet.getInt(1) : 0;
        }
    }

    private static boolean verifyCriticalPatients(Connection connection) throws SQLException {
        String sql = "SELECT COUNT(*) FROM patients WHERE patient_id IN ('215070632', '100000003') "
                + "AND UPPER(priority) = 'CRITICAL'";
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            boolean ok = resultSet.next() && resultSet.getInt(1) == 2;
            System.out.println("critical demo patients " + (ok ? "OK" : "FAIL"));
            return ok;
        }
    }

    private static boolean verifyDemoUsers(Connection connection) throws SQLException {
        String sql = "SELECT COUNT(*) FROM users WHERE active = 1 AND "
                + "((username = 'admin' AND password = 'admin123' AND role = 'ADMIN' AND staff_id = 'U0001') "
                + "OR (username = 'doctor' AND password = 'doctor123' AND role = 'DOCTOR' AND staff_id = 'U0002') "
                + "OR (username = 'nurse' AND password = 'nurse123' AND role = 'NURSE' AND staff_id = 'U0003') "
                + "OR (username = 'secretary' AND password = 'staff123' AND role = 'SECRETARY' AND staff_id = 'U0004'))";
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            boolean ok = resultSet.next() && resultSet.getInt(1) == 4;
            System.out.println("demo login accounts " + (ok ? "OK" : "FAIL"));
            return ok;
        }
    }

    private static boolean verifyCurrentAppointments(Connection connection) throws SQLException {
        String sql = "SELECT COUNT(*) FROM appointments WHERE substr(start_time, 1, 10) = strftime('%d-%m-%Y', 'now')";
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            boolean ok = resultSet.next() && resultSet.getInt(1) >= 3;
            System.out.println("today appointment rows " + (ok ? "OK" : "FAIL"));
            return ok;
        }
    }
}
