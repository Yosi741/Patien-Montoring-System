# Smart Clinic Patient Monitoring System - Project Structure Map

This file is the short teacher-facing guide for explaining the clinic-focused project structure.

Core clinic workflows:
- Login
- Dashboard
- Patients
- Patient File
- Vitals
- Alerts
- Scheduling
- Medical Files
- Staff / Users
- Messages
- Notifications

## 1. Project entry point

- `src/Main.java`
  - Official Java entry point.
- `src/app/AppShell.java`
  - Starts JavaFX, loads the login page, opens the main shell, and handles page navigation.

## 2. App core

- `src/app/`
  - Main application shell and shared infrastructure.
  - Important files:
    - `AppShell.java` - app startup and navigation
    - `AppNavigator.java` - FXML loading and view resolution
    - `AppLayoutController.java` - top bar, sidebar, shared shell actions
    - `DatabaseManager.java` - SQLite connection handling
    - `SchemaInitializer.java` - database setup and safe migrations
    - `SessionContext.java` - current logged-in session context
    - `PasswordHasher.java` - password hashing and verification support
    - `helpers/` - shared dialogs, permissions, file opening, selection safety, and date helpers

## 3. Login

- `src/pages/login/`
  - Login screen, clear form action, forgot-password flow, and local reset helpers.

## 4. Dashboard

- `src/pages/dashboard/`
  - Clinic overview cards, alert summaries, vitals summary, and reminder counters.

## 5. Patients

- `src/pages/patient/`
  - Patient Board
  - Patient File / Detail
  - Add/Edit Patient form
  - Enter Vitals
  - Medical Files
  - Patient services and DAO code used by those pages

## 6. Alerts

- `src/pages/alert/`
  - Alert persistence, alert sound, and alert-related services.

## 7. Scheduling

- `src/pages/scheduling/`
  - Scheduling overview
  - Appointment form
  - Reminder form
  - Scheduling services and reminder engine

## 8. Users and roles

- `src/pages/user/`
  - Staff / User Directory
  - Add/Edit User
  - Profile / Settings
  - User services and DAO code for the user workflow
- `src/users/roles/`
  - Role definitions and role-related support classes

## 9. Messages and notifications

- `src/pages/messages/`
  - Internal staff messaging page and message services
- `src/pages/notification/`
  - Alerts & Notifications inbox and notification services

## 10. Resources

- `src/photo/`
  - Logo and branding image assets used by the login page and app shell
- `src/sound/alarm.wav`
  - Local clinic alert sound resource
- `src/app/styles/dark-theme.css`
  - Main clinic presentation theme

## 11. How to answer teacher questions

- "Where is the Login button code?"
  - `src/pages/login/LoginController.java`
  - method: `handleLogin()`

- "Where is the Clear button?"
  - `src/pages/login/LoginController.java`
  - method: `handleClearLoginForm()`

- "Where is Forgot Password?"
  - `src/pages/login/LoginController.java`
  - method: `handleForgotPasswordRequest()`
  - services in `src/pages/login/`

- "Where is Add Patient?"
  - button flow starts in `src/pages/patient/patient_board/PatientListController.java`
  - form is in `src/pages/patient/patient_form/PatientFormController.java`

- "Where is Enter Vitals?"
  - `src/pages/patient/vitals_entry/VitalsEntryController.java`

- "Where is Medical Files?"
  - `src/pages/patient/medical_files/MedicalFilesController.java`

- "Where is Scheduling?"
  - `src/pages/scheduling/schedule_overview/SchedulingController.java`

- "Where is Staff / Users?"
  - `src/pages/user/user_directory/UserDirectoryController.java`

- "Where is Alert Sound?"
  - `src/pages/alert/AlertSoundService.java`
  - sound file: `src/sound/alarm.wav`

- "Where is database setup?"
  - `src/app/SchemaInitializer.java`
  - `src/app/DatabaseManager.java`

- "Where is password hashing?"
  - `src/app/PasswordHasher.java`
