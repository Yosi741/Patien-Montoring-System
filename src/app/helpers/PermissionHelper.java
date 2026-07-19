package app.helpers;

import pages.user.User;
import pages.user.UserRole;

public final class PermissionHelper {

    private PermissionHelper() {
    }

    public static boolean canViewDashboard(User user) {
        return user != null;
    }

    public static boolean canViewPatients(User user) {
        return user != null;
    }

    public static boolean canCreatePatient(User user) {
        return isAdmin(user) || isDoctor(user) || isNurse(user) || isSecretary(user);
    }

    public static boolean canUpdatePatient(User user) {
        return isAdmin(user) || isDoctor(user) || isNurse(user) || isSecretary(user);
    }

    public static boolean canDeletePatient(User user) {
        return isAdmin(user);
    }

    public static boolean canViewPatientFile(User user) {
        return isAdmin(user) || isDoctor(user);
    }

    public static boolean canDeactivatePatient(User user) {
        return isAdmin(user) || isDoctor(user);
    }

    public static boolean canEnterVitals(User user) {
        return isAdmin(user) || isDoctor(user) || isNurse(user);
    }

    public static boolean canCreateUser(User user) {
        return isAdmin(user);
    }

    public static boolean canUpdateUser(User user) {
        return isAdmin(user);
    }

    public static boolean canDeactivateUser(User user) {
        return isAdmin(user);
    }

    public static boolean canResetUserPassword(User user) {
        return isAdmin(user);
    }

    public static boolean canViewUserDirectory(User user) {
        return isAdmin(user);
    }

    public static boolean canCreateAppointment(User user) {
        return isAdmin(user) || isSecretary(user);
    }

    public static boolean canEditAppointment(User user) {
        return isAdmin(user) || isSecretary(user);
    }

    public static boolean canDeleteAppointment(User user) {
        return isAdmin(user);
    }

    public static boolean canManageAppointment(User user) {
        return canEditAppointment(user);
    }

    public static boolean canViewScheduling(User user) {
        return user != null;
    }

    public static boolean canUploadMedicalFile(User user) {
        return isAdmin(user) || isDoctor(user) || isNurse(user) || isSecretary(user);
    }

    public static boolean canViewMedicalFiles(User user) {
        return isAdmin(user) || isDoctor(user) || isNurse(user) || isSecretary(user);
    }

    public static boolean canDeleteMedicalFile(User user) {
        return isAdmin(user);
    }

    public static boolean canViewMessages(User user) {
        return user != null;
    }

    public static boolean canComposeMessage(User user) {
        return user != null;
    }

    public static boolean canViewNotifications(User user) {
        return isAdmin(user) || isDoctor(user) || isNurse(user);
    }

    public static boolean canAcknowledgeAlerts(User user) {
        return isAdmin(user) || isDoctor(user) || isNurse(user);
    }

    public static boolean canViewBilling(User user) {
        return isAdmin(user) || isSecretary(user);
    }

    public static boolean canManageBilling(User user) {
        return isAdmin(user) || isSecretary(user);
    }

    public static boolean canDeleteInvoice(User user) {
        return isAdmin(user);
    }

    public static String roleGroup(User user) {
        return roleGroup(user == null ? null : user.getRole());
    }

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
