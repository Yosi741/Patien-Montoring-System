package database;

import models.MotherInfo;

import java.io.*;
import java.util.ArrayList;

public class MotherStorage {

    private static final String FILE_PATH = "data/mothers.txt";
    private static final String DELIMITER = "\\|";

    public static ArrayList<MotherInfo> loadMothers() {
        ArrayList<MotherInfo> mothers = new ArrayList<>();
        try {
            File file = new File(FILE_PATH);
            if (!file.exists()) {
                return mothers;
            }
            BufferedReader reader = new BufferedReader(new FileReader(file));
            String line;
            while ((line = reader.readLine()) != null) {
                String[] data = line.split(DELIMITER, -1);
                if (data.length == 5) {
                    mothers.add(new MotherInfo(data[0], data[1], data[2], data[3], data[4]));
                }
            }
            reader.close();
        } catch (Exception e) {
            System.out.println("Error loading mothers: " + e.getMessage());
        }
        return mothers;
    }

    public static MotherInfo findById(String motherId) {
        for (MotherInfo mother : loadMothers()) {
            if (mother.getMotherId().equals(motherId)) {
                return mother;
            }
        }
        return null;
    }

    public static String addMother(MotherInfo mother) {
        if (mother.getMotherId().isBlank()) {
            return "Mother ID cannot be empty.";
        }
        if (findById(mother.getMotherId()) != null) {
            return "Mother ID already exists.";
        }
        ArrayList<MotherInfo> mothers = loadMothers();
        mothers.add(mother);
        saveMothers(mothers);
        return null;
    }

    public static void saveMothers(ArrayList<MotherInfo> mothers) {
        try {
            new File("data").mkdirs();
            PrintWriter writer = new PrintWriter(new FileWriter(FILE_PATH));
            for (MotherInfo mother : mothers) {
                writer.println(clean(mother.getMotherId()) + "|" + clean(mother.getFirstName()) + "|"
                        + clean(mother.getLastName()) + "|" + clean(mother.getContactInfo()) + "|"
                        + clean(mother.getNotes()));
            }
            writer.close();
        } catch (Exception e) {
            System.out.println("Error saving mothers: " + e.getMessage());
        }
    }

    private static String clean(String value) {
        return value == null ? "" : value.replace("|", " ").replace("\n", " / ");
    }
}
