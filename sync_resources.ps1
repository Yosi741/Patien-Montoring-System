$targetViews = "out/production/untitledSmartPatientMonitoringSystem/ui/javafx/views"
$targetPages = "out/production/untitledSmartPatientMonitoringSystem/ui/javafx/pages"
$targetPatients = "out/production/untitledSmartPatientMonitoringSystem/pages/patient"
$targetUsers = "out/production/untitledSmartPatientMonitoringSystem/pages/user"
$targetLogin = "out/production/untitledSmartPatientMonitoringSystem/pages/login"
$targetDashboard = "out/production/untitledSmartPatientMonitoringSystem/pages/dashboard"
$targetAlert = "out/production/untitledSmartPatientMonitoringSystem/pages/alert"
$targetMedication = "out/production/untitledSmartPatientMonitoringSystem/pages/medication"
$targetScheduling = "out/production/untitledSmartPatientMonitoringSystem/pages/scheduling"
$targetCertificate = "out/production/untitledSmartPatientMonitoringSystem/pages/certificate"
$targetRoomSection = "out/production/untitledSmartPatientMonitoringSystem/pages/room_section"
$targetNewborn = "out/production/untitledSmartPatientMonitoringSystem/pages/newborn"
$targetDeceased = "out/production/untitledSmartPatientMonitoringSystem/pages/deceased"
$targetClinicalTimeline = "out/production/untitledSmartPatientMonitoringSystem/pages/clinical_timeline"
$targetMessages = "out/production/untitledSmartPatientMonitoringSystem/pages/messages"
$targetNotification = "out/production/untitledSmartPatientMonitoringSystem/pages/notification"
$targetAuditLog = "out/production/untitledSmartPatientMonitoringSystem/pages/audit_log"
$targetNurseWork = "out/production/untitledSmartPatientMonitoringSystem/pages/nurse_work"
$targetApp = "out/production/untitledSmartPatientMonitoringSystem/app"
$targetAppStyles = "out/production/untitledSmartPatientMonitoringSystem/app/styles"
$targetSound = "out/production/untitledSmartPatientMonitoringSystem/sound"

New-Item -ItemType Directory -Force -Path $targetViews
New-Item -ItemType Directory -Force -Path $targetPages
New-Item -ItemType Directory -Force -Path $targetPatients
New-Item -ItemType Directory -Force -Path $targetUsers
New-Item -ItemType Directory -Force -Path $targetLogin
New-Item -ItemType Directory -Force -Path $targetDashboard
New-Item -ItemType Directory -Force -Path $targetAlert
New-Item -ItemType Directory -Force -Path $targetMedication
New-Item -ItemType Directory -Force -Path $targetScheduling
New-Item -ItemType Directory -Force -Path $targetCertificate
New-Item -ItemType Directory -Force -Path $targetRoomSection
New-Item -ItemType Directory -Force -Path $targetNewborn
New-Item -ItemType Directory -Force -Path $targetDeceased
New-Item -ItemType Directory -Force -Path $targetClinicalTimeline
New-Item -ItemType Directory -Force -Path $targetMessages
New-Item -ItemType Directory -Force -Path $targetNotification
New-Item -ItemType Directory -Force -Path $targetAuditLog
New-Item -ItemType Directory -Force -Path $targetNurseWork
New-Item -ItemType Directory -Force -Path $targetApp
New-Item -ItemType Directory -Force -Path $targetAppStyles
New-Item -ItemType Directory -Force -Path $targetSound

