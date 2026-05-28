# Smart Patient Monitoring System

A modern Java JavaFX hospital-grade patient monitoring platform for managing patients, recording vital signs, viewing live ICU-style dashboards, uploading medical files, and generating rule-based clinical advice notes.

## Features

- **Modern JavaFX UI** with hospital-style dashboard and responsive controls
- Login system with Admin, Doctor, and Nurse roles
- Patient management with add, edit, delete, search, and risk filtering
- Dual persistence: SQLite database + legacy text-file storage in `data/`
- Vital sign recording for temperature, heart rate, blood pressure, and oxygen level
- ICU-style patient dashboard with animated ECG panel and live vital cards
- ECG standby mode until a simulated or future real ECG monitor is connected
- Normal, Warning, Critical, and No Data risk status analysis
- Reliable alarm state management using ACTIVE, ACKNOWLEDGED, STOPPED, and RESOLVED states
- Critical alert alarm using `resources/sounds/alarm.wav` with manual Stop Alarm control
- Smart device connector architecture with a simulated Bluetooth monitor adapter
- Device registry in SQLite and `data/devices.txt`
- Permanent vital-sign history in SQLite and `data/vitals_history.txt`
- Manual and device vital records store source, staff/device ID, serial number, and timestamp
- Hospital sections, room ranges, and room capacity checks
- Sensitive patient medical history with diagnoses, visits, medications, allergies, family history, and files
- Medical file upload linked to each patient
- Rule-based AI advice from current vitals, trends, history, medications, allergies, and files
- Confirmation step before AI-extracted file information is saved to the patient record
- Death pronouncement report generation for authorized staff
- Newborn birth certificate report generation for authorized staff
- Audit logs for major clinical and admin actions
- User management with roles and assigned hospital sections

## User Roles

- **System Admin / Admin**: full technical access, user management, audit logs
- **Hospital Director / Chief Medical Officer**: broad hospital visibility and sensitive review access
- **Department Head / Doctor**: section-based patient access, vitals, history, AI advice, and death pronouncement where authorized
- **Nurse**: section-based patient access, vitals, monitoring, uploads, AI advice, and newborn registration
- **Technician**: device and vitals support
- **Receptionist**: limited patient-registration and certificate support

## Login Examples

This workspace currently contains these users in `data/users.txt`:

- `YasenSalhab` / `2005` / Admin
- `Q2` / `1234` / Doctor
- `Q1` / `1234` / Nurse
- `Q3` / `1234` / Admin

If `data/users.txt` does not exist, the app creates these default users:

- `admin` / `1234` / Admin
- `doctor` / `1234` / Doctor
- `nurse` / `1234` / Nurse
- `dr_ahmad` / `1234` / Doctor
- `nurse_lina` / `1234` / Nurse

## How To Run

Open the project in IntelliJ IDEA and run `src/Main.java` for the default JavaFX application.

From a terminal inside the project folder:

```bash
mkdir -p out/production/untitledSmartPatientMonitoringSystem
find src -name "*.java" | sort > sources.txt
javac -cp "lib/*" -d out/production/untitledSmartPatientMonitoringSystem @sources.txt
java -cp "out/production/untitledSmartPatientMonitoringSystem:lib/*" Main
```

On Windows PowerShell, use `;` instead of `:` in the runtime classpath:

```powershell
$sources = Get-ChildItem -Path src -Recurse -Filter *.java | Sort-Object FullName | ForEach-Object { $_.FullName }
New-Item -ItemType Directory -Force -Path out/production/untitledSmartPatientMonitoringSystem | Out-Null
javac -cp "lib/*" -d out/production/untitledSmartPatientMonitoringSystem $sources
java -cp "out/production/untitledSmartPatientMonitoringSystem;lib/*" Main
```

## Architecture

The application uses a **JavaFX-based modern UI** built with:
- **UI Layer**: `src/ui/javafx/` with controllers and FXML views (40+ screens)
- **Service Layer**: Business logic, validation, and audit logging
- **DAO Layer**: SQLite and text-file persistence
- **Core Models**: Patient, User, Device, VitalSign, etc.

All legacy Swing code has been removed. The application is JavaFX-exclusive.

On Windows PowerShell:

```powershell
java -cp "out/production/untitledSmartPatientMonitoringSystem;lib/*" JavaFxMain
```

JavaFX and SQLite dependencies are stored in `lib/` because this is still a plain Java project:

```text
javafx-base-17.0.15-win.jar
javafx-controls-17.0.15-win.jar
javafx-fxml-17.0.15-win.jar
javafx-graphics-17.0.15-win.jar
sqlite-jdbc-3.53.1.0.jar
slf4j-api-2.0.17.jar
slf4j-simple-2.0.17.jar
```

The JavaFX FXML/CSS files live under:

```text
src/ui/javafx/views/
src/ui/javafx/styles/
```

SQLite creates its local database at:

```text
data/smart_patient_monitoring.db
```

The database file is runtime data and is ignored by Git.

## SQLite Migration Preview

Phase 2 adds SQLite DAOs beside the existing text-file storage. The current Swing app still reads and writes the text files in `data/`. The JavaFX preview reads patients from SQLite after migration.

## Patient Board Subsections

The JavaFX sidebar is simplified so `Deceased Records` and `Newborn Records` are no longer separate sidebar entry points. Their screens, controllers, services, and drill-down methods remain available internally for certificate, notification, and message navigation.

Open `Patients` to access:

- `All Patients`
- `Active Patients`
- `Deceased Patients`
- `Newborns`
- `Critical / Emergency`
- `High Priority`
- `Recently Updated`

`Deceased Patients` filters the normal SQLite patient table to `DECEASED` status. `Newborns` switches the Patient Board content area to a read-only newborn records table from SQLite `newborn_records`; double-clicking a newborn row opens the existing newborn record view.

The JavaFX preview performs a safe startup migration only when SQLite has no users or no patients yet. You can also run migration manually:

```bash
java -cp "out/production/untitledSmartPatientMonitoringSystem:lib/*" DatabaseMigrationMain
```

On Windows PowerShell:

```powershell
java -cp "out/production/untitledSmartPatientMonitoringSystem;lib/*" DatabaseMigrationMain
```

Migration behavior:

- Imports users from `data/users.txt`.
- Imports patients from `data/patients.txt` through the existing `FileStorage` loader.
- Imports vital history from `data/vitals_history.txt`.
- Imports AI notes from `data/ai_notes.txt`.
- Imports uploaded medical file metadata from `data/medical_files.txt`.
- Derives medical history and medication timeline rows from the extended patient fields in `data/patients.txt` when those fields contain data.
- Imports shift handover notes from `data/shift_handover_notes.txt`, `data/shift_handover.txt`, or `data/handover_notes.txt` if one of those files exists.
- Uses upsert/duplicate checks, so running migration more than once is safe.
- Hashes plaintext user passwords before storing them in SQLite.
- Does not modify or delete any existing text files.
- Skips orphaned legacy AI/file rows whose patient IDs no longer exist in `data/patients.txt`, keeping SQLite foreign-key integrity intact.

During the transition:

