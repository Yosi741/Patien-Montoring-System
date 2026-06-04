Legacy Text Storage Archive
===========================

These text files are archived prototype storage from the earlier version of the Smart Patient Monitoring System.

The JavaFX application now uses SQLite as the runtime source of truth. These files are not read or written by the current runtime and are kept only for historical reference during presentation or migration review.

Current runtime storage:

- SQLite database: `data/smart_patient_monitoring.db`
- Uploaded medical files: `data/uploads/`
- Generated certificates: `data/generated/`
- Optional backups: `data/backups/`

Do not restore these text files to the root `data/` folder for normal application use.