if (Test-Path "src/ui/javafx/views") {
    Copy-Item -Path "src/ui/javafx/views/*" -Destination $targetViews -Force
}
if (Test-Path "src/ui/javafx/pages") {
    Copy-Item -Path "src/ui/javafx/pages/*" -Destination $targetPages -Force -Recurse
}
if (Test-Path "src/pages/patient") {
    Copy-Item -Path "src/pages/patient/*" -Destination $targetPatients -Force -Recurse
}
if (Test-Path "src/pages/user") {
    Copy-Item -Path "src/pages/user/*" -Destination $targetUsers -Force -Recurse
}
if (Test-Path "src/pages/login") {
    Copy-Item -Path "src/pages/login/*" -Destination $targetLogin -Force -Recurse
}
if (Test-Path "src/pages/dashboard") {
    Copy-Item -Path "src/pages/dashboard/*" -Destination $targetDashboard -Force -Recurse
}
if (Test-Path "src/pages/alert") {
    Copy-Item -Path "src/pages/alert/*" -Destination $targetAlert -Force -Recurse
}
if (Test-Path "src/pages/medication") {
    Copy-Item -Path "src/pages/medication/*" -Destination $targetMedication -Force -Recurse
}
if (Test-Path "src/pages/scheduling") {
    Copy-Item -Path "src/pages/scheduling/*" -Destination $targetScheduling -Force -Recurse
}
if (Test-Path "src/pages/certificate") {
    Copy-Item -Path "src/pages/certificate/*" -Destination $targetCertificate -Force -Recurse
}
if (Test-Path "src/pages/room_section") {
    Copy-Item -Path "src/pages/room_section/*" -Destination $targetRoomSection -Force -Recurse
}
if (Test-Path "src/pages/newborn") {
    Copy-Item -Path "src/pages/newborn/*" -Destination $targetNewborn -Force -Recurse
}
if (Test-Path "src/pages/deceased") {
    Copy-Item -Path "src/pages/deceased/*" -Destination $targetDeceased -Force -Recurse
}
if (Test-Path "src/pages/clinical_timeline") {
    Copy-Item -Path "src/pages/clinical_timeline/*" -Destination $targetClinicalTimeline -Force -Recurse
}
if (Test-Path "src/pages/messages") {
    Copy-Item -Path "src/pages/messages/*" -Destination $targetMessages -Force -Recurse
}
if (Test-Path "src/pages/notification") {
    Copy-Item -Path "src/pages/notification/*" -Destination $targetNotification -Force -Recurse
}
if (Test-Path "src/pages/audit_log") {
    Copy-Item -Path "src/pages/audit_log/*" -Destination $targetAuditLog -Force -Recurse
}
if (Test-Path "src/pages/nurse_work") {
    Copy-Item -Path "src/pages/nurse_work/*" -Destination $targetNurseWork -Force -Recurse
}
if (Test-Path "src/app") {
    Copy-Item -Path "src/app/*.fxml" -Destination $targetApp -Force
}
if (Test-Path "src/app/styles") {
    Copy-Item -Path "src/app/styles/*" -Destination $targetAppStyles -Force
}
if (Test-Path "src/sound") {
    Copy-Item -Path "src/sound/*" -Destination $targetSound -Force
}

$legacyLoginView = Join-Path $targetViews "LoginView.fxml"
if (Test-Path $legacyLoginView) {
    Remove-Item -Path $legacyLoginView -Force
}
$legacyFeatureLoginView = "out/production/untitledSmartPatientMonitoringSystem/ui/javafx/features/login/LoginView.fxml"
if (Test-Path $legacyFeatureLoginView) {
    Remove-Item -Path $legacyFeatureLoginView -Force
}
$legacyOldLoginFolder = "out/production/untitledSmartPatientMonitoringSystem/ui/javafx/pages/login"
if (Test-Path $legacyOldLoginFolder) {
    Remove-Item -Path $legacyOldLoginFolder -Recurse -Force
}
$legacyOldDashboardFolder = "out/production/untitledSmartPatientMonitoringSystem/ui/javafx/pages/dashboard"
if (Test-Path $legacyOldDashboardFolder) {
    Remove-Item -Path $legacyOldDashboardFolder -Recurse -Force
}
$legacyOldPatientsFolder = "out/production/untitledSmartPatientMonitoringSystem/ui/javafx/pages/patients"
if (Test-Path $legacyOldPatientsFolder) {
    Remove-Item -Path $legacyOldPatientsFolder -Recurse -Force
}
$legacyOldUsersFolder = "out/production/untitledSmartPatientMonitoringSystem/ui/javafx/users"
if (Test-Path $legacyOldUsersFolder) {
    Remove-Item -Path $legacyOldUsersFolder -Recurse -Force
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
$legacyAppLayoutView = Join-Path $targetViews "AppLayout.fxml"
if (Test-Path $legacyAppLayoutView) {
    Remove-Item -Path $legacyAppLayoutView -Force
}
$legacyPlaceholderView = Join-Path $targetViews "PlaceholderView.fxml"
if (Test-Path $legacyPlaceholderView) {
    Remove-Item -Path $legacyPlaceholderView -Force
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
    "out/production/untitledSmartPatientMonitoringSystem/ui/javafx/pages/Alert",
    "out/production/untitledSmartPatientMonitoringSystem/ui/javafx/pages/medications",
    "out/production/untitledSmartPatientMonitoringSystem/ui/javafx/pages/scheduling",
    "out/production/untitledSmartPatientMonitoringSystem/ui/javafx/pages/certificates",
    "out/production/untitledSmartPatientMonitoringSystem/ui/javafx/pages/rooms_sections",
    "out/production/untitledSmartPatientMonitoringSystem/ui/javafx/pages/newborns",
    "out/production/untitledSmartPatientMonitoringSystem/ui/javafx/pages/deceased",
    "out/production/untitledSmartPatientMonitoringSystem/ui/javafx/pages/clinical_timeline",
    "out/production/untitledSmartPatientMonitoringSystem/ui/javafx/pages/messages",
    "out/production/untitledSmartPatientMonitoringSystem/ui/javafx/pages/notifications",
    "out/production/untitledSmartPatientMonitoringSystem/ui/javafx/pages/audit_logs",
    "out/production/untitledSmartPatientMonitoringSystem/ui/javafx/pages/nurse_work_queue"
)

foreach ($legacyFolder in $legacyPatientPages) {
    if (Test-Path $legacyFolder) {
        Remove-Item -Path $legacyFolder -Recurse -Force
    }
}

Write-Host "Resources synced successfully in the active project copy!"