- Swing remains the source of truth for editing.
- SQLite is a preview/read model for the JavaFX app.
- JavaFX login first checks SQLite hashed users, then falls back to legacy text-file users if needed.
- The JavaFX Patient List is read-only and shows SQLite patients with status and priority.
- The JavaFX Dashboard is read-only and SQLite-backed, with hospital operations counters, priority summary, alert summary, recent alerts, and latest vitals.
- Dashboard counters include total patients, active patients, critical/emergency patients, active alerts, acknowledged/resolved alerts today, imported medical files, AI notes, and recent vital readings today.
- Dashboard widgets auto-refresh about every 20 seconds and also include a manual `Refresh` button.
- Dashboard recent alert rows open Alert Center with that alert selected and its detail panel visible.
- The JavaFX shell shows the logged-in username, role badge, and section in the top bar.
- `Profile / Settings` opens a read-only Staff Profile screen showing username, role, section, login source, login time, account status, and a permission preview.
- JavaFX session details are stored in `SessionContext` while the legacy static `Session` remains populated for compatibility with existing code.
- JavaFX login/logout events are written to the SQLite `audit_logs` table.
- Phase 21 adds shared JavaFX write workflow helpers without enabling medical write screens yet: `FormValidationHelper`, `DialogHelper`, `NotificationHelper`, `PermissionHelper`, `AuditWriteHelper`, and `AuditWriteService`.
- Future JavaFX writes should follow Controller -> Service -> DAO. Controllers should not contain SQL.
- JavaFX write permissions are staged as ADMIN full management; DOCTOR patient/vitals/medication/appointments/reminders; NURSE vitals/medication/reminders; STAFF read-only unless explicitly allowed.
- Admin users see a `Create Test Audit Event` button on `Profile / Settings`. It writes only a safe SQLite audit row and does not change patient, vitals, medication, device, or user data.
- Phase 22 adds the first controlled JavaFX patient write workflow. Admin and Doctor users can add, edit, and discharge/deactivate patients in SQLite only.
- Patient List now includes `Add Patient`, `Edit Selected`, and `Discharge` for authorized users. Patient Detail includes `Edit Patient` for authorized users.
- JavaFX patient writes are validated, audited, and routed through `PatientWriteService` before reaching `SqlitePatientDao`; JavaFX controllers do not run patient write SQL directly.
- Manual `Sync From Legacy Storage` still reads old text files, but it skips patient rows when the SQLite record has a newer `updated_at` value than `data/patients.txt`.
- Phase 23 adds JavaFX manual vitals entry from Patient Detail. Admin, Doctor, and Nurse users can enter Heart Rate, Blood Pressure, Oxygen, Temperature, and Sugar Level readings into SQLite only.
- JavaFX vitals writes are validated, audited, and routed through `VitalsWriteService` before reaching `SqliteVitalReadingDao`.
- JavaFX warning/critical manual vitals create SQLite alert rows through the existing alert persistence path. They appear in Alert Center, Dashboard, and Clinical Timeline, but they do not start or stop Swing alarm sounds/dialogs.
- Phase 24 adds a JavaFX medication administration write workflow. Admin and Doctor users can add, edit, and discontinue SQLite medications; Admin, Doctor, and Nurse users can record medication administration events.
- Medication writes are validated, audited, and routed through `MedicationWriteService` before reaching `SqliteMedicationDao`.
- Medication Overview now includes `Add Medication`, `Edit Selected`, `Discontinue Selected`, and `Record Given` for authorized users. Patient Detail includes quick actions for `Add Medication` and `Record Given`.
- Phase 25 adds a JavaFX Medical Devices screen for SQLite device registration and patient assignment foundation.
- Admin and Doctor users can register, edit, and deactivate devices. Admin, Doctor, and Nurse users can assign/unassign available devices.
- Device writes are validated, audited, and routed through `DeviceWriteService` before reaching `SqliteDeviceDao`.
- Medical Devices is registration/assignment only. Real Bluetooth, Apple HealthKit, smart watch, and sensor integrations remain future work.
- Phase 26 adds a JavaFX `Scheduling` screen for SQLite-only appointments and medical reminders.
- Admin and Doctor users can create, edit, cancel, and complete appointments. Admin, Doctor, and Nurse users can create, edit, mark done, and cancel reminders.
- Scheduling writes are validated, audited, and routed through `SchedulingService` before reaching `SqliteAppointmentDao` or `SqliteReminderDao`.
- Scheduling stores appointments in the SQLite `appointments` table and reminders in the SQLite `reminders` table. Legacy text files and external calendar apps are not updated.
- Scheduling overview cards show appointments today, upcoming surgeries, overdue reminders, medication reminders today, and cancelled/missed scheduling items.
- Patient Detail includes `Create Appointment`, `Create Reminder`, and `View Schedule`, which opens Scheduling filtered to the selected patient.
- Medication Overview includes `Create Reminder` for an active selected medication, creating a medication reminder linked to that SQLite medication ID.
- Dashboard now includes lightweight scheduling counters for appointments today and pending reminders.
- Phase 27 adds a local JavaFX Reminder Engine and `Nurse Work Queue`.
- The Reminder Engine evaluates SQLite reminders locally, marks overdue `PENDING` reminders as `OVERDUE`, and never changes `DONE` or `CANCELLED` reminders.
- Nurse Work Queue combines overdue/upcoming reminders, critical active alerts, and patients missing recent vitals into a prioritized task board.
- Work Queue actions can mark reminder tasks `DONE` or `MISSED`, open Patient Detail, open Scheduling filtered to the patient, and open Alert Center for alert-related tasks.
- Local reminder notifications are non-blocking JavaFX status messages with cooldown, so the same reminder is not repeatedly shown every refresh.
- Dashboard now also shows overdue reminders, upcoming reminders, and nurse queue task counters with an `Open Work Queue` shortcut.
- Phase 28 adds JavaFX Medical Files upload and basic analysis backed by SQLite.
- Admin, Doctor, and Nurse users can upload `txt`, `csv`, `pdf`, `png`, `jpg`, and `jpeg` files into `data/uploads/{patientId}/`.
- File uploads are validated, copied with a unique safe filename, and saved to the SQLite `medical_files` table through `MedicalFileUploadService` and `SqliteMedicalFileDao`.
- TXT files are summarized from the first readable lines. CSV files summarize headers/sample rows and flag possible lab/vital columns. PDF files use local PDFBox text extraction only. Images store metadata only; OCR is not implemented.
- Medical Files includes filters by patient ID/name, category, date range, and uploaded-by user, plus a read-only detail panel with stored path, summary, and notes.
- Patient Detail includes `Upload Medical File` and `View Medical Files`; the file view opens with a patient filter chip.
- Uploaded files appear in Clinical Timeline as FILE events through the existing SQLite `medical_files` timeline feed.
- If extracted summary exists, Medical Files can generate a simple rule-based AI summary note into `ai_notes`. No external API is called.
- Phase 29 adds safe local preview/open controls for JavaFX Medical Files.
- Medical file previews validate that the resolved stored path is inside `data/uploads/` before reading or opening any file.
- TXT, CSV, and PDF previews show local text only; PDFs use PDFBox text extraction, not full PDF rendering. Images preview inside JavaFX when they are local and safe.
- Medical Files now includes `Copy Summary`, `Open File`, and a preview panel. `Open File` uses `Desktop.open` only after strict path validation and only when the desktop action is supported.
- Clinical Timeline FILE events include `Open File Details`, which opens Medical Files filtered to the patient and selected file when available.
- Admin users see an `Audit Logs` navigation item in the JavaFX sidebar.
- The JavaFX Audit Log Viewer is read-only, SQLite-backed, and filters by username/action search, date range, and action type.
- Admin users also see a read-only `Staff / Users` directory backed by the SQLite `users` table.
- Staff/User Directory filters include username search, role group, section/department, and active/inactive status. Rows are sorted by role, section, then username.
- Selecting a staff user shows a read-only detail panel with role badge, account status, auth source, created time, and permission preview.
- Admin, Doctor, and Nurse users can open a read-only `Staff Activity` screen for shift awareness.
- Staff Activity combines SQLite `audit_logs`, `alerts`, `patients`, `users`, and `shift_handover_notes` into counters, an activity table, active-alert-by-section rows, and latest handover notes.
- Staff Activity filters include username/action/patient search, role, action type, and date range. Admins see all activity; Doctors/Nurses see a limited section/user-scoped view.
- Admin, Doctor, and Nurse users can open a read-only `Medications` overview backed by SQLite `medications` and `medication_events`.
- Medication Overview shows active medication counts, medication events today, patients with active medications, latest administration event time, a missed/overdue placeholder, medication rows, and administration event rows.
- Patient Detail includes `View Medications`, which opens Medication Overview filtered to that patient with a visible patient filter chip.
- Admin, Doctor, and Nurse users can open the `Rooms / Beds` overview backed by SQLite `rooms` and `patients`; Admin can create/edit/deactivate rooms, and Admin/Doctor/Nurse can assign or move patients.
- Room/Bed Occupancy shows total rooms, occupied rooms/beds, available capacity, active patients by section, critical/emergency patients by section, and assigned patients per room.
- If the SQLite `rooms` table is empty, Room/Bed Occupancy automatically falls back to patient section/room fields so the presentation screen still works with legacy migrated patient data.
- Admin and Doctor users can mark a patient deceased from JavaFX Patient Detail, which writes a SQLite death record and updates the SQLite patient status to `DECEASED`.
- Admin, Doctor, and Nurse users can open `Deceased Records` to view death records, certificate status, patient details, and generated certificate paths.
- Death certificate output is currently a safe local HTML file under `data/generated/death-certificates/`; legacy text files are not updated by this JavaFX workflow.
- Admin, Doctor, and Nurse users can create and update SQLite newborn records from `Newborn Records`.
- Admin and Doctor users can generate local HTML birth certificates under `data/generated/birth-certificates/`.
- Patient Detail includes `View Newborns`, which opens Newborn Records filtered by the selected mother's patient ID.
- Admin, Doctor, and Nurse users can open a read-only `AI Recommendations` board backed by SQLite patients and `ai_notes`.
- Patient Detail includes `Generate Recommendation`, which analyzes SQLite vitals, active/recent alerts, and recent clinical activity, then saves a rule-based recommendation into `ai_notes`.
- Generated AI recommendations appear in Clinical Timeline as AI Notes because they use the existing SQLite `ai_notes` table.
- The JavaFX Patient List is now a read-only hospital patient board with search, section, room, status, priority, quick filters, and active filter chips.
- Patient board quick filters include All Patients, Active Patients, Critical/Emergency, High Priority, and Recently Updated.
- Patient list sorting prioritizes risk first: Emergency, Critical, High/Warning, Normal, Deceased, then recently updated/name order.
- Double-click a patient, or select one and press `View Details`, to open the read-only Patient Detail preview.
- Patient Detail shows demographic/location/status fields, diagnosis, priority badge, and a SQLite vitals timeline.
- Patient Detail includes a patient alert summary card with active alert count, latest alert severity/time, and a `View Patient Alerts` drill-down.
- The vitals timeline can be filtered by vital type and is sorted newest first.
- Patient Detail also includes a read-only JavaFX vital trend chart backed by SQLite vital readings.
- Trend chart filters include Heart Rate, Blood Pressure, Oxygen, Temperature, Sugar Level, plus Last 24 hours, Last 7 days, Last 30 days, and All.
- Blood Pressure trends combine systolic and diastolic readings into separate chart lines when both are present.
- Trend summaries show latest reading, source/staff/device metadata, min/max/average, and basic Normal/Warning/Critical counts.
- Patient Detail now links to a read-only JavaFX Clinical Timeline preview for the selected patient.
- The Clinical Timeline combines SQLite rows from `vital_readings`, `alerts`, `ai_notes`, `medical_files`, `medical_history`, `medication_events`, and `shift_handover_notes` into one newest-first clinical history view.
- Clinical Timeline filters include All Events, Vitals, Alerts, AI Notes, Files, Medical History, Medications, Shift Handover, plus free-text search across title, description, severity, and source.
- Selecting a Clinical Timeline row opens a read-only detail panel with source-specific fields, full description, source table/source ID, and a `Copy Summary` action.
- Selecting a Clinical Timeline alert event enables `Open Alert`, which opens Alert Center with the matching alert selected.
- Timeline detail panels support vitals, alerts, AI notes, uploaded file metadata, medical history, medication events, and shift handover notes.
- Uploaded file timeline details show metadata and stored path only; JavaFX does not preview or open files yet.
- Use `Sync From Legacy Storage` in the JavaFX Patient List when you edited data in Swing and want to refresh SQLite.
- The JavaFX Alert Center is a SQLite preview with filters, search, detail panel, severity badges, and SQLite-only acknowledgement.
- Demo alerts are inserted only when the SQLite alerts table is empty, which helps presentation/testing without duplicating rows every startup.
- New Swing-triggered critical alarms are also written to SQLite in parallel, so JavaFX Alert Center can show future real alerts.
- SQLite alert persistence has basic duplicate protection for the same active patient/severity/message within a short cooldown window.
- Swing alert lifecycle updates are mirrored into SQLite: acknowledge/stop marks the latest active SQLite alert as `ACKNOWLEDGED`, and resolve marks it as `RESOLVED`.
- JavaFX Alert Center auto-refresh can show Swing lifecycle status changes within about 15 seconds.

