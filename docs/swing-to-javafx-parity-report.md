# Swing to JavaFX Parity Report

Phase 31 audits the current Swing application against the JavaFX implementation added in Phases 1-30. This report is intentionally conservative: a Swing screen is not marked fully replaced unless JavaFX supports the main user workflow, expected navigation, storage path, and permissions closely enough to serve as the default workflow.

Swing is still retained. JavaFX has broad hospital-dashboard coverage, but several certificate, newborn, messaging, notification, and legacy workflow features are not yet at parity.

## Summary

| Category | Count | Meaning |
|---|---:|---|
| Swing classes audited under `src/gui` | 35 | All Java classes in the Swing GUI package |
| Additional Swing UI code outside `src/gui` | 1 | `services.AlarmService` owns a Swing alert dialog |
| Fully replaced in JavaFX | 6 | JavaFX can reasonably own the workflow now |
| Partially replaced in JavaFX | 10 | JavaFX covers important parts, but not full legacy behavior |
| Not replaced yet | 10 | No JavaFX equivalent workflow yet |
| Obsolete / safe to remove later | 9 | Swing-only UI helper/navigation classes after Swing retirement |

## Swing Class Parity

| Swing class | Old feature | JavaFX replacement screen | Parity status | Migration risk | Recommendation |
|---|---|---|---|---|---|
| `AddPatientGUI` | Add patient into legacy text-file storage | Patients / Patient Form | Fully replaced in JavaFX | Medium: JavaFX writes SQLite only, not legacy text files | Keep temporarily until SQLite becomes source of truth |
| `AddVitalSignGUI` | Manual vital entry into legacy patient/vitals files and Swing alarm flow | Patient Detail / Vitals Entry | Partially replaced in JavaFX | Medium: JavaFX persists SQLite vitals and SQLite alerts only; it does not start Swing alarm sounds | Keep temporarily until alert engine is unified |
| `AIAdviceGUI` | Rule-based advice notes for a patient | AI Recommendations / Patient Detail / Clinical Timeline | Partially replaced in JavaFX | Low-medium: JavaFX has richer rule-based AI notes but writes SQLite only | Keep temporarily during storage transition |
| `AppHeader` | Shared Swing hospital header with user/profile/actions | AppLayout top bar / JavaFX shell | Obsolete / safe to remove later | Low once Swing screens are retired | Remove only after Swing retirement |
| `AuditLogGUI` | View legacy text-file audit logs | Audit Logs | Partially replaced in JavaFX | Medium: JavaFX shows SQLite audit logs, not all legacy `data/audit_logs.txt` history | Keep temporarily or migrate legacy audit logs |
| `BirthCertificateGUI` | Birth certificate form, mother lookup, newborn validation, PDF generation | No JavaFX replacement yet | Not replaced yet | High: certificate workflow is presentation-critical and still Swing-only | Implement JavaFX certificate workflow before removing Swing |
| `CardPanel` | Shared Swing card styling helper | JavaFX CSS `panel-card` / layout views | Obsolete / safe to remove later | Low after Swing retirement | Remove only after Swing screens are retired |
| `DashboardGUI` | Swing home dashboard and navigation to legacy screens | Dashboard / AppLayout sidebar | Fully replaced in JavaFX | Medium: JavaFX dashboard is stronger, but still lacks some destination screens | Keep temporarily until missing destinations exist |
| `DeathPronouncementGUI` | Doctor death pronouncement workflow, status update, death certificate generation | No JavaFX replacement yet | Not replaced yet | High: legal/clinical certificate workflow remains Swing-only | Must keep temporarily |
| `DeceasedPatientDetailGUI` | Deceased patient details and certificate access | No JavaFX replacement yet | Not replaced yet | High: no JavaFX deceased patient detail workflow | Must keep temporarily |
| `DeceasedPatientsGUI` | List/search deceased patients | No JavaFX replacement yet | Not replaced yet | High: patient lifecycle parity gap | Must keep temporarily |
| `DeviceManagementGUI` | Legacy device registry, simulated Bluetooth monitor connect/disconnect, device readings | Medical Devices / Patient Detail assigned devices | Partially replaced in JavaFX | High: JavaFX registers/assigns devices only; no simulated connection or ECG live integration | Keep temporarily until device runtime is rebuilt |
| `ECGPanel` | Swing ECG standby/live monitor visual behavior | No JavaFX replacement yet | Not replaced yet | Medium-high: visible monitoring feature missing | Rebuild in JavaFX after device runtime parity |
| `EditPatientGUI` | Edit legacy patient data | Patients / Patient Form | Fully replaced in JavaFX | Medium: JavaFX writes SQLite only | Keep temporarily until SQLite is final storage |
| `FileUploadGUI` | Upload legacy medical files and run basic file analysis | Medical Files / Upload / Preview | Partially replaced in JavaFX | Medium: JavaFX upload is stronger but writes SQLite and `data/uploads/`, not legacy `data/files/` metadata | Keep temporarily until legacy file references are migrated/finalized |
| `FormPanel` | Shared Swing GridBag form helper | JavaFX FXML layouts / validation helpers | Obsolete / safe to remove later | Low after Swing retirement | Remove only after Swing screens are retired |
| `HospitalHeaderPanel` | Compatibility wrapper around Swing `AppHeader` | AppLayout top bar | Obsolete / safe to remove later | Low after Swing retirement | Remove only after Swing screens are retired |
| `LoginGUI` | Swing login using legacy users/session | Login | Fully replaced in JavaFX | Low-medium: JavaFX supports SQLite login with legacy fallback | Keep until JavaFX becomes default launcher |
| `MessagesGUI` | Internal staff messaging | No JavaFX replacement yet | Not replaced yet | High: messaging workflow is absent in JavaFX | Implement JavaFX messaging before removing Swing |
| `MotherManagementGUI` | Mother records search/add/manage | No JavaFX replacement yet | Not replaced yet | High: birth workflow dependency missing | Must keep temporarily |
| `NavigationManager` | Swing dashboard reuse/return navigation | AppShell / AppNavigator | Obsolete / safe to remove later | Low after Swing retirement | Remove only after Swing screens are retired |
| `NewbornDetailGUI` | Newborn detail view and measurements | No JavaFX replacement yet | Not replaced yet | High: newborn workflow absent | Must keep temporarily |
| `NewbornManagementGUI` | Newborn list/search/dashboard navigation | No JavaFX replacement yet | Not replaced yet | High: newborn workflow absent | Must keep temporarily |
| `NotificationCenterGUI` | Legacy notification inbox, unread/read/clear behavior | No JavaFX replacement yet | Not replaced yet | High: notification center absent in JavaFX | Implement JavaFX notification center before removing Swing |
| `PatientDashboardGUI` | Legacy patient bedside-style dashboard, vitals cards, ECG, files, AI, history | Patient Detail / Clinical Timeline / Medical Files / AI Recommendations / Trends | Partially replaced in JavaFX | High: JavaFX covers details/history/trends but not live ECG monitor behavior | Keep temporarily until monitoring parity is confirmed |
| `PatientGUI` | Legacy patient management list, search, filters, actions, dashboard navigation | Patients / Patient Detail | Partially replaced in JavaFX | Medium: JavaFX patient board includes writes, filters, and detail, but storage is SQLite-only | Keep temporarily until final storage cutover |
| `PatientHistoryGUI` | Sensitive/clinical patient history view | Clinical Timeline | Partially replaced in JavaFX | Medium: JavaFX timeline shows migrated history but no dedicated sensitive-history workflow or editing | Keep temporarily |
| `RoomSectionGUI` | Manage sections/rooms, add/edit/delete, occupancy validation | Rooms / Beds | Partially replaced in JavaFX | High: JavaFX room view is read-only; Swing room management still owns edits | Keep temporarily until JavaFX room management exists |
| `StyledButton` | Shared Swing button factory | JavaFX CSS buttons | Obsolete / safe to remove later | Low after Swing retirement | Remove only after Swing screens are retired |
| `StyledTable` | Shared Swing table styling helper | JavaFX TableView + CSS | Obsolete / safe to remove later | Low after Swing retirement | Remove only after Swing screens are retired |
| `UITheme` | Shared Swing colors, fonts, panels | JavaFX light/dark CSS | Obsolete / safe to remove later | Low after Swing retirement | Remove only after Swing screens are retired |
| `UserManagementGUI` | Legacy user management | Staff / Users | Partially replaced in JavaFX | High: JavaFX directory is read-only and has no create/edit user workflow | Keep temporarily |
| `UserProfileGUI` | Legacy user profile display | Profile / Settings | Fully replaced in JavaFX | Low: JavaFX has session context, role badge, permission preview | Keep until JavaFX default launcher |
| `VitalsHistoryGUI` | Patient vital history table | Patient Detail vitals timeline / Clinical Timeline / Trend Chart | Fully replaced in JavaFX | Low-medium: JavaFX view is richer, but legacy history remains text-file backed | Keep until SQLite history is final |
| `WindowSizing` | Shared Swing window sizing helper | AppShell stage sizing / JavaFX layout constraints | Obsolete / safe to remove later | Low after Swing retirement | Remove only after Swing screens are retired |

