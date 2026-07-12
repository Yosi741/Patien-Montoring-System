# Data Storage

ClinicPulse uses a local SQLite database as the source of truth.

## Active Runtime Data

```text
data/
  smart_patient_monitoring.db
  uploads/
  profile_photos/
```

- `data/smart_patient_monitoring.db` stores clinic data.
- `data/uploads/` stores uploaded patient files.
- `data/profile_photos/` stores staff profile photos.

## SQLite Tables

The active demo database stores:

- users
- user profiles
- patients
- patient visits
- vital readings
- alerts
- appointments
- medical file metadata
- billing records
- notifications
- messages

Physical uploaded files are normal local files. SQLite stores their metadata and path.

## Removed Legacy Storage

The current app no longer uses old text files for runtime storage. Do not add new runtime reads or writes to legacy `.txt` data files.

## Local Demo Scope

This is a local desktop demo. It does not use cloud storage, remote databases, external payment services, or advanced encryption.
