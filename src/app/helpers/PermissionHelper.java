package app.helpers;

import pages.user.User;
import pages.user.UserRole;

/**
 * Centralizes ClinicPulse role permissions for pages and protected workflow actions.
 */
public final class PermissionHelper {

    /**
     * Creates a permission helper from the supplied record values.
     */
    private PermissionHelper() {
    }

    /**
     * Determines whether can create patient for the current record or user.
     */
    public static boolean canCreatePatient(User user) {
        return isAdmin(user) || isDoctor(user) || isNurse(user) || isSecretary(user);
    }

    /**
     * Determines whether can update patient for the current record or user.
     */
    public static boolean canUpdatePatient(User user) {
        return isAdmin(user) || isDoctor(user) || isNurse(user) || isSecretary(user);
    }

    /**
     * Determines whether can delete patient for the current record or user.
     */
    public static boolean canDeletePatient(User user) {
        return isAdmin(user);
    }

    /**
     * Determines whether can view patient file for the current record or user.
     */
    public static boolean canViewPatientFile(User user) {
        return isAdmin(user) || isDoctor(user);
    }

    /**
     * Determines whether can deactivate patient for the current record or user.
     */
    public static boolean canDeactivatePatient(User user) {
        return isAdmin(user) || isDoctor(user);
    }

    /**
     * Determines whether can enter vitals for the current record or user.
     */
    public static boolean canEnterVitals(User user) {
        return isAdmin(user) || isDoctor(user) || isNurse(user);
    }

    /**
     * Determines whether can create user for the current record or user.
     */
    public static boolean canCreateUser(User user) {
        return isAdmin(user);
    }

    /**
     * Determines whether can update user for the current record or user.
     */
    public static boolean canUpdateUser(User user) {
        return isAdmin(user);
    }

    /**
     * Determines whether can deactivate user for the current record or user.
     */
    public static boolean canDeactivateUser(User user) {
        return isAdmin(user);
    }

    /**
     * Determines whether can view user directory for the current record or user.
     */
    public static boolean canViewUserDirectory(User user) {
        return isAdmin(user);
    }

    /**
     * Determines whether can create appointment for the current record or user.
     */
    public static boolean canCreateAppointment(User user) {
        return isAdmin(user) || isSecretary(user);
    }

    /**
     * Determines whether can edit appointment for the current record or user.
     */
    public static boolean canEditAppointment(User user) {
        return isAdmin(user) || isSecretary(user);
    }

    /**
     * Determines whether can delete appointment for the current record or user.
     */
    public static boolean canDeleteAppointment(User user) {
        return isAdmin(user);
    }

    /**
     * Determines whether can manage appointment for the current record or user.
     */
    public static boolean canManageAppointment(User user) {
        return canEditAppointment(user);
    }

    /**
     * Determines whether can view scheduling for the current record or user.
     */
    public static boolean canViewScheduling(User user) {
        return user != null;
    }

    /**
     * Determines whether can upload medical file for the current record or user.
     */
    public static boolean canUploadMedicalFile(User user) {
        return isAdmin(user) || isDoctor(user) || isNurse(user) || isSecretary(user);
    }

    /**
     * Determines whether can view medical files for the current record or user.
     */
    public static boolean canViewMedicalFiles(User user) {
        return isAdmin(user) || isDoctor(user) || isNurse(user) || isSecretary(user);
    }

    /**
     * Determines whether can delete medical file for the current record or user.
     */
    public static boolean canDeleteMedicalFile(User user) {
        return isAdmin(user);
    }

    /**
     * Determines whether can view messages for the current record or user.
     */
    public static boolean canViewMessages(User user) {
        return user != null;
    }

    /**
     * Determines whether can compose message for the current record or user.
     */
    public static boolean canComposeMessage(User user) {
        return user != null;
    }

    /**
     * Determines whether can view notifications for the current record or user.
     */
    public static boolean canViewNotifications(User user) {
        return isAdmin(user) || isDoctor(user) || isNurse(user);
    }

    /**
     * Determines whether can acknowledge alerts for the current record or user.
     */
    public static boolean canAcknowledgeAlerts(User user) {
        return isAdmin(user) || isDoctor(user) || isNurse(user);
    }

    /**
     * Determines whether can view billing for the current record or user.
     */
    public static boolean canViewBilling(User user) {
        return isAdmin(user) || isSecretary(user);
    }

    /**
     * Determines whether can manage billing for the current record or user.
     */
    public static boolean canManageBilling(User user) {
        return isAdmin(user) || isSecretary(user);
    }

    /**
     * Determines whether can delete invoice for the current record or user.
     */
    public static boolean canDeleteInvoice(User user) {
        return isAdmin(user);
    }

    /**
     * Maps a role to the permission group used by access checks.
     */
    public static String roleGroup(User user) {
        return roleGroup(user == null ? null : user.getRole());
    }

    /**
     * Maps a role to the permission group used by access checks.
     */
    public static String roleGroup(String role) {
        if (role == null) {
            return "UNKNOWN";
        }
        if (role.isBlank() || "UNKNOWN".equalsIgnoreCase(role.trim())) {
            return "UNKNOWN";
        }
        return UserRole.fromValue(role).databaseValue();
    }

    private static boolean isAdmin(User user) {
        return "ADMIN".equals(roleGroup(user));
    }

    private static boolean isDoctor(User user) {
        return "DOCTOR".equals(roleGroup(user));
    }

    private static boolean isNurse(User user) {
        return "NURSE".equals(roleGroup(user));
    }

    private static boolean isSecretary(User user) {
        return "SECRETARY".equals(roleGroup(user));
    }
}