## Swing UI Outside `src/gui`

| Class | Swing feature | JavaFX replacement | Parity status | Migration risk | Recommendation |
|---|---|---|---|---|---|
| `services.AlarmService` | Swing critical-alert dialog and alarm sound behavior | Alert Center / Dashboard alert widgets | Partially replaced in JavaFX | High: JavaFX stores/acknowledges SQLite alerts but does not control Swing dialogs or sounds | Keep temporarily; unify alert runtime before removing Swing |

## JavaFX Coverage by Target Screen

| JavaFX area | Current coverage | Parity notes |
|---|---|---|
| Dashboard | SQLite counters, priority summary, recent alerts, vitals/scheduling/reminder counters | Strong JavaFX replacement for dashboard presentation |
| Patients | Search, filters, add/edit/discharge SQLite patients | Strong, but SQLite-only |
| Patient Detail | Demographics, alerts summary, vitals timeline, trend charts, AI, medications, files, devices, scheduling links | Strong, missing ECG/live monitor parity |
| Vitals Entry | Manual SQLite vital entry and SQLite alert creation for abnormal values | Partial because Swing sound/dialog alert engine is separate |
| Alerts | SQLite Alert Center, filters, details, acknowledge, drill-down | Partial because JavaFX acknowledgement does not stop Swing sounds/dialogs |
| Clinical Timeline | Vitals, alerts, AI notes, files, medications, history, handover notes | Strong read/detail coverage |
| AI Recommendations | Rule-based risk score and recommendation generation into SQLite `ai_notes` | Strong educational/demo replacement |
| Medications | Medication CRUD and administration events in SQLite | Strong JavaFX workflow |
| Devices | Registration, assignment, deactivation in SQLite | Partial because real/simulated connection runtime is not migrated |
| Scheduling | Appointments and reminders in SQLite | Strong JavaFX workflow |
| Reminders / Work Queue | Local reminder engine, overdue detection, nurse queue | Strong JavaFX workflow |
| Medical Files | Upload, metadata, TXT/CSV/PDF text/image preview, safe open, AI summary note | Strong JavaFX workflow, but storage differs from legacy |
| Rooms / Beds | Read-only occupancy board with fallback from patient fields | Partial because room editing is not migrated |
| Staff Activity | Audit/alert/handover activity overview | New JavaFX capability |
| Staff Directory | Read-only SQLite user directory | Partial replacement for Swing user management |
| Audit Logs | SQLite audit viewer | Partial until legacy audit logs are migrated or archived |
| Backup / Export | Local backup ZIP, CSV exports, restore preview only | New JavaFX capability |
| Profile / Settings | Session context, role badge, permission preview, admin test audit write | Strong JavaFX replacement |

