package database;

import models.UserProfile;

import java.io.*;
import java.util.ArrayList;

public class UserProfileStorage {
    private static final String FILE_PATH = "data/user_profiles.txt";
    private static final String DELIMITER = "\\|";

    public static UserProfile getProfile(String username) {
        for (UserProfile profile : loadProfiles()) {
            if (profile.getUsername().equals(username)) return profile;
        }
        return new UserProfile(username, username, "", "", "");
    }

    public static void saveProfile(UserProfile profile) {
        ArrayList<UserProfile> profiles = loadProfiles();
        boolean updated = false;
        for (int i = 0; i < profiles.size(); i++) {
            if (profiles.get(i).getUsername().equals(profile.getUsername())) {
                profiles.set(i, profile);
                updated = true;
                break;
            }
        }
        if (!updated) profiles.add(profile);
        saveProfiles(profiles);
    }

    private static ArrayList<UserProfile> loadProfiles() {
        ArrayList<UserProfile> profiles = new ArrayList<>();
        try {
            File file = new File(FILE_PATH);
            if (!file.exists()) return profiles;
            BufferedReader reader = new BufferedReader(new FileReader(file));
            String line;
            while ((line = reader.readLine()) != null) {
                String[] data = line.split(DELIMITER, -1);
                if (data.length == 5) profiles.add(new UserProfile(data[0], data[1], data[2], data[3], data[4]));
            }
            reader.close();
        } catch (Exception e) {
            System.out.println("Error loading profiles: " + e.getMessage());
        }
        return profiles;
    }

    private static void saveProfiles(ArrayList<UserProfile> profiles) {
        try {
            new File("data").mkdirs();
            PrintWriter writer = new PrintWriter(new FileWriter(FILE_PATH));
            for (UserProfile p : profiles) {
                writer.println(clean(p.getUsername()) + "|" + clean(p.getDisplayName()) + "|" + clean(p.getPhone()) + "|" + clean(p.getEmail()) + "|" + clean(p.getPhotoPath()));
            }
            writer.close();
        } catch (Exception e) {
            System.out.println("Error saving profiles: " + e.getMessage());
        }
    }

    private static String clean(String value) {
        return value == null ? "" : value.replace("|", " ").replace("\n", " ");
    }
}
