# ClinicPulse - Smart Urgent Care Clinic System

ClinicPulse is a JavaFX and SQLite desktop application for a local urgent care clinic demo. It focuses on daily clinic workflows: login, dashboard metrics, patient records, vitals, alerts, appointments, medical files, billing, staff management, messages, and notifications.

## Current Stack

- Java 17
- JavaFX FXML screens
- SQLite local database
- CSS themes for the desktop UI
- Controller -> Service -> DAO architecture
- Local file storage for uploaded patient files

## Core Workflows

- Login and role-based access
- Dashboard
- Patient Management
- Patient File
- Vitals entry with alert creation
- Alerts and notifications
- Appointments
- Billing and payments
- Medical Records / Medical Files
- Staff Management
- Messages
- Profile / Settings

## Demo Roles

- `ADMIN`
- `DOCTOR`
- `NURSE`
- `SECRETARY`

## How To Run

Open the project in IntelliJ IDEA and run:

```text
src/Main.java
```

From Windows `cmd.exe` inside `untitledSmartPatientMonitoringSystem`:

```cmd
javac -d out -cp "src;lib/javafx-base-17.0.15-win.jar;lib/javafx-controls-17.0.15-win.jar;lib/javafx-fxml-17.0.15-win.jar;lib/javafx-graphics-17.0.15-win.jar;lib/pdfbox-app-2.0.36.jar;lib/slf4j-api-2.0.17.jar;lib/slf4j-simple-2.0.17.jar;lib/sqlite-jdbc-3.53.1.0.jar" @sources.txt
java -cp "out;lib/javafx-base-17.0.15-win.jar;lib/javafx-controls-17.0.15-win.jar;lib/javafx-fxml-17.0.15-win.jar;lib/javafx-graphics-17.0.15-win.jar;lib/pdfbox-app-2.0.36.jar;lib/slf4j-api-2.0.17.jar;lib/slf4j-simple-2.0.17.jar;lib/sqlite-jdbc-3.53.1.0.jar" Main
```

## Data Storage

- Main database: `data/smart_patient_monitoring.db`
- Uploaded patient files: `data/uploads/`
- Profile photos: `data/profile_photos/`

SQLite is the source of truth for the running application. Uploaded files remain normal local files on disk, while their metadata is stored in SQLite.

## Security Scope

This is a local desktop demo. It uses a local login system and role-based access to demonstrate application workflow and architecture. It is not a cloud system and does not implement advanced cybersecurity features.

## Project Structure

- `src/app/` - application shell, navigation, database bootstrap, shared helpers, shared styles
- `src/pages/login/` - login and forgot-password flow
- `src/pages/dashboard/` - clinic dashboard and overview metrics
- `src/pages/patient/` - patient board, patient file, patient form, vitals, and medical files
- `src/pages/alert/` - alert services and local alert sound handling
- `src/pages/scheduling/` - appointments
- `src/pages/billing/` - local demo invoices and payment status tracking
- `src/pages/user/` - staff management and profile/settings
- `src/pages/messages/` - internal clinic messages
- `src/pages/notification/` - alerts and notifications inbox
- `src/photo/` - logo and branding image assets
- `src/sound/` - sound resources

Use `PROJECT_STRUCTURE_MAP.md` for the teacher-facing "where is this feature implemented?" guide.