Current JavaFX patient management limitations:

- JavaFX add/edit/discharge patient workflow writes to SQLite only. It does not update `data/patients.txt` or the current Swing text-file source of truth.
- JavaFX manual vitals entry writes to SQLite only. It does not update `data/vitals_history.txt` or the current Swing text-file source of truth.
- JavaFX medication add/edit/discontinue and administration events write to SQLite only. They do not update legacy patient medication text fields in `data/patients.txt`.
- JavaFX device registration and assignment write to SQLite only. They do not update `data/devices.txt` or the current Swing device registry.
- JavaFX appointment and reminder scheduling writes to SQLite only. It does not update legacy text files, Swing screens, or external calendar apps.
- Medication reminders can reference active SQLite medications. The local JavaFX Reminder Engine can detect upcoming/overdue reminders, but it does not control Swing alarms or external calendars.
- Nurse Work Queue actions write reminder status changes to SQLite only and do not update legacy text files.
- JavaFX medical file upload writes metadata to SQLite and copies files under `data/uploads/` only. It does not update `data/medical_files.txt` or the legacy Swing file index.
- PDF handling is text extraction only through PDFBox. Scanned PDFs and image OCR are not supported yet.
- JavaFX file preview/open controls only allow paths that resolve under `data/uploads/`. Legacy files outside that folder are shown as metadata but cannot be previewed/opened through the new safe control.
- Dashboard metrics are presentation/read-only and do not change patient, alert, file, AI, or vitals data.
- Patient vital trend charts are read-only and use simple rule-based thresholds for presentation support only.
- Clinical Timeline is read-only and depends on what has already been migrated or written into SQLite.
- AI notes, uploaded file rows, medical history, medications, and handover notes appear in the Clinical Timeline when matching legacy data exists.
- Timeline event detail panels are read-only. They do not acknowledge alerts, open files, edit notes, or update medications.
- JavaFX alert acknowledgement updates SQLite only.
- Swing alarm dialogs/sounds are unchanged and are not controlled from JavaFX yet.
- JavaFX still does not stop Swing sounds or close Swing dialogs.
- JavaFX permission lists are preview-only. They document intended behavior but full enforcement is still future work.
- JavaFX write helpers are now available, with SQLite-only patient add/edit/discharge, manual vitals entry, medication administration, device registration/assignment, appointments, and reminders. User management write screens are still future work.
- Audit Log Viewer visibility is minimal and safe: Admin users see it in navigation, and non-admin users who reach the screen see an access denied message.
- Staff/User Directory visibility follows the same safe rule: Admin users see it in navigation, and non-admin direct access shows access denied.
- Staff Activity visibility is available to Admin, Doctor, and Nurse roles. Non-authorized users who reach the screen see access denied.
- Medication Overview visibility is available to Admin, Doctor, and Nurse roles. It is read-only and does not create, edit, discontinue, or administer medications.
- Room/Bed Occupancy visibility is available to Admin, Doctor, and Nurse roles. Admin users can create, edit, and deactivate SQLite room records. Admin, Doctor, and Nurse users can assign, move, or remove active patients from SQLite rooms.
- AI Recommendations are rule-based decision support only. They are not medical diagnosis, do not replace clinicians, and do not change the existing alert engine.
- JavaFX settings is still a placeholder.
- Swing remains the fallback production UI for all write operations.

