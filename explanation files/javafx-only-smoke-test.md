# JavaFX-Only Smoke Test Checklist

Use this checklist after code changes, UI fixes, database updates, or before the final presentation.

---

## Startup

- Compile all Java source files.
- Copy FXML, CSS, images, and other JavaFX resources into the output folder if required.
- Run `Main`.
- Confirm that the JavaFX Login screen opens.
- Confirm that no Swing window opens.
- Confirm that the application connects successfully to the local SQLite database.

---

## Login

- Login using the Admin demo account.
- Confirm that valid credentials open the Dashboard.
- Confirm that invalid credentials show a clear error message.
- Confirm that empty username or password fields show validation feedback.
- Test the Clear button:
    - Username field is cleared.
    - Password field is cleared.
    - Status/error message is cleared.
- Test Forgot Password if it is still part of the current application.
- Confirm that Logout returns the user to the Login screen.

---

## Application Shell

- Confirm that the sidebar loads correctly.
- Confirm that all visible navigation buttons open the correct pages.
- Confirm that the top-right profile menu opens.
- Confirm that the following menu items are readable:
    - Profile / Settings
    - Messages
    - Logout
- Confirm that the notification icon and unread count display correctly.
- Confirm that the current theme is applied correctly.
- Test both Light Mode and Dark Mode if both are available.

---

## Dashboard

- Confirm that all dashboard cards load without errors.
- Confirm that patient counters are displayed.
- Confirm that appointment counters are displayed if available.
- Confirm that billing or notification counters are displayed if available.
- Confirm that dashboard numbers are loaded from SQLite and are not hardcoded.
- Confirm that charts and tables display correctly.
- Confirm that dashboard buttons or quick links open the correct pages.
- Refresh the Dashboard and verify that updated database values appear.

---

## Patients

- Open the Patients page.
- Confirm that the patient table loads from SQLite.
- Test search by:
    - Patient name
    - Patient ID
- Test available filters.
- Confirm that ComboBox selected values are visible in Light Mode.
- Confirm that ComboBox dropdown values are readable.
- Select a patient and test available actions:
    - View Patient
    - Edit Patient
    - Delete or deactivate patient, if available
- Confirm that buttons are enabled or disabled according to the selected patient and logged-in role.

---

## Add Patient

- Open the Add Patient form.
- Add a test patient with valid information.
- Confirm that required fields are validated.
- Confirm that invalid values show clear error messages.
- Confirm that ComboBox values are visible.
- Save the patient.
- Confirm that the new patient is stored in SQLite.
- Confirm that the patient appears in the Patients table after refresh.

---

## Edit Patient

- Select an existing patient.
- Open the Edit Patient form.
- Change one or more fields.
- Save the changes.
- Confirm that SQLite is updated.
- Confirm that the patient table shows the new information.

---

## Patient Details

- Open a patient record.
- Confirm that patient information loads correctly.
- Confirm that available medical or administrative details are displayed.
- Confirm that action buttons work.
- Confirm that navigation back to the Patients page works.

---

## Appointments

- Open the Appointments page.
- Confirm that appointments load from SQLite.
- Test appointment filters and ComboBoxes.
- Create a new appointment.
- Confirm that required fields are validated.
- Edit an existing appointment.
- Change appointment status if supported.
- Delete or cancel an appointment if supported.
- Confirm that the updated appointment appears in the table.
- Confirm that appointment data persists after restarting the application.

---

## Medical Files

- Open the Medical Files page.
- Confirm that the medical-file table loads.
- Upload a supported file.
- Confirm that the file metadata is saved in SQLite.
- Confirm that the physical file is stored under:

```text
data/uploads/
```

- Open or preview a stored file.
- Confirm that a missing or unsupported file shows a clear message.
- Delete a medical-file record if this action is available.
- Confirm that the page refreshes correctly after changes.

---

## Billing

- Open the Billing page.
- Confirm that billing records load from SQLite.
- Test search and available filters.
- Create a new invoice or billing record.
- Confirm that patient and service selections work.
- Confirm that amount and required fields are validated.
- Edit an existing billing record if supported.
- Change payment status if supported.
- Delete a billing record if supported.
- Confirm that the table updates after each action.

