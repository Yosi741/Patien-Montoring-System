package database;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class SchemaInitializer {

    private SchemaInitializer() {
    }

    public static void initialize() throws SQLException {
        try (Connection connection = DatabaseManager.getConnection();
             Statement statement = connection.createStatement()) {
            createUsers(statement);
            migrateUsers(statement);
            createUserProfiles(statement);
            createPasswordResetTokens(statement);
            createPatients(statement);
            createVitalReadings(statement);
            createAlerts(statement);
            migrateAlerts(statement);
            createMedicationCatalog(statement);
            createMedicationInteractions(statement);
            seedDemoMedicationInteractions(statement);
            createMedications(statement);
            createMedicationEvents(statement);
            createAppointments(statement);
            createReminders(statement);
            createMedicalHistory(statement);
            createAiNotes(statement);
            migrateAiNotes(statement);
            createMedicalFiles(statement);
            migrateMedicalFiles(statement);
            createSections(statement);
            seedSections(statement);
            createRooms(statement);
            migrateRooms(statement);
            createDeceasedRecords(statement);
            createNewbornRecords(statement);
            migrateCertificateReviewColumns(statement);
            createShiftHandoverNotes(statement);
            createAuditLogs(statement);
            createDevices(statement);
            migrateDevices(statement);
            createMessages(statement);
            createNotifications(statement);
            migrateNotifications(statement);
        }
    }

    private static void createUsers(Statement statement) throws SQLException {
        statement.execute("CREATE TABLE IF NOT EXISTS users ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "username TEXT NOT NULL UNIQUE,"
                + "password_hash TEXT NOT NULL,"
                + "role TEXT NOT NULL,"
                + "section TEXT NOT NULL DEFAULT 'All',"
                + "email TEXT,"
                + "active INTEGER NOT NULL DEFAULT 1,"
                + "created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP"
                + ")");
    }

    private static void migrateUsers(Statement statement) throws SQLException {
        addColumnIfMissing(statement, "users", "email", "TEXT");
    }

    private static void createUserProfiles(Statement statement) throws SQLException {
        statement.execute("CREATE TABLE IF NOT EXISTS user_profiles ("
                + "username TEXT PRIMARY KEY,"
                + "email TEXT,"
                + "phone TEXT,"
                + "updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                + "FOREIGN KEY(username) REFERENCES users(username) ON DELETE CASCADE"
                + ")");
    }

    private static void createPasswordResetTokens(Statement statement) throws SQLException {
        statement.execute("CREATE TABLE IF NOT EXISTS password_reset_tokens ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "username TEXT NOT NULL,"
                + "token_hash TEXT NOT NULL,"
                + "expires_at TEXT NOT NULL,"
                + "used_at TEXT,"
                + "created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                + "FOREIGN KEY(username) REFERENCES users(username) ON DELETE CASCADE"
                + ")");
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
                + "diagnosis TEXT,"
                + "created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                + "updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP"
                + ")");
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

    private static void createMedications(Statement statement) throws SQLException {
        statement.execute("CREATE TABLE IF NOT EXISTS medications ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "patient_id TEXT NOT NULL,"
                + "catalog_medication_id INTEGER,"
                + "name TEXT NOT NULL,"
                + "dose TEXT,"
                + "dose_amount REAL,"
                + "dose_unit TEXT,"
                + "route TEXT,"
                + "frequency TEXT,"
                + "active INTEGER NOT NULL DEFAULT 1,"
                + "FOREIGN KEY(catalog_medication_id) REFERENCES medication_catalog(id),"
                + "FOREIGN KEY(patient_id) REFERENCES patients(patient_id) ON DELETE CASCADE"
                + ")");
        addColumnIfMissing(statement, "medications", "catalog_medication_id", "INTEGER");
        addColumnIfMissing(statement, "medications", "dose_amount", "REAL");
        addColumnIfMissing(statement, "medications", "dose_unit", "TEXT");
    }

    private static void createMedicationCatalog(Statement statement) throws SQLException {
        statement.execute("CREATE TABLE IF NOT EXISTS medication_catalog ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "name TEXT NOT NULL UNIQUE,"
                + "form_type TEXT NOT NULL DEFAULT 'OTHER',"
                + "default_route TEXT,"
                + "default_frequency TEXT,"
                + "default_unit TEXT,"
                + "allowed_units TEXT,"
                + "allowed_routes TEXT,"
                + "min_single_dose REAL,"
                + "max_single_dose REAL,"
                + "max_daily_dose REAL,"
                + "min_interval_minutes REAL,"
                + "min_interval_hours REAL,"
                + "requires_doctor_override INTEGER NOT NULL DEFAULT 0,"
                + "danger_notes TEXT,"
                + "notes TEXT,"
                + "active INTEGER NOT NULL DEFAULT 1,"
                + "created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                + "updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP"
                + ")");
        addColumnIfMissing(statement, "medication_catalog", "form_type", "TEXT NOT NULL DEFAULT 'OTHER'");
        addColumnIfMissing(statement, "medication_catalog", "default_route", "TEXT");
        addColumnIfMissing(statement, "medication_catalog", "default_frequency", "TEXT");
        addColumnIfMissing(statement, "medication_catalog", "default_unit", "TEXT");
        addColumnIfMissing(statement, "medication_catalog", "allowed_units", "TEXT");
        addColumnIfMissing(statement, "medication_catalog", "allowed_routes", "TEXT");
        addColumnIfMissing(statement, "medication_catalog", "min_single_dose", "REAL");
        addColumnIfMissing(statement, "medication_catalog", "max_single_dose", "REAL");
        addColumnIfMissing(statement, "medication_catalog", "max_daily_dose", "REAL");
        addColumnIfMissing(statement, "medication_catalog", "min_interval_minutes", "REAL");
        addColumnIfMissing(statement, "medication_catalog", "min_interval_hours", "REAL");
        addColumnIfMissing(statement, "medication_catalog", "requires_doctor_override", "INTEGER NOT NULL DEFAULT 0");
        addColumnIfMissing(statement, "medication_catalog", "danger_notes", "TEXT");
        addColumnIfMissing(statement, "medication_catalog", "notes", "TEXT");
        addColumnIfMissing(statement, "medication_catalog", "active", "INTEGER NOT NULL DEFAULT 1");
        addColumnIfMissing(statement, "medication_catalog", "created_at", "TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP");
        addColumnIfMissing(statement, "medication_catalog", "updated_at", "TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP");
        statement.execute("CREATE INDEX IF NOT EXISTS idx_medication_catalog_name ON medication_catalog(name)");
    }

    private static void createMedicationInteractions(Statement statement) throws SQLException {
        statement.execute("CREATE TABLE IF NOT EXISTS medication_interactions ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "medication_a_id INTEGER,"
                + "medication_b_id INTEGER,"
                + "medication_a TEXT NOT NULL,"
                + "medication_b TEXT NOT NULL,"
                + "severity TEXT NOT NULL DEFAULT 'WARNING',"
                + "min_wait_minutes INTEGER NOT NULL DEFAULT 0,"
                + "notes TEXT,"
                + "message TEXT NOT NULL,"
                + "active INTEGER NOT NULL DEFAULT 1,"
                + "created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                + "updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                + "UNIQUE(medication_a, medication_b)"
                + ")");
        addColumnIfMissing(statement, "medication_interactions", "medication_a_id", "INTEGER");
        addColumnIfMissing(statement, "medication_interactions", "medication_b_id", "INTEGER");
        addColumnIfMissing(statement, "medication_interactions", "severity", "TEXT NOT NULL DEFAULT 'WARNING'");
        addColumnIfMissing(statement, "medication_interactions", "min_wait_minutes", "INTEGER NOT NULL DEFAULT 0");
        addColumnIfMissing(statement, "medication_interactions", "notes", "TEXT");
        addColumnIfMissing(statement, "medication_interactions", "message", "TEXT NOT NULL DEFAULT ''");
        addColumnIfMissing(statement, "medication_interactions", "active", "INTEGER NOT NULL DEFAULT 1");
        addColumnIfMissing(statement, "medication_interactions", "created_at", "TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP");
        addColumnIfMissing(statement, "medication_interactions", "updated_at", "TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP");
        statement.execute("CREATE INDEX IF NOT EXISTS idx_medication_interactions_pair ON medication_interactions(medication_a, medication_b)");
        statement.execute("CREATE INDEX IF NOT EXISTS idx_medication_interactions_pair_ids ON medication_interactions(medication_a_id, medication_b_id)");
    }

    private static void seedDemoMedicationInteractions(Statement statement) throws SQLException {
        statement.execute("INSERT OR IGNORE INTO medication_catalog(name, form_type, default_route, default_unit, allowed_units, allowed_routes, "
                + "min_single_dose, max_single_dose, max_daily_dose, min_interval_minutes, danger_notes, notes, active, updated_at) "
                + "VALUES('Ibuprofen', 'TABLET', 'Oral', 'mg', 'mg, tablet', 'Oral', 100, 800, 3200, 360, "
                + "'Demo interaction catalog item.', 'Demo interaction catalog item.', 1, CURRENT_TIMESTAMP)");
        statement.execute("INSERT OR IGNORE INTO medication_catalog(name, form_type, default_route, default_unit, allowed_units, allowed_routes, "
                + "min_single_dose, max_single_dose, max_daily_dose, min_interval_minutes, danger_notes, notes, active, updated_at) "
                + "VALUES('Aspirin', 'TABLET', 'Oral', 'mg', 'mg, tablet', 'Oral', 75, 650, 4000, 360, "
                + "'Demo interaction catalog item.', 'Demo interaction catalog item.', 1, CURRENT_TIMESTAMP)");
        statement.execute("INSERT OR IGNORE INTO medication_catalog(name, form_type, default_route, default_unit, allowed_units, allowed_routes, "
                + "min_single_dose, max_single_dose, max_daily_dose, min_interval_minutes, danger_notes, notes, active, updated_at) "
                + "VALUES('Norepinephrine', 'INJECTION', 'IV', 'mcg', 'mcg, mL', 'IV', 1, 50, 500, 30, "
                + "'Demo interaction catalog item.', 'Demo interaction catalog item.', 1, CURRENT_TIMESTAMP)");
        statement.execute("INSERT OR IGNORE INTO medication_interactions(medication_a_id, medication_b_id, medication_a, medication_b, "
                + "severity, min_wait_minutes, notes, message, active, updated_at) "
                + "SELECT a.id, b.id, 'Ibuprofen', 'Aspirin', 'WARNING', 0, "
                + "'Increased bleeding risk in demo rule.', 'Increased bleeding risk in demo rule.', 1, CURRENT_TIMESTAMP "
                + "FROM medication_catalog a, medication_catalog b WHERE a.name = 'Ibuprofen' AND b.name = 'Aspirin'");
        statement.execute("INSERT OR IGNORE INTO medication_interactions(medication_a_id, medication_b_id, medication_a, medication_b, "
                + "severity, min_wait_minutes, notes, message, active, updated_at) "
                + "SELECT a.id, b.id, 'Aspirin', 'Norepinephrine', 'DANGEROUS', 0, "
                + "'Dangerous interaction demo rule requiring doctor override.', "
                + "'Dangerous interaction demo rule requiring doctor override.', 1, CURRENT_TIMESTAMP "
                + "FROM medication_catalog a, medication_catalog b WHERE a.name = 'Aspirin' AND b.name = 'Norepinephrine'");
    }

    private static void createMedicationEvents(Statement statement) throws SQLException {
        statement.execute("CREATE TABLE IF NOT EXISTS medication_events ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "medication_id INTEGER,"
                + "patient_id TEXT NOT NULL,"
                + "given_by TEXT NOT NULL,"
                + "given_at TEXT NOT NULL,"
                + "notes TEXT,"
                + "status TEXT NOT NULL DEFAULT 'GIVEN',"
                + "given_amount REAL,"
                + "given_unit TEXT,"
                + "route TEXT,"
                + "override_used INTEGER NOT NULL DEFAULT 0,"
                + "override_reason TEXT,"
                + "safety_status TEXT,"
                + "FOREIGN KEY(medication_id) REFERENCES medications(id),"
                + "FOREIGN KEY(patient_id) REFERENCES patients(patient_id) ON DELETE CASCADE"
                + ")");
        addColumnIfMissing(statement, "medication_events", "status", "TEXT NOT NULL DEFAULT 'GIVEN'");
        addColumnIfMissing(statement, "medication_events", "given_amount", "REAL");
        addColumnIfMissing(statement, "medication_events", "given_unit", "TEXT");
        addColumnIfMissing(statement, "medication_events", "route", "TEXT");
        addColumnIfMissing(statement, "medication_events", "override_used", "INTEGER NOT NULL DEFAULT 0");
        addColumnIfMissing(statement, "medication_events", "override_reason", "TEXT");
        addColumnIfMissing(statement, "medication_events", "safety_status", "TEXT");
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

    private static void createReminders(Statement statement) throws SQLException {
        statement.execute("CREATE TABLE IF NOT EXISTS reminders ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "patient_id TEXT NOT NULL,"
                + "medication_id INTEGER,"
                + "reminder_type TEXT NOT NULL,"
                + "title TEXT NOT NULL,"
                + "due_time TEXT NOT NULL,"
                + "repeat_rule TEXT,"
                + "status TEXT NOT NULL DEFAULT 'PENDING',"
                + "assigned_to TEXT,"
                + "created_by TEXT,"
                + "notes TEXT,"
                + "created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                + "updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                + "FOREIGN KEY(patient_id) REFERENCES patients(patient_id) ON DELETE CASCADE,"
                + "FOREIGN KEY(medication_id) REFERENCES medications(id)"
                + ")");
    }

    private static void createMedicalHistory(Statement statement) throws SQLException {
        statement.execute("CREATE TABLE IF NOT EXISTS medical_history ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "patient_id TEXT NOT NULL,"
                + "category TEXT NOT NULL,"
                + "details TEXT NOT NULL,"
                + "created_by TEXT,"
                + "created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                + "FOREIGN KEY(patient_id) REFERENCES patients(patient_id) ON DELETE CASCADE"
                + ")");
    }

    private static void createAiNotes(Statement statement) throws SQLException {
        statement.execute("CREATE TABLE IF NOT EXISTS ai_notes ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "patient_id TEXT NOT NULL,"
                + "risk_score INTEGER NOT NULL DEFAULT 0,"
                + "note TEXT NOT NULL,"
                + "source_title TEXT,"
                + "created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                + "FOREIGN KEY(patient_id) REFERENCES patients(patient_id) ON DELETE CASCADE"
                + ")");
    }

    private static void migrateAiNotes(Statement statement) throws SQLException {
        addColumnIfMissing(statement, "ai_notes", "source_title", "TEXT");
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

    private static void migrateMedicalFiles(Statement statement) throws SQLException {
        addColumnIfMissing(statement, "medical_files", "file_size", "INTEGER NOT NULL DEFAULT 0");
        addColumnIfMissing(statement, "medical_files", "notes", "TEXT");
    }

    private static void createRooms(Statement statement) throws SQLException {
        statement.execute("CREATE TABLE IF NOT EXISTS rooms ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "section TEXT NOT NULL,"
                + "room_number TEXT NOT NULL,"
                + "capacity INTEGER NOT NULL DEFAULT 1,"
                + "UNIQUE(section, room_number)"
                + ")");
    }

    private static void createSections(Statement statement) throws SQLException {
        statement.execute("CREATE TABLE IF NOT EXISTS sections ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "name TEXT NOT NULL UNIQUE,"
                + "status TEXT NOT NULL DEFAULT 'ACTIVE',"
                + "notes TEXT,"
                + "created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                + "updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP"
                + ")");
    }

    private static void seedSections(Statement statement) throws SQLException {
        statement.execute("INSERT OR IGNORE INTO sections(name, status, notes) "
                + "SELECT DISTINCT TRIM(section), 'ACTIVE', 'Imported from patient locations' FROM patients "
                + "WHERE section IS NOT NULL AND TRIM(section) <> ''");
        statement.execute("INSERT OR IGNORE INTO sections(name, status, notes) "
                + "SELECT DISTINCT TRIM(section), 'ACTIVE', 'Imported from room records' FROM rooms "
                + "WHERE section IS NOT NULL AND TRIM(section) <> ''");
    }

    private static void migrateRooms(Statement statement) throws SQLException {
        addColumnIfMissing(statement, "rooms", "status", "TEXT NOT NULL DEFAULT 'ACTIVE'");
        addColumnIfMissing(statement, "rooms", "notes", "TEXT");
        addColumnIfMissing(statement, "rooms", "updated_at", "TEXT");
        statement.execute("UPDATE rooms SET status = 'ACTIVE' WHERE status IS NULL OR TRIM(status) = ''");
        statement.execute("UPDATE rooms SET updated_at = CURRENT_TIMESTAMP WHERE updated_at IS NULL OR TRIM(updated_at) = ''");
    }

    private static void createDeceasedRecords(Statement statement) throws SQLException {
        statement.execute("CREATE TABLE IF NOT EXISTS deceased_records ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "patient_id TEXT NOT NULL UNIQUE,"
                + "death_time TEXT NOT NULL,"
                + "pronounced_by TEXT NOT NULL,"
                + "cause_of_death TEXT NOT NULL,"
                + "notes TEXT,"
                + "certificate_path TEXT,"
                + "created_by TEXT,"
                + "created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                + "updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                + "FOREIGN KEY(patient_id) REFERENCES patients(patient_id) ON DELETE CASCADE"
                + ")");
    }

    private static void createNewbornRecords(Statement statement) throws SQLException {
        statement.execute("CREATE TABLE IF NOT EXISTS newborn_records ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "newborn_id TEXT NOT NULL UNIQUE,"
                + "mother_patient_id TEXT,"
                + "father_name TEXT,"
                + "mother_name TEXT NOT NULL,"
                + "baby_name TEXT NOT NULL,"
                + "gender TEXT NOT NULL,"
                + "birth_time TEXT NOT NULL,"
                + "birth_weight REAL NOT NULL,"
                + "birth_length REAL,"
                + "delivery_type TEXT NOT NULL DEFAULT 'UNKNOWN',"
                + "room TEXT,"
                + "section TEXT,"
                + "doctor_or_midwife TEXT,"
                + "notes TEXT,"
                + "certificate_path TEXT,"
                + "created_by TEXT,"
                + "created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                + "updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                + "FOREIGN KEY(mother_patient_id) REFERENCES patients(patient_id) ON DELETE SET NULL"
                + ")");
    }

    private static void migrateCertificateReviewColumns(Statement statement) throws SQLException {
        addCertificateReviewColumns(statement, "deceased_records");
        addCertificateReviewColumns(statement, "newborn_records");
    }

    private static void addCertificateReviewColumns(Statement statement, String table) throws SQLException {
        addColumnIfMissing(statement, table, "review_status", "TEXT NOT NULL DEFAULT 'DRAFT'");
        addColumnIfMissing(statement, table, "reviewed_by", "TEXT");
        addColumnIfMissing(statement, table, "reviewed_at", "TEXT");
        addColumnIfMissing(statement, table, "rejection_reason", "TEXT");
        statement.execute("UPDATE " + table + " SET review_status = 'DRAFT' WHERE review_status IS NULL OR TRIM(review_status) = ''");
    }

    private static void createShiftHandoverNotes(Statement statement) throws SQLException {
        statement.execute("CREATE TABLE IF NOT EXISTS shift_handover_notes ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "patient_id TEXT,"
                + "from_user TEXT NOT NULL,"
                + "to_section TEXT NOT NULL,"
                + "note TEXT NOT NULL,"
                + "created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP"
                + ")");
    }

    private static void createAuditLogs(Statement statement) throws SQLException {
        statement.execute("CREATE TABLE IF NOT EXISTS audit_logs ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "username TEXT NOT NULL,"
                + "action TEXT NOT NULL,"
                + "created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP"
                + ")");
    }

    private static void createDevices(Statement statement) throws SQLException {
        statement.execute("CREATE TABLE IF NOT EXISTS devices ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "device_id TEXT NOT NULL UNIQUE,"
                + "name TEXT NOT NULL,"
                + "type TEXT NOT NULL,"
                + "serial TEXT,"
                + "status TEXT NOT NULL,"
                + "patient_id TEXT"
                + ")");
    }

    private static void migrateDevices(Statement statement) throws SQLException {
        addColumnIfMissing(statement, "devices", "notes", "TEXT");
        addColumnIfMissing(statement, "devices", "updated_at", "TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP");
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
