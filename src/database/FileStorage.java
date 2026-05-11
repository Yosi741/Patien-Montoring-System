package database;

import models.Patient;
import models.VitalSign;

import java.io.*;
import java.util.ArrayList;

public class FileStorage {

    private static final String FILE_PATH = "data/patients.txt";

    public static void savePatients(ArrayList<Patient> patients) {
        try {
            new File("data").mkdirs();

            PrintWriter writer = new PrintWriter(new FileWriter(FILE_PATH));

            for (Patient patient : patients) {
                VitalSign v = patient.getVitalSign();

                String vitalsData = "null,null,null,null,null";

                if (v != null) {
                    vitalsData =
                            v.getTemperature() + "," +
                                    v.getHeartRate() + "," +
                                    v.getSystolicPressure() + "," +
                                    v.getDiastolicPressure() + "," +
                                    v.getOxygenLevel();
                }

                writer.println(
                        patient.getPatientId() + "," +
                                patient.getFirstName() + "," +
                                patient.getLastName() + "," +
                                patient.getBirthDate() + "," +
                                patient.getGender() + "," +
                                patient.getRoom() + "," +
                                vitalsData
                );
            }

            writer.close();

        } catch (Exception e) {
            System.out.println("Error saving patients: " + e.getMessage());
        }
    }

    public static ArrayList<Patient> loadPatients() {
        ArrayList<Patient> patients = new ArrayList<>();

        try {
            File file = new File(FILE_PATH);

            if (!file.exists()) {
                return patients;
            }

            BufferedReader reader = new BufferedReader(new FileReader(FILE_PATH));
            String line;

            while ((line = reader.readLine()) != null) {
                String[] data = line.split(",");

                if (data.length >= 6) {
                    Patient patient = new Patient(
                            data[0],
                            data[1],
                            data[2],
                            data[3],
                            data[4],
                            data[5]
                    );

                    if (data.length == 11 && !data[6].equals("null")) {
                        VitalSign vitalSign = new VitalSign(
                                Double.parseDouble(data[6]),
                                Integer.parseInt(data[7]),
                                Integer.parseInt(data[8]),
                                Integer.parseInt(data[9]),
                                Integer.parseInt(data[10])
                        );

                        patient.setVitalSign(vitalSign);
                    }

                    patients.add(patient);
                }
            }

            reader.close();

        } catch (Exception e) {
            System.out.println("Error loading patients: " + e.getMessage());
        }

        return patients;
    }
}