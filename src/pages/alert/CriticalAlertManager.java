package pages.alert;

import pages.patient.Patient;

public class CriticalAlertManager {

    public static void checkPatient(Patient patient) {
        AlarmService.checkPatient(patient);
    }
}
