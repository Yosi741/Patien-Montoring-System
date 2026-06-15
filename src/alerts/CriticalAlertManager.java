package alerts;

import models.Patient;
import ui.javafx.services.AlarmService;

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
