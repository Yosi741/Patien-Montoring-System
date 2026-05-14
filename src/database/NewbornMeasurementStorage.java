package database;

import models.NewbornMeasurement;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

public class NewbornMeasurementStorage {
    private static final String FILE_PATH = "data/newborn_measurements.txt";
    private static final String DELIMITER = "\\|";

    public static void addMeasurement(String babyId, String type, String value, String notes) {
        ArrayList<NewbornMeasurement> measurements = loadAll();
        measurements.add(new NewbornMeasurement(babyId, now(), type, value, notes));
        saveAll(measurements);
    }

    public static ArrayList<NewbornMeasurement> getForBaby(String babyId) {
        ArrayList<NewbornMeasurement> result = new ArrayList<>();
        for (NewbornMeasurement m : loadAll()) if (m.getBabyId().equals(babyId)) result.add(m);
        return result;
    }

    private static ArrayList<NewbornMeasurement> loadAll() {
        ArrayList<NewbornMeasurement> list = new ArrayList<>();
        try {
            File file = new File(FILE_PATH);
            if (!file.exists()) return list;
            BufferedReader reader = new BufferedReader(new FileReader(file));
            String line;
            while ((line = reader.readLine()) != null) {
                String[] data = line.split(DELIMITER, -1);
                if (data.length == 5) list.add(new NewbornMeasurement(data[0], data[1], data[2], data[3], data[4]));
            }
            reader.close();
        } catch (Exception e) {
            System.out.println("Error loading newborn measurements: " + e.getMessage());
        }
        return list;
    }

    private static void saveAll(ArrayList<NewbornMeasurement> list) {
        try {
            new File("data").mkdirs();
            PrintWriter writer = new PrintWriter(new FileWriter(FILE_PATH));
            for (NewbornMeasurement m : list) writer.println(clean(m.getBabyId()) + "|" + clean(m.getTimestamp()) + "|" + clean(m.getType()) + "|" + clean(m.getValue()) + "|" + clean(m.getNotes()));
            writer.close();
        } catch (Exception e) {
            System.out.println("Error saving newborn measurements: " + e.getMessage());
        }
    }

    private static String now() { return LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss")); }
    private static String clean(String value) { return value == null ? "" : value.replace("|", " ").replace("\n", " / "); }
}
