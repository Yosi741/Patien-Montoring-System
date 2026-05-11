package database;

import users.User;

import java.io.*;
import java.util.ArrayList;

public class UserStorage {

    private static final String FILE_PATH = "data/users.txt";

    public static ArrayList<User> loadUsers() {
        ArrayList<User> users = new ArrayList<>();

        try {
            new File("data").mkdirs();
            File file = new File(FILE_PATH);

            if (!file.exists()) {
                createDefaultUsers();
            }

            BufferedReader reader = new BufferedReader(new FileReader(FILE_PATH));
            String line;

            while ((line = reader.readLine()) != null) {
                String[] data = line.split(",");

                if (data.length == 3) {
                    users.add(new User(data[0], data[1], data[2]));
                }
            }

            reader.close();

        } catch (Exception e) {
            System.out.println("Error loading users: " + e.getMessage());
        }

        return users;
    }

    private static void createDefaultUsers() {
        try {
            new File("data").mkdirs();

            PrintWriter writer = new PrintWriter(new FileWriter(FILE_PATH));

            writer.println("admin,1234,Admin");
            writer.println("doctor,1234,Doctor");
            writer.println("nurse,1234,Nurse");
            writer.println("dr_ahmad,1234,Doctor");
            writer.println("nurse_lina,1234,Nurse");

            writer.close();

        } catch (Exception e) {
            System.out.println("Error creating default users: " + e.getMessage());
        }
    }
    public static void addUser(String username, String password, String role) {
        try {
            new File("data").mkdirs();

            PrintWriter writer =
                    new PrintWriter(new FileWriter(FILE_PATH, true));

            writer.println(username + "," + password + "," + role);

            writer.close();

        } catch (Exception e) {
            System.out.println("Error adding user: " + e.getMessage());
        }
    }

    public static void deleteUser(String username) {
        ArrayList<User> users = loadUsers();

        users.removeIf(user ->
                user.getUsername().equals(username)
        );

        saveUsers(users);
    }

    private static void saveUsers(ArrayList<User> users) {
        try {
            new File("data").mkdirs();

            PrintWriter writer =
                    new PrintWriter(new FileWriter(FILE_PATH));

            for (User user : users) {
                writer.println(
                        user.getUsername() + "," +
                                user.getPassword() + "," +
                                user.getRole()
                );
            }

            writer.close();

        } catch (Exception e) {
            System.out.println("Error saving users: " + e.getMessage());
        }
    }
    public static void updateUser(
            String oldUsername,
            String newUsername,
            String newPassword,
            String newRole
    ) {
        ArrayList<User> users = loadUsers();

        for (int i = 0; i < users.size(); i++) {
            User user = users.get(i);

            if (user.getUsername().equals(oldUsername)) {
                users.set(
                        i,
                        new User(newUsername, newPassword, newRole)
                );
                break;
            }
        }

        saveUsers(users);
    }
}