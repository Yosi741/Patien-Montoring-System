# Project Structure

## Architecture

The application follows a layered architecture that separates the user interface, business logic, and data access.

```text
JavaFX View (FXML)
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

Each layer has a clear responsibility:

- **View (FXML)** – Defines the user interface.
- **Controller** – Handles user interaction and updates the UI.
- **Service** – Contains business logic, validation, and workflow.
- **DAO (Data Access Object)** – Reads and writes data in SQLite.
- **SQLite** – Stores all application data.

---

# Main Packages

## `database/`

Contains the database infrastructure.

- `DatabaseManager` – Opens and manages SQLite connections.
- `SchemaInitializer` – Creates and updates database tables.

---

## `dao/`

Contains all SQLite Data Access Objects.

DAOs are responsible for:

- Reading data
- Saving data
- Updating records
- Deleting records

Examples include:

- Patient DAO
- Appointment DAO
- User DAO
- Billing DAO
- Medical File DAO
- Notification DAO

---

## `services/`

Contains the business logic.

Services:

- Validate user input
- Execute application workflows
- Coordinate multiple DAOs
- Apply application rules

Controllers should never communicate directly with SQLite.

---

## `controllers/`

JavaFX Controllers.

Responsibilities:

- Receive user actions
- Validate basic UI input
- Call Services
- Update the interface

Controllers do not execute SQL.

---

## `views/`

FXML files used to build the JavaFX user interface.

Examples:

- Login
- Dashboard
- Patients
- Appointments
- Billing
- Staff Management
- Medical Files
- Messages
- Notifications
- Profile

---

## `styles/`

Contains JavaFX CSS files used for the application's visual design.

---

## `helpers/`

Reusable helper classes such as:

- Dialog helpers
- Validation helpers
- Permission helpers
- File helpers
- UI utilities

---

## `models/`

Contains the application's data models.

Examples:

- User
- Patient
- Appointment
- BillingRecord
- MedicalFile
- Notification

---

# Runtime Data

All application data is stored locally using SQLite.

Medical files remain on disk while their information is stored in the database.

---

# Login & Permissions

The application includes a local login system.

Supported user roles:

- Admin
- Doctor
- Nurse
- Secretary
- Staff

Each role has different permissions that determine which pages and actions are available inside the system.

---

# Technologies

- Java 17
- JavaFX
- FXML
- CSS
- SQLite
- IntelliJ IDEA
- GitHub