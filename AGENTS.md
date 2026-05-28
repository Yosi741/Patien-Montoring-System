# AGENTS: How to be productive in this codebase

**Status: Full JavaFX Migration Complete** ✓ All Swing code removed.

## Quick Start
- **Main entry**: Run `Main.java` (launches JavaFX)
- **Database setup**: Run `DatabaseMigrationMain` first, then restart app
- **SQLite preview**: Check `data/smart_patient_monitoring.db` after running app

## Recommended Pattern
1. Modify Service (validation, audit) → DAO (persistence) → Controller (UI binding)
2. Controllers should NOT execute SQL directly; use DAOs and Services
3. When adding writes: include AuditWriteHelper calls and update SqliteMigrationService if needed

Key entry points and commands
- Main Java launchers:
  - `Main` (default launcher)
  - `JavaFxMain` (explicit JavaFX)
  - `LegacySwingMain` (explicit Swing)
  - `DatabaseMigrationMain` (migrate text files -> SQLite safely)
- Build/run from terminal (README examples): compile with `javac -cp "lib/*" -d out/production/... @sources.txt` then run with `java -cp "out/production/...;lib/*" Main` on PowerShell.

## Project-specific conventions & gotchas
- **Dual persistence**: Text-file storage (`data/*.txt`) and SQLite (`data/smart_patient_monitoring.db`) both exist for data redundancy.
- **Safe file handling**: Uploads and generated certificates live under `data/uploads/` and `data/generated/...`
- **Audit-first**: All writes use `AuditWriteHelper` / `AuditWriteService` (see src/services/)
- **Passwords**: Use `PasswordHasher` (do not log raw passwords)
- **UI Layer**: Controllers are in `src/ui/javafx/controllers/`, FXML views in `src/ui/javafx/views/`

Integration points & external deps
- Local jars in `lib/`: JavaFX, SQLite JDBC, PDFBox, SLF4J. All runtime classpath examples include `lib/*`.
- PDF text extraction uses PDFBox (jar in `lib/pdfbox-app-*.jar`). No external OCR or cloud APIs.

## Files & directories to inspect first (examples)
- `src/ui/javafx/AppShell.java` — JavaFX application entry point, theme loading, stage management
- `src/ui/javafx/AppNavigator.java` — scene navigation and controller routing
- `src/ui/javafx/controllers/AppLayoutController.java` — main app layout and sidebar
- `src/ui/javafx/controllers/LoginController.java` — authentication flow
- `src/database/DatabaseManager.java` — database path, PRAGMA config, connection helper
- `src/database/MedicalFileStorage.java` — file upload storage and indexing
- `src/ai/AIAdviceEngine.java` — rule-based AI note generation
- `src/dao/Sqlite*.java` — SQLite schema and query layer

How to make a safe change (recommended pattern)
1. Run the app and reproduce behavior with the smallest entry point (use `JavaFxMain` or `LegacySwingMain`).
2. Modify Service (validation/audit) + DAO (persistence) — keep Controller changes minimal.
3. Add unit-like manual test: run migration if you touched persistence, open UI, run the flow, and check `data/` files and `data/smart_patient_monitoring.db`.
4. If adding a write path for JavaFX, update migration logic and add audit entries.

Where to leave notes for other agents
- Update this file `AGENTS.md` or `README.md` when you change high-level flows, file locations, or migration behavior.

If unsure: prefer non-destructive changes and add explicit checks (e.g., do not overwrite text-file legacy stores; prefer adding an opt-in migration path).

---
Short references: `Main.java`, `JavaFxMain.java`, `LegacySwingMain.java`, `DatabaseMigrationMain.java`, `src/database/DatabaseManager.java`, `src/database/MedicalFileStorage.java`, `src/ai/AIAdviceEngine.java`, `src/dao/SqlitePatientDao.java`.

