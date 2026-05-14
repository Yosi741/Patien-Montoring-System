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
                        clean(patient.getPatientId()) + "," +
                                clean(patient.getFirstName()) + "," +
                                clean(patient.getLastName()) + "," +
                                clean(patient.getBirthDate()) + "," +
                                clean(patient.getGender()) + "," +
                                clean(patient.getRoom()) + "," +
                                vitalsData + "," +
                                clean(patient.getSection()) + "," +
                                clean(patient.getStatus()) + "," +
                                clean(patient.getDiagnosis()) + "," +
                                clean(patient.getMedicalHistory()) + "," +
                                clean(patient.getCurrentMedications()) + "," +
                                clean(patient.getPastMedications()) + "," +
                                clean(patient.getAllergies()) + "," +
                                clean(patient.getFamilyHistory()) + "," +
                                clean(patient.getDeathDateTime()) + "," +
                                clean(patient.getDeathCause()) + "," +
                                clean(patient.getDeathClinicalSummary()) + "," +
                                clean(patient.getDeathNotes()) + "," +
                                clean(patient.getPronouncingDoctorName()) + "," +
                                clean(patient.getPronouncingDoctorId()) + "," +
                                clean(patient.getDeathCertificatePath()) + "," +
                                clean(patient.getDeathCertificateNumber())
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
                String[] data = line.split(",", -1);

                if (data.length >= 6) {
                    Patient patient = new Patient(
                            data[0],
                            data[1],
                            data[2],
                            data[3],
                            data[4],
                            data[5]
                    );

                    if (data.length >= 11 && !data[6].equals("null")) {
                        VitalSign vitalSign = new VitalSign(
                                Double.parseDouble(data[6]),
                                Integer.parseInt(data[7]),
                                Integer.parseInt(data[8]),
                                Integer.parseInt(data[9]),
                                Integer.parseInt(data[10])
                        );

                        patient.setVitalSign(vitalSign);
                    }

                    if (data.length >= 12) patient.setSection(data[11]);
                    if (data.length >= 13) patient.setStatus(data[12]);
                    if (data.length >= 14) patient.setDiagnosis(data[13]);
                    if (data.length >= 15) patient.setMedicalHistory(data[14]);
                    if (data.length >= 16) patient.setCurrentMedications(data[15]);
                    if (data.length >= 17) patient.setPastMedications(data[16]);
                    if (data.length >= 18) patient.setAllergies(data[17]);
                    if (data.length >= 19) patient.setFamilyHistory(data[18]);
                    if (data.length >= 20) patient.setDeathDateTime(data[19]);
                    if (data.length >= 21) patient.setDeathCause(data[20]);
                    if (data.length >= 22) patient.setDeathClinicalSummary(data[21]);
                    if (data.length >= 23) patient.setDeathNotes(data[22]);
                    if (data.length >= 24) patient.setPronouncingDoctorName(data[23]);
                    if (data.length >= 25) patient.setPronouncingDoctorId(data[24]);
                    if (data.length >= 26) patient.setDeathCertificatePath(data[25]);
                    if (data.length >= 27) patient.setDeathCertificateNumber(data[26]);

                    patients.add(patient);
                }
            }

            reader.close();

        } catch (Exception e) {
            System.out.println("Error loading patients: " + e.getMessage());
        }

        return patients;
    }

    private static String clean(String value) {
        if (value == null) {
            return "";
        }
        return value.replace(",", " ").replace("\n", " / ");
    }
}
