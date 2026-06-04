# Presentation Demo Guide

## What The System Is

Smart Patient Monitoring System is a JavaFX + SQLite hospital patient monitoring prototype. It demonstrates how a desktop hospital workflow can organize patients, vitals, medications, rooms, reminders, notifications, certificates, staff accounts, and audit logs through a layered architecture.

## Main Demo Workflows

1. Login and show the Hospital Navy interface.
2. Open Dashboard and explain the core counters: patients, critical/emergency cases, active alerts, births, deceased records, pending certificates, and vitals today.
3. Open Patient Board and show active, deceased, and newborn subsections.
4. Add or edit a patient record.
5. Enter a dangerous vital and show the alert popup, notification, patient priority update, and audit trail.
6. Open Medications and demonstrate catalog-based medication safety rules.
7. Record medication given and explain dose, interval, override, and interaction checks as demo decision support.
8. Open Scheduling and Nurse Work Queue to show reminders and staff tasks.
9. Open Rooms / Beds to explain departments, rooms, and patient assignment.
10. Open Newborns through Patient Board and show a newborn linked to a mother.
11. Open Deceased Patients through Patient Board and generate a local certificate.
12. Open Notifications for alerts and system events.
13. As admin, open Staff / Users and Audit Logs.

## Disabled Features And Why

- AI recommendations are disabled because they add complexity that is not needed for a 15-minute board presentation.
- Bluetooth and medical device integration are disabled because no real hospital pilot or device integration is connected yet.
- Backup/export is hidden in demo mode to keep the presentation focused on clinical workflow.
- Medical file management is hidden from the sidebar in demo mode unless it is re-enabled for a dedicated file-management presentation.

The code and database tables for hidden modules are retained for future extension.

## Architecture

```text
JavaFX UI
  -> Controllers
  -> Services
  -> SQLite DAOs
  -> SQLite database
```

Controllers handle UI events and validation display. Services contain workflow decisions. DAOs contain SQLite access. The runtime data source is the local SQLite database at `data/smart_patient_monitoring.db`.

## Evolution

The project started as a simpler Swing and text-file prototype. It was migrated to JavaFX and SQLite to provide a cleaner user interface, better separation of concerns, searchable records, stronger auditability, and safer local persistence.

## Suggested 15-Minute Flow

- 0:00-2:00: Login, architecture, and Dashboard.
- 2:00-5:00: Patient Board, Patient File, and vitals alert.
- 5:00-8:00: Medication catalog and medication safety checks.
- 8:00-10:00: Scheduling and Nurse Work Queue.
- 10:00-12:00: Rooms / Beds and department workflow.
- 12:00-14:00: Newborn/deceased certificate workflows and Notifications.
- 14:00-15:00: Audit Logs, limitations, and future extensions.

## Demo Notes

- Medication safety rules are demo decision-support rules, not medical diagnosis.
- Certificates are local prototype documents, not official legal or government certificates.
- External email, SMS, cloud backup, real Bluetooth devices, and AI diagnosis are future extensions.