To test Alert Center:

1. Run `JavaFxMain`.
2. Login with an existing user.
3. Open `Alerts` from the sidebar.
4. Try severity/status filters and patient search.
5. Select an alert to view the detail panel.
6. Press `Acknowledge` to update only the SQLite alert row.
7. Trigger a critical alert in the Swing app, then press `Refresh` in JavaFX Alert Center. The new alert should appear if it is not within the duplicate cooldown.
8. Acknowledge/stop/resolve the alert from Swing and refresh JavaFX. The SQLite status should update.

Important: JavaFX acknowledgement does not stop Swing alarm sounds yet. Use the Swing alert controls for the current production alarm behavior.

Alert drill-down paths:

1. From Dashboard, click a recent alert row to open Alert Center with that alert selected.
2. From Patient Detail, click `View Patient Alerts` to open Alert Center filtered by that patient ID.
3. From Clinical Timeline, select an alert event and click `Open Alert`.
4. In patient-specific Alert Center mode, use `Clear Patient Filter` to return to the full alert list.
5. `Acknowledge` remains SQLite-only and does not stop Swing alarm sounds or dialogs.

To test Clinical Timeline:

1. Run `JavaFxMain`.
2. Login with an existing user.
3. Open `Patients` from the sidebar.
4. Select or double-click a patient.
5. Press `Open Clinical Timeline`.
6. Try event type filters and the search box.
7. Patients with migrated vitals, SQLite alerts, AI notes, files, history, medications, or handover notes should show timeline rows when matching source data exists.
8. Select a timeline row to view the detail panel.
9. Press `Copy Summary` to copy a read-only event summary to the clipboard.

To test Patient Vital Trend Charts:

1. Run `JavaFxMain`.
2. Login and open `Patients`.
3. Open a patient with migrated vital readings.
4. In Patient Detail, use the `Vital Trend Chart` card.
5. Test Heart Rate, Blood Pressure, Oxygen, Temperature, and Sugar Level filters.
6. Test Last 24 hours, Last 7 days, Last 30 days, and All.
7. Confirm latest reading, min/max/average, and Normal/Warning/Critical counts update.

Trend thresholds are basic rule-based indicators for education and demo use. They are not medical diagnosis or treatment guidance.

To test Dashboard metrics:

1. Run `DatabaseMigrationMain` if SQLite needs to be refreshed from legacy text storage.
2. Run `JavaFxMain`.
3. Login and open `Dashboard`.
4. Confirm counters match SQLite data for patients, alerts, files, AI notes, and vitals.
5. Open `Alert Center`, acknowledge or trigger alerts through the existing flow, then return to Dashboard or press `Refresh`.
6. Recent alert rows on the dashboard can be clicked to open the Alert Center.

To test Patient Search and Filters:

1. Run `JavaFxMain`.
2. Login and open `Patients`.
3. Search by patient ID or name.
4. Filter by section, room, status, and priority.
5. Try quick filters: All Patients, Active Patients, Critical/Emergency, High Priority, Recently Updated.
6. Confirm filter chips update and `Clear Filters` resets the board.
7. Double-click or select a filtered patient and press `View Details`.

To test JavaFX Patient Add/Edit/Discharge:

1. Run `JavaFxMain` and login as an Admin or Doctor user.
2. Open `Patients`.
3. Press `Add Patient`, enter patient ID, first name, last name, birth date, gender, section, room, status, priority, and diagnosis, then save.
4. Confirm the new patient appears in the Patient List and Dashboard counters refresh from SQLite.
5. Select the test patient and press `Edit Selected`, or open Patient Detail and press `Edit Patient`.
6. Change a safe field such as diagnosis, room, priority, or status, then save and confirm Patient List/Patient Detail update.
7. Select the test patient and press `Discharge`; the SQLite status becomes `DISCHARGED`.
8. Open `Audit Logs` and confirm `CREATE_PATIENT`, `UPDATE_PATIENT`, and `DISCHARGE_PATIENT` rows.
9. Login as a Nurse or Staff-style user and confirm add/edit/discharge buttons are hidden.

Important: Phase 22 patient writes are SQLite-only. They do not update `data/patients.txt`, so the current Swing app remains unchanged. If you later use `Sync From Legacy Storage`, newer SQLite patient edits are skipped instead of being overwritten by older legacy text-file rows.

To test JavaFX Manual Vitals Entry:

1. Run `JavaFxMain` and login as an Admin, Doctor, or Nurse user.
2. Open `Patients`, select a patient, and open Patient Detail.
3. Press `Enter Vitals`.
4. Enter a normal reading, such as Heart Rate `80 bpm`, and save.
5. Confirm the Patient Detail vitals table and trend chart refresh.
6. Open Clinical Timeline and confirm the new vital reading appears.
7. Enter an abnormal reading, such as Oxygen `88 %`, and save.
8. Confirm a SQLite alert appears in Alert Center and Dashboard, and that the alert also appears in Clinical Timeline.
9. Open `Audit Logs` and confirm an `ENTER_VITALS` row with username, patient ID, vital type, value, and status.
10. Login as a Staff-style user and confirm `Enter Vitals` is hidden.

Important: JavaFX warning/critical vitals persist SQLite alerts only. They do not start Swing alarm sounds, stop Swing sounds, or control Swing alarm dialogs.

To test JavaFX Medication Administration Writes:

1. Run `JavaFxMain` and login as an Admin or Doctor user.
2. Open `Patients`, select a patient, open Patient Detail, and press `Add Medication`.
3. Enter medication name, dose, route, and frequency, then save.
4. Open `View Medications` and confirm the medication appears in Medication Overview.
5. Select the medication and press `Edit Selected`; change dose, route, frequency, or active status, then save.
6. Login as an Admin, Doctor, or Nurse and press `Record Given` for an active medication.
7. Confirm a medication administration event appears in Medication Overview and Clinical Timeline.
8. Login as an Admin or Doctor and press `Discontinue Selected`; the medication becomes inactive.
9. Confirm `Record Given` is blocked for inactive/discontinued medications.
10. Open `Audit Logs` and confirm `ADD_MEDICATION`, `UPDATE_MEDICATION`, `GIVE_MEDICATION`, and `DISCONTINUE_MEDICATION` rows.
11. Login as a Staff-style user and confirm medication write buttons are hidden or access is denied.

