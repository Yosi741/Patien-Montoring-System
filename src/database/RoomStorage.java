package database;

import models.RoomInfo;

import java.io.*;
import java.util.ArrayList;
import java.util.LinkedHashSet;

public class RoomStorage {

    private static final String FILE_PATH = "data/rooms.txt";
    private static final String DELIMITER = "\\|";

    public static ArrayList<RoomInfo> loadRooms() {
        ensureDefaultRooms();
        ArrayList<RoomInfo> rooms = new ArrayList<>();

        try {
            BufferedReader reader = new BufferedReader(new FileReader(FILE_PATH));
            String line;

            while ((line = reader.readLine()) != null) {
                String[] data = line.split(DELIMITER, -1);
                if (data.length == 3) {
                    rooms.add(new RoomInfo(data[0], data[1], Integer.parseInt(data[2])));
                }
            }

            reader.close();
        } catch (Exception e) {
            System.out.println("Error loading rooms: " + e.getMessage());
        }

        return rooms;
    }

    public static void saveRooms(ArrayList<RoomInfo> rooms) {
        try {
            new File("data").mkdirs();
            PrintWriter writer = new PrintWriter(new FileWriter(FILE_PATH));

            for (RoomInfo room : rooms) {
                writer.println(clean(room.getSectionName()) + "|" + clean(room.getRoomNumber()) + "|" + room.getCapacity());
            }

            writer.close();
        } catch (Exception e) {
            System.out.println("Error saving rooms: " + e.getMessage());
        }
    }

    public static LinkedHashSet<String> loadSections() {
        LinkedHashSet<String> sections = new LinkedHashSet<>();
        for (RoomInfo room : loadRooms()) {
            sections.add(room.getSectionName());
        }
        return sections;
    }

    private static void ensureDefaultRooms() {
        File file = new File(FILE_PATH);
        if (file.exists()) {
            return;
        }

        ArrayList<RoomInfo> rooms = new ArrayList<>();
        addRange(rooms, "ER", 1, 30, 3, true);
        addRange(rooms, "Cardiology", 110, 130, 2, false);
        addRange(rooms, "Surgery", 140, 170, 2, false);
        addRange(rooms, "ICU", 200, 220, 1, false);
        addRange(rooms, "Pediatrics", 300, 330, 2, false);
        addRange(rooms, "Internal Medicine", 400, 430, 2, false);
        saveRooms(rooms);
    }

    private static void addRange(ArrayList<RoomInfo> rooms, String section, int start, int end, int capacity, boolean padded) {
        for (int room = start; room <= end; room++) {
            rooms.add(new RoomInfo(section, padded ? String.format("%03d", room) : String.valueOf(room), capacity));
        }
    }

    private static String clean(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("|", " ").trim();
    }
}
