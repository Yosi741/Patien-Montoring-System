package database;

import models.VitalRecord;

import java.io.*;
import java.util.ArrayList;

public class VitalStorage {

    private static final String FILE_PATH = "data/vitals_history.txt";
    private static final String DELIMITER = "\\|";

    public static void addRecord(VitalRecord record) {
        try {
            new File("data").mkdirs();
            PrintWriter writer = new PrintWriter(new FileWriter(FILE_PATH, true));
            writer.println(
                    clean(record.getRecordId()) + "|" +
                            clean(record.getPatientId()) + "|" +
                            clean(record.getVitalType()) + "|" +
                            clean(record.getValue()) + "|" +
                            clean(record.getUnit()) + "|" +
                            clean(record.getDateTime()) + "|" +
                            clean(record.getSourceType()) + "|" +
                            clean(record.getStaffName()) + "|" +
                            clean(record.getDeviceId()) + "|" +
                            clean(record.getDeviceSerial()) + "|" +
                            clean(record.getDeviceName()) + "|" +
                            clean(record.getDeviceType())
            );
            writer.close();
        } catch (Exception e) {
            System.out.println("Error saving vital record: " + e.getMessage());
        }
    }

    public static ArrayList<VitalRecord> loadAllRecords() {
        ArrayList<VitalRecord> records = new ArrayList<>();

        try {
            File file = new File(FILE_PATH);
            if (!file.exists()) {
                return records;
            }

            BufferedReader reader = new BufferedReader(new FileReader(file));
            String line;

            while ((line = reader.readLine()) != null) {
                String[] data = line.split(DELIMITER, -1);
                if (data.length == 12) {
                    records.add(new VitalRecord(
                            data[0], data[1], data[2], data[3], data[4], data[5],
                            data[6], data[7], data[8], data[9], data[10], data[11]
                    ));
                }
            }

            reader.close();
        } catch (Exception e) {
            System.out.println("Error loading vital records: " + e.getMessage());
        }

        return records;
    }

    public static ArrayList<VitalRecord> getRecordsForPatient(String patientId) {
        ArrayList<VitalRecord> patientRecords = new ArrayList<>();

        for (VitalRecord record : loadAllRecords()) {
            if (record.getPatientId().equals(patientId)) {
                patientRecords.add(record);
            }
        }

        return patientRecords;
    }

    private static String clean(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("|", " ");
    }
}
