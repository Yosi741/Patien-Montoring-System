# ClinicPulse Project Structure Map

This is the short teacher-facing guide for explaining where the current urgent care clinic features live.

## Active Workflows

- Login
- Dashboard
- Patient Management
- Patient File
- Vitals
- Alerts
- Appointments
- Billing / Payments
- Medical Records / Medical Files
- Staff Management
- Messages
- Notifications
- Profile / Settings

## 1. Entry Point

- `src/Main.java`
  - Official Java entry point.
- `src/app/AppShell.java`
  - Starts JavaFX, loads the login page, opens the main shell, and handles page navigation.

## 2. App Core

- `src/app/AppShell.java` - JavaFX startup and navigation
- `src/app/AppNavigator.java` - FXML loading and view resolution
- `src/app/layout/AppLayoutController.java` - top bar, sidebar, shared shell actions
- `src/app/database/DatabaseManager.java` - SQLite connection handling
- `src/app/database/SchemaInitializer.java` - database setup and safe migrations
- `src/app/session/SessionContext.java` - current logged-in session
- `src/app/helpers/` - shared dialogs, permissions, file opening, selection safety, and date helpers
- `src/app/styles/` - shared JavaFX CSS themes

## 3. Login

- `src/pages/login/LoginController.java`
- `src/pages/login/LoginView.fxml`
- Login, clear form, and forgot-password flow.

## 4. Dashboard

- `src/pages/dashboard/DashboardController.java`
- `src/pages/dashboard/DashboardView.fxml`
- `src/pages/dashboard/services/DashboardMetricsService.java`
- Clinic overview cards, alerts, appointments, billing, and patient metrics.

## 5. Patients

- `src/pages/patient/patient_board/PatientListController.java` - Patient Management page
- `src/pages/patient/patient_detail/PatientDetailController.java` - Patient File page
- `src/pages/patient/patient_form/PatientFormController.java` - Add/Edit Patient form
- `src/pages/patient/vitals_entry/VitalsEntryController.java` - Enter Vitals form
- `src/pages/patient/medical_files/MedicalFilesController.java` - Medical Records page
- `src/pages/patient/dao/SqlitePatientDao.java` - patient persistence
- `src/pages/patient/services/` - patient workflow and validation services

## 6. Alerts

- `src/pages/alert/AlertService.java`
- `src/pages/alert/AlertSoundService.java`
- `src/pages/alert/AlarmService.java`
- Sound file: `src/sound/alarm.wav`

## 7. Appointments

- `src/pages/scheduling/schedule_overview/SchedulingController.java`
- `src/pages/scheduling/appointment_form/AppointmentFormController.java`
- `src/pages/scheduling/dao/SqliteAppointmentDao.java`
- `src/pages/scheduling/services/AppointmentWriteService.java`

## 8. Billing

- `src/pages/billing/billing_overview/BillingController.java`
- `src/pages/billing/dao/SqliteBillingDao.java`
- `src/pages/billing/services/BillingService.java`
- Local SQLite demo invoices only. No real payment gateway is included.

## 9. Staff Management And Roles

- `src/pages/user/user_directory/UserDirectoryController.java`
- `src/pages/user/user_form/UserFormController.java`
- `src/pages/user/profile_settings/UserProfileController.java`
- `src/pages/user/UserRole.java`
- Final roles: `ADMIN`, `DOCTOR`, `NURSE`, `SECRETARY`

## 10. Messages And Notifications

- `src/pages/messages/MessagingController.java`
- `src/pages/messages/MessagingService.java`
- `src/pages/notification/NotificationCenterController.java`
- `src/pages/notification/NotificationService.java`

## 11. Resources

- `src/photo/` - logo and branding image assets
- `src/sound/alarm.wav` - local alert sound
- `src/app/styles/dark-theme.css` - dark theme
- `src/app/styles/light-theme.css` - light theme

## Common Teacher Questions

- "Where is the Login button code?"
  - `src/pages/login/LoginController.java`, method `handleLogin()`

- "Where is the Clear Login button?"
  - `src/pages/login/LoginController.java`, method `handleClearLoginForm()`

- "Where is Forgot Password?"
  - `src/pages/login/LoginController.java`, method `handleForgotPasswordRequest()`

- "Where is Add Patient?"
  - `src/pages/patient/patient_board/PatientListController.java`
  - form: `src/pages/patient/patient_form/PatientFormController.java`

- "Where is Enter Vitals?"
  - `src/pages/patient/vitals_entry/VitalsEntryController.java`

- "Where are Medical Records?"
  - `src/pages/patient/medical_files/MedicalFilesController.java`

- "Where are Appointments?"
  - `src/pages/scheduling/schedule_overview/SchedulingController.java`

- "Where is Billing?"
  - `src/pages/billing/billing_overview/BillingController.java`

- "Where is Staff Management?"
  - `src/pages/user/user_directory/UserDirectoryController.java`

- "Where is database setup?"
  - `src/app/database/SchemaInitializer.java`
  - `src/app/database/DatabaseManager.java`
