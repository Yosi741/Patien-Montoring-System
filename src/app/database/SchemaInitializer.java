package app.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class SchemaInitializer {

    private SchemaInitializer() {
    }

    public static void initialize() throws SQLException {
        try (Connection connection = DatabaseManager.getConnection();
             Statement statement = connection.createStatement()) {
            createUsers(statement);
            migrateUsers(connection, statement);
            createUserProfiles(statement);
            migrateUserProfiles(statement);
            createPatients(statement);
            migratePatients(statement);
            createPatientVisits(statement);
            createVitalReadings(statement);
            createAlerts(statement);
            migrateAlerts(statement);
            createAppointments(statement);
            createMedicalFiles(statement);
            migrateMedicalFiles(statement);
            createBillingRecords(statement);
            createMessages(statement);
            createNotifications(statement);
            migrateNotifications(statement);
        }
    }

    private static void createUsers(Statement statement) throws SQLException {
        statement.execute("CREATE TABLE IF NOT EXISTS users ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "staff_id TEXT UNIQUE,"
                + "username TEXT NOT NULL UNIQUE,"
                + "password_hash TEXT NOT NULL,"
                + "role TEXT NOT NULL,"
                + "section TEXT NOT NULL DEFAULT 'All',"
                + "email TEXT,"
                + "active INTEGER NOT NULL DEFAULT 1,"
                + "created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP"
                + ")");
    }

    private static void migrateUsers(Connection connection, Statement statement) throws SQLException {
        addColumnIfMissing(statement, "users", "staff_id", "TEXT");
        addColumnIfMissing(statement, "users", "email", "TEXT");
        backfillUserStaffIds(connection);
        statement.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_users_staff_id_unique ON users(staff_id)");
    }

    private static void backfillUserStaffIds(Connection connection) throws SQLException {
        ArrayList<UserStaffIdUpdate> updates = new ArrayList<>();
        Set<String> usedStaffIds = new HashSet<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id, staff_id FROM users ORDER BY id");
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                long id = resultSet.getLong("id");
                String current = normalizeStaffId(resultSet.getString("staff_id"));
                if (current.isEmpty() || usedStaffIds.contains(current)) {
                    String generated = uniqueStaffIdForId(id, usedStaffIds);
                    updates.add(new UserStaffIdUpdate(id, generated));
                    usedStaffIds.add(generated);
                } else {
                    usedStaffIds.add(current);
                }
            }
        }
        if (updates.isEmpty()) {
            return;
        }
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE users SET staff_id = ? WHERE id = ?")) {
            for (UserStaffIdUpdate row : updates) {
                update.setString(1, row.staffId());
                update.setLong(2, row.id());
                update.addBatch();
            }
            update.executeBatch();
        }
    }

    private static String normalizeStaffId(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim().toUpperCase(Locale.ROOT);
        return trimmed.matches("U\\d{4,}") ? trimmed : "";
    }

    private static String uniqueStaffIdForId(long id, Set<String> usedStaffIds) {
        long candidate = Math.max(1L, id);
        String staffId = formatStaffId(candidate);
        while (usedStaffIds.contains(staffId)) {
            candidate++;
            staffId = formatStaffId(candidate);
        }
        return staffId;
    }

    private static String formatStaffId(long number) {
        return String.format(Locale.ROOT, "U%04d", Math.max(1L, number));
    }

    private record UserStaffIdUpdate(long id, String staffId) {
    }

    private static void createUserProfiles(Statement statement) throws SQLException {
        statement.execute("CREATE TABLE IF NOT EXISTS user_profiles ("
                + "username TEXT PRIMARY KEY,"
                + "full_name TEXT,"
                + "email TEXT,"
                + "phone TEXT,"
                + "address TEXT,"
                + "duty_status TEXT NOT NULL DEFAULT 'On Duty',"
                + "profile_photo_path TEXT,"
                + "updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                + "FOREIGN KEY(username) REFERENCES users(username) ON DELETE CASCADE"
                + ")");
    }

    private static void migrateUserProfiles(Statement statement) throws SQLException {
        addColumnIfMissing(statement, "user_profiles", "full_name", "TEXT");
        addColumnIfMissing(statement, "user_profiles", "address", "TEXT");
        addColumnIfMissing(statement, "user_profiles", "duty_status", "TEXT NOT NULL DEFAULT 'On Duty'");
        addColumnIfMissing(statement, "user_profiles", "profile_photo_path", "TEXT");
        statement.execute("UPDATE user_profiles SET duty_status = 'On Duty' WHERE duty_status IS NULL OR TRIM(duty_status) = ''");
    }

    private static void createPatients(Statement statement) throws SQLException {
        statement.execute("CREATE TABLE IF NOT EXISTS patients ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "patient_id TEXT NOT NULL UNIQUE,"
                + "first_name TEXT NOT NULL,"
                + "last_name TEXT NOT NULL,"
                + "birth_date TEXT,"
                + "gender TEXT,"
                + "section TEXT,"
                + "room TEXT,"
                + "status TEXT NOT NULL DEFAULT 'Active',"
                + "priority TEXT NOT NULL DEFAULT 'NORMAL',"
                + "blood_type TEXT NOT NULL DEFAULT 'Unknown',"
                + "diagnosis TEXT,"
                + "allergies TEXT NOT NULL DEFAULT 'Unknown',"
                + "phone TEXT,"
                + "email TEXT,"
                + "address TEXT,"
                + "emergency_contact_name TEXT,"
                + "emergency_contact_phone TEXT,"
                + "assigned_doctor_username TEXT,"
                + "assigned_staff_username TEXT,"
                + "created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                + "updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP"
                + ")");
    }

    private static void migratePatients(Statement statement) throws SQLException {
        addColumnIfMissing(statement, "patients", "assigned_doctor_username", "TEXT");
        addColumnIfMissing(statement, "patients", "assigned_staff_username", "TEXT");
        addColumnIfMissing(statement, "patients", "blood_type", "TEXT NOT NULL DEFAULT 'Unknown'");
        addColumnIfMissing(statement, "patients", "allergies", "TEXT NOT NULL DEFAULT 'Unknown'");
        addColumnIfMissing(statement, "patients", "phone", "TEXT");
        addColumnIfMissing(statement, "patients", "email", "TEXT");
        addColumnIfMissing(statement, "patients", "address", "TEXT");
        addColumnIfMissing(statement, "patients", "emergency_contact_name", "TEXT");
        addColumnIfMissing(statement, "patients", "emergency_contact_phone", "TEXT");
        statement.execute("UPDATE patients SET blood_type = 'Unknown' WHERE blood_type IS NULL OR TRIM(blood_type) = ''");
        statement.execute("UPDATE patients SET allergies = 'Unknown' WHERE allergies IS NULL OR TRIM(allergies) = ''");
    }

    private static void createVitalReadings(Statement statement) throws SQLException {
        statement.execute("CREATE TABLE IF NOT EXISTS vital_readings ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "patient_id TEXT NOT NULL,"
                + "vital_type TEXT NOT NULL,"
                + "value TEXT NOT NULL,"
                + "unit TEXT,"
                + "recorded_at TEXT NOT NULL,"
                + "source_type TEXT,"
                + "staff_user TEXT,"
                + "device_id TEXT,"
                + "FOREIGN KEY(patient_id) REFERENCES patients(patient_id) ON DELETE CASCADE"
                + ")");
    }

    private static void createAlerts(Statement statement) throws SQLException {
        statement.execute("CREATE TABLE IF NOT EXISTS alerts ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "patient_id TEXT,"
                + "severity TEXT NOT NULL,"
                + "message TEXT NOT NULL,"
                + "status TEXT NOT NULL DEFAULT 'ACTIVE',"
                + "created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                + "updated_at TEXT,"
                + "acknowledged_by TEXT,"
                + "acknowledged_at TEXT,"
                + "cooldown_until TEXT"
                + ")");
    }

    private static void migrateAlerts(Statement statement) throws SQLException {
        addColumnIfMissing(statement, "alerts", "updated_at", "TEXT");
    }

    private static void addColumnIfMissing(Statement statement, String table, String column, String definition) throws SQLException {
        try {
            statement.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        } catch (SQLException e) {
            String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
            if (!message.contains("duplicate column")) {
                throw e;
            }
        }
    }

    private static void createAppointments(Statement statement) throws SQLException {
        statement.execute("CREATE TABLE IF NOT EXISTS appointments ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "patient_id TEXT NOT NULL,"
                + "title TEXT NOT NULL,"
                + "appointment_type TEXT NOT NULL,"
                + "start_time TEXT NOT NULL,"
                + "end_time TEXT NOT NULL,"
                + "location TEXT,"
                + "assigned_staff TEXT,"
                + "status TEXT NOT NULL DEFAULT 'SCHEDULED',"
                + "notes TEXT,"
                + "created_by TEXT,"
                + "created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                + "updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                + "FOREIGN KEY(patient_id) REFERENCES patients(patient_id) ON DELETE CASCADE"
                + ")");
    }

    private static void createPatientVisits(Statement statement) throws SQLException {
        statement.execute("CREATE TABLE IF NOT EXISTS patient_visits ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "patient_id TEXT NOT NULL,"
                + "visit_date TEXT NOT NULL,"
                + "discharge_date TEXT,"
                + "status TEXT NOT NULL,"
                + "report TEXT,"
                + "created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                + "FOREIGN KEY(patient_id) REFERENCES patients(patient_id) ON DELETE CASCADE"
                + ")");
    }

    private static void createMedicalFiles(Statement statement) throws SQLException {
        statement.execute("CREATE TABLE IF NOT EXISTS medical_files ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "file_id TEXT NOT NULL UNIQUE,"
                + "patient_id TEXT NOT NULL,"
                + "original_name TEXT NOT NULL,"
                + "stored_path TEXT NOT NULL,"
                + "file_type TEXT,"
                + "uploaded_by TEXT,"
                + "uploaded_at TEXT NOT NULL,"
                + "extracted_summary TEXT,"
                + "FOREIGN KEY(patient_id) REFERENCES patients(patient_id) ON DELETE CASCADE"
                + ")");
    }

    private static void createBillingRecords(Statement statement) throws SQLException {
        statement.execute("CREATE TABLE IF NOT EXISTS billing_records ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "invoice_no TEXT NOT NULL UNIQUE,"
                + "patient_id TEXT NOT NULL,"
                + "patient_name TEXT,"
                + "service_name TEXT NOT NULL,"
                + "visit_type TEXT,"
                + "amount REAL NOT NULL,"
                + "payment_status TEXT NOT NULL,"
                + "payment_method TEXT,"
                + "notes TEXT,"
                + "created_at TEXT NOT NULL,"
                + "paid_at TEXT,"
                + "created_by TEXT"
                + ")");
    }

    private static void migrateMedicalFiles(Statement statement) throws SQLException {
        addColumnIfMissing(statement, "medical_files", "file_size", "INTEGER NOT NULL DEFAULT 0");
        addColumnIfMissing(statement, "medical_files", "notes", "TEXT");
    }

    private static void createNotifications(Statement statement) throws SQLException {
        statement.execute("CREATE TABLE IF NOT EXISTS notifications ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "username TEXT NOT NULL,"
                + "severity TEXT NOT NULL,"
                + "message TEXT NOT NULL,"
                + "read INTEGER NOT NULL DEFAULT 0,"
                + "created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP"
                + ")");
    }

    private static void createMessages(Statement statement) throws SQLException {
        statement.execute("CREATE TABLE IF NOT EXISTS messages ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "sender_username TEXT NOT NULL,"
                + "recipient_username TEXT,"
                + "recipient_role TEXT,"
                + "recipient_section TEXT,"
                + "patient_id TEXT,"
                + "subject TEXT NOT NULL,"
                + "body TEXT NOT NULL,"
                + "priority TEXT NOT NULL DEFAULT 'NORMAL',"
                + "status TEXT NOT NULL DEFAULT 'SENT',"
                + "created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                + "read_at TEXT"
                + ")");
    }

    private static void migrateNotifications(Statement statement) throws SQLException {
        addColumnIfMissing(statement, "notifications", "role", "TEXT");
        addColumnIfMissing(statement, "notifications", "section", "TEXT");
        addColumnIfMissing(statement, "notifications", "patient_id", "TEXT");
        addColumnIfMissing(statement, "notifications", "title", "TEXT");
        addColumnIfMissing(statement, "notifications", "status", "TEXT NOT NULL DEFAULT 'UNREAD'");
        addColumnIfMissing(statement, "notifications", "source_type", "TEXT");
        addColumnIfMissing(statement, "notifications", "source_id", "TEXT");
        addColumnIfMissing(statement, "notifications", "read_at", "TEXT");
        statement.execute("UPDATE notifications SET title = severity || ' notification' WHERE title IS NULL OR TRIM(title) = ''");
        statement.execute("UPDATE notifications SET status = CASE WHEN read = 1 THEN 'READ' ELSE 'UNREAD' END "
                + "WHERE status IS NULL OR TRIM(status) = ''");
    }

}
