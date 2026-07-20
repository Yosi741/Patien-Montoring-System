# ClinicPulse - Smart Urgent Care Clinic System

ClinicPulse is a Java 17, JavaFX, and SQLite desktop application for a local urgent care clinic demo. It focuses on practical clinic workflows: login, dashboard metrics, patient records, vitals, alerts, appointments, medical files, billing, staff management, internal messages, notifications, and profile settings.

## Current Stack

- Java 17
- JavaFX FXML screens
- SQLite local database
- CSS themes for the desktop UI
- Controller -> Service -> DAO architecture
- Local file storage for uploaded patient files

## Active Features

- Login
- Dashboard
- Patient Management
- Patient File
- Vitals
- Alerts / Notifications
- Appointments
- Medical Files
- Billing
- Staff Management
- Messages
- Profile / Settings

## Demo Roles

- `ADMIN`
- `DOCTOR`
- `NURSE`
- `SECRETARY`

## Main Architecture

- Entry point: `src/Main.java`
- App shell: `src/app/core/AppShell.java`
- Session context: `src/app/core/SessionContext.java`
- Navigator: `src/app/navigation/AppNavigator.java`
- Layout controller: `src/app/layout/AppLayoutController.java`
- Database manager: `src/app/database/DatabaseManager.java`
- Schema initializer: `src/app/database/SchemaInitializer.java`
- Permissions: `src/app/helpers/PermissionHelper.java`

The app flow is:

```text
JavaFX View/FXML -> Controller -> Service -> DAO -> SQLite
```

## How To Run

Open the project in IntelliJ IDEA and run:

```text
src/Main.java
```

Before compiling from the command line, rebuild `sources.txt` from the real Java files under `src` and `tools`:

```cmd
dir /s /b src\*.java tools\*.java > sources.txt
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

## Active SQLite Tables

- `users`
- `user_profiles`
- `patients`
- `patient_visits`
- `vital_readings`
- `alerts`
- `appointments`
- `medical_files`
- `billing_records`
- `notifications`
- `messages`

## Security Scope

This is a local desktop demo. It uses a local login system and role-based access to demonstrate application workflow and architecture. It is not a cloud system and does not implement advanced cybersecurity features.

## Project Structure

- `src/app/core/` - JavaFX app shell and session context
- `src/app/navigation/` - FXML loading and navigation helper
- `src/app/layout/` - top bar, sidebar, and shared shell layout
- `src/app/database/` - SQLite connection and schema setup
- `src/app/helpers/` - shared dialogs, permissions, file opening, selection safety, validation, and date helpers
- `src/app/styles/` - shared JavaFX CSS themes
- `src/pages/login/` - login and forgot-password flow
- `src/pages/dashboard/` - clinic dashboard and overview metrics
- `src/pages/patient/` - patient board, patient file, patient form, vitals, and medical files
- `src/pages/alert/` - alert persistence and local alert sound handling
- `src/pages/scheduling/` - appointments
- `src/pages/billing/` - local demo invoices and payment status tracking
- `src/pages/user/` - staff management and profile/settings
- `src/pages/messages/` - internal clinic messages
- `src/pages/notification/` - alerts and notifications inbox
- `src/photo/` - logo and branding image assets
- `src/sound/` - sound resources
- `tools/` - demo reset, verification, login, and performance helper tools

Use `PROJECT_STRUCTURE_MAP.md` for the teacher-facing "where is this feature implemented?" guide.