Important: Phase 24 medication writes are SQLite-only. They do not update legacy text files or Swing medication/history fields.

To test JavaFX Medical Device Registration:

1. Run `JavaFxMain` and login as an Admin or Doctor user.
2. Open `Medical Devices` from the sidebar.
3. Press `Register Device`, enter device ID, name, type, serial number, status, and optional notes, then save.
4. Select the device and press `Edit Selected`; update name, status, or notes.
5. Select an AVAILABLE device and press `Assign`; enter an existing patient ID.
6. Open Patient Detail for that patient and use `View Devices` to confirm the assigned device appears.
7. Press `Unassign` to return the device to AVAILABLE.
8. Press `Deactivate` to mark the device INACTIVE and clear assignment.
9. Open `Audit Logs` and confirm `REGISTER_DEVICE`, `UPDATE_DEVICE`, `ASSIGN_DEVICE`, `UNASSIGN_DEVICE`, and `DEACTIVATE_DEVICE` rows.
10. Login as a Nurse and confirm assignment/unassignment is available, but registration/edit/deactivation is hidden.
11. Login as a Staff-style user and confirm device write actions are hidden or denied.

Important: Phase 25 Medical Devices is registration and assignment foundation only. It does not connect to Bluetooth devices, Apple HealthKit, smart watches, live sensors, or the legacy Swing `data/devices.txt` registry.

To test JavaFX Appointments & Medical Reminders:

1. Run `JavaFxMain` and login as an Admin or Doctor user.
2. Open `Scheduling` from the sidebar.
3. Press `New Appointment`, enter an existing patient ID, title, type, start/end time, location, assigned staff, and notes, then save.
4. Select the appointment and press `Edit Selected`; update a field and save.
5. Press `Complete` or `Cancel` and confirm the appointment status changes in SQLite.
6. Press `New Reminder`, enter an existing patient ID, reminder type, title, due time, repeat rule, assigned staff, and notes, then save.
7. Login as a Nurse and confirm reminders can be created/updated/marked done while appointment write actions are hidden or denied.
8. Open Patient Detail and press `View Schedule` to confirm Scheduling opens with a patient filter chip.
9. From Medication Overview, select an active medication and press `Create Reminder` to create a medication reminder linked to that SQLite medication ID.
10. Open `Audit Logs` and confirm `CREATE_APPOINTMENT`, `UPDATE_APPOINTMENT`, `COMPLETE_APPOINTMENT` or `CANCEL_APPOINTMENT`, `CREATE_REMINDER`, `UPDATE_REMINDER`, `COMPLETE_REMINDER`, or `CANCEL_REMINDER` rows.
11. Login as a Staff-style user and confirm Scheduling is hidden or access is denied.

Important: Phase 26 scheduling writes are SQLite-only. They do not update Swing screens, legacy text files, or external calendar apps.

To test JavaFX Reminder Engine and Nurse Work Queue:

1. Run `JavaFxMain` and login as an Admin, Doctor, or Nurse user.
2. Open `Scheduling` and create a reminder due within the next two hours. It should appear in `Nurse Work Queue` as `UPCOMING`.
3. Create or edit a reminder with a due time before the current time, then open `Nurse Work Queue` or refresh Dashboard. The local engine should mark it `OVERDUE`.
4. Select an overdue reminder and press `Mark Reminder Done`; confirm the task disappears from the active queue and the reminder status becomes `DONE`.
5. Create another overdue reminder and press `Mark Reminder Missed`; confirm the reminder status becomes `MISSED`.
6. Confirm Dashboard counters update for overdue reminders, upcoming reminders, and nurse queue tasks.
7. Select queue tasks and test `Open Patient`, `Open Schedule`, and `Open Alert Center`.
8. Open `Audit Logs` and confirm `OPEN_WORK_QUEUE`, `MARK_REMINDER_DONE`, `MARK_REMINDER_MISSED`, and `REMINDER_OVERDUE_DETECTED` rows when those actions occur.
9. Login as a Staff-style user and confirm Work Queue is hidden or access is denied.

Important: Phase 27 reminder notifications are local JavaFX status messages with cooldown. They do not integrate with external calendars, phones, email, or Swing alarm sounds.

To test Staff Profile and Session Context:

1. Run `JavaFxMain`.
2. Login with an existing user.
3. Confirm the JavaFX shell top bar shows username, role badge, and section.
4. Open `Profile / Settings`.
5. Confirm username, role, section, login source, login time, account status, and permission preview.
6. As an Admin user, press `Create Test Audit Event`.
7. Open `Audit Logs` and confirm a `CREATE_TEST_AUDIT_EVENT` row appears.
8. Login as a non-admin user and confirm the test write button is hidden.
9. Press `Logout` and confirm the app returns to Login.
10. JavaFX login/logout actions are recorded in SQLite `audit_logs`.

To test Audit Log Viewer:

1. Run `JavaFxMain` and login as an Admin user.
2. Open `Audit Logs` from the sidebar.
3. Test search, date range, and action type filters.
4. Logout and login again, then confirm JavaFX login/logout rows appear.
5. Acknowledge a SQLite alert in Alert Center and confirm an alert audit row appears.
6. Login as a non-admin user and confirm Audit Logs is hidden; direct access shows access denied.

Currently audited JavaFX actions include login, logout, SQLite alert acknowledgement, sync from legacy storage, opening patient detail, creating/updating/discharging SQLite patients, marking patients deceased, updating death records, generating/opening death certificates, creating/updating newborn records, generating/opening birth certificates, certificate event notifications, certificate notice messages, certificate source-record drill-down, certificate opening from notifications/messages, opening Certificate Registry, generating/opening/sending/copying certificates from Certificate Registry, submitting/approving/rejecting/resetting certificate reviews, sending certificate review notes, entering SQLite manual vitals, adding/updating/discontinuing SQLite medications, recording SQLite medication administration events, registering/updating/deactivating/assigning/unassigning SQLite devices, creating/updating/completing/cancelling SQLite appointments and reminders, opening Nurse Work Queue, marking reminders done/missed, detecting overdue reminders, uploading/viewing/opening SQLite medical files, copying file summaries, generating file AI summary notes, opening Staff/User Directory, opening Staff Activity, opening Medication Overview, opening Room/Bed Occupancy, creating/updating/deactivating SQLite rooms, assigning/moving/removing patients from SQLite rooms, opening patient detail from Room/Bed Occupancy, opening AI Recommendations, and generating AI recommendations. Legacy Swing audit logs remain in `data/audit_logs.txt` and are not fully migrated yet.

To test Staff/User Directory:

1. Run `JavaFxMain` and login as an Admin user.
2. Open `Staff / Users` from the sidebar.
3. Search by username and filter by role, section/department, and active status.
4. Select a user and confirm the read-only detail panel, role badge, auth source, and permission preview.
5. Press `Add User`, enter username, role, section/department, active status, and password, then save.
6. Select the test user and press `Edit Selected User`; update role, section, or active status.
7. Press `Reset Password` and set a new password. Password hashes are never displayed.
8. Press `Deactivate User` to mark the SQLite account inactive.
9. Open `Audit Logs` and confirm directory open/detail-view rows plus `CREATE_USER`, `UPDATE_USER`, `RESET_USER_PASSWORD`, and `DEACTIVATE_USER` rows.
10. Login as a non-admin user and confirm `Staff / Users` is hidden; direct screen access shows access denied.

