$targetViews = "out/production/untitledSmartPatientMonitoringSystem/ui/javafx/views"
$targetPages = "out/production/untitledSmartPatientMonitoringSystem/ui/javafx/pages"
$targetPatients = "out/production/untitledSmartPatientMonitoringSystem/ui/javafx/patients"
$targetUsers = "out/production/untitledSmartPatientMonitoringSystem/ui/javafx/users"
$targetLogin = "out/production/untitledSmartPatientMonitoringSystem/ui/javafx/login"
$targetDashboard = "out/production/untitledSmartPatientMonitoringSystem/ui/javafx/dashboard"
$targetStyles = "out/production/untitledSmartPatientMonitoringSystem/ui/javafx/styles"

New-Item -ItemType Directory -Force -Path $targetViews
New-Item -ItemType Directory -Force -Path $targetPages
New-Item -ItemType Directory -Force -Path $targetPatients
New-Item -ItemType Directory -Force -Path $targetUsers
New-Item -ItemType Directory -Force -Path $targetLogin
New-Item -ItemType Directory -Force -Path $targetDashboard
New-Item -ItemType Directory -Force -Path $targetStyles

Copy-Item -Path "src/ui/javafx/views/*" -Destination $targetViews -Force
Copy-Item -Path "src/ui/javafx/pages/*" -Destination $targetPages -Force -Recurse
Copy-Item -Path "src/ui/javafx/patients/*" -Destination $targetPatients -Force -Recurse
Copy-Item -Path "src/ui/javafx/users/*" -Destination $targetUsers -Force -Recurse
Copy-Item -Path "src/ui/javafx/login/*" -Destination $targetLogin -Force -Recurse
Copy-Item -Path "src/ui/javafx/dashboard/*" -Destination $targetDashboard -Force -Recurse
Copy-Item -Path "src/ui/javafx/styles/*" -Destination $targetStyles -Force

