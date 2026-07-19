package pages.alert;

import pages.patient.patient_detail.Patient;

public class CriticalAlertManager {

    public static void checkPatient(Patient patient) {
        AlarmService.checkPatient(patient);
    }
}
