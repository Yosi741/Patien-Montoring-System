package database;

import models.NewbornRecord;

import java.io.*;
import java.util.ArrayList;

public class NewbornStorage {

    private static final String FILE_PATH = "data/newborns.txt";
    private static final String DELIMITER = "\\|";

    public static ArrayList<NewbornRecord> loadNewborns() {
        ArrayList<NewbornRecord> newborns = new ArrayList<>();
        try {
            File file = new File(FILE_PATH);
            if (!file.exists()) {
                return newborns;
            }
            BufferedReader reader = new BufferedReader(new FileReader(file));
            String line;
            while ((line = reader.readLine()) != null) {
                String[] data = line.split(DELIMITER, -1);
                if (data.length == 18) {
                    newborns.add(new NewbornRecord(
                            data[0], data[1], data[2], data[3], data[4], data[5], data[6],
                            data[7], data[8], Double.parseDouble(data[9]), data[10], data[11], data[12],
                            data[13], Boolean.parseBoolean(data[14]), data[15], data[16], data[17]
                    ));
                }
            }
            reader.close();
        } catch (Exception e) {
            System.out.println("Error loading newborns: " + e.getMessage());
        }
        return newborns;
    }

    public static void addNewborn(NewbornRecord newborn) {
        ArrayList<NewbornRecord> newborns = loadNewborns();
        newborns.add(newborn);
        saveNewborns(newborns);
    }

    public static void saveNewborns(ArrayList<NewbornRecord> newborns) {
        try {
            new File("data").mkdirs();
            PrintWriter writer = new PrintWriter(new FileWriter(FILE_PATH));
            for (NewbornRecord n : newborns) {
                writer.println(clean(n.getBabyId()) + "|" + clean(n.getBabyName()) + "|" + clean(n.getMotherId()) + "|"
                        + clean(n.getMotherFirstName()) + "|" + clean(n.getMotherLastName()) + "|"
                        + clean(n.getFatherFirstName()) + "|" + clean(n.getFatherLastName()) + "|"
                        + clean(n.getBirthDateTime()) + "|" + clean(n.getGender()) + "|" + n.getBirthWeightKg() + "|"
                        + clean(n.getDeliveryType()) + "|" + clean(n.getSection()) + "|" + clean(n.getRoom()) + "|"
                        + clean(n.getHospitalStatus()) + "|" + n.isPremature() + "|" + clean(n.getVitals()) + "|"
                        + clean(n.getNotes()) + "|" + clean(n.getCertificatePath()));
            }
            writer.close();
        } catch (Exception e) {
            System.out.println("Error saving newborns: " + e.getMessage());
        }
    }

    private static String clean(String value) {
        return value == null ? "" : value.replace("|", " ").replace("\n", " / ");
    }
}
