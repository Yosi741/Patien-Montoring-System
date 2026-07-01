$targetViews = "out/production/untitledSmartPatientMonitoringSystem/ui/javafx/views"
$targetPages = "out/production/untitledSmartPatientMonitoringSystem/ui/javafx/pages"
$targetStyles = "out/production/untitledSmartPatientMonitoringSystem/ui/javafx/styles"

New-Item -ItemType Directory -Force -Path $targetViews
New-Item -ItemType Directory -Force -Path $targetPages
New-Item -ItemType Directory -Force -Path $targetStyles

Copy-Item -Path "src/ui/javafx/views/*" -Destination $targetViews -Force
Copy-Item -Path "src/ui/javafx/pages/*" -Destination $targetPages -Force -Recurse
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

Write-Host "Resources synced successfully in the active project copy!"