Important: JavaFX admin user management writes to SQLite only. It does not update legacy `data/users.txt`, and legacy fallback login remains unchanged. New and reset passwords are stored with PBKDF2 hashes through `PasswordHasher`; raw passwords are not logged or displayed.

To test JavaFX Messaging and Notification Center:

1. Run `Main` and login as an Admin, Doctor, or Nurse.
2. Open `Messages` from the sidebar.
3. Compose a message to a specific user, role, or section/department.
4. Add an optional patient ID, subject, body, and priority, then send.
5. Confirm the message appears in Sent for the sender and Inbox for matching recipients.
6. Select a message and test `Mark Read`, `Archive`, and `Open Patient` when a patient ID is linked.
7. Open `Notifications` from the sidebar or top-bar Notifications button.
8. Test severity/status/date/patient filters.
9. Select a notification and test `Mark Read`, `Dismiss`, and `Open Linked Item`.
10. Confirm the top-bar unread count updates after read/dismiss actions.
11. Trigger a JavaFX warning/critical vital, overdue reminder, or high AI risk score and confirm a SQLite notification is created.
12. Open `Audit Logs` and confirm `SEND_MESSAGE`, `READ_MESSAGE`, `ARCHIVE_MESSAGE`, `MARK_NOTIFICATION_READ`, and `DISMISS_NOTIFICATION` rows when those actions occur.

Important: JavaFX messaging and notifications are SQLite-only. They do not update legacy `data/messages.txt` or `data/notifications.txt`, and they do not send external email, SMS, push notifications, or hospital paging messages.

To test Staff Activity / Shift Overview:

1. Run `JavaFxMain` and login as an Admin user.
2. Open `Staff Activity` from the sidebar.
3. Confirm the cards for logins, alert acknowledgements, patient detail views, sync operations, recent actions, and active alerts by section.
4. Test username/action/patient search, role filter, action type filter, and date range filter.
5. Confirm latest shift handover notes appear when SQLite `shift_handover_notes` has rows; otherwise the screen shows an empty state.
6. Login as a Doctor or Nurse and confirm the limited activity scope message.
7. Login as a non-authorized staff role and confirm `Staff Activity` is hidden; direct screen access shows access denied.

To test Medication Overview:

1. Run `JavaFxMain` and login as an Admin, Doctor, or Nurse.
2. Open `Medications` from the sidebar.
3. Confirm the cards for active medications, medication events today, missed/overdue placeholder, patients with active medications, and latest event time.
4. Test search by patient ID/name/medication, active status, route, and event date range.
5. Open `Patients`, select a patient, and press `View Medications`.
6. Confirm Medication Overview opens with `Patient ID = ...` shown as a filter chip, then use `Clear Patient Filter`.
7. Open Patient Detail > Clinical Timeline for a patient with medication events and select a medication event to verify source-specific details.
8. Login as a non-authorized staff role and confirm `Medications` is hidden; direct screen access shows access denied.

To test Room/Bed Occupancy and room assignment writes:

1. Run `Main` and login as an Admin.
2. Open `Rooms / Beds` from the sidebar.
3. Confirm total rooms, occupied rooms/beds, available capacity, active patients by section, and critical/emergency by section.
4. Filter by section, room search, patient status, and priority.
5. Press `Add Room`, enter section, room number, capacity, status, and optional notes, then save.
6. Select the new room and press `Edit Room`; confirm duplicate section + room number is blocked and capacity cannot be reduced below current occupancy.
7. Press `Assign Patient` or `Move Patient`, choose an active patient and destination room, then confirm Patient Detail and the Room/Bed board show the updated location.
8. Try assigning beyond capacity or assigning to a maintenance/inactive room; the write service should block it.
9. Select an occupied row and press `Remove Patient From Room`; the patient section/room is cleared in SQLite only.
10. Deactivate an empty room as Admin and confirm occupied rooms cannot be deactivated.
11. If SQLite `rooms` has no rows, confirm the screen shows fallback mode and builds room rows from patient section/room assignments.
12. Login as Doctor or Nurse and confirm assignment/move/remove controls are available while Add/Edit/Deactivate room controls are hidden.
13. Login as a non-authorized staff role and confirm `Rooms / Beds` is hidden; direct screen access shows access denied.
14. Run `LegacySwingMain` and confirm the old Swing app still opens; JavaFX room writes do not update legacy `data/rooms.txt`.

To test Deceased Records and death certificates:

1. Run `Main` and login as an Admin or Doctor.
2. Open `Patients`, choose a safe test patient, and open Patient Detail.
3. Press `Mark Deceased`, enter death time, pronounced-by, cause of death, and notes, then save.
4. Confirm Patient Detail shows status `DECEASED`; Active patient filters exclude the patient, while the Patient List `DECEASED` status filter can show them.
5. Open `Deceased Records` from the sidebar and confirm the patient appears.
6. Select the record and press `Generate Certificate`; confirm an HTML certificate is created under `data/generated/death-certificates/`.
7. Confirm a SQLite notification is created for Admin/Doctor users.
8. Press `Send Certificate Notice` and confirm the message appears in the recipient role inbox.
9. Press `Copy Summary` and confirm `COPY_CERTIFICATE_SUMMARY` is audited.
10. Press `Open Certificate` to open the generated local certificate if desktop file opening is supported.
11. Confirm the Deceased Records report cards and Dashboard pending death certificate/deaths-this-month counters update.
12. Open the generated notification in `Notifications`; confirm `Open Related Record` jumps to the Deceased Records detail and `Open Certificate` opens the HTML certificate safely.
13. Open the notice message in `Messaging`; confirm message detail shows certificate metadata, `Open Related Record`, and `Open Certificate`.
14. Open `Audit Logs` and confirm `MARK_PATIENT_DECEASED`, `GENERATE_DEATH_CERTIFICATE`, `CERTIFICATE_NOTIFICATION_CREATED`, `SEND_DEATH_CERTIFICATE_NOTICE`, `OPEN_CERTIFICATE_SOURCE_RECORD`, `OPEN_MESSAGE_SOURCE_RECORD`, `OPEN_CERTIFICATE_FROM_NOTIFICATION`, `OPEN_CERTIFICATE_FROM_MESSAGE`, and `OPEN_DEATH_CERTIFICATE` rows where those actions were used.
15. Login as Nurse and confirm Deceased Records is view-only.
16. Login as Staff and confirm Deceased Records is hidden or shows access denied.
17. Run `LegacySwingMain` and confirm the old Swing app still opens; JavaFX deceased records do not update legacy text files.

To test Newborn Records and birth certificates:

