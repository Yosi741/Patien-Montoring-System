# Smart Clinic Patient Monitoring System

This project is a JavaFX + SQLite desktop clinic application for a local presentation/demo environment. It focuses on patient monitoring, patient files, vitals, alerts, scheduling, messages, medical files, and staff access inside a small clinic workflow.

## Current stack

- Java source code with JavaFX FXML screens
- SQLite local database at `data/smart_patient_monitoring.db`
- Dark clinic presentation theme
- Controller -> Service -> DAO architecture
- Local file storage for uploaded patient files and generated runtime assets

## Core clinic workflows

- Login and role-based access
- Dashboard
- Patients
- Patient File
- Vitals entry with alert generation
- Alerts & Notifications
- Scheduling / Appointments / Checkups
- Billing / Payments with local demo invoices
- Medical Files
- Staff / Users
- Messages
- Profile / Settings

## How to run

Open the project in IntelliJ IDEA and run `src/Main.java`.

From Windows `cmd.exe` inside `untitledSmartPatientMonitoringSystem`:

```cmd
javac -d out -cp "src;lib/javafx-base-17.0.15-win.jar;lib/javafx-controls-17.0.15-win.jar;lib/javafx-fxml-17.0.15-win.jar;lib/javafx-graphics-17.0.15-win.jar;lib/pdfbox-app-2.0.36.jar;lib/slf4j-api-2.0.17.jar;lib/slf4j-simple-2.0.17.jar;lib/sqlite-jdbc-3.53.1.0.jar" @sources.txt
javac -d out/production/untitledSmartPatientMonitoringSystem -cp "src;lib/javafx-base-17.0.15-win.jar;lib/javafx-controls-17.0.15-win.jar;lib/javafx-fxml-17.0.15-win.jar;lib/javafx-graphics-17.0.15-win.jar;lib/pdfbox-app-2.0.36.jar;lib/slf4j-api-2.0.17.jar;lib/slf4j-simple-2.0.17.jar;lib/sqlite-jdbc-3.53.1.0.jar" @sources.txt
powershell -ExecutionPolicy Bypass -File .\sync_resources.ps1
java -cp "out/production/untitledSmartPatientMonitoringSystem;lib/*" Main
```

## Data storage

- Main database: `data/smart_patient_monitoring.db`
- Uploaded files: `data/uploads/`
- Generated runtime files: `data/generated/`
- Backups: `data/backups/`

SQLite is the source of truth for the running application. Uploaded files remain local files on disk, while their metadata is stored in SQLite.

## Clinic presentation notes

- The project uses a local login system with Admin, Doctor, Nurse, and Staff roles.
- Passwords are not stored as plain text.
- The clinic presentation focuses on workflow and layered architecture, not on cloud deployment or advanced cybersecurity.
- This is a local desktop app for demonstration, not a networked clinic installation.
- Billing is local SQLite demo billing only. There is no real payment gateway, no online payment flow, and no credit-card storage.

## Project structure

- `src/app/` - application shell, navigation, theme, database bootstrap, shared helpers
- `src/pages/login/` - login and password-reset flow
- `src/pages/dashboard/` - clinic dashboard and overview metrics
- `src/pages/patient/` - patient board, patient file, patient form, vitals, and medical files
- `src/pages/alert/` - alert services and local alarm sound handling
- `src/pages/scheduling/` - appointments, reminders, and checkups
- `src/pages/billing/` - local demo invoices, billing overview, and payment status tracking
- `src/pages/user/` - staff/user directory and profile/settings
- `src/pages/messages/` - internal staff messaging
- `src/pages/notification/` - alerts and notifications inbox
- `src/photo/` - branding image assets
- `src/sound/` - sound resources

Use `PROJECT_STRUCTURE_MAP.md` for the teacher-facing “where is this feature implemented?” guide.
