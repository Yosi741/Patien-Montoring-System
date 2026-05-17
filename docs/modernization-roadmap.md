# Smart Patient Monitoring System Modernization Roadmap

## Current Project Assessment

The current application is a plain Java desktop project using Swing, text-file persistence, and direct static service/storage calls. It already contains useful hospital workflow features, but several parts are tightly coupled:

- `src/gui`: 35 Swing GUI classes, mostly `JFrame` screens.
- `src/database`: text-file storage classes for patients, users, vitals, rooms, messages, notifications, files, mothers, newborns, and devices.
- `src/services`: business logic for vitals, alarms, rooms, devices, certificates, files, patients, and permissions.
- `src/ai`: lightweight rule-based analysis.
- `src/alerts`: alert facade over `AlarmService`.
- `src/users`: session and user role models.
- `data`: runtime text files and uploaded/generated files.

The most important migration risk is that UI classes currently call storage and global state directly, especially `HospitalData.patientManager`, `FileStorage.savePatients(...)`, `Session`, and many static storage classes. A safe migration should introduce adapters and new layers before replacing every screen.

## Target Architecture

Recommended package layout:

```text
src/app/                 Application startup and dependency wiring
src/ui/javafx/            JavaFX application, controllers, FXML, CSS
src/ui/javafx/controllers Screen controllers
src/ui/javafx/views       FXML files
src/ui/javafx/styles      CSS themes
src/services/             Business logic and use cases
src/dao/                  SQLite DAO interfaces/implementations
src/database/             SQLite connection, schema, migrations
src/models/               Domain models and DTOs
src/alerts/               Alert rules, cooldown, sound, notification dispatch
src/ai/                   Trend analysis, risk scoring, recommendations
src/devices/              Device interfaces and simulator adapters
src/security/             Password hashing, session, authorization helpers
src/logs/                 Audit logging abstraction
```

## Phase 1: Safe Foundation

Goal: prepare the project for JavaFX and SQLite without breaking the existing Swing application.

- Keep Swing screens runnable while JavaFX screens are introduced.
- Add JavaFX dependencies carefully because JDK 11 does not include JavaFX.
- Add SQLite JDBC under `lib/`.
- Create a database initializer that creates tables but does not yet delete or replace text files.
- Add DAO interfaces beside existing storage classes.
- Add a migration utility that can import current text files into SQLite.
- Add password hashing utilities, but keep old plaintext login compatible during transition.

## Phase 2: SQLite Data Layer

Introduce relational tables:

```sql
users(id, username, password_hash, role, section, active, created_at)
patients(id, patient_id, first_name, last_name, birth_date, gender, section, room, status, priority, diagnosis)
vital_readings(id, patient_id, type, value, unit, recorded_at, source_type, staff_user, device_id)
alerts(id, patient_id, severity, message, status, created_at, acknowledged_by, acknowledged_at, cooldown_until)
rooms(id, section, room_number, capacity)
medications(id, patient_id, name, dose, route, frequency, active)
medication_events(id, medication_id, patient_id, given_by, given_at, notes)
medical_history(id, patient_id, category, details, created_by, created_at)
ai_notes(id, patient_id, risk_score, note, created_at)
medical_files(id, patient_id, original_name, stored_path, file_type, uploaded_by, uploaded_at, extracted_summary)
shift_handover_notes(id, patient_id, from_user, to_section, note, created_at)
audit_logs(id, username, action, created_at)
devices(id, device_id, name, type, serial, status, patient_id)
notifications(id, username, severity, message, read, created_at)
```

DAO migration order:

1. `UserDao`
2. `PatientDao`
3. `VitalReadingDao`
4. `RoomDao`
5. `AlertDao`
6. `MedicalFileDao`
7. `AiNoteDao`
8. `MedicationDao`
9. `AuditLogDao`

## Phase 3: JavaFX Shell

Create a JavaFX app shell before migrating every screen:

- `Main` should launch JavaFX after dependencies are configured.
- `ModernApp` should own the JavaFX `Stage`.
- `AppNavigator` should switch scenes/views without duplicate windows.
- Use FXML for Scene Builder friendly screens.
- Use CSS theme files for light and dark mode.
- Use JavaFX `Task`, `Service`, and `Timeline` for background work and live updates.

Initial JavaFX screens:

1. Login
2. Dashboard shell
3. Patient list
4. Patient detail / monitoring dashboard
5. Alert center

Keep old Swing screens available only as temporary fallback until matching JavaFX screens exist.

## Phase 4: Hospital Workflow Features

Add practical workflow modules after SQLite is stable:

- Patient priority: `LOW`, `NORMAL`, `HIGH`, `CRITICAL`, `EMERGENCY`.
- Bed/room occupancy dashboard.
- Shift handover notes.
- Medication administration records.
- Timeline view combining vitals, alerts, files, medication events, notes, and audit events.
- Staff activity overview and daily statistics.
- Search/filter by patient ID, name, section, room, status, risk, priority.

## Phase 5: Smart Alerts

Replace the current single critical alarm path with an alert engine:

- `AlertRule` interface.
- Rules for heart rate, blood pressure, oxygen, temperature, sugar, and sudden trend changes.
- Severity levels: `WARNING`, `CRITICAL`, `EMERGENCY`.
- Cooldown per patient/rule to prevent spam.
- Separate alarm sound controller from alert dialogs.
- Stop/acknowledge/reset state stored in SQLite.
- UI notifications should not keep sound running after window close.

## Phase 6: AI And File Analysis

Keep AI rule-based but make it structured:

- `RiskScoringService`
- `TrendAnalysisService`
- `RecommendationService`
- `MedicalFileExtractionService`

File handling roadmap:

- TXT/CSV: parse directly.
- Excel: add Apache POI later if needed.
- PDF: use PDFBox text extraction.
- Images: attach first, OCR later.
- Store extracted facts and generated summaries in SQLite.

## Phase 7: Security

Minimum real-world baseline:

- Password hashing with PBKDF2, BCrypt, or Argon2.
- Backward-compatible migration from plaintext passwords.
- Central `SessionContext`.
- Permission checks in services, not only UI buttons.
- Audit every login, patient update, vitals entry, medication event, alert acknowledgement, file upload, and certificate generation.
- Avoid storing sensitive data in plain text long term; plan encrypted SQLite or OS-level disk encryption for real pilots.

## First Safe Implementation Slice

Recommended next commit:

1. Add JavaFX and SQLite jars under `lib/`.
2. Add `database/DatabaseManager.java`.
3. Add `database/SchemaInitializer.java`.
4. Add `dao/UserDao.java` and `dao/SqliteUserDao.java`.
5. Add `security/PasswordHasher.java`.
6. Add JavaFX app shell with Login + Dashboard placeholder.
7. Keep the current Swing app available until JavaFX has equivalent core workflows.

This avoids a dangerous full rewrite while proving the new stack works.