1. Run `Main` and login as Admin, Doctor, or Nurse.
2. Open `Newborn Records` from the sidebar.
3. Press `Add Newborn`, enter newborn ID, baby name, gender, birth time, birth weight, optional birth length, mother/father details, delivery type, room, section, doctor/midwife, and notes.
4. Save and confirm the newborn appears in the table and detail panel.
5. Select the newborn and press `Edit Selected`; update a safe field and save.
6. Login as Admin or Doctor and press `Generate Certificate`; confirm an HTML certificate is created under `data/generated/birth-certificates/`.
7. Confirm a SQLite notification is created for Admin/Doctor/Nurse users.
8. Press `Send Birth Notice` and confirm the message appears in the recipient role inbox.
9. Press `Copy Summary` and confirm `COPY_CERTIFICATE_SUMMARY` is audited.
10. Press `Open Certificate` to open the generated local certificate if desktop file opening is supported.
11. Open a mother patient in Patient Detail and press `View Newborns`; confirm the Newborn Records screen is filtered by that mother patient ID.
12. Confirm Dashboard shows newborn, births-today, and pending birth certificate counters.
13. Open the generated notification in `Notifications`; confirm `Open Related Record` jumps to the Newborn Records detail and `Open Certificate` opens the HTML certificate safely.
14. Open the notice message in `Messaging`; confirm message detail shows certificate metadata, `Open Related Record`, and `Open Certificate`.
15. Open `Audit Logs` and confirm `CREATE_NEWBORN_RECORD`, `UPDATE_NEWBORN_RECORD`, `GENERATE_BIRTH_CERTIFICATE`, `CERTIFICATE_NOTIFICATION_CREATED`, `SEND_BIRTH_CERTIFICATE_NOTICE`, `OPEN_CERTIFICATE_SOURCE_RECORD`, `OPEN_MESSAGE_SOURCE_RECORD`, `OPEN_CERTIFICATE_FROM_NOTIFICATION`, `OPEN_CERTIFICATE_FROM_MESSAGE`, and `OPEN_BIRTH_CERTIFICATE` rows where those actions were used.
16. Login as Staff and confirm Newborn Records is hidden or access denied.
17. Run `LegacySwingMain` and confirm Swing still opens; JavaFX newborn writes do not update legacy newborn text files.

To test Certificate Registry:

1. Run `Main` and login as Admin or Doctor.
2. Open `Certificates` from the sidebar.
3. Filter by `BIRTH`, `DEATH`, `GENERATED`, `PENDING`, date range, section, and ID/name search.
4. Select a pending certificate and press `Generate Certificate`; confirm the matching HTML certificate is created under the safe generated folder.
5. Select a generated certificate and press `Open Certificate`; confirm safe local opening or a clear desktop unsupported message.
6. Press `Open Related Record`; confirm birth rows open Newborn Records detail and death rows open Deceased Records detail.
7. Press `Copy Summary` and confirm `COPY_CERTIFICATE_REGISTRY_SUMMARY` appears in Audit Logs.
8. Press `Send Notice` for a generated row and confirm the internal message appears in Messaging.
9. Press `Submit for Review`; confirm review status becomes `PENDING_REVIEW` and notifications are created.
10. Login as Admin and approve one generated pending-review certificate; confirm review status becomes `APPROVED`.
11. Submit another certificate and reject it with a reason; confirm review status becomes `REJECTED` and the reason appears in the detail panel.
12. Confirm rejected certificates cannot be opened from the registry or sent as final notices.
13. Press `Reset Draft` for a review row and confirm status returns to `DRAFT`.
14. Optionally press `Send Review Note` and confirm an internal message is created without attachments.
15. Open `Audit Logs` and confirm `SUBMIT_CERTIFICATE_REVIEW`, `APPROVE_CERTIFICATE`, `REJECT_CERTIFICATE`, `RESET_CERTIFICATE_DRAFT`, and optional `SEND_CERTIFICATE_REVIEW_NOTE` rows.
16. Login as Nurse and confirm registry visibility follows existing newborn/deceased record permissions, while approval/generation actions are disabled.
17. Login as Staff and confirm the Certificates screen is hidden or access denied.
18. Run `LegacySwingMain` and confirm Swing still opens.

To test AI Recommendations:

1. Run `JavaFxMain` and login as an Admin, Doctor, or Nurse.
2. Open `AI Recommendations` from the sidebar.
3. Filter by section and risk level.
4. Open `Patients`, select a patient, and press `Generate Recommendation`.
5. Confirm the Patient Detail AI card shows risk score, recommendation text, and generated time.
6. Open Clinical Timeline for that patient and confirm the generated recommendation appears as an AI Note.
7. Open `Audit Logs` and confirm an AI recommendation generation row appears.

AI risk scoring is rule-based and currently considers missing recent vitals, rising heart rate, low/falling oxygen, fever trend, high/rising blood pressure, repeated critical/emergency alerts, active critical/emergency alerts, and recent clinical timeline volume. Scores are presentation/decision-support values from 0-100 and are not medical diagnosis.

To test JavaFX Medical Files:

1. Run `JavaFxMain` and login as an Admin, Doctor, or Nurse.
2. Open `Patients`, select a patient, and press `Upload Medical File`.
3. Upload a `.txt` file and confirm it is copied under `data/uploads/{patientId}/`, appears in `Medical Files`, and has a first-lines summary.
4. Upload a `.csv` file and confirm headers/sample rows appear in the extracted summary.
5. Upload a `.pdf` file and confirm PDFBox extracts text when the PDF contains selectable text.
6. Upload a `.png`, `.jpg`, or `.jpeg` and confirm metadata is stored with a note that OCR is not implemented.
7. Open `Medical Files` from the sidebar and test patient/name search, category, date range, and uploaded-by filters.
8. Select a file and confirm the detail panel shows original name, stored path, category, uploaded by/time, file size, notes, and extracted summary.
9. Press `Generate AI Summary Note` for a file with extracted summary, then open Clinical Timeline and confirm the AI Note appears.
10. Open Patient Detail > `View Medical Files` and confirm the patient filter chip is shown.
11. Open Patient Detail > Clinical Timeline and confirm uploaded files appear as FILE events.
12. Open `Audit Logs` and confirm `UPLOAD_MEDICAL_FILE`, `VIEW_MEDICAL_FILE`, and optionally `GENERATE_FILE_AI_SUMMARY` rows.
13. Login as a Staff-style user and confirm upload/file screens are hidden or access is denied.

Important: JavaFX medical file upload is SQLite-only and writes copied files to `data/uploads/`. It does not update Swing's legacy `data/medical_files.txt` index.

To test JavaFX Safe Medical File Preview:

1. Run `JavaFxMain` and login as an Admin, Doctor, or Nurse.
2. Open `Medical Files` and select an uploaded `.txt` file. Confirm the preview panel shows readable text.
3. Select a `.csv` file and confirm the preview shows headers and first rows.
4. Select a text-based `.pdf` file and confirm PDFBox text appears. Scanned PDFs show a clear no-selectable-text message.
5. Select a `.png`, `.jpg`, or `.jpeg` file and confirm JavaFX shows an image preview.
6. Select a missing file row, if present, and confirm a clear preview-unavailable message appears.
7. Confirm rows with stored paths outside `data/uploads/` are blocked from preview/open.
8. Use `Copy Summary` and confirm the file summary/preview text is copied.
9. Use `Open File` only for trusted test uploads and confirm it opens locally or shows a Desktop unsupported message.
10. Open Patient Detail > Clinical Timeline, select a FILE event, and press `Open File Details`.
11. Open `Audit Logs` and confirm `VIEW_MEDICAL_FILE`, `COPY_FILE_SUMMARY`, and `OPEN_MEDICAL_FILE` rows when those actions occur.
12. Login as a Staff-style user and confirm Medical Files is hidden or access is denied.

Important: Safe preview/open controls do not render full PDFs, perform OCR, call external APIs, or permit files outside `data/uploads/`.