$legacyLoginView = Join-Path $targetViews "LoginView.fxml"
if (Test-Path $legacyLoginView) {
    Remove-Item -Path $legacyLoginView -Force
}
$legacyFeatureLoginView = "out/production/untitledSmartPatientMonitoringSystem/ui/javafx/features/login/LoginView.fxml"
if (Test-Path $legacyFeatureLoginView) {
    Remove-Item -Path $legacyFeatureLoginView -Force
}
$legacyDashboardView = Join-Path $targetViews "DashboardView.fxml"
if (Test-Path $legacyDashboardView) {
    Remove-Item -Path $legacyDashboardView -Force
}
$legacyUserProfileView = Join-Path $targetViews "UserProfileView.fxml"
if (Test-Path $legacyUserProfileView) {
    Remove-Item -Path $legacyUserProfileView -Force
}
$legacyPatientListView = Join-Path $targetViews "PatientListView.fxml"
if (Test-Path $legacyPatientListView) {
    Remove-Item -Path $legacyPatientListView -Force
}
$legacyPatientDetailView = Join-Path $targetViews "PatientDetailView.fxml"
if (Test-Path $legacyPatientDetailView) {
    Remove-Item -Path $legacyPatientDetailView -Force
}
$legacyPatientFormView = Join-Path $targetViews "PatientFormView.fxml"
if (Test-Path $legacyPatientFormView) {
    Remove-Item -Path $legacyPatientFormView -Force
}
$legacyVitalsEntryView = Join-Path $targetViews "VitalsEntryView.fxml"
if (Test-Path $legacyVitalsEntryView) {
    Remove-Item -Path $legacyVitalsEntryView -Force
}
$legacyMedicalFilesView = Join-Path $targetViews "MedicalFilesView.fxml"
if (Test-Path $legacyMedicalFilesView) {
    Remove-Item -Path $legacyMedicalFilesView -Force
}
$legacyMedicalFileUploadView = Join-Path $targetViews "MedicalFileUploadView.fxml"
if (Test-Path $legacyMedicalFileUploadView) {
    Remove-Item -Path $legacyMedicalFileUploadView -Force
}
$legacyUserDirectoryManagementView = Join-Path $targetViews "UserDirectoryManagementView.fxml"
if (Test-Path $legacyUserDirectoryManagementView) {
    Remove-Item -Path $legacyUserDirectoryManagementView -Force
}
$legacyUserDirectoryView = Join-Path $targetViews "UserDirectoryView.fxml"
if (Test-Path $legacyUserDirectoryView) {
    Remove-Item -Path $legacyUserDirectoryView -Force
}
$legacyUserFormView = Join-Path $targetViews "UserFormView.fxml"
if (Test-Path $legacyUserFormView) {
    Remove-Item -Path $legacyUserFormView -Force
}
$legacyMedicationOverviewView = Join-Path $targetViews "MedicationOverviewView.fxml"
if (Test-Path $legacyMedicationOverviewView) {
    Remove-Item -Path $legacyMedicationOverviewView -Force
}
$legacyMedicationFormView = Join-Path $targetViews "MedicationFormView.fxml"
if (Test-Path $legacyMedicationFormView) {
    Remove-Item -Path $legacyMedicationFormView -Force
}
$legacySchedulingView = Join-Path $targetViews "SchedulingView.fxml"
if (Test-Path $legacySchedulingView) {
    Remove-Item -Path $legacySchedulingView -Force
}
$legacyAppointmentFormView = Join-Path $targetViews "AppointmentFormView.fxml"
if (Test-Path $legacyAppointmentFormView) {
    Remove-Item -Path $legacyAppointmentFormView -Force
}
$legacyReminderFormView = Join-Path $targetViews "ReminderFormView.fxml"
if (Test-Path $legacyReminderFormView) {
    Remove-Item -Path $legacyReminderFormView -Force
}
$legacyAuditLogView = Join-Path $targetViews "AuditLogView.fxml"
if (Test-Path $legacyAuditLogView) {
    Remove-Item -Path $legacyAuditLogView -Force
}
$legacyCertificateRegistryView = Join-Path $targetViews "CertificateRegistryView.fxml"
if (Test-Path $legacyCertificateRegistryView) {
    Remove-Item -Path $legacyCertificateRegistryView -Force
}
$legacyClinicalTimelineView = Join-Path $targetViews "ClinicalTimelineView.fxml"
if (Test-Path $legacyClinicalTimelineView) {
    Remove-Item -Path $legacyClinicalTimelineView -Force
}
$legacyDeathRecordFormView = Join-Path $targetViews "DeathRecordFormView.fxml"
if (Test-Path $legacyDeathRecordFormView) {
    Remove-Item -Path $legacyDeathRecordFormView -Force
}
$legacyDeceasedRecordsView = Join-Path $targetViews "DeceasedRecordsView.fxml"
if (Test-Path $legacyDeceasedRecordsView) {
    Remove-Item -Path $legacyDeceasedRecordsView -Force
}
$legacyMedicationCatalogView = Join-Path $targetViews "MedicationCatalogView.fxml"
if (Test-Path $legacyMedicationCatalogView) {
    Remove-Item -Path $legacyMedicationCatalogView -Force
}
$legacyMedicationGivenView = Join-Path $targetViews "MedicationGivenView.fxml"
if (Test-Path $legacyMedicationGivenView) {
    Remove-Item -Path $legacyMedicationGivenView -Force
}
$legacyMessagingView = Join-Path $targetViews "MessagingView.fxml"
if (Test-Path $legacyMessagingView) {
    Remove-Item -Path $legacyMessagingView -Force
}
$legacyNewbornFormView = Join-Path $targetViews "NewbornFormView.fxml"
if (Test-Path $legacyNewbornFormView) {
    Remove-Item -Path $legacyNewbornFormView -Force
}
$legacyNewbornRecordsView = Join-Path $targetViews "NewbornRecordsView.fxml"
if (Test-Path $legacyNewbornRecordsView) {
    Remove-Item -Path $legacyNewbornRecordsView -Force
}
$legacyNotificationCenterView = Join-Path $targetViews "NotificationCenterView.fxml"
if (Test-Path $legacyNotificationCenterView) {
    Remove-Item -Path $legacyNotificationCenterView -Force
}
$legacyNurseWorkQueueView = Join-Path $targetViews "NurseWorkQueueView.fxml"
if (Test-Path $legacyNurseWorkQueueView) {
    Remove-Item -Path $legacyNurseWorkQueueView -Force
}
$legacyRoomAssignmentView = Join-Path $targetViews "RoomAssignmentView.fxml"
if (Test-Path $legacyRoomAssignmentView) {
    Remove-Item -Path $legacyRoomAssignmentView -Force
}
$legacyRoomBedOccupancyView = Join-Path $targetViews "RoomBedOccupancyView.fxml"
if (Test-Path $legacyRoomBedOccupancyView) {
    Remove-Item -Path $legacyRoomBedOccupancyView -Force
}
$legacyRoomFormView = Join-Path $targetViews "RoomFormView.fxml"
if (Test-Path $legacyRoomFormView) {
    Remove-Item -Path $legacyRoomFormView -Force
}
$legacySectionFormView = Join-Path $targetViews "SectionFormView.fxml"
if (Test-Path $legacySectionFormView) {
    Remove-Item -Path $legacySectionFormView -Force
}

$legacyPatientPages = @(
    "out/production/untitledSmartPatientMonitoringSystem/ui/javafx/pages/patient_board",
    "out/production/untitledSmartPatientMonitoringSystem/ui/javafx/pages/patient_detail",
    "out/production/untitledSmartPatientMonitoringSystem/ui/javafx/pages/patient_form",
    "out/production/untitledSmartPatientMonitoringSystem/ui/javafx/pages/vitals_entry",
    "out/production/untitledSmartPatientMonitoringSystem/ui/javafx/pages/medical_files",
    "out/production/untitledSmartPatientMonitoringSystem/ui/javafx/pages/users",
    "out/production/untitledSmartPatientMonitoringSystem/ui/javafx/pages/profile_settings",
    "out/production/untitledSmartPatientMonitoringSystem/ui/javafx/pages/login",
    "out/production/untitledSmartPatientMonitoringSystem/ui/javafx/pages/dashboard"
)

foreach ($legacyFolder in $legacyPatientPages) {
    if (Test-Path $legacyFolder) {
        Remove-Item -Path $legacyFolder -Recurse -Force
    }
}

Write-Host "Resources synced successfully in the active project copy!"
