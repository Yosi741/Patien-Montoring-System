package ui.javafx.users.services;

import models.Patient;
import users.User;

import java.util.Set;

public class RolePermissionService {

    private static final Set<String> HOSPITAL_WIDE_ROLES = Set.of(
            "Admin", "System Admin", "Hospital Director", "Chief Medical Officer", "Chief Nursing Officer"
    );

    public static boolean canViewAllSections(User user) {
        return user != null && HOSPITAL_WIDE_ROLES.contains(user.getRole());
    }

    public static boolean canAccessPatient(User user, Patient patient) {
        if (user == null || patient == null) {
            return false;
        }
        return canViewAllSections(user)
                || user.getSection().equals("All")
                || user.getSection().equals(patient.getSection());
    }

    public static boolean canViewSensitiveHistory(User user) {
        return hasAnyRole(user, "Admin", "System Admin", "Hospital Director", "Chief Medical Officer",
                "Department Head", "Doctor", "Nurse");
    }

    public static boolean canEditPatient(User user) {
        return hasAnyRole(user, "Admin", "System Admin", "Hospital Director", "Chief Medical Officer",
                "Department Head", "Doctor", "Receptionist");
    }

    public static boolean canDeletePatient(User user) {
        return hasAnyRole(user, "Admin", "System Admin", "Hospital Director");
    }

    public static boolean canAddVitals(User user) {
        return hasAnyRole(user, "Admin", "System Admin", "Doctor", "Nurse", "Technician");
    }

    public static boolean canManageDevices(User user) {
        return hasAnyRole(user, "Admin", "System Admin", "Doctor", "Nurse", "Technician");
    }

    public static boolean canViewAIAdvice(User user) {
        return hasAnyRole(user, "Admin", "System Admin", "Hospital Director", "Chief Medical Officer",
                "Department Head", "Doctor", "Nurse");
    }

    public static boolean canPronounceDeath(User user) {
        return hasAnyRole(user, "Admin", "System Admin", "Hospital Director", "Chief Medical Officer",
                "Department Head", "Doctor");
    }

    public static boolean canCreateBirthCertificate(User user) {
        return hasAnyRole(user, "Admin", "System Admin", "Doctor", "Nurse", "Receptionist");
    }

    public static boolean canManageRooms(User user) {
        return hasAnyRole(user, "Admin", "System Admin", "Hospital Director", "Chief Medical Officer",
                "Chief Nursing Officer", "Department Head");
    }

    public static boolean canViewDeceasedPatients(User user) {
        return hasAnyRole(user, "Admin", "System Admin", "Hospital Director", "Chief Medical Officer",
                "Department Head", "Doctor", "Nurse");
    }

    public static boolean canEditDeathRecord(User user) {
        return hasAnyRole(user, "Admin", "System Admin", "Hospital Director", "Chief Medical Officer",
                "Department Head", "Doctor");
    }

    public static boolean canGenerateCertificates(User user) {
        return hasAnyRole(user, "Admin", "System Admin", "Hospital Director", "Chief Medical Officer",
                "Department Head", "Doctor", "Nurse", "Receptionist");
    }

    public static boolean canManageUsers(User user) {
        return hasAnyRole(user, "Admin", "System Admin");
    }

    public static boolean canViewAuditLogs(User user) {
        return hasAnyRole(user, "Admin", "System Admin", "Hospital Director");
    }

    private static boolean hasAnyRole(User user, String... roles) {
        if (user == null) {
            return false;
        }
        for (String role : roles) {
            if (user.getRole().equals(role)) {
                return true;
            }
        }
        return false;
    }
}
