# Smart Patient Monitoring System

A JavaFX desktop hospital management and patient monitoring system. The application uses a local SQLite database, JavaFX screens, service-layer business logic, DAO persistence classes, audit logging, alerts, patient files, scheduling, notifications, certificates, and backup/export tools.

## Current Stack

- Java 11+ source code
- JavaFX UI with FXML and shared light/dark theme styles
- SQLite local database at `data/smart_patient_monitoring.db`
- Plain Java project structure with dependencies in `lib/`
- Controller -> Service -> DAO architecture

## Main Features

- Staff login, profile, session context, and role-based UI access
- Dashboard with hospital metrics, alert summaries, reminders, and patient counters
- Patient Board with active, deceased, and newborn subsections
- Patient File with demographics, vitals, alerts, clinical timeline, medical files, medications, scheduling, and room actions
- Manual vitals entry with rule-based thresholds, JavaFX alerts, notifications, and local alarm sound
- Alert notifications and clinical alert follow-up through the Notification Center and patient workflows
- Notification Center and internal messaging
- Room, bed, and section management
- Medication administration overview and write workflows
- Appointment and reminder scheduling with Nurse Work Queue
- Medical file upload, safe local preview, and basic text extraction
- Deceased patient records with local HTML death certificate generation
- Newborn records with local HTML birth certificate generation
- Unified certificate registry and review workflow
- Staff/user directory and admin user management
- Audit log viewer
- Local ZIP backup and CSV export tools

## Demo Presentation Mode

The project currently starts in a simplified 15-minute demo configuration:

- `DEMO_MODE=true`
- `APP_FEATURE_AI=false`
- `APP_FEATURE_DEVICES=false`

AI recommendation and medical-device screens are hidden for the presentation, but their code and database tables remain in the project for later phases. Core hospital workflows stay active: patients, vitals, medications, rooms/beds, scheduling, Nurse Work Queue, medical files, certificates, notifications, profile, and admin tools.

## How To Run

Open the project in IntelliJ IDEA and run `src/Main.java`.

Windows PowerShell from the project folder:

```powershell
$sources = Get-ChildItem -Path src -Recurse -Filter *.java | Sort-Object FullName | ForEach-Object { $_.FullName }
New-Item -ItemType Directory -Force -Path out/production/untitledSmartPatientMonitoringSystem | Out-Null
javac -cp "lib/*" -d out/production/untitledSmartPatientMonitoringSystem $sources
Copy-Item -Path src/ui -Destination out/production/untitledSmartPatientMonitoringSystem -Recurse -Force
java -cp "out/production/untitledSmartPatientMonitoringSystem;lib/*" Main
```

Linux/macOS:

```bash
mkdir -p out/production/untitledSmartPatientMonitoringSystem
find src -name "*.java" | sort > sources.txt
javac -cp "lib/*" -d out/production/untitledSmartPatientMonitoringSystem @sources.txt
cp -R src/ui out/production/untitledSmartPatientMonitoringSystem/
java -cp "out/production/untitledSmartPatientMonitoringSystem:lib/*" Main
```

## Dependencies

This is a plain Java project. Required JAR files are stored in `lib/`:

- `javafx-base-17.0.15-win.jar`
- `javafx-controls-17.0.15-win.jar`
- `javafx-fxml-17.0.15-win.jar`
- `javafx-graphics-17.0.15-win.jar`
- `sqlite-jdbc-3.53.1.0.jar`
- `pdfbox-app-2.0.36.jar`
- `slf4j-api-2.0.17.jar`
- `slf4j-simple-2.0.17.jar`

## Data Storage

Runtime data is stored locally under `data/`.

- Main database: `data/smart_patient_monitoring.db`
- Uploaded files: `data/uploads/`
- Generated certificates: `data/generated/`
- Backups: `data/backups/`
- Certificate templates, if used later: `data/certificate_templates/`

Older archive files may still exist in `data/` from earlier development phases, but the running application no longer reads or writes operational data from text files.

## SQLite-Only Runtime

The runtime data path is now:

JavaFX UI -> Services -> SQLite DAOs -> `data/smart_patient_monitoring.db`

Patient records, vitals, alerts, messages, notifications, rooms, medications, newborn/deceased records, certificates, audit logs, and staff accounts are stored in SQLite. Backup/export tools remain available for presentation and recovery workflows.

## Architecture

- `src/ui/javafx/`: JavaFX shell, controllers, FXML views, and styles
- `src/services/`: validation, business workflows, alerts, certificates, backups, scheduling, AI recommendations, and write services
- `src/dao/`: SQLite DAO classes and query models
- `src/database/`: SQLite database manager and schema initializer
- `src/models/`: core model objects
- `src/security/`: password hashing

Controllers should not contain SQL. Write workflows should use Controller -> Service -> DAO.

## Certificates

Birth and death certificates currently generate safe local HTML files:

- Birth: `data/generated/birth-certificates/`
- Death: `data/generated/death-certificates/`

Certificate files are opened only after path validation. PDF/template overlay support can be reintroduced later as a dedicated certificate output phase.

## Backup And Export

The Backup / Export screen can:

- Create a local ZIP backup containing the SQLite database and uploaded files
- Export patient, alert, audit, medication, and scheduling summaries to CSV
- Preview backup ZIP contents before a future restore workflow

Restore is intentionally preview-only; it does not overwrite the live database.

## Notes And Limitations

- This is a local desktop application, not a networked hospital deployment.
- Rule-based AI recommendation code is retained but hidden in the current demo configuration.
- Real device, smart watch, external email/SMS, and hospital paging integrations are future work.
- HTML certificates are presentation-ready foundations, not official legal certification.
- Passwords are hashed for SQLite users; raw passwords are not displayed or logged.

## Smoke Testing

Use [docs/javafx-only-smoke-test.md](docs/javafx-only-smoke-test.md) for the current JavaFX-only verification checklist.
