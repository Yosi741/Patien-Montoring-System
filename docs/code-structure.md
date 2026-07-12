# Code Structure

ClinicPulse uses a simple layered JavaFX architecture:

```text
FXML View
    -> Controller
    -> Service
    -> DAO
    -> SQLite
```

## Main Areas

- `src/Main.java` - official entry point.
- `src/app/` - app shell, navigation, shared helpers, session state, database bootstrap, and CSS.
- `src/pages/` - feature pages grouped by topic.
- `src/photo/` - logo and image assets.
- `src/sound/` - alert sound resources.

## Active Page Modules

- `src/pages/login/` - login and forgot-password flow.
- `src/pages/dashboard/` - urgent care overview.
- `src/pages/patient/` - patient board, patient file, patient form, vitals, and medical files.
- `src/pages/alert/` - alert services and sound handling.
- `src/pages/scheduling/` - appointments.
- `src/pages/billing/` - local billing and payment status.
- `src/pages/user/` - staff management, roles, and profile/settings.
- `src/pages/messages/` - internal messages.
- `src/pages/notification/` - alerts and notifications inbox.

## Shared Infrastructure

- `src/app/database/DatabaseManager.java` - SQLite connection helper.
- `src/app/database/SchemaInitializer.java` - creates and migrates tables.
- `src/app/helpers/` - reusable UI, permission, date, file, and selection helpers.
- `src/app/session/SessionContext.java` - current logged-in user context.
- `src/app/layout/` - shared top bar, sidebar, and main content shell.
- `src/app/styles/` - dark and light JavaFX themes.

## Roles

The final role set is:

- `ADMIN`
- `DOCTOR`
- `NURSE`
- `SECRETARY`

Role normalization is handled by `src/pages/user/UserRole.java`.
