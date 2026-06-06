# Data Storage

The Smart Patient Monitoring System is JavaFX + SQLite. SQLite is the source of truth for operational data.

## SQLite Database

The database file is stored at:

```text
data/smart_patient_monitoring.db
```

SQLite stores patients, users, vitals, alerts, medications, medication events, reminders, appointments, audit logs, notifications, certificate metadata, newborn/deceased records, and medical file metadata.

The `medical_files` table is active. It stores uploaded-file metadata such as patient ID, original filename, stored path, category, uploader, upload time, notes, size, and extracted summary. The physical file stays under `data/uploads/`.

Inactive experimental modules such as AI recommendations and medical-device/Bluetooth registry are not part of the active presentation schema.

## Uploaded Files

Physical uploaded medical files are stored under:

```text
data/uploads/
```

The Medical Files screen does not scan this folder as its source of truth. It loads file metadata from the SQLite `medical_files` table, then uses the stored path for preview/open actions.

Uploaded medical files are normal local files. They are not converted into database rows; SQLite stores their metadata and file path.

## Generated Files

Generated local certificates are stored under:

```text
data/generated/
```

SQLite stores the certificate metadata and generated file path. The generated HTML certificate files are local file outputs, not database records by themselves.

Presentation certificate folders:

```text
data/generated/birth-certificates/
data/generated/death-certificates/
```

Generated certificates are normal local HTML files created from SQLite records.

## Backups

Optional local backup ZIP files are stored under:

```text
data/backups/
```

Manual backups may copy the SQLite database and uploaded files outside the app. The inactive Backup / Export UI module is not part of the presentation build.

## Archived Prototype Concepts

Older prototype text-file storage is documented under:

```text
docs/archive/
```

Those text files are not used at runtime and are no longer kept inside the active `data/` folder. The active runtime should not read or write `patients.txt`, `users.txt`, `vitals_history.txt`, `medical_files.txt`, or similar prototype files.
