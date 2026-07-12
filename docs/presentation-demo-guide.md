# Presentation Demo Guide

ClinicPulse is a JavaFX + SQLite urgent care clinic desktop application. It demonstrates how a small clinic can manage patient records, vitals, alerts, appointments, billing, medical files, staff users, messages, and notifications in one local system.

## Demo Accounts

| Username | Password | Role |
| --- | --- | --- |
| `admin` | `admin123` | `ADMIN` |
| `doctor` | `doctor123` | `DOCTOR` |
| `nurse` | `nurse123` | `NURSE` |
| `secretary` | `secretary123` | `SECRETARY` |

## Clean Demo Patients

The clean demo database uses 9-digit patient IDs, including:

- `100000001` - Omar Khalil
- `100000002` - Lina Mansour
- `100000003` - Yara Nasser
- `100000006` - Samir Darwish

## Suggested Demo Flow

1. Login as `admin`.
2. Open the dashboard and explain the urgent care overview.
3. Open Patient Management and search for a patient.
4. Open a Patient File and show vitals or alerts.
5. Create or edit an appointment.
6. Open Medical Records and explain uploaded file metadata.
7. Open Billing and show local invoice status.
8. Open Staff Management and explain the four roles.
9. Open Messages and Notifications.
10. Open Profile / Settings and logout.

## Architecture Talking Point

```text
FXML View
    -> Controller
    -> Service
    -> DAO
    -> SQLite
```

## Runtime Data

```text
data/
  smart_patient_monitoring.db
  uploads/
  profile_photos/
```

SQLite stores the records. Uploaded files and profile photos are local files.

## Project Scope

This is a local desktop demo. It does not include cloud sync, SMS, external email services, online payment processing, AI decision support, medical device integration, or hospital ward modules.
