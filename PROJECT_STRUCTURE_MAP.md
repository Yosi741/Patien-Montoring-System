# Smart Clinic Patient Monitoring System - Project Structure Map

This file is a short guide for explaining the clinic-focused project structure during the teacher review.

Core clinic workflows:
- Login
- Dashboard
- Patients
- Vitals
- Alerts
- Scheduling
- Users

## 1. Project entry point

- `src/Main.java`
  - Official Java entry point.
- `src/app/AppShell.java`
  - Starts JavaFX, loads the login page, opens the main shell, and handles page navigation.

## 2. App core

- `src/app`
  - Main application shell and shared infrastructure.
  - Important files:
    - `AppShell.java` - app startup and navigation
    - `AppNavigator.java` - FXML loading and view resolution
    - `AppLayoutController.java` - top bar, sidebar, shared shell actions
    - `DatabaseManager.java` - SQLite connection handling
    - `SchemaInitializer.java` - database setup and safe migrations
    - `SessionContext.java` - current logged-in session context
    - `PasswordHasher.java` - password hashing and password verification support
    - `helpers/` - shared dialogs, selection safety, permissions, date pickers, file open helpers

## 3. Login

- `src/pages/login`
  - Login screen, clear form action, forgot password flow, password reset helpers, local email outbox flow.
  - Main files:
    - `LoginController.java`
    - `LoginView.fxml`
    - `ForgotPasswordService.java`
    - `PasswordResetService.java`
    - `dao/`

## 4. Dashboard

- `src/pages/dashboard`
  - Dashboard cards, shell landing page, and overview metrics.

## 5. Patients

- `src/pages/patient`
  - Patient Board
  - Patient File / Detail
  - Add/Edit Patient form
  - Enter Vitals
  - Medical Files
  - Supporting patient services and DAO code used by patient pages

## 6. Users and roles

- `src/pages/user`
  - Staff / User Directory
  - Add/Edit User
  - Profile / Settings
  - Staff ID handling
  - User services and DAO code for the user workflow
- `src/users/roles`
  - Role definitions and role-related support classes

## 7. Alerts

- `src/pages/alert`
  - Critical alerts, alert persistence, alert sound, and alert services.

## 8. Scheduling

- `src/pages/scheduling`
  - Scheduling overview
  - Appointment form
  - Reminder form
  - Scheduling services and reminder engine

## 9. Rooms and sections

- `src/pages/room_section`
  - Room occupancy
  - Room form
  - Section form
  - Room assignment
  - Related services and DAO classes

## 10. Certificates and deceased records

- `src/pages/certificate`
  - Certificate registry
  - Death record form
  - Certificate services and certificate generation helpers
- `src/pages/deceased`
  - Deceased records workflow and supporting service/DAO code

## 11. Newborns

- `src/pages/newborn`
  - Newborn records
  - Newborn form
  - Mother linking and newborn services/DAO code

## 12. Timeline, messages, notifications, audit logs, nurse work

- `src/pages/clinical_timeline`
  - Patient clinical timeline
- `src/pages/messages`
  - Internal messaging page and message services
- `src/pages/notification`
  - Notification Center and notification services
- `src/pages/audit_log`
  - Audit log page and audit write/read support
- `src/pages/nurse_work`
  - Nurse work queue and handover support

## 13. Resources

- `src/photo`
  - Logo and branding image assets used by the login page and app shell
- `src/sound/alarm.wav`
  - Alarm sound resource used by the alert sound service
- `src/app/styles/dark-theme.css`
  - Main Hospital Navy presentation theme

## 14. How to answer teacher questions

- "Where is the Login button code?"
  - `src/pages/login/LoginController.java`
  - method: `handleLogin()`

- "Where is the Clear button?"
  - `src/pages/login/LoginController.java`
  - method: `handleClearLoginForm()`

- "Where is Forgot Password?"
  - `src/pages/login/LoginController.java`
  - method: `handleForgotPasswordRequest()`
  - service: `src/pages/login/ForgotPasswordService.java`

- "Where is Add Patient?"
  - button flow starts from `src/pages/patient/patient_board/PatientListController.java`
  - form is in `src/pages/patient/patient_form/PatientFormController.java`

- "Where is Add User?"
  - button flow starts from `src/pages/user/user_directory/UserDirectoryController.java`
  - form is in `src/pages/user/user_form/UserFormController.java`

- "Where is Alert Sound?"
  - `src/pages/alert/AlertSoundService.java`
  - sound file: `src/sound/alarm.wav`

- "Where is database setup?"
  - `src/app/SchemaInitializer.java`
  - `src/app/DatabaseManager.java`

- "Where is password hashing?"
  - `src/app/PasswordHasher.java`
