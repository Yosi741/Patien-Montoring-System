# Data Storage

ClinicPulse uses **SQLite** as its primary local database.

All application data is stored locally, providing a lightweight and portable solution that does not require an external database server.

---

# SQLite Database

The main database file is located at:

```text
data/smart_patient_monitoring.db
```

SQLite stores the application's operational data, including:

- Users
- Patients
- Appointments
- Medical Files metadata
- Billing records
- Messages
- Notifications

SQLite is the single source of truth for all application data.

---

# Medical Files

Uploaded medical files are stored locally under:

```text
data/uploads/
```

The application stores only the file metadata inside SQLite, including:

- Patient ID
- Original file name
- Stored file path
- File category
- Upload date
- Uploaded by
- Notes

The physical files remain on disk while SQLite keeps track of their information.

---

# Runtime Data

The application reads and writes data directly through SQLite using the DAO layer.

Data flow:

```text
JavaFX View
        │
        ▼
Controller
        │
        ▼
Service / DAO
        │
        ▼
SQLite Database
```

Controllers never communicate directly with the database.

---

# Local Storage Structure

```text
data/
│
├── smart_patient_monitoring.db
└── uploads/
```

- `smart_patient_monitoring.db` contains all application data.
- `uploads/` stores medical files uploaded through the application.

---

# Why SQLite?

SQLite was selected because it provides:

- Lightweight local database
- No external server required
- Fast data access
- SQL querying support
- Easy deployment
- Reliable local storage
- Simple backup and portability

---

# Prototype Evolution

The first prototype stored information inside plain text files.

As the project grew, text files became difficult to maintain because they made searching, updating, and organizing data more complicated.

The project was migrated to SQLite to provide a structured and scalable storage solution.

The current version no longer uses text files for runtime data.

SQLite is now the only active data storage used by the application.