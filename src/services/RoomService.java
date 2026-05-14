package services;

import database.HospitalData;
import database.RoomStorage;
import models.Patient;
import models.RoomInfo;

import java.util.ArrayList;
import java.util.LinkedHashSet;

public class RoomService {

    public static String[] getSections() {
        LinkedHashSet<String> sections = RoomStorage.loadSections();
        return sections.toArray(new String[0]);
    }

    public static ArrayList<RoomInfo> getAllRooms() {
        return RoomStorage.loadRooms();
    }

    public static ArrayList<String> getRoomsForSection(String section) {
        ArrayList<String> rooms = new ArrayList<>();

        for (RoomInfo room : RoomStorage.loadRooms()) {
            if (room.getSectionName().equals(section)) {
                rooms.add(room.getRoomNumber());
            }
        }

        return rooms;
    }

    public static String detectSectionFromRoom(String roomNumber) {
        for (RoomInfo room : RoomStorage.loadRooms()) {
            if (room.getRoomNumber().equals(roomNumber)) {
                return room.getSectionName();
            }
        }
        String[] sections = getSections();
        return sections.length == 0 ? "ER" : sections[0];
    }

    public static boolean isRoomFull(String section, String room, String ignoredPatientId) {
        return getRoomOccupancy(section, room, ignoredPatientId) >= getCapacity(section, room);
    }

    public static boolean roomExists(String section, String roomNumber) {
        for (RoomInfo room : RoomStorage.loadRooms()) {
            if (room.getSectionName().equals(section) && room.getRoomNumber().equals(roomNumber)) {
                return true;
            }
        }
        return false;
    }

    public static int getRoomOccupancy(String section, String room, String ignoredPatientId) {
        int count = 0;

        for (Patient patient : HospitalData.patientManager.getPatients()) {
            if (ignoredPatientId != null && patient.getPatientId().equals(ignoredPatientId)) {
                continue;
            }
            if (!patient.isDeceased()
                    && patient.getSection().equals(section)
                    && patient.getRoom().equals(room)) {
                count++;
            }
        }

        return count;
    }

    public static int getCapacity(String section) {
        ArrayList<String> rooms = getRoomsForSection(section);
        if (rooms.isEmpty()) {
            return 0;
        }
        return getCapacity(section, rooms.get(0));
    }

    public static int getCapacity(String section, String roomNumber) {
        for (RoomInfo room : RoomStorage.loadRooms()) {
            if (room.getSectionName().equals(section) && room.getRoomNumber().equals(roomNumber)) {
                return room.getCapacity();
            }
        }
        return 0;
    }

    public static String validateNewSection(String sectionName) {
        if (sectionName == null || sectionName.trim().isEmpty()) {
            return "Section name cannot be empty.";
        }
        for (String section : getSections()) {
            if (section.equalsIgnoreCase(sectionName.trim())) {
                return "Section name already exists.";
            }
        }
        return null;
    }

    public static String addSection(String sectionName) {
        String error = validateNewSection(sectionName);
        if (error != null) {
            return error;
        }
        ArrayList<RoomInfo> rooms = RoomStorage.loadRooms();
        rooms.add(new RoomInfo(sectionName.trim(), "Room-1", 1));
        RoomStorage.saveRooms(rooms);
        return null;
    }

    public static String renameSection(String oldName, String newName) {
        if (oldName == null || oldName.isBlank()) {
            return "Please select a section.";
        }
        if (newName == null || newName.trim().isEmpty()) {
            return "Section name cannot be empty.";
        }
        for (String section : getSections()) {
            if (!section.equals(oldName) && section.equalsIgnoreCase(newName.trim())) {
                return "Section name already exists.";
            }
        }

        ArrayList<RoomInfo> rooms = RoomStorage.loadRooms();
        for (RoomInfo room : rooms) {
            if (room.getSectionName().equals(oldName)) {
                room.setSectionName(newName.trim());
            }
        }
        for (Patient patient : HospitalData.patientManager.getPatients()) {
            if (patient.getSection().equals(oldName)) {
                patient.setSection(newName.trim());
            }
        }
        database.FileStorage.savePatients(HospitalData.patientManager.getPatients());
        RoomStorage.saveRooms(rooms);
        return null;
    }

    public static String deleteSection(String sectionName) {
        for (Patient patient : HospitalData.patientManager.getPatients()) {
            if (!patient.isDeceased() && patient.getSection().equals(sectionName)) {
                return "Cannot delete section while active patients are assigned to it.";
            }
        }

        ArrayList<RoomInfo> rooms = RoomStorage.loadRooms();
        rooms.removeIf(room -> room.getSectionName().equals(sectionName));
        RoomStorage.saveRooms(rooms);
        return null;
    }

    public static String addRoom(String sectionName, String roomNumber, int capacity) {
        String error = validateRoom(sectionName, roomNumber, capacity, null);
        if (error != null) {
            return error;
        }
        ArrayList<RoomInfo> rooms = RoomStorage.loadRooms();
        rooms.add(new RoomInfo(sectionName, roomNumber.trim(), capacity));
        RoomStorage.saveRooms(rooms);
        return null;
    }

    public static String updateRoom(String sectionName, String oldRoomNumber, String newRoomNumber, int capacity) {
        String error = validateRoom(sectionName, newRoomNumber, capacity, oldRoomNumber);
        if (error != null) {
            return error;
        }
        ArrayList<RoomInfo> rooms = RoomStorage.loadRooms();
        for (RoomInfo room : rooms) {
            if (room.getSectionName().equals(sectionName) && room.getRoomNumber().equals(oldRoomNumber)) {
                room.setRoomNumber(newRoomNumber.trim());
                room.setCapacity(capacity);
            }
        }
        for (Patient patient : HospitalData.patientManager.getPatients()) {
            if (patient.getSection().equals(sectionName) && patient.getRoom().equals(oldRoomNumber)) {
                patient.setRoom(newRoomNumber.trim());
            }
        }
        database.FileStorage.savePatients(HospitalData.patientManager.getPatients());
        RoomStorage.saveRooms(rooms);
        return null;
    }

    public static String deleteRoom(String sectionName, String roomNumber) {
        if (getRoomOccupancy(sectionName, roomNumber, null) > 0) {
            return "Cannot delete room while active patients are assigned to it.";
        }
        ArrayList<RoomInfo> rooms = RoomStorage.loadRooms();
        rooms.removeIf(room -> room.getSectionName().equals(sectionName) && room.getRoomNumber().equals(roomNumber));
        RoomStorage.saveRooms(rooms);
        return null;
    }

    private static String validateRoom(String sectionName, String roomNumber, int capacity, String oldRoomNumber) {
        if (sectionName == null || sectionName.isBlank()) {
            return "Please select a section.";
        }
        if (roomNumber == null || roomNumber.trim().isEmpty()) {
            return "Room number cannot be empty.";
        }
        if (capacity <= 0) {
            return "Room capacity must be positive.";
        }

        for (RoomInfo room : RoomStorage.loadRooms()) {
            boolean sameOriginal = oldRoomNumber != null
                    && room.getSectionName().equals(sectionName)
                    && room.getRoomNumber().equals(oldRoomNumber);
            if (!sameOriginal
                    && room.getSectionName().equals(sectionName)
                    && room.getRoomNumber().equalsIgnoreCase(roomNumber.trim())) {
                return "Room number already exists inside this section.";
            }
        }
        int occupancy = oldRoomNumber == null ? 0 : getRoomOccupancy(sectionName, oldRoomNumber, null);
        if (capacity < occupancy) {
            return "Capacity cannot be lower than current occupancy.";
        }
        return null;
    }
}
