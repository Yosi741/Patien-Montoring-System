package ui.javafx.pages.Alert;

import ui.javafx.pages.patients.Patient;

public class CriticalAlertManager {

    public static void checkPatient(Patient patient) {
        AlarmService.checkPatient(patient);
    }

    public static void stopAlarm() {
        AlarmService.stopAlarm();
    }

    public static void muteAlarm() {
        AlarmService.acknowledgeAlarm();
    }
}
