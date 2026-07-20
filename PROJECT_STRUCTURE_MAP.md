# ClinicPulse Project Structure Map

This is the short teacher-facing guide for explaining where the current JavaFX + SQLite urgent care clinic features live.

## Active Workflows

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

## 1. Entry Point

- `src/Main.java`
  - Official Java entry point.
- `src/app/core/AppShell.java`
  - Starts JavaFX, loads the login page, opens the main shell, and handles page navigation.

## 2. App Core

- `src/app/core/AppShell.java` - JavaFX startup, login window, main shell, page routing, and theme switching
- `src/app/core/SessionContext.java` - current logged-in session metadata
- `src/app/navigation/AppNavigator.java` - FXML loading and view resolution
- `src/app/layout/AppLayout.fxml` - shared desktop layout
- `src/app/layout/AppLayoutController.java` - top bar, sidebar, profile menu, badges, and shared shell actions
- `src/app/database/DatabaseManager.java` - SQLite connection handling
- `src/app/database/SchemaInitializer.java` - database setup and safe migrations
- `src/app/helpers/PermissionHelper.java` - role-based access rules
- `src/app/helpers/` - shared dialogs, validation, date picker, file opening, and selection safety helpers
- `src/app/styles/` - shared JavaFX CSS themes

## 3. Login

- `src/pages/login/LoginView.fxml`
- `src/pages/login/LoginController.java`
- `src/pages/login/ForgotPasswordService.java`
- Handles login, clear form, and forgot-password flow.

## 4. Dashboard

- `src/pages/dashboard/DashboardView.fxml`
- `src/pages/dashboard/DashboardController.java`
- `src/pages/dashboard/services/DashboardMetricsService.java`
- Shows clinic overview cards, recent alerts, latest vitals, appointments, billing, and patient metrics.

## 5. Patient Management

- `src/pages/patient/patient_board/PatientListView.fxml`
- `src/pages/patient/patient_board/PatientListController.java`
- `src/pages/patient/patient_board/PatientDao.java`
- Patient list, search/filter, row actions, Add Patient, Edit Patient, and Add Vitals shortcut.

## 6. Patient File

- `src/pages/patient/patient_detail/PatientDetailView.fxml`
- `src/pages/patient/patient_detail/PatientDetailController.java`
- `src/pages/patient/patient_detail/Patient.java`
- `src/pages/patient/patient_detail/SqlitePatientDao.java`
- `src/pages/patient/patient_detail/PatientWriteService.java`
- `src/pages/patient/patient_detail/PatientVisitService.java`
- `src/pages/patient/patient_detail/SqlitePatientVisitDao.java`
- Full patient file, visit history, vitals timeline, trends, alerts summary, and patient-specific actions.

## 7. Patient Form

- `src/pages/patient/patient_form/PatientFormView.fxml`
- `src/pages/patient/patient_form/PatientFormController.java`
- Add/Edit Patient form and validation.

## 8. Vitals

- `src/pages/patient/vitals_entry/VitalsEntryView.fxml`
- `src/pages/patient/vitals_entry/VitalsEntryController.java`
- `src/pages/patient/vitals_entry/VitalsWriteService.java`
- `src/pages/patient/vitals_entry/VitalsTrendService.java`
- `src/pages/patient/vitals_entry/VitalThresholdService.java`
- `src/pages/patient/vitals_entry/SqliteVitalReadingDao.java`
- Vitals entry, validation, threshold classification, trend loading, SQLite writes, and alert integration.

## 9. Alerts / Notifications

- `src/pages/alert/AlertPersistenceService.java`
- `src/pages/alert/SqliteAlertDao.java`
- `src/pages/alert/AlertSoundService.java`
- `src/pages/notification/NotificationCenterView.fxml`
- `src/pages/notification/NotificationCenterController.java`
- `src/pages/notification/NotificationCenterService.java`
- `src/pages/notification/SqliteNotificationDao.java`
- Alert persistence, local alarm sound, and notification inbox.

Sound file:

- `src/sound/alarm.wav`

## 10. Appointments

- `src/pages/scheduling/schedule_overview/SchedulingView.fxml`
- `src/pages/scheduling/schedule_overview/SchedulingController.java`
- `src/pages/scheduling/appointment_form/AppointmentFormView.fxml`
- `src/pages/scheduling/appointment_form/AppointmentFormController.java`
- `src/pages/scheduling/SchedulingService.java`
- `src/pages/scheduling/SqliteAppointmentDao.java`
- Appointment list, filters, create/edit dialog, date validation, status updates, and SQLite persistence.

