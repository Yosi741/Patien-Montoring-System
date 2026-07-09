package app.helpers;

import pages.user.User;

public final class PermissionHelper {



    public static boolean canCreatePatient(User user) {
        return isAdmin(user) || isDoctor(user);
    }

    public static boolean canUpdatePatient(User user) {
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

    public static boolean canCreateAppointment(User user) {
        return isAdmin(user) || isDoctor(user);
    }

    public static boolean canManageAppointment(User user) {
        return isAdmin(user) || isDoctor(user);
    }

    public static boolean canViewScheduling(User user) {
        return isAdmin(user) || isDoctor(user) || isNurse(user);
    }

    public static boolean canUploadMedicalFile(User user) {
        return isAdmin(user) || isDoctor(user) || isNurse(user);
    }

    public static boolean canViewMedicalFiles(User user) {
        return isAdmin(user) || isDoctor(user) || isNurse(user);
    }

    public static boolean canViewMessages(User user) {
        return user != null;
    }

    public static boolean canComposeMessage(User user) {
        return isAdmin(user) || isDoctor(user) || isNurse(user);
    }

    public static boolean canViewNotifications(User user) {
        return user != null;
    }

    public static boolean canViewBilling(User user) {
        return user != null;
    }

    public static boolean canManageBilling(User user) {
        return user != null;
    }

    public static String roleGroup(User user) {
        return roleGroup(user == null ? null : user.getRole());
    }

    public static String roleGroup(String role) {
        if (role == null) {
            return "UNKNOWN";
        }
        String upper = role.toUpperCase();
        if (upper.contains("ADMIN")) {
            return "ADMIN";
        }
        if (upper.contains("DOCTOR") || upper.contains("MEDICAL") || upper.contains("DEPARTMENT HEAD")) {
            return "DOCTOR";
        }
        if (upper.contains("NURSE") || upper.contains("NURSING")) {
            return "NURSE";
        }
        if (upper.isBlank() || upper.equals("UNKNOWN")) {
            return "UNKNOWN";
        }
        return "STAFF";
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
}
