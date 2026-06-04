# Data Storage

The Smart Patient Monitoring System is JavaFX + SQLite. SQLite is the source of truth for operational data.

## SQLite Database

The database file is stored at:

```text
data/smart_patient_monitoring.db
```

SQLite stores patients, users, vitals, alerts, medications, medication events, reminders, appointments, audit logs, notifications, certificate metadata, newborn/deceased records, and medical file metadata.

## Uploaded Files

Physical uploaded medical files are stored under:

```text
data/uploads/
```

The Medical Files screen does not scan this folder as its source of truth. It loads file metadata from the SQLite `medical_files` table, then uses the stored path for preview/open actions.

## Generated Files

Generated local certificates are stored under:

```text
data/generated/
```

SQLite stores the certificate metadata and generated file path. The generated HTML certificate files are local file outputs, not database records by themselves.

## Backups

Optional local backup ZIP files are stored under:

```text
data/backups/
```

Backups may include the SQLite database and uploaded files.

## Legacy Text Storage

Old prototype text files are archived under:

```text
data/archive/legacy-text-storage/
```

They are not used at runtime. The active runtime should not read or write `patients.txt`, `users.txt`, `vitals_history.txt`, `medical_files.txt`, or similar prototype files.
