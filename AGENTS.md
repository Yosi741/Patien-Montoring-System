# AGENTS.md

This project is now a JavaFX + SQLite urgent care clinic demo named ClinicPulse.

## Safe Working Rules

- Do not run `git reset`, `git checkout`, or `git clean`.
- Keep JavaFX as the only UI.
- Keep SQLite as the active local database.
- Preserve the Controller -> Service -> DAO structure.
- Do not reintroduce deleted hospital modules.
- Prefer small scoped changes with compile verification.

## Entry Point

- Run `src/Main.java`.
- Main JavaFX shell: `src/app/AppShell.java`.

## Compile Command

From Windows `cmd.exe` inside `untitledSmartPatientMonitoringSystem`:

```cmd
javac -d out -cp "src;lib/javafx-base-17.0.15-win.jar;lib/javafx-controls-17.0.15-win.jar;lib/javafx-fxml-17.0.15-win.jar;lib/javafx-graphics-17.0.15-win.jar;lib/pdfbox-app-2.0.36.jar;lib/slf4j-api-2.0.17.jar;lib/slf4j-simple-2.0.17.jar;lib/sqlite-jdbc-3.53.1.0.jar" @sources.txt
```

## Current Active Modules

- `src/pages/login/`
- `src/pages/dashboard/`
- `src/pages/patient/`
- `src/pages/alert/`
- `src/pages/scheduling/`
- `src/pages/billing/`
- `src/pages/user/`
- `src/pages/messages/`
- `src/pages/notification/`

## Shared Code

- `src/app/database/` - SQLite connection and schema setup
- `src/app/helpers/` - shared UI, permission, date, file, and selection helpers
- `src/app/layout/` - shared shell layout controller and FXML
- `src/app/session/` - current session context
- `src/app/styles/` - JavaFX CSS themes

## Data

- Main database: `data/smart_patient_monitoring.db`
- Uploaded medical files: `data/uploads/`
- Profile photos: `data/profile_photos/`

SQLite is the source of truth. Runtime data should not be read from old text files.

## Roles

Final active roles:

- `ADMIN`
- `DOCTOR`
- `NURSE`
- `SECRETARY`

Use `src/pages/user/UserRole.java` when normalizing role values.

## Notes

- The login system is local and demo-oriented.
- Passwords are stored in the local SQLite users table for this demo scope.
- Do not describe the project as using advanced encryption or a cloud identity provider.
