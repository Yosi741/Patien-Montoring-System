package services;

import dao.SqliteSectionDao;
import ui.javafx.helpers.AuditAction;
import ui.javafx.helpers.AuditWriteHelper;
import ui.javafx.helpers.DialogHelper;
import ui.javafx.helpers.FormValidationHelper;
import ui.javafx.helpers.PermissionHelper;
import users.User;

import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class SectionService {

    private static final Set<String> SECTION_STATUSES = Set.of("ACTIVE", "INACTIVE");

    private final SqliteSectionDao sectionDao;

    public SectionService() {
        this(new SqliteSectionDao());
    }

    public SectionService(SqliteSectionDao sectionDao) {
        this.sectionDao = sectionDao;
    }

    public long createSection(User currentUser, SectionRequest request) throws SQLException {
        requireAdmin(currentUser);
        SqliteSectionDao.SectionRecord section = cleanAndValidate(request);
        if (sectionDao.existsByName(section.getName(), 0)) {
            throw new IllegalArgumentException("Section name already exists.");
        }
        long id = sectionDao.insertSection(section);
        AuditWriteHelper.write(username(currentUser), AuditAction.CREATE_SECTION, "section=" + section.getName());
        return id;
    }

    public void updateSection(User currentUser, long id, SectionRequest request, boolean updateRelatedRecords) throws SQLException {
        requireAdmin(currentUser);
        SqliteSectionDao.SectionRecord existing = findSection(id);
        SqliteSectionDao.SectionRecord section = cleanAndValidate(request);
        if (sectionDao.existsByName(section.getName(), id)) {
            throw new IllegalArgumentException("Section name already exists.");
        }
        boolean renamed = !safe(existing.getName()).equalsIgnoreCase(section.getName());
        if (renamed && !updateRelatedRecords) {
            throw new IllegalArgumentException("Renaming a section requires confirmation to update related rooms and patients.");
        }
        sectionDao.updateSection(id, section);
        if (renamed) {
            sectionDao.renameRoomsAndPatients(existing.getName(), section.getName());
        }
        AuditWriteHelper.write(username(currentUser), AuditAction.UPDATE_SECTION,
                "section=" + existing.getName() + " -> " + section.getName() + ", status=" + section.getStatus());
    }

    public void deactivateSection(User currentUser, long id, boolean confirmedWithActiveRecords) throws SQLException {
        requireAdmin(currentUser);
        SqliteSectionDao.SectionRecord section = findSection(id);
        int activeRooms = sectionDao.countActiveRooms(section.getName());
        int activePatients = sectionDao.countActivePatients(section.getName());
        int activeUsers = sectionDao.countActiveUsers(section.getName());
        if (activeRooms > 0 || activePatients > 0 || activeUsers > 0) {
            throw new IllegalArgumentException("Section is in use. Move users, patients, and rooms before deactivating.");
        }
        sectionDao.updateSection(id, new SqliteSectionDao.SectionRecord(section.getName(), "INACTIVE", section.getNotes()));
        AuditWriteHelper.write(username(currentUser), AuditAction.DEACTIVATE_SECTION,
                "section=" + section.getName() + ", active_rooms=" + activeRooms
                        + ", active_patients=" + activePatients + ", active_users=" + activeUsers);
    }

    public List<SqliteSectionDao.SectionRecord> findSections() throws SQLException {
        return sectionDao.findAll();
    }

    public List<String> findActiveSectionNames() throws SQLException {
        return sectionDao.findActiveSectionNames();
    }

    public SqliteSectionDao.SectionRecord findSection(long id) throws SQLException {
        return sectionDao.findById(id).orElseThrow(() -> new IllegalArgumentException("Section not found in SQLite."));
    }

    public SqliteSectionDao.SectionRecord cleanAndValidate(SectionRequest request) {
        String name = safe(request == null ? "" : request.name);
        String status = safe(request == null ? "" : request.status).toUpperCase(Locale.ROOT);
        String notes = safe(request == null ? "" : request.notes);
        if (status.isBlank()) {
            status = "ACTIVE";
        }
        FormValidationHelper.ValidationResult validation = FormValidationHelper.combine(
                FormValidationHelper.validateRequired("Section name", name),
                FormValidationHelper.validateMaxLength("Section name", name, 80),
                FormValidationHelper.validateMaxLength("Notes", notes, 300)
        );
        if (!validation.isValid()) {
            throw new IllegalArgumentException(validation.getMessage());
        }
        if (!SECTION_STATUSES.contains(status)) {
            throw new IllegalArgumentException("Section status must be ACTIVE or INACTIVE.");
        }
        return new SqliteSectionDao.SectionRecord(name, status, notes);
    }

    public boolean confirmRelatedUpdate(String sectionName) {
        return DialogHelper.confirm("Update related records",
                "Rename section '" + sectionName + "' and update related SQLite rooms and patients?");
    }

    public boolean confirmDeactivateWithActiveRecords(String sectionName) {
        return DialogHelper.confirm("Deactivate section",
                "Deactivate section '" + sectionName + "'? Sections in use are blocked for safety.");
    }

    private void requireAdmin(User currentUser) {
        if (!PermissionHelper.canManageRooms(currentUser)) {
            throw new SecurityException("Only Admin users can manage sections.");
        }
    }

    private String username(User currentUser) {
        return currentUser == null || currentUser.getUsername() == null || currentUser.getUsername().isBlank()
                ? "Unknown"
                : currentUser.getUsername();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    public static class SectionRequest {
        private final String name;
        private final String status;
        private final String notes;

        public SectionRequest(String name, String status, String notes) {
            this.name = name;
            this.status = status;
            this.notes = notes;
        }

        public String getName() { return name; }
        public String getStatus() { return status; }
        public String getNotes() { return notes; }
    }
}
