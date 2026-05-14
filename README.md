# Smart Patient Monitoring System

A Java Swing hospital-style patient monitoring platform for managing patients, recording vital signs, viewing live ICU-style dashboards, uploading medical files, and generating rule-based clinical advice notes.

## Features

- Java Swing desktop GUI with a soft blue, white, and light gray hospital dashboard style
- Login system with Admin, Doctor, and Nurse roles
- Patient management with add, edit, delete, search, and risk filtering
- Persistent text-file storage in `data/`
- Vital sign recording for temperature, heart rate, blood pressure, and oxygen level
- ICU-style patient dashboard with animated ECG panel and live vital cards
- ECG standby mode until a simulated or future real ECG monitor is connected
- Normal, Warning, Critical, and No Data risk status analysis
- Reliable alarm state management using ACTIVE, ACKNOWLEDGED, STOPPED, and RESOLVED states
- Critical alert alarm using `resources/sounds/alarm.wav` with manual Stop Alarm control
- Smart device connector architecture with a simulated Bluetooth monitor adapter
- Device registry in `data/devices.txt`
- Permanent vital-sign history in `data/vitals_history.txt`
- Manual and device vital records store source, staff/device ID, serial number, and timestamp
- Hospital sections, room ranges, and room capacity checks
- Sensitive patient medical history with diagnoses, visits, medications, allergies, family history, and files
- Medical file upload linked to each patient
- Rule-based AI advice from current vitals, trends, history, medications, allergies, and files
- Confirmation step before AI-extracted file information is saved to the patient record
- Death pronouncement report generation for authorized staff
- Newborn birth certificate report generation for authorized staff
- Audit logs for major clinical and admin actions
- User management with roles and assigned hospital sections

## User Roles

- **System Admin / Admin**: full technical access, user management, audit logs
- **Hospital Director / Chief Medical Officer**: broad hospital visibility and sensitive review access
- **Department Head / Doctor**: section-based patient access, vitals, history, AI advice, and death pronouncement where authorized
- **Nurse**: section-based patient access, vitals, monitoring, uploads, AI advice, and newborn registration
- **Technician**: device and vitals support
- **Receptionist**: limited patient-registration and certificate support

## Login Examples

This workspace currently contains these users in `data/users.txt`:

- `YasenSalhab` / `2005` / Admin
- `Q2` / `1234` / Doctor
- `Q1` / `1234` / Nurse
- `Q3` / `1234` / Admin

If `data/users.txt` does not exist, the app creates these default users:

- `admin` / `1234` / Admin
- `doctor` / `1234` / Doctor
- `nurse` / `1234` / Nurse
- `dr_ahmad` / `1234` / Doctor
- `nurse_lina` / `1234` / Nurse

## How To Run

Open the project in IntelliJ IDEA and run `src/Main.java`.

From a terminal inside the project folder:

```bash
javac -d out/production/untitledSmartPatientMonitoringSystem src/Main.java src/ai/*.java src/alerts/*.java src/database/*.java src/devices/*.java src/gui/*.java src/logs/*.java src/managers/*.java src/models/*.java src/users/*.java
java -cp out/production/untitledSmartPatientMonitoringSystem Main
```

## Medical File Uploads

Staff can upload patient-related files from the patient list or patient dashboard. Uploaded files are copied into:

```text
data/files/{patientId}/
```

Metadata is recorded in:

```text
data/medical_files.txt
```

Supported upload examples include blood test results, medical reports, medication notes, doctor notes, TXT, CSV, DOC/DOCX, and PDF files. TXT and CSV files are scanned automatically. PDF and document files are stored for manual review without breaking the project.

## AI Advice Notes

Uploaded TXT and CSV files are scanned with rule-based logic for keywords and abnormal values such as high glucose, low oxygen, high CRP, high WBC, fever, high blood pressure, and low hemoglobin.

Generated notes are stored in:

```text
data/ai_notes.txt
```

The patient dashboard shows the latest AI advice notes for the selected patient.

## Device And Vital History

The app now separates device readings from manual readings:

- Manual vitals include the staff username from `Session`
- Device vitals include device ID, serial number, name, and type
- The simulated Bluetooth monitor is stored as a connected medical device
- Future real Bluetooth integration can replace `BluetoothMedicalDeviceAdapter`

## Sections And Rooms

Patients belong to a hospital section such as ER, Cardiology, Surgery, ICU, Pediatrics, or Internal Medicine. Rooms are limited by section range and capacity. If a room is full, the app blocks the assignment.

## Certificates

Generated reports are stored under:

```text
data/certificates/death/
data/certificates/birth/
```

## Disclaimer

The AI analysis in this project is rule-based and intended for educational use only. It is not real medical AI and must not be used for diagnosis, treatment decisions, or clinical care.
