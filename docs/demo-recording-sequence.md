# ClinicPulse Demo Recording Sequence

Target length: 2.5 to 3 minutes.

## Demo Account

- Username: `admin`
- Password: `admin123`
- Role: `ADMIN`

## Main Demo Patient

- Patient: `100000002` - Lina Mansour
- Reason: clean patient profile, current vitals, visit history, alert, appointment, medical file, billing record, messages, and notifications.

## Recording Flow

| Time | Page | Action | Expected Result |
| --- | --- | --- | --- |
| 0:00-0:20 | Login | Log in as `admin` / `admin123`. | Dashboard opens with no leftover login error message. |
| 0:20-0:45 | Dashboard | Show top cards, patient flow chart, recent alerts, and latest vitals. | Counts show 8 patients, today's appointments, active alerts, and recent vitals. |
| 0:45-1:10 | Patients | Search for `Lina`, clear search, then filter if desired. | Patient table stays clean and responsive. |
| 1:10-1:35 | Patient Profile | Open Lina Mansour. | Personal info, visits, vitals, alerts, and medical files load without empty/broken sections. |
| 1:35-2:00 | Vitals | From Patients or Profile, add normal vital: Heart Rate `82` bpm. Then add abnormal vital: Heart Rate `126` bpm. | Normal vital saves. Abnormal vital saves and creates a critical alert popup/notification. |
| 2:00-2:20 | Appointments | Open Appointments, briefly show 5 appointments with scheduled/completed/cancelled statuses. | Table shows clean clinic appointment records. New Appointment button is available. |
| 2:20-2:35 | Medical Records | Open Medical Records and select Lina's TXT file. | Metadata and safe preview are visible. |
| 2:35-2:45 | Billing | Open Billing / Payments. | Three invoices appear: unpaid, paid, and cancelled. Create Invoice button is available. |
| 2:45-2:55 | Staff Management | Open Staff Management. | Roles shown: Admin, Doctor, Nurse, Secretary. Add Staff/Edit actions are available. |
| 2:55-3:00 | Notifications/Profile | Open Alerts or top notification badge, then Profile / Settings and Logout. | Unread counters are visible, Profile opens, and Logout returns to Login. |

## Prepared Demo Data

- Patients: 8 clean urgent care records.
- Main patient: Lina Mansour (`100000002`).
- Vitals: normal and abnormal readings are present.
- Alerts: active warning/critical alerts plus one acknowledged warning.
- Appointments: 5 records for today.
- Medical file: `data/uploads/100000002/lina-mansour-visit-summary.txt`.
- Billing: 3 invoices with `UNPAID`, `PAID`, and `CANCELLED` statuses.
- Staff: 4 active users with roles `ADMIN`, `DOCTOR`, `NURSE`, `SECRETARY`.
- Messages/notifications: clean unread items for the admin account.

## Values To Enter During Recording

Normal vital:

- Patient: Lina Mansour
- Vital Type: Heart Rate
- Value: `82`
- Unit: bpm

Abnormal vital:

- Patient: Lina Mansour
- Vital Type: Heart Rate
- Value: `126`
- Unit: bpm
- Expected result: vital saves, critical alert popup appears, Alerts/Notifications count updates after refresh/navigation.

## Recovery Steps

- If login fails: use lowercase `admin` and password `admin123`.
- If a page looks stale: navigate away and back using the sidebar.
- If the abnormal vital does not create a visible popup: open Alerts and filter by patient `100000002`.
- If Medical Records upload is not needed: use the prepared Lina Mansour TXT file already in the list.
- If the app is too bright/dark for recording: use the top-bar Light/Dark toggle before starting the main flow.