## 11. Medical Files

- `src/pages/patient/medical_files/MedicalFilesView.fxml`
- `src/pages/patient/medical_files/MedicalFilesController.java`
- `src/pages/patient/medical_files/MedicalFileUploadView.fxml`
- `src/pages/patient/medical_files/MedicalFileUploadController.java`
- `src/pages/patient/medical_files/MedicalFileUploadService.java`
- `src/pages/patient/medical_files/MedicalFilePreviewService.java`
- `src/pages/patient/medical_files/SqliteMedicalFileDao.java`
- Medical Records page, upload flow, metadata, safe preview, and local stored files.

## 12. Billing

- `src/pages/billing/billing_overview/BillingView.fxml`
- `src/pages/billing/billing_overview/BillingController.java`
- `src/pages/billing/BillingService.java`
- `src/pages/billing/SqliteBillingDao.java`
- `src/pages/billing/BillingDao.java`
- `src/pages/billing/BillingRecord.java`
- Local SQLite demo invoices and payment status tracking. No real payment gateway is included.

## 13. Staff Management And Roles

- `src/pages/user/user_directory/UserDirectoryManagementView.fxml`
- `src/pages/user/user_directory/UserDirectoryController.java`
- `src/pages/user/user_directory/StaffProfileDialogView.fxml`
- `src/pages/user/user_directory/StaffProfileDialogController.java`
- `src/pages/user/user_form/UserFormView.fxml`
- `src/pages/user/user_form/UserFormController.java`
- `src/pages/user/User.java`
- `src/pages/user/UserRole.java`
- `src/pages/user/Session.java`
- Staff list, add/edit staff, profile photo fields, role display, and local user records.

Final roles:

- `ADMIN`
- `DOCTOR`
- `NURSE`
- `SECRETARY`

## 14. Profile / Settings

- `src/pages/user/profile_settings/UserProfileView.fxml`
- `src/pages/user/profile_settings/UserProfileController.java`
- `src/pages/user/profile_settings/UserProfileService.java`
- `src/pages/user/profile_settings/UserWriteService.java`
- `src/pages/user/profile_settings/SqliteUserDao.java`
- `src/pages/user/profile_settings/SqliteUserProfileDao.java`
- Current user contact fields, password change, and permission preview.

## 15. Messages

- `src/pages/messages/MessagingView.fxml`
- `src/pages/messages/MessagingController.java`
- `src/pages/messages/MessagingService.java`
- `src/pages/messages/SqliteMessageDao.java`
- Internal clinic messages and unread counts.

## 16. Resources

- `src/photo/` - logo and branding image assets
- `src/sound/alarm.wav` - local alert sound
- `src/app/styles/dark-theme.css` - dark theme
- `src/app/styles/light-theme.css` - light theme
- `data/uploads/` - physical uploaded patient files
- `data/smart_patient_monitoring.db` - local SQLite database

## 17. Demo Tools

- `tools/DemoDatabaseReset.java` - resets and seeds presentation demo data
- `tools/DemoDatabaseVerify.java` - verifies active clinic table counts
- `tools/DemoLoginVerify.java` - verifies demo login accounts
- `tools/DemoPerformanceCheck.java` - quick local query performance check

Before compiling from the command line, rebuild `sources.txt` from the current Java tree:

```cmd
dir /s /b src\*.java tools\*.java > sources.txt
```

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

- "Where is the patient profile/file?"
  - `src/pages/patient/patient_detail/PatientDetailController.java`

- "Where is Enter Vitals?"
  - `src/pages/patient/vitals_entry/VitalsEntryController.java`
  - save logic: `src/pages/patient/vitals_entry/VitalsWriteService.java`

- "Where are Medical Records?"
  - `src/pages/patient/medical_files/MedicalFilesController.java`

- "Where are Appointments?"
  - `src/pages/scheduling/schedule_overview/SchedulingController.java`

- "Where is Billing?"
  - `src/pages/billing/billing_overview/BillingController.java`

- "Where is Staff Management?"
  - `src/pages/user/user_directory/UserDirectoryController.java`

- "Where are permissions?"
  - `src/app/helpers/PermissionHelper.java`

- "Where is database setup?"
  - `src/app/database/SchemaInitializer.java`
  - `src/app/database/DatabaseManager.java`