---

## Staff Management

- Login as Admin.
- Open Staff Management.
- Confirm that the user table loads from SQLite.
- Confirm that roles shown in the system match the current roles:
    - Admin
    - Doctor
    - Nurse
    - Secretary
- Add a new user.
- Edit an existing user.
- Change the user's role or account status if supported.
- Delete or deactivate a user if supported.
- Confirm that role ComboBoxes are readable in Light Mode.
- Confirm that non-Admin users cannot access restricted staff-management actions.

---

## Roles And Permissions

Test the application with each available role:

### Admin

- Confirm access to all administrative pages.
- Confirm access to Staff Management.
- Confirm access to patient, appointment, billing, notification, and profile workflows.

### Doctor

- Confirm access only to the pages and actions allowed for Doctors.
- Confirm restricted Admin actions are not visible or not accessible.

### Nurse

- Confirm access only to the pages and actions allowed for Nurses.
- Confirm restricted Admin actions are not visible or not accessible.

### Secretary

- Confirm access to the administrative/front-desk workflows allowed for Secretaries.
- Confirm restricted clinical or Admin actions are not visible or not accessible.

---

## Messages

- Open Messages from the profile menu.
- Confirm that the inbox loads.
- Select an exact recipient account.
- Send a test message.
- Confirm that the message appears in the correct mailbox.
- Open and read a message.
- Archive or delete a message if supported.
- Confirm that message priority labels are readable.
- Confirm that role restrictions are applied where relevant.

---

## Notifications

- Open Notification Center from the top bar.
- Confirm that notifications load from SQLite.
- Test search and available filters.
- Mark a notification as read.
- Dismiss or delete a notification if supported.
- Confirm that the unread counter updates.
- Confirm that notification text and status labels are readable in both Light Mode and Dark Mode.

---

## Profile / Settings

- Open Profile / Settings from the top-right profile menu.
- Confirm that the logged-in user's information loads.
- Edit supported profile fields.
- Save changes.
- Confirm that the updates persist in SQLite.
- Test theme switching if available.
- Confirm that profile-menu text is readable in Light Mode.
- Confirm that Logout works.

---

## Light Mode UI Check

Check the following controls across multiple pages:

- Profile dropdown menu
- Menu item text
- Menu hover state
- ComboBox selected value
- ComboBox prompt text
- ComboBox dropdown values
- Text fields
- Table text
- Buttons
- Status labels
- Error messages
- Notification badges

Test at least:

- Staff Management
- Patients
- Appointments
- Billing

Confirm that all text remains readable on light backgrounds.

---

## Dark Mode UI Check

- Confirm that Light Mode fixes did not break Dark Mode.
- Confirm that profile menu items remain readable.
- Confirm that ComboBox selected values and dropdown items remain readable.
- Confirm that tables, buttons, labels, and form fields have sufficient contrast.

---

## SQLite Persistence

- Add or edit a record.
- Close the application.
- Start the application again.
- Confirm that the change still exists.
- Confirm that the application uses:

```text
data/smart_patient_monitoring.db
```

- Confirm that active application data is not loaded from old text files.

---

## Final Compile And Runtime Check

- Regenerate the Java source list if the project uses `sources.txt`.
- Compile the full current project.
- Confirm that compilation passes without errors.
- Run `Main`.
- Confirm that the application starts successfully.
- Confirm that no deleted page appears in the sidebar or menus.
- Confirm that no navigation button opens a removed feature.
- Confirm that no active code depends on old Swing screens.
- Confirm that no active runtime code reads or writes old text-file storage.

---

## Final Presentation Check

Before the presentation:

- Reset or prepare clean demo data.
- Confirm all demo accounts work.
- Confirm the Admin account can access the required pages.
- Confirm one demo patient is available.
- Confirm appointments, billing records, medical files, messages, and notifications are ready.
- Confirm Light Mode and Dark Mode are readable.
- Confirm the presentation PowerPoint opens.
- Confirm all PowerPoint links and Before / After buttons work.
- Keep a PDF copy of the presentation as backup.
- Keep a short demo video as backup in case the live application fails.