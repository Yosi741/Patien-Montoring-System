# Smart Patient Monitoring System

A JavaFX desktop hospital patient monitoring prototype for a local presentation/demo environment. The application uses a local SQLite database, JavaFX screens, service-layer business logic, Data_Access_Object persistence classes, audit logging, alerts, patient files, scheduling, notifications, certificates, and medication safety workflows.

## Current Stack

- Java 11+ source code
- JavaFX UI with FXML and a forced Hospital Navy presentation theme
- SQLite local database at `data/smart_patient_monitoring.db`
- Plain Java project structure with dependencies in `lib/`
- Controller -> Service -> Data_Access_Object architecture

## Main Features

- Staff login, profile, session context, and role-based UI access
- Dashboard with hospital metrics, alert summaries, reminders, and patient counters
- Patient Board with active, deceased, and newborn subsections
- Patient File with demographics, vitals, alerts, clinical timeline, medications, scheduling, and room actions
- Manual vitals entry with rule-based thresholds, JavaFX alerts, notifications, and local alarm sound
- Alert notifications and clinical alert follow-up through the Notification Center and patient workflows
- Notification Center and internal messaging
- Room, bed, and section management
- Medication administration overview and write workflows
- Appointment and reminder scheduling with Nurse Work Queue
- Deceased patient records with local HTML death certificate generation
- Newborn records with local HTML birth certificate generation
- Unified certificate registry and review workflow
- Staff/user directory and admin user management
- Audit log viewer

## Demo Presentation Mode

The project is cleaned for a focused 15-minute JavaFX + SQLite presentation. Inactive experimental modules were removed from the active code path: AI recommendations, medical-device/Bluetooth registry, Backup / Export, Staff Activity, and the separate Alert Center page. Alerts remain part of vitals, patient files, dashboard metrics, and Notification Center.

Core hospital workflows stay active: patients, vitals, medications, rooms/beds, scheduling, Nurse Work Queue, certificates, notifications, profile, and admin tools.

The visible presentation sidebar focuses on:

- Dashboard
- Patients
- Medications
- Scheduling
- Nurse Work Queue
- Rooms / Beds
- Certificates
- Notifications
- Staff / Users, admin only
- Audit Logs, admin only
- Profile / Settings

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

Older archive files may still exist in `data/` from earlier development phases, but the running application no longer reads or writes operational data from text files.

Uploaded files and generated certificates are normal local files. The project uses a local SQLite database for records and metadata.

## SQLite-Only Runtime

The runtime data path is now:

JavaFX UI -> Services -> SQLite DAOs -> `data/smart_patient_monitoring.db`

Patient records, vitals, alerts, messages, notifications, rooms, medications, newborn/deceased records, certificates, audit logs, medical-file metadata, and staff accounts are stored in SQLite.

## Architecture

- `src/ui/javafx/`: JavaFX shell, controllers, FXML views, and styles
- `src/ui.javafx.services/`: validation, business workflows, alerts, certificates, scheduling, medication safety, notifications, and write ui.javafx.services
- `src/Data_Access_Object/`: SQLite Data_Access_Object classes and query models
- `src/database/`: SQLite database manager and schema initializer
- `src/models/`: core model objects
- `src/security/`: password hashing

Controllers should not contain SQL. Write workflows should use Controller -> Service -> Data_Access_Object.

## Certificates

Birth and death certificates currently generate safe local HTML files:

- Birth: `data/generated/birth-certificates/`
- Death: `data/generated/death-certificates/`

Certificate files are opened only after path validation. They are prototype-generated local certificates based on SQLite records, not official legal or government certificates. PDF/template overlay support can be reintroduced later as a dedicated certificate output phase.

## Notes And Limitations

- This is a local desktop application, not a networked hospital deployment.
- The project uses a local login system with role-based access for Admin, Doctor, Nurse, and Staff users.
- Passwords are not stored as plain text and are not displayed in the app or documentation.
- The presentation focuses on hospital workflow and layered architecture, not advanced cybersecurity.
- Real device integration, external email/SMS, cloud backup, and hospital paging integrations are future extensions outside this demo build.
- HTML certificates are local prototype documents, not official legal certification.

## Presentation Guide

Use [docs/presentation-demo-guide.md](docs/presentation-demo-guide.md) for the recommended 15-minute college board demo flow.

## Smoke Testing

Use [docs/javafx-only-smoke-test.md](explanation%20files/javafx-only-smoke-test.md) for the current JavaFX-only verification checklist.
