import app.database.DatabaseManager;
import app.database.SchemaInitializer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class DemoDatabaseReset {

    private static final DateTimeFormatter SQL_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DISPLAY_DATE_TIME = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter BACKUP_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    public static void main(String[] args) throws Exception {
        SchemaInitializer.initialize();
        Path backupPath = backupDatabase();
        try (Connection connection = DatabaseManager.getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                clearActiveTables(connection);
                seedStaffUsers(connection);
                seedPatients(connection);
                seedPatientVisits(connection);
                seedVitals(connection);
                seedAlerts(connection);
                seedNotifications(connection);
                seedAppointments(connection);
                seedBillingRecords(connection);
                seedMedicalFiles(connection);
                seedMessages(connection);
                resetSqliteSequences(connection);
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }
        }

        System.out.println("Presentation demo database reset complete.");
        System.out.println("Backup: " + backupPath);
        System.out.println("Demo users: admin/admin123, doctor/doctor123, nurse/nurse123, secretary/staff123");
    }

    private static Path backupDatabase() throws SQLException, IOException {
        Path database = Path.of(DatabaseManager.getDatabasePath());
        Files.createDirectories(Path.of("data", "backups"));
        try (Connection connection = DatabaseManager.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA wal_checkpoint(TRUNCATE)");
        }
        String stamp = LocalDateTime.now().format(BACKUP_STAMP);
        Path backup = Path.of("data", "backups", "pre-presentation-demo-reset-" + stamp + ".db");
        if (Files.exists(database)) {
            Files.copy(database, backup, StandardCopyOption.REPLACE_EXISTING);
        }
        return backup;
    }

    private static void clearActiveTables(Connection connection) throws SQLException {
        List<String> tables = List.of(
                "messages",
                "notifications",
                "alerts",
                "billing_records",
                "medical_files",
                "appointments",
                "vital_readings",
                "patient_visits",
                "patients",
                "user_profiles",
                "users"
        );
        try (Statement statement = connection.createStatement()) {
            for (String table : tables) {
                statement.executeUpdate("DELETE FROM " + table);
            }
        }
    }

    private static void resetSqliteSequences(Connection connection) throws SQLException {
        String sql = "DELETE FROM sqlite_sequence WHERE name IN ("
                + "'messages','notifications','alerts','billing_records','medical_files','appointments',"
                + "'vital_readings','patient_visits','patients','user_profiles','users')";
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private static void seedStaffUsers(Connection connection) throws SQLException {
        insertUser(connection, "U0001", "admin", "admin123", "ADMIN", "Administration",
                "admin@smartcare.local", true);
        insertUserProfile(connection, "admin", "Admin User", "admin@smartcare.local", "0590000001",
                "SmartCare front desk", "On Duty");

        insertUser(connection, "U0002", "doctor", "doctor123", "DOCTOR", "Clinic",
                "doctor@smartcare.local", true);
        insertUserProfile(connection, "doctor", "Dr. Sarah Johnson", "doctor@smartcare.local", "0590000002",
                "SmartCare provider office", "On Duty");

        insertUser(connection, "U0003", "nurse", "nurse123", "NURSE", "Clinic",
                "nurse@smartcare.local", true);
        insertUserProfile(connection, "nurse", "Nurse Omar Khalil", "nurse@smartcare.local", "0590000003",
                "SmartCare triage station", "On Duty");

        insertUser(connection, "U0004", "secretary", "staff123", "SECRETARY", "Front Desk",
                "secretary@smartcare.local", true);
        insertUserProfile(connection, "secretary", "Lina Haddad", "secretary@smartcare.local", "0590000004",
                "SmartCare reception", "On Duty");
    }

    private static void insertUser(Connection connection, String staffId, String username, String password,
                                   String role, String section, String email, boolean active) throws SQLException {
        String sql = "INSERT INTO users(staff_id, username, password, role, section, email, active, created_at) "
                + "VALUES(?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, staffId);
            statement.setString(2, username);
            statement.setString(3, password);
            statement.setString(4, role);
            statement.setString(5, section);
            statement.setString(6, email);
            statement.setInt(7, active ? 1 : 0);
            statement.setString(8, sqlNow());
            statement.executeUpdate();
        }
    }

    private static void insertUserProfile(Connection connection, String username, String fullName, String email,
                                          String phone, String address, String dutyStatus) throws SQLException {
        String sql = "INSERT INTO user_profiles(username, full_name, email, phone, address, duty_status, profile_photo_path, updated_at) "
                + "VALUES(?, ?, ?, ?, ?, ?, '', ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            statement.setString(2, fullName);
            statement.setString(3, email);
            statement.setString(4, phone);
            statement.setString(5, address);
            statement.setString(6, dutyStatus);
            statement.setString(7, sqlNow());
            statement.executeUpdate();
        }
    }

    private static void seedPatients(Connection connection) throws SQLException {
        insertPatient(connection, "215070632", "Arial", "Shmohan", "14-04-2001", "Male",
                "Clinic", "", "ACTIVE", "CRITICAL", "O+", "Rapid heart rate and dizziness",
                "No allergies", "0539339053", "arial.shmohan@example.com",
                "12 Olive Street, Ramallah", "Maya Shmohan", "0591112001", "doctor", "nurse");
        insertPatient(connection, "328015839", "Mohamad", "Dahbour", "22-09-1994", "Male",
                "Clinic", "", "ACTIVE", "NORMAL", "A+", "Wound dressing after minor injury",
                "Penicillin", "0592221304", "mohamad.dahbour@example.com",
                "8 Cedar Road, Nablus", "Rania Dahbour", "0592221305", "doctor", "nurse");
        insertPatient(connection, "100000003", "Omar", "Nasser", "03-02-1988", "Male",
                "Clinic", "", "ACTIVE", "CRITICAL", "B+", "High fever and dehydration",
                "Unknown", "0593331206", "omar.nasser@example.com",
                "24 Jasmine Lane, Bethlehem", "Hala Nasser", "0593331207", "doctor", "nurse");
        insertPatient(connection, "147258368", "Sajda", "Mds", "19-07-1999", "Female",
                "Clinic", "", "ACTIVE", "HIGH", "A+", "Snake bite follow-up",
                "No allergies", "0594441208", "sajda.mds@example.com",
                "5 Fig Street, Hebron", "Amal Mds", "0594441209", "doctor", "nurse");
        insertPatient(connection, "700000009", "Demo Patient", "0010", "11-11-1979", "Male",
                "Clinic", "", "DISCHARGED", "NORMAL", "B+", "Follow-up visit",
                "No allergies", "0595551210", "demo0010@example.com",
                "31 Market Street, Jericho", "Salim Demo", "0595551211", "doctor", "nurse");
        insertPatient(connection, "700000036", "Demo Patient", "0037", "27-05-1967", "Female",
                "Clinic", "", "ACTIVE", "HIGH", "AB+", "Blood pressure monitoring",
                "Latex", "0596661212", "demo0037@example.com",
                "9 Palm Avenue, Ramallah", "Nadia Demo", "0596661213", "doctor", "nurse");
        insertPatient(connection, "700000021", "Demo Patient", "0021", "02-12-1983", "Female",
                "Clinic", "", "DISCHARGED", "NORMAL", "O-", "Lab test review",
                "Unknown", "0597771214", "demo0021@example.com",
                "18 Hill Road, Jenin", "Kareem Demo", "0597771215", "doctor", "nurse");
        insertPatient(connection, "100000002", "Lina", "Mansour", "08-08-1992", "Female",
                "Clinic", "", "ACTIVE", "NORMAL", "A-", "Migraine and nausea",
                "Aspirin", "0598881216", "lina.mansour@example.com",
                "6 Garden Street, Tulkarem", "Samir Mansour", "0598881217", "doctor", "nurse");
    }

    private static void insertPatient(Connection connection, String patientId, String firstName, String lastName,
                                      String birthDate, String gender, String section, String room, String status,
                                      String priority, String bloodType, String diagnosis, String allergies,
                                      String phone, String email, String address, String emergencyName,
                                      String emergencyPhone, String doctorUsername, String staffUsername) throws SQLException {
        String sql = "INSERT INTO patients(patient_id, first_name, last_name, birth_date, gender, section, room, status, priority, "
                + "blood_type, diagnosis, allergies, phone, email, address, emergency_contact_name, emergency_contact_phone, "
                + "assigned_doctor_username, assigned_staff_username, created_at, updated_at) "
                + "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            statement.setString(index++, patientId);
            statement.setString(index++, firstName);
            statement.setString(index++, lastName);
            statement.setString(index++, birthDate);
            statement.setString(index++, gender);
            statement.setString(index++, section);
            statement.setString(index++, room);
            statement.setString(index++, status);
            statement.setString(index++, priority);
            statement.setString(index++, bloodType);
            statement.setString(index++, diagnosis);
            statement.setString(index++, allergies);
            statement.setString(index++, phone);
            statement.setString(index++, email);
            statement.setString(index++, address);
            statement.setString(index++, emergencyName);
            statement.setString(index++, emergencyPhone);
            statement.setString(index++, doctorUsername);
            statement.setString(index++, staffUsername);
            statement.setString(index++, sqlNow());
            statement.setString(index, sqlNow());
            statement.executeUpdate();
        }
    }

    private static void seedPatientVisits(Connection connection) throws SQLException {
        LocalDate today = LocalDate.now();
        insertVisit(connection, "215070632", today.atTime(8, 15), null, "ACTIVE",
                "Rapid heart rate observation. Patient remains in clinic for repeat vitals.");
        insertVisit(connection, "328015839", today.minusDays(2).atTime(10, 0), today.minusDays(2).atTime(10, 45), "COMPLETED",
                "Wound dressing completed. No signs of infection.");
        insertVisit(connection, "100000003", today.atTime(12, 30), null, "ACTIVE",
                "Fever and dehydration treatment with oral fluids and provider review.");
        insertVisit(connection, "147258368", today.minusDays(5).atTime(9, 20), today.minusDays(5).atTime(10, 10), "COMPLETED",
                "Snake bite follow-up. Swelling improved and safety instructions reviewed.");
        insertVisit(connection, "700000009", today.minusDays(12).atTime(14, 0), today.minusDays(12).atTime(14, 35), "DISCHARGED",
                "Follow-up visit completed. Patient discharged with routine advice.");
        insertVisit(connection, "700000036", today.minusDays(1).atTime(16, 10), null, "ACTIVE",
                "Blood pressure monitoring. Follow-up reading requested.");
        insertVisit(connection, "700000021", today.minusDays(8).atTime(11, 5), today.minusDays(8).atTime(11, 40), "DISCHARGED",
                "Lab result review completed. No urgent follow-up required.");
        insertVisit(connection, "100000002", today.minusDays(3).atTime(13, 15), today.minusDays(3).atTime(13, 50), "COMPLETED",
                "Migraine and nausea improved after clinic visit.");
    }

    private static void insertVisit(Connection connection, String patientId, LocalDateTime visitDate,
                                    LocalDateTime dischargeDate, String status, String report) throws SQLException {
        String sql = "INSERT INTO patient_visits(patient_id, visit_date, discharge_date, status, report, created_at) "
                + "VALUES(?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, patientId);
            statement.setString(2, visitDate.format(SQL_DATE_TIME));
            statement.setString(3, dischargeDate == null ? "" : dischargeDate.format(SQL_DATE_TIME));
            statement.setString(4, status);
            statement.setString(5, report);
            statement.setString(6, visitDate.format(SQL_DATE_TIME));
            statement.executeUpdate();
        }
    }

    private static void seedVitals(Connection connection) throws SQLException {
        LocalDateTime base = LocalDateTime.now().minusHours(3).withSecond(0).withNano(0);
        insertVitalSet(connection, "215070632", base, "90", "36.9", "120", "78", "98", "96");
        insertVital(connection, "215070632", "Heart Rate", "110", "bpm", base.plusMinutes(35), "nurse");
        insertVital(connection, "215070632", "Heart Rate", "120", "bpm", base.plusMinutes(70), "nurse");
        insertVitalSet(connection, "328015839", base.plusMinutes(10), "82", "36.8", "118", "76", "99", "102");
        insertVitalSet(connection, "100000003", base.plusMinutes(20), "118", "39.2", "145", "95", "96", "104");
        insertVitalSet(connection, "147258368", base.plusMinutes(30), "88", "37.1", "122", "80", "98", "99");
        insertVitalSet(connection, "700000009", base.minusDays(1), "76", "36.7", "116", "74", "99", "92");
        insertVitalSet(connection, "700000036", base.plusMinutes(40), "92", "36.8", "158", "98", "97", "118");
        insertVitalSet(connection, "700000021", base.minusDays(2), "72", "36.6", "112", "70", "99", "90");
        insertVitalSet(connection, "100000002", base.plusMinutes(50), "84", "36.9", "118", "74", "99", "95");
    }

    private static void insertVitalSet(Connection connection, String patientId, LocalDateTime recordedAt,
                                       String heartRate, String temperature, String systolic, String diastolic,
                                       String oxygen, String sugar) throws SQLException {
        insertVital(connection, patientId, "Heart Rate", heartRate, "bpm", recordedAt, "nurse");
        insertVital(connection, patientId, "Temperature", temperature, "C", recordedAt.plusMinutes(1), "nurse");
        insertVital(connection, patientId, "Systolic Pressure", systolic, "mmHg", recordedAt.plusMinutes(2), "nurse");
        insertVital(connection, patientId, "Diastolic Pressure", diastolic, "mmHg", recordedAt.plusMinutes(3), "nurse");
        insertVital(connection, patientId, "Oxygen Saturation", oxygen, "%", recordedAt.plusMinutes(4), "nurse");
        insertVital(connection, patientId, "Sugar Level", sugar, "mg/dL", recordedAt.plusMinutes(5), "nurse");
    }

    private static void insertVital(Connection connection, String patientId, String type, String value, String unit,
                                    LocalDateTime recordedAt, String staffUser) throws SQLException {
        String sql = "INSERT INTO vital_readings(patient_id, vital_type, value, unit, recorded_at, source_type, staff_user, device_id) "
                + "VALUES(?, ?, ?, ?, ?, 'Manual', ?, '')";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, patientId);
            statement.setString(2, type);
            statement.setString(3, value);
            statement.setString(4, unit);
            statement.setString(5, recordedAt.format(SQL_DATE_TIME));
            statement.setString(6, staffUser);
            statement.executeUpdate();
        }
    }

    private static void seedAlerts(Connection connection) throws SQLException {
        LocalDateTime now = LocalDateTime.now().withSecond(0).withNano(0);
        insertAlert(connection, "215070632", "CRITICAL", "Critical heart rate for Arial Shmohan: 120 bpm.", "ACTIVE",
                now.minusMinutes(35), null, "");
        insertAlert(connection, "100000003", "CRITICAL", "High fever for Omar Nasser: 39.2 C with elevated heart rate.", "ACTIVE",
                now.minusMinutes(25), null, "");
        insertAlert(connection, "700000036", "WARNING", "High blood pressure for Demo Patient 0037: 158/98 mmHg.", "ACTIVE",
                now.minusMinutes(18), null, "");
        insertAlert(connection, "328015839", "WARNING", "Wound care follow-up is due for Mohamad Dahbour.", "ACKNOWLEDGED",
                now.minusHours(2), "nurse", now.minusHours(1).format(SQL_DATE_TIME));
        insertAlert(connection, "147258368", "WARNING", "Follow-up review needed after snake bite visit.", "RESOLVED",
                now.minusDays(1), "doctor", now.minusHours(4).format(SQL_DATE_TIME));
    }

    private static void insertAlert(Connection connection, String patientId, String severity, String message,
                                    String status, LocalDateTime createdAt, String acknowledgedBy,
                                    String acknowledgedAt) throws SQLException {
        String sql = "INSERT INTO alerts(patient_id, severity, message, status, created_at, updated_at, acknowledged_by, acknowledged_at, cooldown_until) "
                + "VALUES(?, ?, ?, ?, ?, ?, ?, ?, '')";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, patientId);
            statement.setString(2, severity);
            statement.setString(3, message);
            statement.setString(4, status);
            statement.setString(5, createdAt.format(SQL_DATE_TIME));
            statement.setString(6, sqlNow());
            statement.setString(7, acknowledgedBy == null ? "" : acknowledgedBy);
            statement.setString(8, acknowledgedAt == null ? "" : acknowledgedAt);
            statement.executeUpdate();
        }
    }

    private static void seedNotifications(Connection connection) throws SQLException {
        insertNotification(connection, "doctor", "DOCTOR", "Clinic", "215070632", "CRITICAL",
                "Critical heart rate", "Arial Shmohan has a critical heart rate reading.", "UNREAD", "ALERT", "215070632");
        insertNotification(connection, "doctor", "DOCTOR", "Clinic", "100000003", "CRITICAL",
                "High fever", "Omar Nasser needs provider review for fever and dehydration.", "UNREAD", "ALERT", "100000003");
        insertNotification(connection, "nurse", "NURSE", "Clinic", "700000036", "WARNING",
                "Repeat blood pressure", "Repeat blood pressure for Demo Patient 0037.", "UNREAD", "ALERT", "700000036");
        insertNotification(connection, "secretary", "SECRETARY", "Front Desk", "", "INFO",
                "Appointment schedule", "Today has multiple urgent care appointments ready for check-in.", "UNREAD", "SYSTEM", "");
        insertNotification(connection, "admin", "ADMIN", "Administration", "", "INFO",
                "Billing review", "One unpaid invoice is waiting for review.", "READ", "BILLING", "");
    }

    private static void insertNotification(Connection connection, String username, String role, String section,
                                           String patientId, String severity, String title, String message,
                                           String status, String sourceType, String sourceId) throws SQLException {
        String sql = "INSERT INTO notifications(username, role, section, patient_id, severity, title, message, read, status, source_type, source_id, created_at, read_at) "
                + "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        boolean isRead = "READ".equalsIgnoreCase(status);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            statement.setString(2, role);
            statement.setString(3, section);
            statement.setString(4, patientId);
            statement.setString(5, severity);
            statement.setString(6, title);
            statement.setString(7, message);
            statement.setInt(8, isRead ? 1 : 0);
            statement.setString(9, status);
            statement.setString(10, sourceType);
            statement.setString(11, sourceId);
            statement.setString(12, sqlNow());
            statement.setString(13, isRead ? sqlNow() : "");
            statement.executeUpdate();
        }
    }

    private static void seedAppointments(Connection connection) throws SQLException {
        LocalDate today = LocalDate.now();
        insertAppointment(connection, "215070632", "Arial urgent care review", "VISIT",
                today.atTime(8, 0), today.atTime(8, 30), "Clinic Room 1", "doctor", "SCHEDULED",
                "Review rapid heart rate and dizziness.");
        insertAppointment(connection, "328015839", "Mohamad wound dressing", "PROCEDURE",
                today.atTime(10, 30), today.atTime(11, 0), "Treatment Room", "nurse", "SCHEDULED",
                "Clean and redress minor injury.");
        insertAppointment(connection, "100000003", "Omar fever review", "VISIT",
                today.atTime(13, 0), today.atTime(13, 30), "Clinic Room 2", "doctor", "COMPLETED",
                "Completed fever and dehydration review.");
        insertAppointment(connection, "147258368", "Sajda follow up", "FOLLOW_UP",
                today.plusDays(1).atTime(9, 0), today.plusDays(1).atTime(9, 30), "Clinic Room 1", "doctor", "SCHEDULED",
                "Snake bite follow-up.");
        insertAppointment(connection, "100000002", "Lina migraine follow up", "FOLLOW_UP",
                today.plusDays(7).atTime(11, 0), today.plusDays(7).atTime(11, 30), "Clinic Room 2", "doctor", "SCHEDULED",
                "Migraine and nausea follow-up.");
        insertAppointment(connection, "700000021", "Lab review call", "LAB_TEST",
                today.plusDays(2).atTime(12, 0), today.plusDays(2).atTime(12, 20), "Front Desk", "secretary", "CANCELLED",
                "Cancelled after results were reviewed by phone.");
    }

    private static void insertAppointment(Connection connection, String patientId, String title, String type,
                                          LocalDateTime start, LocalDateTime end, String location, String assignedStaff,
                                          String status, String notes) throws SQLException {
        String sql = "INSERT INTO appointments(patient_id, title, appointment_type, start_time, end_time, location, assigned_staff, status, notes, created_by, created_at, updated_at) "
                + "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, 'admin', ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, patientId);
            statement.setString(2, title);
            statement.setString(3, type);
            statement.setString(4, start.format(DISPLAY_DATE_TIME));
            statement.setString(5, end.format(DISPLAY_DATE_TIME));
            statement.setString(6, location);
            statement.setString(7, assignedStaff);
            statement.setString(8, status);
            statement.setString(9, notes);
            statement.setString(10, sqlNow());
            statement.setString(11, sqlNow());
            statement.executeUpdate();
        }
    }

    private static void seedBillingRecords(Connection connection) throws SQLException {
        LocalDateTime now = LocalDateTime.now();
        insertBilling(connection, "INV-DEMO-0001", "215070632", "Arial Shmohan",
                "Doctor Consultation, Nurse Assessment, Vital Signs Check", "Urgent Care Visit", 280.00,
                "PAID", "Insurance", "Paid through insurance at check-out.", now.minusHours(2), now.minusHours(2), "secretary");
        insertBilling(connection, "INV-DEMO-0002", "100000003", "Omar Nasser",
                "Doctor Consultation, IV Fluids, Lab Test", "Urgent Care Visit", 460.00,
                "UNPAID", "", "Waiting for family payment review.", now.minusHours(1), null, "secretary");
        insertBilling(connection, "INV-DEMO-0003", "328015839", "Mohamad Dahbour",
                "Wound Dressing, Doctor Consultation", "Procedure", 270.00,
                "PAID", "Cash", "Paid at front desk.", now.minusDays(1), now.minusDays(1), "secretary");
        insertBilling(connection, "INV-DEMO-0004", "100000002", "Lina Mansour",
                "Doctor Consultation", "Follow Up", 150.00,
                "CANCELLED", "Cancelled", "Cancelled duplicate invoice.", now.minusDays(2), null, "secretary");
    }

    private static void insertBilling(Connection connection, String invoiceNo, String patientId, String patientName,
                                      String serviceName, String visitType, double amount, String status,
                                      String paymentMethod, String notes, LocalDateTime createdAt,
                                      LocalDateTime paidAt, String createdBy) throws SQLException {
        String sql = "INSERT INTO billing_records(invoice_no, patient_id, patient_name, service_name, visit_type, amount, payment_status, payment_method, notes, created_at, paid_at, created_by) "
                + "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, invoiceNo);
            statement.setString(2, patientId);
            statement.setString(3, patientName);
            statement.setString(4, serviceName);
            statement.setString(5, visitType);
            statement.setDouble(6, amount);
            statement.setString(7, status);
            statement.setString(8, paymentMethod);
            statement.setString(9, notes);
            statement.setString(10, createdAt.format(SQL_DATE_TIME));
            statement.setString(11, paidAt == null ? "" : paidAt.format(SQL_DATE_TIME));
            statement.setString(12, createdBy);
            statement.executeUpdate();
        }
    }

    private static void seedMedicalFiles(Connection connection) throws SQLException, IOException {
        insertMedicalFile(connection, "MF-DEMO-0001", "215070632", "arial-lab-result.txt", "Lab Result",
                "admin", "Heart rate and basic lab review for presentation demo.",
                "Arial Shmohan lab result\nHeart rate trend requires provider review.\n");
        insertMedicalFile(connection, "MF-DEMO-0002", "215070632", "arial-imaging-note.txt", "Imaging",
                "doctor", "No acute imaging findings. Follow-up based on symptoms.",
                "Arial Shmohan imaging note\nNo acute finding in demo placeholder file.\n");
        insertMedicalFile(connection, "MF-DEMO-0003", "328015839", "mohamad-wound-care-note.txt", "Visit Note",
                "nurse", "Wound care note and dressing instructions.",
                "Mohamad Dahbour wound care note\nDressing changed and patient advised on warning signs.\n");
        insertMedicalFile(connection, "MF-DEMO-0004", "100000003", "omar-lab-result.txt", "Lab Result",
                "doctor", "Fever and dehydration lab review.",
                "Omar Nasser lab result\nHydration and temperature monitoring recommended.\n");
        insertMedicalFile(connection, "MF-DEMO-0005", "100000002", "lina-visit-note.txt", "Visit Note",
                "nurse", "Migraine visit note with nausea assessment.",
                "Lina Mansour visit note\nMigraine improved after clinic care.\n");
    }

    private static void insertMedicalFile(Connection connection, String fileId, String patientId, String originalName,
                                          String fileType, String uploadedBy, String summary, String fileContent)
            throws SQLException, IOException {
        Path patientUploadDir = Path.of("data", "uploads", patientId);
        Files.createDirectories(patientUploadDir);
        Path storedPath = patientUploadDir.resolve(originalName);
        Files.writeString(storedPath, fileContent, StandardCharsets.UTF_8);
        long size = Files.size(storedPath);

        String sql = "INSERT INTO medical_files(file_id, patient_id, original_name, stored_path, file_type, uploaded_by, uploaded_at, extracted_summary, file_size, notes) "
                + "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, fileId);
            statement.setString(2, patientId);
            statement.setString(3, originalName);
            statement.setString(4, storedPath.toString());
            statement.setString(5, fileType);
            statement.setString(6, uploadedBy);
            statement.setString(7, sqlNow());
            statement.setString(8, summary);
            statement.setLong(9, size);
            statement.setString(10, "Presentation demo placeholder file.");
            statement.executeUpdate();
        }
    }

    private static void seedMessages(Connection connection) throws SQLException {
        insertMessage(connection, "admin", "doctor", "DOCTOR", "Clinic", "215070632",
                "Review critical patient", "Please review Arial Shmohan's recent heart rate readings.", "HIGH", "SENT");
        insertMessage(connection, "nurse", "doctor", "DOCTOR", "Clinic", "100000003",
                "Fever patient update", "Omar Nasser has a high temperature and needs review.", "HIGH", "SENT");
        insertMessage(connection, "secretary", "admin", "ADMIN", "Administration", "",
                "Billing question", "One unpaid invoice is waiting for review.", "NORMAL", "SENT");
        insertMessage(connection, "doctor", "nurse", "NURSE", "Clinic", "215070632",
                "Vitals follow-up", "Please repeat vitals for Arial Shmohan in 30 minutes.", "NORMAL", "SENT");
    }

    private static void insertMessage(Connection connection, String sender, String recipientUsername,
                                      String recipientRole, String recipientSection, String patientId,
                                      String subject, String body, String priority, String status) throws SQLException {
        String sql = "INSERT INTO messages(sender_username, recipient_username, recipient_role, recipient_section, patient_id, subject, body, priority, status, created_at, read_at) "
                + "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, '')";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, sender);
            statement.setString(2, recipientUsername);
            statement.setString(3, recipientRole);
            statement.setString(4, recipientSection);
            statement.setString(5, patientId);
            statement.setString(6, subject);
            statement.setString(7, body);
            statement.setString(8, priority);
            statement.setString(9, status);
            statement.setString(10, sqlNow());
            statement.executeUpdate();
        }
    }

    private static String sqlNow() {
        return LocalDateTime.now().format(SQL_DATE_TIME);
    }
}
