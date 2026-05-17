package services;

import dao.SqliteDeviceDao;
import dao.SqlitePatientDao;
import ui.javafx.helpers.AuditAction;
import ui.javafx.helpers.AuditWriteHelper;
import ui.javafx.helpers.FormValidationHelper;
import ui.javafx.helpers.PermissionHelper;
import users.User;

import java.sql.SQLException;
import java.util.Set;

public class DeviceWriteService {

    private static final Set<String> DEVICE_TYPES = Set.of("Watch", "Blood Pressure Monitor", "Oximeter", "Thermometer", "Glucose Meter", "Other");
    private static final Set<String> STATUSES = Set.of("AVAILABLE", "ASSIGNED", "MAINTENANCE", "INACTIVE");

    private final SqliteDeviceDao deviceDao;
    private final SqlitePatientDao patientDao;

    public DeviceWriteService() {
        this(new SqliteDeviceDao(), new SqlitePatientDao());
    }

    public DeviceWriteService(SqliteDeviceDao deviceDao, SqlitePatientDao patientDao) {
        this.deviceDao = deviceDao;
        this.patientDao = patientDao;
    }

    public void registerDevice(User currentUser, DeviceRequest request) throws SQLException {
        requireManagePermission(currentUser);
        validateDevice(request, true);
        if (deviceDao.existsByDeviceId(request.deviceId)) {
            throw new IllegalArgumentException("Device ID already exists in SQLite.");
        }
        deviceDao.insertDevice(clean(request));
        AuditWriteHelper.write(username(currentUser), AuditAction.REGISTER_DEVICE,
                "device_id=" + request.deviceId + ", type=" + request.type);
    }

    public void updateDevice(User currentUser, DeviceRequest request) throws SQLException {
        requireManagePermission(currentUser);
        validateDevice(request, false);
        if (!deviceDao.existsByDeviceId(request.deviceId)) {
            throw new IllegalArgumentException("Device does not exist in SQLite: " + request.deviceId);
        }
        deviceDao.updateDevice(clean(request));
        AuditWriteHelper.write(username(currentUser), AuditAction.UPDATE_DEVICE,
                "device_id=" + request.deviceId + ", status=" + request.status);
    }

    public void deactivateDevice(User currentUser, String deviceId) throws SQLException {
        requireManagePermission(currentUser);
        SqliteDeviceDao.DeviceRecord device = deviceDao.findByDeviceId(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("Device not found in SQLite: " + deviceId));
        deviceDao.deactivateDevice(deviceId);
        AuditWriteHelper.write(username(currentUser), AuditAction.DEACTIVATE_DEVICE,
                "device_id=" + device.getDeviceId());
    }

    public void assignDeviceToPatient(User currentUser, String deviceId, String patientId) throws SQLException {
        if (!PermissionHelper.canAssignDevice(currentUser)) {
            throw new SecurityException("Only Admin, Doctor, and Nurse users can assign devices.");
        }
        SqliteDeviceDao.DeviceRecord device = deviceDao.findByDeviceId(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("Device not found in SQLite: " + deviceId));
        if (!"AVAILABLE".equalsIgnoreCase(device.getStatus())) {
            throw new IllegalArgumentException("Only AVAILABLE devices can be assigned.");
        }
        if (device.getPatientId() != null && !device.getPatientId().isBlank()) {
            throw new IllegalArgumentException("Device is already assigned to patient " + device.getPatientId() + ".");
        }
        if (!patientDao.existsByPatientId(patientId)) {
            throw new IllegalArgumentException("Assigned patient does not exist in SQLite: " + patientId);
        }
        deviceDao.assignDeviceToPatient(deviceId, patientId);
        AuditWriteHelper.write(username(currentUser), AuditAction.ASSIGN_DEVICE,
                "device_id=" + deviceId + ", patient_id=" + patientId);
    }

    public void unassignDevice(User currentUser, String deviceId) throws SQLException {
        if (!PermissionHelper.canAssignDevice(currentUser)) {
            throw new SecurityException("Only Admin, Doctor, and Nurse users can unassign devices.");
        }
        SqliteDeviceDao.DeviceRecord device = deviceDao.findByDeviceId(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("Device not found in SQLite: " + deviceId));
        deviceDao.unassignDevice(deviceId);
        AuditWriteHelper.write(username(currentUser), AuditAction.UNASSIGN_DEVICE,
                "device_id=" + deviceId + ", patient_id=" + device.getPatientId());
    }

    private void requireManagePermission(User currentUser) {
        if (!PermissionHelper.canManageDevice(currentUser)) {
            throw new SecurityException("Only Admin and Doctor users can register, edit, or deactivate devices.");
        }
    }

    private void validateDevice(DeviceRequest request, boolean create) throws SQLException {
        FormValidationHelper.ValidationResult validation = FormValidationHelper.combine(
                FormValidationHelper.validateRequired("Device ID", request.deviceId),
                FormValidationHelper.validateMaxLength("Device ID", request.deviceId, 60),
                FormValidationHelper.validateRequired("Device name", request.name),
                FormValidationHelper.validateMaxLength("Device name", request.name, 120),
                FormValidationHelper.validateRequired("Device type", request.type),
                FormValidationHelper.validateRequired("Serial number", request.serial),
                FormValidationHelper.validateMaxLength("Serial number", request.serial, 100),
                FormValidationHelper.validateMaxLength("Notes", request.notes, 300)
        );
        if (!validation.isValid()) {
            throw new IllegalArgumentException(validation.getMessage());
        }
        if (!DEVICE_TYPES.contains(request.type)) {
            throw new IllegalArgumentException("Device type must be one of: " + String.join(", ", DEVICE_TYPES));
        }
        if (!STATUSES.contains(normalizeStatus(request.status))) {
            throw new IllegalArgumentException("Status must be AVAILABLE, ASSIGNED, MAINTENANCE, or INACTIVE.");
        }
        if (deviceDao.serialExists(request.serial, create ? "" : request.deviceId)) {
            throw new IllegalArgumentException("Serial number already exists in SQLite.");
        }
        if (hasText(request.patientId) && !patientDao.existsByPatientId(request.patientId)) {
            throw new IllegalArgumentException("Assigned patient does not exist in SQLite: " + request.patientId);
        }
    }

    private SqliteDeviceDao.DeviceRecord clean(DeviceRequest request) {
        String status = normalizeStatus(request.status);
        String patientId = "ASSIGNED".equals(status) ? trim(request.patientId) : "";
        return new SqliteDeviceDao.DeviceRecord(
                trim(request.deviceId),
                trim(request.name),
                trim(request.type),
                trim(request.serial),
                status,
                patientId,
                "",
                trim(request.notes),
                ""
        );
    }

    private String normalizeStatus(String status) {
        return status == null || status.isBlank() ? "AVAILABLE" : status.trim().toUpperCase();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String username(User currentUser) {
        return currentUser == null || currentUser.getUsername() == null || currentUser.getUsername().isBlank()
                ? "Unknown"
                : currentUser.getUsername();
    }

    public static class DeviceRequest {
        private final String deviceId;
        private final String name;
        private final String type;
        private final String serial;
        private final String status;
        private final String patientId;
        private final String notes;

        public DeviceRequest(String deviceId, String name, String type, String serial, String status, String patientId, String notes) {
            this.deviceId = deviceId;
            this.name = name;
            this.type = type;
            this.serial = serial;
            this.status = status;
            this.patientId = patientId;
            this.notes = notes;
        }
    }
}
