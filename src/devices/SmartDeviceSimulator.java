package devices;

import alerts.CriticalAlertManager;
import database.FileStorage;
import database.HospitalData;
import models.Patient;
import models.VitalSign;

import java.util.Random;

public class SmartDeviceSimulator {

    private static boolean running = false;

    public static void startMonitoring(Patient patient) {

        if (running) {
            return;
        }

        running = true;

        Thread monitorThread = new Thread(() -> {

            Random random = new Random();

            while (running) {

                try {

                    double temperature =
                            36 + (random.nextDouble() * 4);

                    int heartRate =
                            60 + random.nextInt(80);

                    int systolic =
                            100 + random.nextInt(70);

                    int diastolic =
                            60 + random.nextInt(40);

                    int oxygen =
                            85 + random.nextInt(15);

                    VitalSign vitalSign =
                            new VitalSign(
                                    temperature,
                                    heartRate,
                                    systolic,
                                    diastolic,
                                    oxygen
                            );

                    patient.setVitalSign(vitalSign);

                    FileStorage.savePatients(
                            HospitalData.patientManager.getPatients()
                    );

                    CriticalAlertManager.checkPatient(patient);

                    System.out.println(
                            "Smart monitor updated vitals for: "
                                    + patient.getName()
                    );

                    Thread.sleep(5000);

                } catch (Exception e) {

                    System.out.println(
                            "Monitor error: "
                                    + e.getMessage()
                    );
                }
            }
        });

        monitorThread.start();
    }

    public static void stopMonitoring() {

        running = false;

    }
}