# Code Structure

The current application architecture is:

```text
JavaFX View
  -> Controller
  -> Service
  -> Data_Access_Object
  -> SQLite
```

Controllers should coordinate UI state and call ui.javafx.services. They should not contain SQL. Services hold business rules, validation, permission checks, and workflow logic. DAOs contain SQLite access.

## Packages

### `database/`

Database startup and schema code.

- `DatabaseManager`: opens SQLite connections.
- `SchemaInitializer`: creates and migrates tables safely.
- Seed/demo database utilities live here when needed.

### `Data_Access_Object/`

SQLite data access classes. These classes query and update database tables and map rows into DTOs/records.

Examples include patient, alert, medication, reminder, notification, medical file, certificate, audit log, and room DAOs.

Section and room data is stored in SQLite. Sections represent hospital departments. Rooms belong to sections, include capacity and status, and patient room assignments are kept on patient records.

### `ui.javafx.services/`

Business logic and workflow ui.javafx.services.

Services validate input, enforce permissions, coordinate multiple DAOs, run rule-based decision-support checks, and write audit events.

Room and section workflows follow the same service pattern: controllers collect form input, ui.javafx.services validate capacity/status/permissions, and DAOs persist changes in SQLite.

### `ui/javafx/controllers/`

JavaFX screen and dialog controllers.

Controllers load screen data, bind UI controls, respond to button clicks, and call ui.javafx.services. SQL should stay out of controllers.

### `ui/javafx/views/`

FXML screens and dialogs used by the JavaFX UI.

### `ui/javafx/styles/`

Theme files for the JavaFX application. The presentation build currently uses the Hospital Navy styling.

### `ui/javafx/helpers/`

Reusable UI and workflow helpers, including validation helpers, dialog helpers, notification helpers, permission helpers, audit helpers, and safe file-opening helpers.

### `models/`

Domain model classes such as patient, user, medication, alert, and medical file objects.

## Runtime Data

Runtime data flows through SQLite DAOs. Uploaded files and generated certificates are stored on disk, but their metadata is tracked in SQLite.

## Security Wording For Demo

The presentation build uses a local login system with role-based access for Admin, Doctor, Nurse, and Staff users. Passwords are not stored as plain text. Uploaded files and generated certificates remain normal local files, and the presentation focuses on hospital workflow and layered architecture rather than advanced cybersecurity.
