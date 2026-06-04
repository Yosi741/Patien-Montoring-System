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

## Demo Accounts

Use these local SQLite demo accounts:

| Username | Password | Role | Section |
| --- | --- | --- | --- |
| `yasen` | `demo123` | ADMIN | All |
| `doctor_demo` | `demo123` | DOCTOR | ER |
| `nurse_demo` | `demo123` | NURSE | Maternity |

These accounts are presentation-only. Passwords are stored with the app password hashing utility.

## Clean Demo Departments And Rooms

Departments:

- ER
- Surgery
- Internal Medicine
- Maternity
- Pediatrics
- Cardiology

Rooms:

- ER-101
- SUR-201
- INT-301
- MAT-401
- PED-501
- CAR-601

## Demo Patient IDs

All demo patient and newborn IDs are exactly 9 digits.

| ID | Person | Workflow |
| --- | --- | --- |
| `100000001` | John Carter | Normal adult patient |
| `100000002` | Sara Haddad | Critical patient with active alert |
| `100000003` | Omar Nasser | Emergency patient with active alert |
| `100000004` | Lina Mansour | Surgery patient and medication demo |
| `100000005` | Mariam Saleh | Mother patient in Maternity |
| `100000006` | Adam Saleh | Newborn record linked to mother |
| `100000007` | Nabil Khoury | Deceased patient with death certificate |

## Demo Medication Rules

The clean demo catalog includes:

- Aspirin
- Ibuprofen
- Amoxicillin
- Metoprolol
- Vancomycin
- Norepinephrine

Interaction rules:

- Ibuprofen + Aspirin = WARNING
- Aspirin + Norepinephrine = DANGEROUS

These are demo decision-support rules only, not medical diagnosis or real prescribing guidance.

## Clean Demo Reminders And Notifications

Seeded reminders:

- Checkup: Heart Rate, Blood Pressure, CBC, CRP
- Medication review reminder
- Nurse follow-up task

Seeded notifications:

- Critical vital alert notification
- Pending checkup notification
- Certificate generated notification

## Medical Files Demo State

Medical file records are clean and currently empty. Old physical upload files were archived before the reset. The Medical Files page should open normally and will show new uploads after a fresh upload creates a SQLite metadata row.

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
- 2:00-5:00: Patient Board, Patient File, and vitals alert using Sara Haddad (`100000002`) or Omar Nasser (`100000003`).
- 5:00-8:00: Medication catalog and medication safety checks using Lina Mansour (`100000004`).
- 8:00-10:00: Scheduling and Nurse Work Queue using the checkup reminder for Sara Haddad.
- 10:00-12:00: Rooms / Beds and department workflow with the clean six-room layout.
- 12:00-14:00: Newborn/deceased certificate workflows using Adam Saleh (`100000006`) and Nabil Khoury (`100000007`).
- 14:00-15:00: Audit Logs, limitations, and future extensions.

## Demo Notes

- Medication safety rules are demo decision-support rules, not medical diagnosis.
- Certificates are local prototype documents, not official legal or government certificates.
- External email, SMS, cloud backup, real Bluetooth devices, and AI diagnosis are future extensions.
