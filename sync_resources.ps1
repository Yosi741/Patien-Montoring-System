$targetViews = "out/production/untitledSmartPatientMonitoringSystem/ui/javafx/views"
$targetStyles = "out/production/untitledSmartPatientMonitoringSystem/ui/javafx/styles"

New-Item -ItemType Directory -Force -Path $targetViews
New-Item -ItemType Directory -Force -Path $targetStyles

Copy-Item -Path "src/ui/javafx/views/*" -Destination $targetViews -Force
Copy-Item -Path "src/ui/javafx/styles/*" -Destination $targetStyles -Force

Write-Host "Resources synced successfully in the active project copy!"
