package database;

import models.MedicalDevice;

import java.io.*;
import java.util.ArrayList;

public class DeviceStorage {

    private static final String FILE_PATH = "data/devices.txt";
    private static final String DELIMITER = "\\|";

    public static void saveDevices(ArrayList<MedicalDevice> devices) {
        try {
            new File("data").mkdirs();
            PrintWriter writer = new PrintWriter(new FileWriter(FILE_PATH));

            for (MedicalDevice device : devices) {
                writer.println(
                        clean(device.getDeviceId()) + "|" +
                                clean(device.getDeviceName()) + "|" +
                                clean(device.getDeviceType()) + "|" +
                                clean(device.getSerialNumber()) + "|" +
                                clean(device.getConnectionStatus()) + "|" +
                                clean(device.getLastConnectionTime()) + "|" +
                                clean(device.getPatientId())
                );
            }

            writer.close();
        } catch (Exception e) {
            System.out.println("Error saving devices: " + e.getMessage());
        }
    }

    public static ArrayList<MedicalDevice> loadDevices() {
        ArrayList<MedicalDevice> devices = new ArrayList<>();

        try {
            File file = new File(FILE_PATH);
            if (!file.exists()) {
                return devices;
            }

            BufferedReader reader = new BufferedReader(new FileReader(file));
            String line;

            while ((line = reader.readLine()) != null) {
                String[] data = line.split(DELIMITER, -1);
                if (data.length == 7) {
                    devices.add(new MedicalDevice(data[0], data[1], data[2], data[3], data[4], data[5], data[6]));
                }
            }

            reader.close();
        } catch (Exception e) {
            System.out.println("Error loading devices: " + e.getMessage());
        }

        return devices;
    }

    public static void upsertDevice(MedicalDevice device) {
        ArrayList<MedicalDevice> devices = loadDevices();
        boolean updated = false;

        for (int i = 0; i < devices.size(); i++) {
            if (devices.get(i).getDeviceId().equals(device.getDeviceId())) {
                devices.set(i, device);
                updated = true;
                break;
            }
        }

        if (!updated) {
            devices.add(device);
        }

        saveDevices(devices);
    }

    private static String clean(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("|", " ");
    }
}