To test JavaFX Backup / Export:

1. Run `JavaFxMain` and login as an Admin.
2. Open `Backup / Export` from the sidebar.
3. Press `Create Backup`. Choose a folder or cancel to use `data/backups/`.
4. Confirm the generated ZIP contains `data/smart_patient_monitoring.db`, `README-backup-info.txt`, and any files under `data/uploads/`.
5. Export Patients, Alerts, Audit Logs, Medication Events, and Scheduling CSV files.
6. Press `Preview Restore Backup`, select the generated ZIP, and confirm the preview lists metadata and ZIP contents without changing the current database.
7. Open `Audit Logs` and confirm `CREATE_BACKUP`, export, and `PREVIEW_RESTORE_BACKUP` rows appear.
8. Login as a Doctor and confirm clinical CSV exports are available, while backup, restore preview, and audit-log export are hidden or denied.
9. Login as a Staff-style user and confirm Backup / Export is hidden or access is denied.

JavaFX local backups are ZIP files only. They include the SQLite database and local JavaFX uploads under `data/uploads/`; they do not include files outside that folder, cloud storage, encryption, or automatic restore. Restore is intentionally preview-only in this phase so the live database cannot be overwritten from the UI.

## Medical File Uploads

Staff can upload patient-related files from the patient list or patient dashboard. Uploaded files are copied into:

```text
data/files/{patientId}/
```

Metadata is recorded in:

```text
data/medical_files.txt
```

Supported upload examples include blood test results, medical reports, medication notes, doctor notes, TXT, CSV, DOC/DOCX, and PDF files. TXT and CSV files are scanned automatically. PDF and document files are stored for manual review without breaking the project.

## AI Advice Notes

Uploaded TXT and CSV files are scanned with rule-based logic for keywords and abnormal values such as high glucose, low oxygen, high CRP, high WBC, fever, high blood pressure, and low hemoglobin.

Generated notes are stored in:

```text
data/ai_notes.txt
```

The patient dashboard shows the latest AI advice notes for the selected patient.

## Device And Vital History

The app now separates device readings from manual readings:

- Manual vitals include the staff username from `Session`
- Device vitals include device ID, serial number, name, and type
- The simulated Bluetooth monitor is stored as a connected medical device
- Future real Bluetooth integration can replace `BluetoothMedicalDeviceAdapter`

## Sections And Rooms

Patients belong to a hospital section such as ER, Cardiology, Surgery, ICU, Pediatrics, or Internal Medicine. Rooms are limited by section range and capacity. If a room is full, the app blocks the assignment.

In JavaFX, room and section management now writes to SQLite only. The `rooms` table stores section, room number, capacity, status, notes, and last update time. Moving or assigning a patient updates that patient's SQLite `section` and `room` fields, which immediately affects Patient Detail, Dashboard metrics, and Room/Bed Occupancy. Legacy Swing room text storage is retained as fallback and is not updated by JavaFX in this phase.

## Certificates

Generated reports are stored under:

```text
data/certificates/death/
data/certificates/birth/
```

JavaFX Phase 36 death certificates are currently generated as local HTML files under:

```text
data/generated/death-certificates/
```

Those HTML certificates are created from SQLite `deceased_records` data and are a safe foundation for later JavaFX PDF/template output. They do not write back to legacy text files.

JavaFX Phase 37 birth certificates are currently generated as local HTML files under:

```text
data/generated/birth-certificates/
```

Those HTML birth certificates are created from SQLite `newborn_records` data. Full JavaFX PDF template overlay is intentionally left for a later safer phase.

JavaFX Phase 38 adds certificate reporting polish around those HTML outputs:

- Death certificate generation creates SQLite notifications for Admin and Doctor roles.
- Birth certificate generation creates SQLite notifications for Admin, Doctor, and Nurse roles.
- Deceased Records and Newborn Records show report cards for total records, generated certificates, pending certificates, and monthly/today counters.
- Deceased Records can send an internal SQLite death certificate notice; Newborn Records can send an internal SQLite birth notice.
- Certificate summaries can be copied from the detail panel and are audited.
- Dashboard now includes deaths-this-month plus pending birth/death certificate counters.

Certificate notices use the existing internal SQLite Messaging screen only. They do not attach certificate files and do not send email, SMS, push notifications, or external hospital paging messages. The JavaFX certificate workflow remains SQLite-only and does not update legacy text-file storage.

JavaFX Phase 39 adds drill-down navigation for certificate events:

- Certificate notifications show source type/source ID and can open the related deceased or newborn record.
- Certificate notifications can open the generated local certificate through the existing safe certificate services.
- Certificate notice messages include metadata lines for certificate type, source type, source ID, patient/newborn ID, and certificate path.
- Message detail shows a related certificate source card and buttons for `Open Related Record` and `Open Certificate`.
- Metadata is stored as text only; certificate files are not attached to messages.
- Opening certificates still validates the allowed generated folders: `data/generated/death-certificates/` and `data/generated/birth-certificates/`.

JavaFX Phase 40 adds a unified `Certificates` registry screen:

- Birth certificate rows from SQLite `newborn_records` and death certificate rows from SQLite `deceased_records` appear in one table.
- Filters include certificate type, generated/pending status, date range, section, and search by ID/name.
- The detail panel shows source record ID, patient/newborn ID, person name, event time, status, section/room, and a validated local path when generated.
- Registry actions include `Open Related Record`, `Generate Certificate`, `Open Certificate`, `Copy Summary`, and `Send Notice`.
- The registry still uses HTML certificate output only and does not attach files to messages.
- Staff users are denied; Admin/Doctor can generate and send according to existing certificate permissions, while Nurse visibility follows the existing birth/deceased screen permissions.

JavaFX Phase 41 adds a review and approval layer to the registry:

- SQLite `newborn_records` and `deceased_records` now track `review_status`, `reviewed_by`, `reviewed_at`, and `rejection_reason`.
- Review statuses are `DRAFT`, `PENDING_REVIEW`, `APPROVED`, and `REJECTED`.
- Generated means an HTML certificate file exists; approved means an authorized user reviewed and approved that generated certificate.
- Admin and Doctor users can submit certificates for review. Admin and Doctor users can approve or reject according to the current `PermissionHelper` rules.
- Rejection requires a reason. Rejected certificates cannot be opened from the registry or sent as final notices unless a future phase explicitly adds an override.
- Submitting, approving, and rejecting certificates creates SQLite notifications for relevant clinical roles.
- This review status is an internal workflow marker only. It is not a legal, governmental, or official certification claim.

Certificate generation uses Apache PDFBox when a blank template PDF is available. The project is still a plain Java project, so the PDFBox dependency is stored as a jar file:

```text
lib/pdfbox-app-2.0.36.jar
```

This standalone jar contains PDFBox and its runtime dependencies, including FontBox and Commons Logging. Keep it in `lib/` and compile/run with `lib/*` on the classpath as shown above.

To use real PDF certificate templates, place blank template PDFs here:

```text
data/certificate_templates/birth_template.pdf
data/certificate_templates/death_template.pdf
```

When a template exists, the app loads it, overlays certificate data in fixed positions, optionally adds the imported signature image, and saves the final PDF under the matching certificate folder. When a template is missing, the app continues to use the existing internal fallback PDF generator.

## Disclaimer

The AI analysis in this project is rule-based and intended for educational use only. It is not real medical AI and must not be used for diagnosis, treatment decisions, or clinical care.
