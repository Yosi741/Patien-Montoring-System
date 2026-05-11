package managers;

import database.FileStorage;
import models.Alert;
import models.Patient;
import models.VitalSign;

import java.util.ArrayList;

public class PatientManager {

    private ArrayList<Patient> patients;

    public PatientManager() {

        patients = FileStorage.loadPatients();

    }

    public void addPatient(Patient patient) {

        patients.add(patient);

        FileStorage.savePatients(patients);

        System.out.println(
                "Patient added. Total patients: "
                        + patients.size()
        );

    }
    public void deletePatient(Patient patient) {
        patients.remove(patient);
        database.FileStorage.savePatients(patients);
    }

    public ArrayList<Patient> getPatients() {

        return patients;

    }

    public ArrayList<Alert> generateAlerts(
            VitalSign vitalSign
    ) {

        ArrayList<Alert> alerts =
                new ArrayList<>();

        if (vitalSign == null) {

            return alerts;

        }

        if (vitalSign.getTemperature() >= 38.0) {

            alerts.add(
                    new Alert(
                            "High temperature detected",
                            "Warning"
                    )
            );

        }

        if (vitalSign.getHeartRate() > 100) {

            alerts.add(
                    new Alert(
                            "High heart rate detected",
                            "Warning"
                    )
            );

        }

        if (
                vitalSign.getSystolicPressure() >= 140 ||
                        vitalSign.getDiastolicPressure() >= 90
        ) {

            alerts.add(
                    new Alert(
                            "High blood pressure detected",
                            "Warning"
                    )
            );

        }

        if (vitalSign.getOxygenLevel() < 94) {

            alerts.add(
                    new Alert(
                            "Low oxygen level detected",
                            "Critical"
                    )
            );

        }

        return alerts;

    }

}