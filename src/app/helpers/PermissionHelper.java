package app.helpers;

import pages.user.User;

public final class PermissionHelper {

    private PermissionHelper() {
    }

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

    public static boolean canAddMedication(User user) {
        return isAdmin(user) || isDoctor(user);
    }

    public static boolean canGiveMedication(User user) {
        return isAdmin(user) || isDoctor(user) || isNurse(user);
    }

    public static boolean canManageMedicationCatalog(User user) {
        return isAdmin(user) || isDoctor(user);
    }

    public static boolean canViewMedicationCatalog(User user) {
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

    public static boolean canCreateReminder(User user) {
        return isAdmin(user) || isDoctor(user) || isNurse(user);
    }

    public static boolean canManageReminder(User user) {
        return isAdmin(user) || isDoctor(user) || isNurse(user);
    }

    public static boolean canCompleteReminder(User user) {
        return isAdmin(user) || isDoctor(user) || isNurse(user);
    }

    public static boolean canViewScheduling(User user) {
        return isAdmin(user) || isDoctor(user) || isNurse(user);
    }

    public static boolean canViewWorkQueue(User user) {
        return isAdmin(user) || isDoctor(user) || isNurse(user);
    }

    public static boolean canUploadMedicalFile(User user) {
        return isAdmin(user) || isDoctor(user) || isNurse(user);
    }

    public static boolean canViewMedicalFiles(User user) {
        return isAdmin(user) || isDoctor(user) || isNurse(user);
    }

    public static boolean canManageRooms(User user) {
        return isAdmin(user);
    }

    public static boolean canAssignPatientRoom(User user) {
        return isAdmin(user) || isDoctor(user) || isNurse(user);
    }

    public static boolean canViewDeceasedRecords(User user) {
        return isAdmin(user) || isDoctor(user) || isNurse(user);
    }

    public static boolean canMarkPatientDeceased(User user) {
        return isAdmin(user) || isDoctor(user);
    }

    public static boolean canGenerateDeathCertificate(User user) {
        return isAdmin(user) || isDoctor(user);
    }

    public static boolean canViewNewbornRecords(User user) {
        return isAdmin(user) || isDoctor(user) || isNurse(user);
    }

    public static boolean canManageNewbornRecords(User user) {
        return isAdmin(user) || isDoctor(user) || isNurse(user);
    }

    public static boolean canGenerateBirthCertificate(User user) {
        return isAdmin(user) || isDoctor(user);
    }

    public static boolean canSendDeathCertificateNotice(User user) {
        return isAdmin(user) || isDoctor(user);
    }

    public static boolean canSendBirthCertificateNotice(User user) {
        return isAdmin(user) || isDoctor(user) || isNurse(user);
    }

    public static boolean canViewCertificateRegistry(User user) {
        return canViewDeceasedRecords(user) || canViewNewbornRecords(user);
    }

    public static boolean canSubmitCertificateReview(User user) {
        return isAdmin(user) || isDoctor(user);
    }

    public static boolean canApproveCertificateReview(User user) {
        return isAdmin(user) || isDoctor(user);
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

    public static boolean canCreateTestAuditEvent(User user) {
        return isAdmin(user);
    }

    public static boolean isReadOnly(User user) {
        return !isAdmin(user) && !isDoctor(user) && !isNurse(user);
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
