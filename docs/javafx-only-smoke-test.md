# JavaFX-Only Smoke Test Checklist

Use this checklist after compile or UI cleanup changes.

## Startup

- Compile all Java source files with `javac -cp "lib/*"`.
- Copy `src/ui` into the compiled output folder.
- Run `Main`.
- Confirm the JavaFX Login screen opens.
- Confirm no Swing window opens.

## Login And Shell

- Login as an Admin user.
- Confirm Dashboard opens.
- Confirm sidebar navigation works.
- Confirm top notification count/profile menu render correctly.
- Toggle dark mode and return to light mode.

## Dashboard

- Confirm metric cards load.
- Confirm alert/reminder/patient counters display.
- Use quick links to open Patients, Notifications, and Nurse Work Queue.
- Confirm AI Recommendations and Medical Devices are hidden in demo mode.

## Patients

- Open Patient Board.
- Test quick filters: All Patients, Active Patients, Deceased Patients, Newborns, Critical / Emergency, High Priority, Recently Updated.
- Confirm normal patient mode shows Add Patient, Edit Patient, Enter Vitals, View Patient File, and Discharge / Deactivate according to role.
- Confirm Deceased Patients mode blocks clinical actions such as Enter Vitals.
- Confirm Newborns mode shows Add Newborn and View Newborn Record.

## Add/Edit Patient

- Add a test patient.
- Edit the test patient.
- Confirm section and room fields load choices from the local database where available.
- Confirm validation errors show clearly.

## Vitals Entry

- Open Enter Vitals from Patient Board or Patient File.
- Enter a normal vital.
- Enter a warning or critical vital.
- Confirm the vital saves, a JavaFX alert appears, notification count updates, and alarm sound stops after acknowledgement.

## Alerts

- Open Notification Center from the top-bar bell.
- Confirm alert notifications are visible in the notification feed.
- Open linked patient files where available.
- Confirm acknowledging clinical alerts from the relevant workflow stops JavaFX alert sound.

## Messages

- Open Messaging from the profile menu.
- Send a message to an exact selected user account.
- Open the inbox and read/archive a message.

## Notifications

- Open Notification Center.
- Test filter chips and search.
- Mark a notification read.
- Dismiss a notification.
- Open linked items where available.

## Rooms / Beds And Sections

- Open Rooms / Beds.
- Confirm section table/list loads.
- Add, edit, and deactivate a section as Admin.
- Add, edit, and deactivate a room.
- Assign, move, and remove a patient from a room.
- Confirm capacity and inactive-room validation.

## Newborns

- Open Patient Board > Newborns.
- Add a newborn record.
- Link a mother patient ID if available.
- Open the newborn record.
- Generate and open a birth certificate.

## Deceased Records

- Open Patient File for a test patient.
- Mark the patient deceased as Admin/Doctor.
- Open Patient Board > Deceased Patients.
- Open the death record.
- Generate and open a death certificate.

## Certificates

- Open Certificate Registry.
- Filter birth/death and pending/generated records.
- Submit, approve, reject, and reset review status according to role.
- Open related records and generated certificates.

## Medical Files

- Upload TXT, CSV, PDF, and image files for a patient.
- Confirm the Medical Files screen shows metadata and safe preview.
- Confirm unsupported/missing files show a clear message.

## Scheduling

- Create an appointment.
- Create a reminder.
- Mark reminder done/missed from Scheduling or Nurse Work Queue.
- Confirm dashboard counters update after refresh.

## Nurse Work Queue

- Open Nurse Work Queue as Admin/Doctor/Nurse.
- Confirm overdue/upcoming reminders and critical alerts are listed.
- Mark a reminder done or missed.
- Open linked patient/scheduling/alert screens.

## Backup / Export

- Open Backup / Export as Admin.
- Create a backup ZIP.
- Export patients, alerts, audit logs, medications, and scheduling CSVs.
- Preview a backup ZIP and confirm restore remains preview-only.

## Audit Logs

- Open Audit Logs as Admin.
- Confirm login, logout, alerts, patient writes, room/section writes, certificate actions, and backup/export actions appear.

## Final Check

- Run a source search for old text-storage runtime helpers and migration commands.
- Confirm no active text-file storage package remains.
- Confirm `Main` is the only launcher needed for normal use.
