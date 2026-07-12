# Presentation Demo Guide

# What The System Is

ClinicPulse is a JavaFX + SQLite desktop application developed as a final preparatory project.

The system demonstrates how a modern clinic management application can organize daily clinic workflows through a clean user interface, layered architecture, and a local SQLite database.

The current prototype includes:

- User Login
- Dashboard
- Patient Management
- Appointments
- Medical Files
- Billing
- Staff Management
- Messages
- Notifications
- User Profile

The goal of the project is to centralize clinic information in one desktop application and simplify everyday workflows.

---

# Main Demo Workflow

Follow this order during the presentation.

1. Login
    - Login as Admin.
    - Explain the local SQLite authentication.
    - Show that different roles receive different permissions.

2. Dashboard
    - Explain the dashboard.
    - Show patient counters.
    - Show active notifications.
    - Explain that dashboard values are loaded from SQLite.

3. Patients
    - Open the Patients page.
    - Search for a patient.
    - Open Patient Details.
    - Add a new patient (or demonstrate the form).

4. Appointments
    - Create a new appointment.
    - Edit an existing appointment.
    - Show appointment status.

5. Medical Files
    - Open Medical Files.
    - Show existing records.
    - Demonstrate uploading a new medical file.

6. Billing
    - Open Billing.
    - Create an invoice.
    - Show invoice status.

7. Staff Management
    - Open Staff Management.
    - Explain user roles.
    - Demonstrate adding or editing a staff member.

8. Messages
    - Open the Messages page.
    - Show internal communication.

9. Notifications
    - Open Notifications.
    - Explain how the system displays important events.

10. User Profile
    - Open Profile.
    - Show editable personal information.

---

# Demo Accounts

Use the following demo accounts.

| Username | Password | Role |
|----------|----------|------|
| admin | admin123 | ADMIN |
| doctor | doctor123 | DOCTOR |
| nurse | nurse123 | NURSE |
| secretary | secretary123 | SECRETARY |
| staff | staff123 | STAFF |

The system uses a local SQLite login system with role-based permissions.

---

# Demo Patient

Use one patient consistently throughout the presentation.

Example:

| Patient ID | Name |
|------------|------|
| 100000001 | John Carter |

Using one patient makes the workflow easier to understand.

---

# Application Architecture

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

Layer responsibilities:

• View
Defines the user interface.

• Controller
Receives user actions and updates the interface.

• Service
Contains business logic and application workflow.

• DAO
Reads and writes SQLite data.

• SQLite
Stores all application information.

---

# Runtime Data

Application data is stored locally using SQLite.

Medical files are stored on disk while their metadata is stored inside SQLite.

```text
data/
    smart_patient_monitoring.db
    uploads/
```

---

# Suggested Presentation Flow (10–12 Minutes)

### 0:00 – 2:00

• Project introduction

• Problem and solution

• Login

---

### 2:00 – 4:00

Dashboard

Explain:

- Total Patients
- Dashboard counters
- Notifications

---

### 4:00 – 6:00

Patients

- Search
- Open Patient
- Add Patient

---

### 6:00 – 7:30

Appointments

Create or edit an appointment.

---

### 7:30 – 9:00

Medical Files

Show records and upload demonstration.

---

### 9:00 – 10:30

Billing

Create an invoice.

Explain billing workflow.

---

### 10:30 – 11:30

Staff Management

Explain roles and permissions.

---

### 11:30 – 12:00

Messages

Notifications

Profile

Questions

---

# Technologies

Development technologies:

- Java 17
- JavaFX
- FXML
- CSS
- SQLite
- IntelliJ IDEA

Development tools:

- GitHub
- ChatGPT
- Codex
- Gemini (logo and design assistance)

---

# Notes

This project is a desktop prototype.

The application focuses on:

- Software architecture
- JavaFX desktop development
- SQLite database integration
- User workflow
- Role-based permissions

The project does not include:

- Cloud synchronization
- External email services
- SMS integration
- Medical device integration
- AI decision support

These can be implemented as future extensions.

---

# Future Improvements

Possible future work includes:

- Cloud database support
- Mobile companion application
- Email notifications
- SMS reminders
- Integration with hospital information systems
- Medical device connectivity
- AI-assisted clinical recommendations
