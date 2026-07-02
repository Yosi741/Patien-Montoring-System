import database.DatabaseManager;
import database.SchemaInitializer;
import security.PasswordHasher;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class DemoDatabaseReset {

    private static final DateTimeFormatter DISPLAY_DATE_TIME = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
    private static final DateTimeFormatter ISO_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static void main(String[] args) throws Exception {
        SchemaInitializer.initialize();
        try (Connection connection = DatabaseManager.getConnection()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA foreign_keys = OFF");
            }
            clearOperationalData(connection);
            seedUsers(connection);
            seedSectionsAndRooms(connection);
            seedPatients(connection);
            seedVitals(connection);
            seedAlerts(connection);
            seedMedicationCatalog(connection);
            seedMedications(connection);
            seedReminders(connection);
            seedNotifications(connection);
            seedCertificates(connection);
            seedAuditLogs(connection);
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA foreign_keys = ON");
            }
            connection.commit();
        }
        System.out.println("Presentation demo database reset complete.");
    }

    private static void clearOperationalData(Connection connection) throws Exception {
        String[] tables = {
                "notifications", "messages", "audit_logs", "shift_handover_notes",
                "medical_files", "medical_history", "reminders", "appointments",
                "medication_events", "medications", "medication_interactions", "medication_catalog",
                "alerts", "vital_readings", "newborn_records", "deceased_records", "rooms", "sections",
                "password_reset_tokens", "user_profiles", "users", "patients"
        };
        try (Statement statement = connection.createStatement()) {
            for (String table : tables) {
                statement.executeUpdate("DELETE FROM " + table);
            }
            for (String table : tables) {
                statement.executeUpdate("DELETE FROM sqlite_sequence WHERE name = '" + table + "'");
            }
        }
    }

    private static void seedUsers(Connection connection) throws Exception {
        insertUser(connection, "U0001", "admin", "admin123", "ADMIN", "All", "admin.demo@spms.local", "0590000001");
        insertUser(connection, "U0002", "doctor", "doctor123", "DOCTOR", "ER", "doctor.demo@spms.local", "0590000002");
        insertUser(connection, "U0003", "nurse", "nurse123", "NURSE", "Maternity", "nurse.demo@spms.local", "0590000003");
        insertUser(connection, "U0004", "staff", "staff123", "STAFF", "Front Desk", "staff.demo@spms.local", "0590000004");
    }

    private static void insertUser(Connection connection, String staffId, String username, String password, String role,
                                   String section, String email, String phone) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO users(staff_id, username, password_hash, role, section, email, active, created_at) VALUES(?, ?, ?, ?, ?, ?, 1, CURRENT_TIMESTAMP)")) {
            statement.setString(1, staffId);
            statement.setString(2, username);
            statement.setString(3, PasswordHasher.hash(password.toCharArray()));
            statement.setString(4, role);
            statement.setString(5, section);
            statement.setString(6, email);
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO user_profiles(username, email, phone, updated_at) VALUES(?, ?, ?, CURRENT_TIMESTAMP)")) {
            statement.setString(1, username);
            statement.setString(2, email);
            statement.setString(3, phone);
            statement.executeUpdate();
        }
    }

    private static void seedSectionsAndRooms(Connection connection) throws Exception {
        String[][] sections = {
                {"ER", "Emergency department"},
                {"Surgery", "Surgical unit"},
                {"Internal Medicine", "Internal medicine ward"},
                {"Maternity", "Maternity and newborn care"},
                {"Pediatrics", "Pediatric ward"},
                {"Cardiology", "Cardiology care"}
        };
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO sections(name, status, notes, created_at, updated_at) VALUES(?, 'ACTIVE', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")) {
            for (String[] section : sections) {
                statement.setString(1, section[0]);
                statement.setString(2, section[1]);
                statement.executeUpdate();
            }
        }
        insertRoom(connection, "ER", "ER-101", 3);
        insertRoom(connection, "Surgery", "SUR-201", 2);
        insertRoom(connection, "Internal Medicine", "INT-301", 4);
        insertRoom(connection, "Maternity", "MAT-401", 2);
        insertRoom(connection, "Pediatrics", "PED-501", 3);
        insertRoom(connection, "Cardiology", "CAR-601", 2);
    }

    private static void insertRoom(Connection connection, String section, String room, int capacity) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO rooms(section, room_number, capacity, status, notes, updated_at) VALUES(?, ?, ?, 'ACTIVE', 'Clean presentation room', CURRENT_TIMESTAMP)")) {
            statement.setString(1, section);
            statement.setString(2, room);
            statement.setInt(3, capacity);
            statement.executeUpdate();
        }
    }

    private static void seedPatients(Connection connection) throws Exception {
        insertPatient(connection, "100000001", "John", "Carter", "14-04-1984", "Male", "Internal Medicine", "INT-301", "Active", "NORMAL", "Routine observation after mild dehydration.");
        insertPatient(connection, "100000002", "Sara", "Haddad", "22-09-1977", "Female", "ER", "ER-101", "Active", "CRITICAL", "Low oxygen with elevated heart rate.");
        insertPatient(connection, "100000003", "Omar", "Nasser", "03-01-1956", "Male", "Cardiology", "CAR-601", "Active", "EMERGENCY", "Severe cardiac instability requiring immediate review.");
        insertPatient(connection, "100000004", "Lina", "Mansour", "18-11-1991", "Female", "Surgery", "SUR-201", "Active", "HIGH", "Post-operative monitoring.");
        insertPatient(connection, "100000005", "Mariam", "Saleh", "07-06-1994", "Female", "Maternity", "MAT-401", "Active", "NORMAL", "Post-delivery mother care.");
        insertPatient(connection, "100000007", "Nabil", "Khoury", "12-02-1942", "Male", "Internal Medicine", "INT-301", "DECEASED", "NORMAL", "Deceased record retained for certificate workflow demo.");
    }

    private static void insertPatient(Connection connection, String id, String first, String last, String birthDate,
                                      String gender, String section, String room, String status, String priority,
                                      String diagnosis) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO patients(patient_id, first_name, last_name, birth_date, gender, section, room, status, priority, diagnosis, created_at, updated_at) "
                        + "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")) {
            statement.setString(1, id);
            statement.setString(2, first);
            statement.setString(3, last);
            statement.setString(4, birthDate);
            statement.setString(5, gender);
            statement.setString(6, section);
            statement.setString(7, room);
            statement.setString(8, status);
            statement.setString(9, priority);
            statement.setString(10, diagnosis);
            statement.executeUpdate();
        }
    }

    private static void seedVitals(Connection connection) throws Exception {
        LocalDateTime now = LocalDateTime.now().withSecond(0).withNano(0);
        addVitalsSet(connection, "100000001", now.minusHours(6), 76, 118, 76, 98, 36.8, 104);
        addVitalsSet(connection, "100000001", now.minusHours(2), 78, 120, 78, 98, 36.9, 108);
        addVitalsSet(connection, "100000002", now.minusHours(4), 118, 142, 88, 91, 38.3, 138);
        addVitalsSet(connection, "100000002", now.minusHours(1), 132, 156, 94, 87, 39.1, 154);
        addVitalsSet(connection, "100000003", now.minusHours(3), 138, 182, 116, 84, 39.5, 188);
        addVitalsSet(connection, "100000003", now.minusMinutes(35), 148, 196, 124, 80, 40.1, 206);
        addVitalsSet(connection, "100000004", now.minusHours(5), 92, 132, 82, 96, 37.2, 126);
        addVitalsSet(connection, "100000005", now.minusHours(2), 84, 116, 74, 99, 36.7, 94);
    }

    private static void addVitalsSet(Connection connection, String patientId, LocalDateTime time, int heartRate,
                                     int systolic, int diastolic, int oxygen, double temperature, int sugar) throws Exception {
        insertVital(connection, patientId, "Heart Rate", String.valueOf(heartRate), "bpm", time);
        insertVital(connection, patientId, "Systolic Pressure", String.valueOf(systolic), "mmHg", time);
        insertVital(connection, patientId, "Diastolic Pressure", String.valueOf(diastolic), "mmHg", time);
        insertVital(connection, patientId, "Oxygen Saturation", String.valueOf(oxygen), "%", time);
        insertVital(connection, patientId, "Temperature", String.valueOf(temperature), "C", time);
        insertVital(connection, patientId, "Sugar Level", String.valueOf(sugar), "mg/dL", time);
    }

    private static void insertVital(Connection connection, String patientId, String type, String value, String unit,
                                    LocalDateTime recordedAt) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO vital_readings(patient_id, vital_type, value, unit, recorded_at, source_type, staff_user, device_id) VALUES(?, ?, ?, ?, ?, 'Manual', 'nurse', '')")) {
            statement.setString(1, patientId);
            statement.setString(2, type);
            statement.setString(3, value);
            statement.setString(4, unit);
            statement.setString(5, recordedAt.format(DISPLAY_DATE_TIME));
            statement.executeUpdate();
        }
    }

    private static void seedAlerts(Connection connection) throws Exception {
        insertAlert(connection, "100000002", "CRITICAL", "Critical oxygen and heart-rate trend for Sara Haddad.", "ACTIVE");
        insertAlert(connection, "100000003", "EMERGENCY", "Emergency cardiac vital pattern for Omar Nasser.", "ACTIVE");
        insertAlert(connection, "100000004", "WARNING", "Post-operative blood pressure should be reviewed.", "ACKNOWLEDGED");
    }

    private static void insertAlert(Connection connection, String patientId, String severity, String message, String status) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO alerts(patient_id, severity, message, status, created_at, updated_at, acknowledged_by, acknowledged_at) "
                        + "VALUES(?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?, ?)")) {
            statement.setString(1, patientId);
            statement.setString(2, severity);
            statement.setString(3, message);
            statement.setString(4, status);
            statement.setString(5, "ACKNOWLEDGED".equals(status) ? "doctor" : "");
            statement.setString(6, "ACKNOWLEDGED".equals(status) ? LocalDateTime.now().minusHours(1).format(DISPLAY_DATE_TIME) : "");
            statement.executeUpdate();
        }
    }

    private static void seedMedicationCatalog(Connection connection) throws Exception {
        insertCatalog(connection, "Aspirin", "TABLET", "Oral", "Once daily", "mg", "mg, tablet", "Oral", 75, 650, 4000, 360, false, "Bleeding-risk demo medication.");
        insertCatalog(connection, "Ibuprofen", "TABLET", "Oral", "Every 8 hours", "mg", "mg, tablet", "Oral", 100, 800, 3200, 360, false, "NSAID demo medication.");
        insertCatalog(connection, "Amoxicillin", "CAPSULE", "Oral", "Three times daily", "mg", "mg, capsule", "Oral", 250, 1000, 3000, 480, false, "Antibiotic demo medication.");
        insertCatalog(connection, "Metoprolol", "TABLET", "Oral", "Twice daily", "mg", "mg, tablet", "Oral", 12.5, 100, 400, 720, false, "Cardiology demo medication.");
        insertCatalog(connection, "Vancomycin", "INJECTION", "IV", "Every 12 hours", "mg", "mg, mL", "IV", 250, 2000, 4000, 720, true, "Requires careful review in demo rules.");
        insertCatalog(connection, "Norepinephrine", "INJECTION", "IV", "As needed", "mcg", "mcg, mL", "IV", 1, 50, 500, 30, true, "Emergency vasopressor demo medication.");
        insertInteraction(connection, "Ibuprofen", "Aspirin", "WARNING", 0, "Increased bleeding risk in demo rule.");
        insertInteraction(connection, "Aspirin", "Norepinephrine", "DANGEROUS", 0, "Dangerous interaction demo rule requiring doctor override.");
    }

    private static void insertCatalog(Connection connection, String name, String formType, String route, String frequency,
                                      String unit, String units, String routes, double minSingle, double maxSingle,
                                      double maxDaily, int intervalMinutes, boolean override, String notes) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO medication_catalog(name, form_type, default_route, default_frequency, default_unit, allowed_units, allowed_routes, "
                        + "min_single_dose, max_single_dose, max_daily_dose, min_interval_minutes, requires_doctor_override, danger_notes, notes, active, updated_at) "
                        + "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, CURRENT_TIMESTAMP)")) {
            statement.setString(1, name);
            statement.setString(2, formType);
            statement.setString(3, route);
            statement.setString(4, frequency);
            statement.setString(5, unit);
            statement.setString(6, units);
            statement.setString(7, routes);
            statement.setDouble(8, minSingle);
            statement.setDouble(9, maxSingle);
            statement.setDouble(10, maxDaily);
            statement.setInt(11, intervalMinutes);
            statement.setInt(12, override ? 1 : 0);
            statement.setString(13, notes);
            statement.setString(14, notes);
            statement.executeUpdate();
        }
    }

    private static void insertInteraction(Connection connection, String a, String b, String severity, int waitMinutes, String notes) throws Exception {
        long aId = catalogId(connection, a);
        long bId = catalogId(connection, b);
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO medication_interactions(medication_a_id, medication_b_id, medication_a, medication_b, severity, min_wait_minutes, notes, message, active, updated_at) "
                        + "VALUES(?, ?, ?, ?, ?, ?, ?, ?, 1, CURRENT_TIMESTAMP)")) {
            statement.setLong(1, aId);
            statement.setLong(2, bId);
            statement.setString(3, a.compareToIgnoreCase(b) <= 0 ? a : b);
            statement.setString(4, a.compareToIgnoreCase(b) <= 0 ? b : a);
            statement.setString(5, severity);
            statement.setInt(6, waitMinutes);
            statement.setString(7, notes);
            statement.setString(8, notes);
            statement.executeUpdate();
        }
    }

    private static void seedMedications(Connection connection) throws Exception {
        insertMedication(connection, "100000001", "Aspirin", 100, "mg", "Oral", "Once daily");
        insertMedication(connection, "100000002", "Amoxicillin", 500, "mg", "Oral", "Three times daily");
        insertMedication(connection, "100000003", "Norepinephrine", 8, "mcg", "IV", "As needed");
        insertMedication(connection, "100000004", "Ibuprofen", 400, "mg", "Oral", "Every 8 hours");
        insertMedication(connection, "100000005", "Metoprolol", 25, "mg", "Oral", "Twice daily");
    }

    private static void insertMedication(Connection connection, String patientId, String name, double amount,
                                         String unit, String route, String frequency) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO medications(patient_id, catalog_medication_id, name, dose, dose_amount, dose_unit, route, frequency, active) VALUES(?, ?, ?, ?, ?, ?, ?, ?, 1)",
                Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, patientId);
            statement.setLong(2, catalogId(connection, name));
            statement.setString(3, name);
            statement.setString(4, formatAmount(amount) + " " + unit);
            statement.setDouble(5, amount);
            statement.setString(6, unit);
            statement.setString(7, route);
            statement.setString(8, frequency);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    insertMedicationEvent(connection, keys.getLong(1), patientId, amount, unit, route);
                }
            }
        }
    }

    private static void insertMedicationEvent(Connection connection, long medicationId, String patientId, double amount, String unit, String route) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO medication_events(medication_id, patient_id, given_by, given_at, notes, status, given_amount, given_unit, route, override_used, override_reason, safety_status) "
                        + "VALUES(?, ?, 'nurse', ?, 'Clean demo administration record.', 'GIVEN', ?, ?, ?, 0, '', 'NORMAL')")) {
            statement.setLong(1, medicationId);
            statement.setString(2, patientId);
            statement.setString(3, LocalDateTime.now().minusHours(3).format(DISPLAY_DATE_TIME));
            statement.setDouble(4, amount);
            statement.setString(5, unit);
            statement.setString(6, route);
            statement.executeUpdate();
        }
    }

    private static void seedReminders(Connection connection) throws Exception {
        LocalDateTime now = LocalDateTime.now().withSecond(0).withNano(0);
        insertReminder(connection, "100000002", null, "CHECKUP", "Checkup: Heart Rate, Blood Pressure, CBC, CRP", now.plusHours(2), "PENDING", "nurse", "Requested checkups/tests: Heart Rate, Blood Pressure, CBC, CRP");
        insertReminder(connection, "100000004", medicationId(connection, "100000004", "Ibuprofen"), "MEDICATION", "Medication review reminder", now.plusHours(4), "PENDING", "doctor", "Review post-operative pain medication plan.");
        insertReminder(connection, "100000005", null, "CUSTOM", "Nurse follow-up task", now.plusHours(1), "PENDING", "nurse", "Post-delivery follow-up and family education.");
    }

    private static void insertReminder(Connection connection, String patientId, Long medicationId, String type, String title,
                                       LocalDateTime due, String status, String assignedTo, String notes) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO reminders(patient_id, medication_id, reminder_type, title, due_time, repeat_rule, status, assigned_to, created_by, notes, created_at, updated_at) "
                        + "VALUES(?, ?, ?, ?, ?, '', ?, ?, 'admin', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")) {
            statement.setString(1, patientId);
            if (medicationId == null) {
                statement.setNull(2, java.sql.Types.INTEGER);
            } else {
                statement.setLong(2, medicationId);
            }
            statement.setString(3, type);
            statement.setString(4, title);
            statement.setString(5, due.format(DISPLAY_DATE_TIME));
            statement.setString(6, status);
            statement.setString(7, assignedTo);
            statement.setString(8, notes);
            statement.executeUpdate();
        }
    }

    private static void seedNotifications(Connection connection) throws Exception {
        insertNotification(connection, "", "DOCTOR", "ER", "100000002", "CRITICAL", "Critical vital alert", "Sara Haddad has a critical oxygen and heart-rate trend.", "ALERT", "100000002");
        insertNotification(connection, "", "NURSE", "ER", "100000002", "INFO", "Pending checkup order", "Checkup: Heart Rate, Blood Pressure, CBC, CRP is pending.", "REMINDER", "100000002");
        insertNotification(connection, "", "DOCTOR", "Maternity", "100000005", "INFO", "Certificate generated", "Birth certificate generated for newborn 100000006.", "BIRTH_CERTIFICATE", "100000006");
    }

    private static void insertNotification(Connection connection, String username, String role, String section, String patientId,
                                           String severity, String title, String message, String sourceType, String sourceId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO notifications(username, role, section, patient_id, severity, title, message, status, source_type, source_id, read, created_at) "
                        + "VALUES(?, ?, ?, ?, ?, ?, ?, 'UNREAD', ?, ?, 0, CURRENT_TIMESTAMP)")) {
            statement.setString(1, username);
            statement.setString(2, role);
            statement.setString(3, section);
            statement.setString(4, patientId);
            statement.setString(5, severity);
            statement.setString(6, title);
            statement.setString(7, message);
            statement.setString(8, sourceType);
            statement.setString(9, sourceId);
            statement.executeUpdate();
        }
    }

    private static void seedCertificates(Connection connection) throws Exception {
        Path birthDir = Path.of("data", "generated", "birth-certificates");
        Path deathDir = Path.of("data", "generated", "death-certificates");
        Files.createDirectories(birthDir);
        Files.createDirectories(deathDir);
        Path birthCertificate = birthDir.resolve("demo-birth-100000006.html");
        Path deathCertificate = deathDir.resolve("demo-death-100000007.html");
        Files.writeString(birthCertificate, certificateHtml("Birth Certificate", "Baby Adam Saleh", "Newborn ID: 100000006", "Mother: Mariam Saleh (100000005)"), StandardCharsets.UTF_8);
        Files.writeString(deathCertificate, certificateHtml("Death Certificate", "Nabil Khoury", "Patient ID: 100000007", "Cause: Cardiac arrest complications"), StandardCharsets.UTF_8);
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO newborn_records(newborn_id, mother_patient_id, father_name, mother_name, baby_name, gender, birth_time, birth_weight, birth_length, delivery_type, room, section, doctor_or_midwife, notes, certificate_path, created_by, created_at, updated_at, review_status) "
                        + "VALUES('100000006', '100000005', 'Yousef Saleh', 'Mariam Saleh', 'Adam Saleh', 'Male', ?, 3.4, 51, 'NATURAL', 'MAT-401', 'Maternity', 'doctor', 'Clean linked newborn demo record.', ?, 'admin', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'APPROVED')")) {
            statement.setString(1, LocalDateTime.now().minusDays(1).format(ISO_DATE_TIME));
            statement.setString(2, birthCertificate.toString());
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO deceased_records(patient_id, death_time, pronounced_by, cause_of_death, notes, certificate_path, created_by, created_at, updated_at, review_status) "
                        + "VALUES('100000007', ?, 'Dr. Demo', 'Cardiac arrest complications', 'Clean deceased record for certificate workflow.', ?, 'admin', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'APPROVED')")) {
            statement.setString(1, LocalDateTime.now().minusDays(2).format(ISO_DATE_TIME));
            statement.setString(2, deathCertificate.toString());
            statement.executeUpdate();
        }
    }

    private static String certificateHtml(String title, String person, String line1, String line2) {
        return "<!doctype html><html><head><meta charset=\"UTF-8\"><title>" + title + "</title>"
                + "<style>body{font-family:Arial,sans-serif;background:#f8fafc;color:#0f172a;padding:48px;}"
                + ".card{border:2px solid #2563eb;padding:36px;max-width:760px;margin:auto;}h1{color:#1d4ed8;}</style></head>"
                + "<body><div class=\"card\"><h1>" + title + "</h1><h2>" + person + "</h2><p>" + line1 + "</p><p>" + line2
                + "</p><p>Generated for SPMS presentation demo only.</p></div></body></html>";
    }

    private static void seedAuditLogs(Connection connection) throws Exception {
        insertAudit(connection, "admin", "LOGIN");
        insertAudit(connection, "nurse", "ENTER_VITALS patient_id=100000002 status=CRITICAL");
        insertAudit(connection, "doctor", "CREATE_REMINDER patient_id=100000002 title=Checkup order");
        insertAudit(connection, "admin", "GENERATE_BIRTH_CERTIFICATE newborn_id=100000006");
        insertAudit(connection, "admin", "GENERATE_DEATH_CERTIFICATE patient_id=100000007");
    }

    private static void insertAudit(Connection connection, String username, String action) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO audit_logs(username, action, created_at) VALUES(?, ?, CURRENT_TIMESTAMP)")) {
            statement.setString(1, username);
            statement.setString(2, action);
            statement.executeUpdate();
        }
    }

    private static long catalogId(Connection connection, String name) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("SELECT id FROM medication_catalog WHERE name = ?")) {
            statement.setString(1, name);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getLong(1);
                }
            }
        }
        throw new IllegalStateException("Catalog medication missing: " + name);
    }

    private static Long medicationId(Connection connection, String patientId, String name) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("SELECT id FROM medications WHERE patient_id = ? AND name = ? LIMIT 1")) {
            statement.setString(1, patientId);
            statement.setString(2, name);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getLong(1) : null;
            }
        }
    }

    private static String formatAmount(double amount) {
        return amount == Math.rint(amount) ? String.valueOf((long) amount) : String.valueOf(amount);
    }
}
