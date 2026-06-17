import database.DatabaseManager;
import database.SchemaInitializer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public class DemoLargeDataSeeder {

    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter DISPLAY_DATE_TIME = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
    private static final DateTimeFormatter ISO_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter BACKUP_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private static final String[] SECTIONS = {
            "ER", "Surgery", "Internal Medicine", "Maternity", "Pediatrics", "Cardiology"
    };

    private static final Map<String, String> SECTION_PREFIXES = Map.of(
            "ER", "ER",
            "Surgery", "SUR",
            "Internal Medicine", "INT",
            "Maternity", "MAT",
            "Pediatrics", "PED",
            "Cardiology", "CAR"
    );

    private static final String[] BLOOD_TYPES = {"A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-"};
    private static final String[] GENDERS = {"Male", "Female"};
    private static final String[] REMINDER_TYPES = {"MEDICATION", "APPOINTMENT", "CHECKUP", "CUSTOM"};
    private static final String[] REMINDER_STATUSES = {"PENDING", "PENDING", "PENDING", "OVERDUE", "DONE", "MISSED"};
    private static final String[] APPOINTMENT_TYPES = {"CHECKUP", "SURGERY", "FOLLOW_UP", "LAB_TEST", "MEDICATION_REVIEW", "OTHER"};
    private static final String[] APPOINTMENT_STATUSES = {"SCHEDULED", "SCHEDULED", "SCHEDULED", "COMPLETED", "CANCELLED", "MISSED"};
    private static final String[] ALERT_SEVERITIES = {"WARNING", "CRITICAL", "EMERGENCY"};
    private static final String[] NOTIFICATION_SEVERITIES = {"INFO", "WARNING", "CRITICAL"};
    private static final String[] MESSAGE_PRIORITIES = {"NORMAL", "HIGH", "URGENT"};
    private static final String[] USERS = {"admin", "doctor", "nurse", "staff"};

    private static final List<MedicationTemplate> MEDICATIONS = List.of(
            new MedicationTemplate("Aspirin", "TABLET", "Oral", "Once daily", "mg", "mg, tablet", "Oral", 75, 650, 4000, 360, false, "Demo medication for cardiac and post-op workflows."),
            new MedicationTemplate("Ibuprofen", "TABLET", "Oral", "Every 8 hours", "mg", "mg, tablet", "Oral", 100, 800, 3200, 360, false, "Demo anti-inflammatory medication."),
            new MedicationTemplate("Amoxicillin", "CAPSULE", "Oral", "Three times daily", "mg", "mg, capsule", "Oral", 250, 1000, 3000, 480, false, "Demo antibiotic medication."),
            new MedicationTemplate("Metoprolol", "TABLET", "Oral", "Twice daily", "mg", "mg, tablet", "Oral", 12.5, 100, 400, 720, false, "Demo cardiology medication."),
            new MedicationTemplate("Vancomycin", "INJECTION", "IV", "Every 12 hours", "mg", "mg, mL", "IV", 250, 2000, 4000, 720, true, "Demo monitored IV antibiotic."),
            new MedicationTemplate("Norepinephrine", "INJECTION", "IV", "As needed", "mcg", "mcg, mL", "IV", 1, 50, 500, 30, true, "Demo emergency vasopressor.")
    );

    private static final SeedConfig SMALL = new SeedConfig("small", 50, 500, 100, 50, 20, 10, 5, 300, 20, 40, 15);
    private static final SeedConfig LARGE = new SeedConfig("large", 1000, 10000, 2000, 1000, 500, 200, 100, 5000, 250, 800, 180);

    public static void main(String[] args) throws Exception {
        SeedConfig config = parseConfig(args);
        if (config == null) {
            printUsage();
            return;
        }

        SchemaInitializer.initialize();
        Path databasePath = Path.of(DatabaseManager.getDatabasePath()).toAbsolutePath().normalize();
        Path backupPath = backupDatabase(databasePath);

        SeedSummary summary = new DemoLargeDataSeeder().seed(config, databasePath, backupPath);
        printSummary(summary);
    }

    private SeedSummary seed(SeedConfig config, Path databasePath, Path backupPath) throws Exception {
        SeedSummary summary = new SeedSummary(config.label, databasePath, backupPath);
        Random random = new Random(20260617L + config.patientCount);

        try (Connection connection = DatabaseManager.getConnection()) {
            connection.setAutoCommit(false);
            ensureSectionsAndRooms(connection, config, summary);
            ensureMedicationCatalog(connection);

            List<SeedPatient> patients = insertPatients(connection, config, random, summary);
            insertVitals(connection, config, random, patients, summary);
            List<MedicationAssignment> medicationAssignments = insertMedications(connection, config, random, patients, summary);
            insertReminders(connection, config, random, patients, medicationAssignments, summary);
            insertAppointments(connection, config, random, patients, summary);
            insertAlerts(connection, config, random, patients, summary);
            insertNewbornRecords(connection, config, random, patients, summary);
            insertDeceasedRecords(connection, config, random, patients, summary);
            insertNotifications(connection, config, random, patients, summary);
            insertMessages(connection, config, random, patients, summary);
            insertAuditLogs(connection, config, random, patients, summary);
            connection.commit();
        }

        return summary;
    }

    private static SeedConfig parseConfig(String[] args) {
        if (args == null || args.length == 0) {
            return SMALL;
        }
        for (String arg : args) {
            if ("--small".equalsIgnoreCase(arg)) {
                return SMALL;
            }
            if ("--large".equalsIgnoreCase(arg)) {
                return LARGE;
            }
        }
        return null;
    }

    private static void printUsage() {
        System.out.println("Usage: java -cp \"out;lib/*\" DemoLargeDataSeeder --small|--large");
    }

    private static Path backupDatabase(Path databasePath) throws Exception {
        Path backupsDir = Path.of("data", "backups");
        Files.createDirectories(backupsDir);
        Path backupPath = backupsDir.resolve("smart_patient_monitoring_before_seed_"
                + LocalDateTime.now().format(BACKUP_STAMP) + ".db").toAbsolutePath().normalize();

        try (Connection connection = DatabaseManager.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA wal_checkpoint(FULL)");
            statement.execute("VACUUM INTO '" + sqlPath(backupPath) + "'");
            return backupPath;
        } catch (Exception vacuumFailure) {
            Files.copy(databasePath, backupPath);
            return backupPath;
        }
    }

    private void ensureSectionsAndRooms(Connection connection, SeedConfig config, SeedSummary summary) throws Exception {
        int roomsPerSection = Math.max(6, (config.patientCount / SECTIONS.length / 8) + 2);
        try (PreparedStatement sectionStatement = connection.prepareStatement(
                "INSERT OR IGNORE INTO sections(name, status, notes, created_at, updated_at) "
                        + "VALUES(?, 'ACTIVE', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
             PreparedStatement roomStatement = connection.prepareStatement(
                     "INSERT OR IGNORE INTO rooms(section, room_number, capacity, floor_number, room_sequence, status, notes, updated_at) "
                             + "VALUES(?, ?, ?, ?, ?, 'ACTIVE', ?, CURRENT_TIMESTAMP)")) {
            for (int i = 0; i < SECTIONS.length; i++) {
                String section = SECTIONS[i];
                sectionStatement.setString(1, section);
                sectionStatement.setString(2, "Demo seeder maintained section.");
                summary.sectionsInserted += sectionStatement.executeUpdate();

                for (int sequence = 1; sequence <= roomsPerSection; sequence++) {
                    String roomNumber = SECTION_PREFIXES.get(section) + "-" + (i + 1) + String.format(Locale.ROOT, "%03d", sequence);
                    roomStatement.setString(1, section);
                    roomStatement.setString(2, roomNumber);
                    roomStatement.setInt(3, section.equals("ER") ? 4 : 2 + (sequence % 3));
                    roomStatement.setInt(4, i + 1);
                    roomStatement.setInt(5, sequence);
                    roomStatement.setString(6, "Demo generated room.");
                    summary.roomsInserted += roomStatement.executeUpdate();
                }
            }
        }
    }

    private void ensureMedicationCatalog(Connection connection) throws Exception {
        try (PreparedStatement catalogStatement = connection.prepareStatement(
                "INSERT OR IGNORE INTO medication_catalog(name, form_type, default_route, default_frequency, default_unit, allowed_units, allowed_routes, "
                        + "min_single_dose, max_single_dose, max_daily_dose, min_interval_minutes, requires_doctor_override, danger_notes, notes, active, updated_at) "
                        + "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, CURRENT_TIMESTAMP)");
             PreparedStatement interactionStatement = connection.prepareStatement(
                     "INSERT OR IGNORE INTO medication_interactions(medication_a_id, medication_b_id, medication_a, medication_b, severity, min_wait_minutes, notes, message, active, updated_at) "
                             + "VALUES(?, ?, ?, ?, ?, ?, ?, ?, 1, CURRENT_TIMESTAMP)")) {
            for (MedicationTemplate medication : MEDICATIONS) {
                catalogStatement.setString(1, medication.name);
                catalogStatement.setString(2, medication.formType);
                catalogStatement.setString(3, medication.defaultRoute);
                catalogStatement.setString(4, medication.defaultFrequency);
                catalogStatement.setString(5, medication.defaultUnit);
                catalogStatement.setString(6, medication.allowedUnits);
                catalogStatement.setString(7, medication.allowedRoutes);
                catalogStatement.setDouble(8, medication.minSingleDose);
                catalogStatement.setDouble(9, medication.maxSingleDose);
                catalogStatement.setDouble(10, medication.maxDailyDose);
                catalogStatement.setDouble(11, medication.minIntervalMinutes);
                catalogStatement.setInt(12, medication.requiresDoctorOverride ? 1 : 0);
                catalogStatement.setString(13, medication.notes);
                catalogStatement.setString(14, medication.notes);
                catalogStatement.executeUpdate();
            }

            insertInteraction(connection, interactionStatement, "Ibuprofen", "Aspirin", "WARNING",
                    "Increased bleeding risk in demo rule.");
            insertInteraction(connection, interactionStatement, "Aspirin", "Norepinephrine", "DANGEROUS",
                    "Dangerous interaction demo rule requiring doctor override.");
        }
    }

    private void insertInteraction(Connection connection, PreparedStatement statement, String a, String b,
                                   String severity, String note) throws Exception {
        long aId = catalogId(connection, a);
        long bId = catalogId(connection, b);
        statement.setLong(1, aId);
        statement.setLong(2, bId);
        statement.setString(3, a);
        statement.setString(4, b);
        statement.setString(5, severity);
        statement.setInt(6, 0);
        statement.setString(7, note);
        statement.setString(8, note);
        statement.executeUpdate();
    }

    private List<SeedPatient> insertPatients(Connection connection, SeedConfig config, Random random, SeedSummary summary) throws Exception {
        ArrayList<SeedPatient> patients = new ArrayList<>();
        long nextPatientNumber = nextNumericId(connection, "patients", "patient_id", 700000000L, 799999999L);
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO patients(patient_id, first_name, last_name, birth_date, gender, section, room, status, priority, blood_type, diagnosis, "
                        + "assigned_doctor_username, assigned_staff_username, created_at, updated_at) "
                        + "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")) {
            for (int i = 1; i <= config.patientCount; i++) {
                String patientId = String.format(Locale.ROOT, "%09d", nextPatientNumber++);
                String section = sectionForIndex(i);
                String room = roomFor(section, i, config);
                String gender = genderFor(section, i, random);
                String status = statusForPatient(i);
                String priority = priorityForPatient(i, status);
                String birthDate = birthDateFor(section, gender, random);
                String bloodType = BLOOD_TYPES[i % BLOOD_TYPES.length];
                String diagnosis = diagnosisFor(section, priority, i);

                statement.setString(1, patientId);
                statement.setString(2, "Demo");
                statement.setString(3, "Patient " + String.format(Locale.ROOT, "%04d", i));
                statement.setString(4, birthDate);
                statement.setString(5, gender);
                statement.setString(6, section);
                statement.setString(7, room);
                statement.setString(8, status);
                statement.setString(9, priority);
                statement.setString(10, bloodType);
                statement.setString(11, diagnosis);
                statement.setString(12, "doctor");
                statement.setString(13, "nurse");
                statement.executeUpdate();
                summary.patientsInserted++;

                patients.add(new SeedPatient(patientId, "Demo Patient " + String.format(Locale.ROOT, "%04d", i),
                        section, room, status, priority, gender));
            }
        }
        return patients;
    }

    private void insertVitals(Connection connection, SeedConfig config, Random random, List<SeedPatient> patients,
                              SeedSummary summary) throws Exception {
        String[] vitalTypes = {
                "Heart Rate", "Systolic Pressure", "Diastolic Pressure",
                "Oxygen Saturation", "Temperature", "Sugar Level"
        };
        String[] units = {"bpm", "mmHg", "mmHg", "%", "C", "mg/dL"};
        List<SeedPatient> activePatients = activePatients(patients);
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO vital_readings(patient_id, vital_type, value, unit, recorded_at, source_type, staff_user, device_id) "
                        + "VALUES(?, ?, ?, ?, ?, 'Manual', 'nurse', '')")) {
            for (int i = 0; i < config.vitalCount; i++) {
                SeedPatient patient = activePatients.get(i % activePatients.size());
                int typeIndex = i % vitalTypes.length;
                LocalDateTime time = LocalDateTime.now().minusMinutes((i * 17L) % (60L * 24 * 45));
                statement.setString(1, patient.patientId);
                statement.setString(2, vitalTypes[typeIndex]);
                statement.setString(3, vitalValue(patient.priority, typeIndex, random));
                statement.setString(4, units[typeIndex]);
                statement.setString(5, time.format(DISPLAY_DATE_TIME));
                statement.executeUpdate();
                summary.vitalsInserted++;
            }
        }
    }

    private List<MedicationAssignment> insertMedications(Connection connection, SeedConfig config, Random random,
                                                         List<SeedPatient> patients, SeedSummary summary) throws Exception {
        ArrayList<MedicationAssignment> assignments = new ArrayList<>();
        List<SeedPatient> activePatients = activePatients(patients);
        try (PreparedStatement medicationStatement = connection.prepareStatement(
                "INSERT INTO medications(patient_id, catalog_medication_id, name, dose, dose_amount, dose_unit, route, frequency, active) "
                        + "VALUES(?, ?, ?, ?, ?, ?, ?, ?, 1)", Statement.RETURN_GENERATED_KEYS);
             PreparedStatement eventStatement = connection.prepareStatement(
                     "INSERT INTO medication_events(medication_id, patient_id, given_by, given_at, notes, status, given_amount, given_unit, route, override_used, override_reason, safety_status) "
                             + "VALUES(?, ?, 'nurse', ?, ?, 'GIVEN', ?, ?, ?, 0, '', 'NORMAL')")) {
            for (int i = 1; i <= config.medicationCount; i++) {
                SeedPatient patient = activePatients.get(i % activePatients.size());
                MedicationTemplate medication = MEDICATIONS.get(i % MEDICATIONS.size());
                double amount = medication.minSingleDose + (random.nextInt(4) * Math.max(1.0, medication.minSingleDose));
                amount = Math.min(amount, medication.maxSingleDose);

                medicationStatement.setString(1, patient.patientId);
                medicationStatement.setLong(2, catalogId(connection, medication.name));
                medicationStatement.setString(3, medication.name);
                medicationStatement.setString(4, formatAmount(amount) + " " + medication.defaultUnit);
                medicationStatement.setDouble(5, amount);
                medicationStatement.setString(6, medication.defaultUnit);
                medicationStatement.setString(7, medication.defaultRoute);
                medicationStatement.setString(8, medication.defaultFrequency);
                medicationStatement.executeUpdate();
                summary.medicationsInserted++;

                long medicationId;
                try (ResultSet keys = medicationStatement.getGeneratedKeys()) {
                    medicationId = keys.next() ? keys.getLong(1) : 0L;
                }
                assignments.add(new MedicationAssignment(medicationId, patient.patientId, medication.name));

                eventStatement.setLong(1, medicationId);
                eventStatement.setString(2, patient.patientId);
                eventStatement.setString(3, LocalDateTime.now().minusHours(i % 120).format(DISPLAY_DATE_TIME));
                eventStatement.setString(4, "Demo medication administration " + String.format(Locale.ROOT, "%04d", i));
                eventStatement.setDouble(5, amount);
                eventStatement.setString(6, medication.defaultUnit);
                eventStatement.setString(7, medication.defaultRoute);
                eventStatement.executeUpdate();
                summary.medicationEventsInserted++;
            }
        }
        return assignments;
    }

    private void insertReminders(Connection connection, SeedConfig config, Random random, List<SeedPatient> patients,
                                 List<MedicationAssignment> medications, SeedSummary summary) throws Exception {
        List<SeedPatient> activePatients = activePatients(patients);
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO reminders(patient_id, medication_id, reminder_type, title, due_time, repeat_rule, status, assigned_to, created_by, notes, created_at, updated_at) "
                        + "VALUES(?, ?, ?, ?, ?, ?, ?, ?, 'admin', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")) {
            for (int i = 1; i <= config.reminderCount; i++) {
                SeedPatient patient = activePatients.get(i % activePatients.size());
                String type = REMINDER_TYPES[i % REMINDER_TYPES.length];
                String status = REMINDER_STATUSES[i % REMINDER_STATUSES.length];
                MedicationAssignment medication = medications.get(i % medications.size());
                Long medicationId = "MEDICATION".equals(type) ? medication.medicationId : null;
                LocalDateTime due = LocalDateTime.now().plusMinutes((i * 35L) % (60L * 24 * 20) - (60L * 24 * 5));

                statement.setString(1, patient.patientId);
                if (medicationId == null || medicationId <= 0) {
                    statement.setNull(2, java.sql.Types.INTEGER);
                } else {
                    statement.setLong(2, medicationId);
                }
                statement.setString(3, type);
                statement.setString(4, "Demo Reminder " + String.format(Locale.ROOT, "%04d", i));
                statement.setString(5, due.format(DISPLAY_DATE_TIME));
                statement.setString(6, "CUSTOM".equals(type) ? "" : "Every shift");
                statement.setString(7, status);
                statement.setString(8, "CHECKUP".equals(type) ? "doctor" : "nurse");
                statement.setString(9, "Demo reminder note " + String.format(Locale.ROOT, "%04d", i));
                statement.executeUpdate();
                summary.remindersInserted++;
            }
        }
    }

    private void insertAppointments(Connection connection, SeedConfig config, Random random, List<SeedPatient> patients,
                                    SeedSummary summary) throws Exception {
        List<SeedPatient> activePatients = activePatients(patients);
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO appointments(patient_id, title, appointment_type, start_time, end_time, location, assigned_staff, status, notes, created_by, created_at, updated_at) "
                        + "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, 'admin', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")) {
            for (int i = 1; i <= config.appointmentCount; i++) {
                SeedPatient patient = activePatients.get(i % activePatients.size());
                String type = APPOINTMENT_TYPES[i % APPOINTMENT_TYPES.length];
                String status = APPOINTMENT_STATUSES[i % APPOINTMENT_STATUSES.length];
                LocalDateTime start = LocalDateTime.now().plusHours((i * 7L) % (24L * 30) - (24L * 3));
                LocalDateTime end = start.plusMinutes(30 + random.nextInt(120));

                statement.setString(1, patient.patientId);
                statement.setString(2, "Demo Appointment " + String.format(Locale.ROOT, "%04d", i));
                statement.setString(3, type);
                statement.setString(4, start.format(DISPLAY_DATE_TIME));
                statement.setString(5, end.format(DISPLAY_DATE_TIME));
                statement.setString(6, patient.section + " Desk");
                statement.setString(7, "doctor");
                statement.setString(8, status);
                statement.setString(9, "Demo appointment note " + String.format(Locale.ROOT, "%04d", i));
                statement.executeUpdate();
                summary.appointmentsInserted++;
            }
        }
    }

    private void insertAlerts(Connection connection, SeedConfig config, Random random, List<SeedPatient> patients,
                              SeedSummary summary) throws Exception {
        List<SeedPatient> alertPatients = activePatients(patients);
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO alerts(patient_id, severity, message, status, created_at, updated_at, acknowledged_by, acknowledged_at) "
                        + "VALUES(?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?, ?)")) {
            for (int i = 1; i <= config.alertCount; i++) {
                SeedPatient patient = alertPatients.get(i % alertPatients.size());
                String severity = ALERT_SEVERITIES[i % ALERT_SEVERITIES.length];
                String status = i % 6 == 0 ? "ACKNOWLEDGED" : "ACTIVE";
                statement.setString(1, patient.patientId);
                statement.setString(2, severity);
                statement.setString(3, "Demo alert " + String.format(Locale.ROOT, "%04d", i) + " for " + patient.displayName);
                statement.setString(4, status);
                statement.setString(5, "ACKNOWLEDGED".equals(status) ? "doctor" : "");
                statement.setString(6, "ACKNOWLEDGED".equals(status)
                        ? LocalDateTime.now().minusMinutes((i * 13L) % 600).format(DISPLAY_DATE_TIME) : "");
                statement.executeUpdate();
                summary.alertsInserted++;
            }
        }
    }

    private void insertNewbornRecords(Connection connection, SeedConfig config, Random random, List<SeedPatient> patients,
                                      SeedSummary summary) throws Exception {
        List<SeedPatient> mothers = motherCandidates(patients);
        long nextNewbornNumber = nextNumericId(connection, "newborn_records", "newborn_id", 800000000L, 899999999L);
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO newborn_records(newborn_id, mother_patient_id, father_name, mother_name, baby_name, gender, birth_time, birth_weight, birth_length, delivery_type, room, section, doctor_or_midwife, notes, certificate_path, created_by, created_at, updated_at, review_status) "
                        + "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'Maternity', 'doctor', ?, '', 'admin', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'DRAFT')")) {
            for (int i = 1; i <= config.newbornCount; i++) {
                SeedPatient mother = mothers.get(i % mothers.size());
                String newbornId = String.format(Locale.ROOT, "%09d", nextNewbornNumber++);
                LocalDateTime birthTime = LocalDateTime.now().minusHours((i * 11L) % (24L * 120));

                statement.setString(1, newbornId);
                statement.setString(2, mother.patientId);
                statement.setString(3, "Demo Father " + String.format(Locale.ROOT, "%04d", i));
                statement.setString(4, mother.displayName);
                statement.setString(5, "Demo Newborn " + String.format(Locale.ROOT, "%04d", i));
                statement.setString(6, i % 2 == 0 ? "Female" : "Male");
                statement.setString(7, birthTime.format(ISO_DATE_TIME));
                statement.setDouble(8, 2.6 + ((i % 12) * 0.12));
                statement.setDouble(9, 47 + (i % 8));
                statement.setString(10, i % 5 == 0 ? "C_SECTION" : "NATURAL");
                statement.setString(11, mother.room);
                statement.setString(12, "Demo newborn note " + String.format(Locale.ROOT, "%04d", i));
                statement.executeUpdate();
                summary.newbornsInserted++;
            }
        }
    }

    private void insertDeceasedRecords(Connection connection, SeedConfig config, Random random, List<SeedPatient> patients,
                                       SeedSummary summary) throws Exception {
        List<SeedPatient> candidates = new ArrayList<>();
        for (SeedPatient patient : patients) {
            if (!"DECEASED".equalsIgnoreCase(patient.status)) {
                candidates.add(patient);
            }
        }

        try (PreparedStatement patientUpdate = connection.prepareStatement(
                "UPDATE patients SET status = 'DECEASED', priority = 'NORMAL', updated_at = CURRENT_TIMESTAMP WHERE patient_id = ?");
             PreparedStatement deceasedInsert = connection.prepareStatement(
                     "INSERT INTO deceased_records(patient_id, death_time, pronounced_by, cause_of_death, notes, certificate_path, created_by, created_at, updated_at, review_status) "
                             + "VALUES(?, ?, ?, ?, ?, '', 'admin', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'DRAFT')")) {
            for (int i = 1; i <= config.deceasedCount; i++) {
                SeedPatient patient = candidates.get(candidates.size() - i);
                patientUpdate.setString(1, patient.patientId);
                patientUpdate.executeUpdate();

                deceasedInsert.setString(1, patient.patientId);
                deceasedInsert.setString(2, LocalDateTime.now().minusHours((i * 9L) % (24L * 90)).format(ISO_DATE_TIME));
                deceasedInsert.setString(3, "Dr. Demo " + ((i % 4) + 1));
                deceasedInsert.setString(4, "Demo clinical cause " + String.format(Locale.ROOT, "%04d", i));
                deceasedInsert.setString(5, "Demo deceased record " + String.format(Locale.ROOT, "%04d", i));
                deceasedInsert.executeUpdate();
                summary.deceasedInserted++;
            }
        }
    }

    private void insertNotifications(Connection connection, SeedConfig config, Random random, List<SeedPatient> patients,
                                     SeedSummary summary) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO notifications(username, role, section, patient_id, severity, title, message, status, source_type, source_id, read, created_at, read_at) "
                        + "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            for (int i = 1; i <= config.notificationCount; i++) {
                SeedPatient patient = patients.get(i % patients.size());
                String severity = NOTIFICATION_SEVERITIES[i % NOTIFICATION_SEVERITIES.length];
                String status = i % 5 == 0 ? "READ" : "UNREAD";
                String role = i % 3 == 0 ? "DOCTOR" : "NURSE";
                LocalDateTime createdAt = LocalDateTime.now().minusMinutes((i * 19L) % (60L * 24 * 30));

                statement.setString(1, "");
                statement.setString(2, role);
                statement.setString(3, patient.section);
                statement.setString(4, patient.patientId);
                statement.setString(5, severity);
                statement.setString(6, "Demo Notification " + String.format(Locale.ROOT, "%04d", i));
                statement.setString(7, "Demo notification for " + patient.displayName);
                statement.setString(8, status);
                statement.setString(9, i % 2 == 0 ? "ALERT" : "REMINDER");
                statement.setString(10, patient.patientId);
                statement.setInt(11, "READ".equals(status) ? 1 : 0);
                statement.setString(12, createdAt.format(ISO_DATE_TIME));
                statement.setString(13, "READ".equals(status) ? createdAt.plusMinutes(15).format(ISO_DATE_TIME) : null);
                statement.executeUpdate();
                summary.notificationsInserted++;
            }
        }
    }

    private void insertMessages(Connection connection, SeedConfig config, Random random, List<SeedPatient> patients,
                                SeedSummary summary) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO messages(sender_username, recipient_username, recipient_role, recipient_section, patient_id, subject, body, priority, status, created_at, read_at) "
                        + "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            for (int i = 1; i <= config.messageCount; i++) {
                SeedPatient patient = patients.get(i % patients.size());
                String sender = USERS[i % USERS.length];
                String recipient = USERS[(i + 1) % USERS.length];
                String status = i % 4 == 0 ? "READ" : "SENT";
                LocalDateTime createdAt = LocalDateTime.now().minusMinutes((i * 23L) % (60L * 24 * 30));

                statement.setString(1, sender);
                statement.setString(2, recipient);
                statement.setString(3, null);
                statement.setString(4, null);
                statement.setString(5, patient.patientId);
                statement.setString(6, "Demo Message " + String.format(Locale.ROOT, "%04d", i));
                statement.setString(7, "Demo internal message about " + patient.displayName);
                statement.setString(8, MESSAGE_PRIORITIES[i % MESSAGE_PRIORITIES.length]);
                statement.setString(9, status);
                statement.setString(10, createdAt.format(ISO_DATE_TIME));
                statement.setString(11, "READ".equals(status) ? createdAt.plusMinutes(10).format(ISO_DATE_TIME) : null);
                statement.executeUpdate();
                summary.messagesInserted++;
            }
        }
    }

    private void insertAuditLogs(Connection connection, SeedConfig config, Random random, List<SeedPatient> patients,
                                 SeedSummary summary) throws Exception {
        String[] actions = {
                "LOGIN",
                "VIEW_PATIENT_FILE",
                "ENTER_VITALS",
                "CREATE_REMINDER",
                "CREATE_APPOINTMENT",
                "OPEN_NOTIFICATION_CENTER",
                "VIEW_MEDICATION_OVERVIEW",
                "OPEN_CERTIFICATE_REGISTRY"
        };
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO audit_logs(username, action, created_at) VALUES(?, ?, ?)")) {
            for (int i = 1; i <= config.auditLogCount; i++) {
                SeedPatient patient = patients.get(i % patients.size());
                String action = actions[i % actions.length] + " demo_patient_id=" + patient.patientId
                        + " demo_index=" + String.format(Locale.ROOT, "%04d", i);
                LocalDateTime createdAt = LocalDateTime.now().minusMinutes((i * 29L) % (60L * 24 * 45));

                statement.setString(1, USERS[i % USERS.length]);
                statement.setString(2, action);
                statement.setString(3, createdAt.format(ISO_DATE_TIME));
                statement.executeUpdate();
                summary.auditLogsInserted++;
            }
        }
    }

    private static List<SeedPatient> activePatients(List<SeedPatient> patients) {
        ArrayList<SeedPatient> active = new ArrayList<>();
        for (SeedPatient patient : patients) {
            if (!"DECEASED".equalsIgnoreCase(patient.status) && !"DISCHARGED".equalsIgnoreCase(patient.status)) {
                active.add(patient);
            }
        }
        return active.isEmpty() ? patients : active;
    }

    private static List<SeedPatient> motherCandidates(List<SeedPatient> patients) {
        ArrayList<SeedPatient> mothers = new ArrayList<>();
        for (SeedPatient patient : patients) {
            if ("Maternity".equals(patient.section) && "Female".equalsIgnoreCase(patient.gender)
                    && !"DECEASED".equalsIgnoreCase(patient.status)) {
                mothers.add(patient);
            }
        }
        return mothers.isEmpty() ? activePatients(patients) : mothers;
    }

    private static String sectionForIndex(int index) {
        return SECTIONS[index % SECTIONS.length];
    }

    private static String roomFor(String section, int index, SeedConfig config) {
        int roomsPerSection = Math.max(6, (config.patientCount / SECTIONS.length / 8) + 2);
        int sectionIndex = 0;
        for (int i = 0; i < SECTIONS.length; i++) {
            if (SECTIONS[i].equals(section)) {
                sectionIndex = i;
                break;
            }
        }
        int sequence = (index % roomsPerSection) + 1;
        return SECTION_PREFIXES.get(section) + "-" + (sectionIndex + 1) + String.format(Locale.ROOT, "%03d", sequence);
    }

    private static String genderFor(String section, int index, Random random) {
        if ("Maternity".equals(section)) {
            return index % 4 == 0 ? "Male" : "Female";
        }
        return GENDERS[random.nextInt(GENDERS.length)];
    }

    private static String statusForPatient(int index) {
        return index % 29 == 0 ? "DISCHARGED" : "Active";
    }

    private static String priorityForPatient(int index, String status) {
        if (!"Active".equalsIgnoreCase(status)) {
            return "NORMAL";
        }
        if (index % 101 == 0) {
            return "EMERGENCY";
        }
        if (index % 37 == 0) {
            return "CRITICAL";
        }
        if (index % 11 == 0) {
            return "HIGH";
        }
        return "NORMAL";
    }

    private static String birthDateFor(String section, String gender, Random random) {
        int startYear = ("Maternity".equals(section) && "Female".equalsIgnoreCase(gender)) ? 1988 : 1945;
        int endYear = ("Pediatrics".equals(section)) ? 2020 : 2010;
        int year = startYear + random.nextInt(Math.max(1, endYear - startYear + 1));
        int dayOfYear = 1 + random.nextInt(Year.of(year).length());
        return LocalDate.ofYearDay(year, dayOfYear).format(DISPLAY_DATE);
    }

    private static String diagnosisFor(String section, String priority, int index) {
        return "Demo " + section + " case " + String.format(Locale.ROOT, "%04d", index)
                + " with priority " + priority + ".";
    }

    private static String vitalValue(String priority, int typeIndex, Random random) {
        switch (typeIndex) {
            case 0:
                return String.valueOf("EMERGENCY".equals(priority) ? 140 + random.nextInt(20)
                        : "CRITICAL".equals(priority) ? 120 + random.nextInt(18)
                        : "HIGH".equals(priority) ? 100 + random.nextInt(12)
                        : 68 + random.nextInt(26));
            case 1:
                return String.valueOf("EMERGENCY".equals(priority) ? 185 + random.nextInt(25)
                        : "CRITICAL".equals(priority) ? 155 + random.nextInt(20)
                        : "HIGH".equals(priority) ? 138 + random.nextInt(12)
                        : 108 + random.nextInt(18));
            case 2:
                return String.valueOf("EMERGENCY".equals(priority) ? 110 + random.nextInt(18)
                        : "CRITICAL".equals(priority) ? 95 + random.nextInt(12)
                        : "HIGH".equals(priority) ? 86 + random.nextInt(10)
                        : 68 + random.nextInt(12));
            case 3:
                return String.valueOf("EMERGENCY".equals(priority) ? 80 + random.nextInt(6)
                        : "CRITICAL".equals(priority) ? 85 + random.nextInt(6)
                        : "HIGH".equals(priority) ? 90 + random.nextInt(5)
                        : 96 + random.nextInt(4));
            case 4:
                return formatAmount("EMERGENCY".equals(priority) ? 39.8 + (random.nextInt(5) * 0.1)
                        : "CRITICAL".equals(priority) ? 38.8 + (random.nextInt(6) * 0.1)
                        : "HIGH".equals(priority) ? 37.8 + (random.nextInt(6) * 0.1)
                        : 36.4 + (random.nextInt(8) * 0.1));
            default:
                return String.valueOf("EMERGENCY".equals(priority) ? 180 + random.nextInt(30)
                        : "CRITICAL".equals(priority) ? 145 + random.nextInt(25)
                        : "HIGH".equals(priority) ? 120 + random.nextInt(20)
                        : 88 + random.nextInt(24));
        }
    }

    private static long catalogId(Connection connection, String name) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id FROM medication_catalog WHERE LOWER(name) = LOWER(?)")) {
            statement.setString(1, name);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getLong(1);
                }
            }
        }
        throw new IllegalStateException("Missing medication catalog row for " + name);
    }

    private static long nextNumericId(Connection connection, String table, String column, long minInclusive, long maxInclusive) throws Exception {
        String sql = "SELECT COALESCE(MAX(CAST(" + column + " AS INTEGER)), ?) FROM " + table
                + " WHERE LENGTH(" + column + ") = 9 AND " + column + " GLOB '[0-9]*' AND CAST(" + column + " AS INTEGER) BETWEEN ? AND ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, minInclusive - 1);
            statement.setLong(2, minInclusive);
            statement.setLong(3, maxInclusive);
            try (ResultSet resultSet = statement.executeQuery()) {
                long value = resultSet.next() ? resultSet.getLong(1) : minInclusive - 1;
                return Math.max(minInclusive, value + 1);
            }
        }
    }

    private static String sqlPath(Path path) {
        return path.toAbsolutePath().normalize().toString().replace("\\", "/").replace("'", "''");
    }

    private static String formatAmount(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format(Locale.ROOT, "%.1f", value);
    }

    private static void printSummary(SeedSummary summary) {
        System.out.println("Demo large data seeding complete (" + summary.mode + ").");
        System.out.println("patients inserted=" + summary.patientsInserted);
        System.out.println("vitals inserted=" + summary.vitalsInserted);
        System.out.println("medications inserted=" + summary.medicationsInserted);
        System.out.println("reminders inserted=" + summary.remindersInserted);
        System.out.println("appointments inserted=" + summary.appointmentsInserted);
        System.out.println("newborns inserted=" + summary.newbornsInserted);
        System.out.println("deceased inserted=" + summary.deceasedInserted);
        System.out.println("audit logs inserted=" + summary.auditLogsInserted);
        System.out.println("alerts inserted=" + summary.alertsInserted);
        System.out.println("notifications inserted=" + summary.notificationsInserted);
        System.out.println("messages inserted=" + summary.messagesInserted);
        System.out.println("medication events inserted=" + summary.medicationEventsInserted);
        System.out.println("sections inserted=" + summary.sectionsInserted);
        System.out.println("rooms inserted=" + summary.roomsInserted);
        System.out.println("database path=" + summary.databasePath);
        System.out.println("backup path=" + summary.backupPath);
    }

    private static class SeedConfig {
        private final String label;
        private final int patientCount;
        private final int vitalCount;
        private final int medicationCount;
        private final int reminderCount;
        private final int appointmentCount;
        private final int newbornCount;
        private final int deceasedCount;
        private final int auditLogCount;
        private final int messageCount;
        private final int notificationCount;
        private final int alertCount;

        private SeedConfig(String label, int patientCount, int vitalCount, int medicationCount, int reminderCount,
                           int appointmentCount, int newbornCount, int deceasedCount, int auditLogCount,
                           int messageCount, int notificationCount, int alertCount) {
            this.label = label;
            this.patientCount = patientCount;
            this.vitalCount = vitalCount;
            this.medicationCount = medicationCount;
            this.reminderCount = reminderCount;
            this.appointmentCount = appointmentCount;
            this.newbornCount = newbornCount;
            this.deceasedCount = deceasedCount;
            this.auditLogCount = auditLogCount;
            this.messageCount = messageCount;
            this.notificationCount = notificationCount;
            this.alertCount = alertCount;
        }
    }

    private static class SeedSummary {
        private final String mode;
        private final Path databasePath;
        private final Path backupPath;
        private int patientsInserted;
        private int vitalsInserted;
        private int medicationsInserted;
        private int medicationEventsInserted;
        private int remindersInserted;
        private int appointmentsInserted;
        private int newbornsInserted;
        private int deceasedInserted;
        private int auditLogsInserted;
        private int alertsInserted;
        private int notificationsInserted;
        private int messagesInserted;
        private int sectionsInserted;
        private int roomsInserted;

        private SeedSummary(String mode, Path databasePath, Path backupPath) {
            this.mode = mode;
            this.databasePath = databasePath;
            this.backupPath = backupPath;
        }
    }

    private static class SeedPatient {
        private final String patientId;
        private final String displayName;
        private final String section;
        private final String room;
        private final String status;
        private final String priority;
        private final String gender;

        private SeedPatient(String patientId, String displayName, String section, String room,
                            String status, String priority, String gender) {
            this.patientId = patientId;
            this.displayName = displayName;
            this.section = section;
            this.room = room;
            this.status = status;
            this.priority = priority;
            this.gender = gender;
        }
    }

    private static class MedicationAssignment {
        private final long medicationId;
        private final String patientId;
        private final String medicationName;

        private MedicationAssignment(long medicationId, String patientId, String medicationName) {
            this.medicationId = medicationId;
            this.patientId = patientId;
            this.medicationName = medicationName;
        }
    }

    private static class MedicationTemplate {
        private final String name;
        private final String formType;
        private final String defaultRoute;
        private final String defaultFrequency;
        private final String defaultUnit;
        private final String allowedUnits;
        private final String allowedRoutes;
        private final double minSingleDose;
        private final double maxSingleDose;
        private final double maxDailyDose;
        private final double minIntervalMinutes;
        private final boolean requiresDoctorOverride;
        private final String notes;

        private MedicationTemplate(String name, String formType, String defaultRoute, String defaultFrequency,
                                   String defaultUnit, String allowedUnits, String allowedRoutes,
                                   double minSingleDose, double maxSingleDose, double maxDailyDose,
                                   double minIntervalMinutes, boolean requiresDoctorOverride, String notes) {
            this.name = name;
            this.formType = formType;
            this.defaultRoute = defaultRoute;
            this.defaultFrequency = defaultFrequency;
            this.defaultUnit = defaultUnit;
            this.allowedUnits = allowedUnits;
            this.allowedRoutes = allowedRoutes;
            this.minSingleDose = minSingleDose;
            this.maxSingleDose = maxSingleDose;
            this.maxDailyDose = maxDailyDose;
            this.minIntervalMinutes = minIntervalMinutes;
            this.requiresDoctorOverride = requiresDoctorOverride;
            this.notes = notes;
        }
    }
}