## Remaining Blockers Before Removing Swing

Swing cannot be removed yet. The main blockers are:

- Birth certificate workflow and mother lookup are still Swing-only.
- Death pronouncement, deceased patient list/details, and death certificate workflow are still Swing-only.
- Mother and newborn management screens are still Swing-only.
- Internal messaging is still Swing-only.
- Notification center is still Swing-only.
- Room/section management is read-only in JavaFX; add/edit/delete room workflows remain Swing-only.
- User management create/edit workflows are not yet in JavaFX; JavaFX Staff Directory is read-only.
- Simulated device connection, device readings, and ECG live/standby monitor behavior are not fully migrated.
- JavaFX alert acknowledgement updates SQLite only and does not control Swing alarm sounds/dialogs.
- Some JavaFX write workflows are SQLite-only and do not write back to legacy text-file storage.
- Legacy audit-log and medical-file indexes are not fully migrated/finalized.

## Recommended Next Phases

### Phase 32: JavaFX Default Launcher Preparation

- Add `LegacySwingMain` as the explicit Swing launcher.
- Change `Main` only after a final launch review so JavaFX can become the default entry point.
- Keep Swing runnable from `LegacySwingMain`.
- Update README run commands and any GitHub Actions smoke checks.

### Phase 33: Remaining JavaFX Feature Parity

- Implement JavaFX certificate workflows for birth and death.
- Implement JavaFX mother/newborn management.
- Implement JavaFX notification center and internal messaging.
- Implement JavaFX room/section management writes.
- Implement JavaFX admin user create/edit workflows.
- Implement JavaFX ECG/device runtime parity or intentionally retire the old simulated monitor.

### Phase 34: Archive Swing Screens

- Move Swing classes to a legacy package or archive folder only after feature parity is signed off.
- Keep a tagged branch or release with the last Swing production version.
- Remove Swing-only helpers once no active code imports them.

### Phase 35: Finalize Storage Strategy

- Decide whether SQLite becomes the permanent source of truth.
- Complete final one-way migration from text files.
- Freeze or archive text-file storage.
- Add backup/restore production hardening before any destructive restore workflow.

## Removal Decision

Swing should not be removed now. JavaFX is strong enough for dashboards, patient board/detail, vitals, alerts preview, clinical timeline, AI recommendations, medications, devices registration, scheduling, work queue, medical files, staff activity, staff directory, audit logs, backup/export, and profile/settings. It is not yet complete enough for certificate, birth/death, mother/newborn, messaging, notification, room-management write, user-management write, device runtime, ECG, and unified alert-sound behavior.
