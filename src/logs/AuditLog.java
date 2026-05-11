package logs;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class AuditLog {

    private static final String FILE_PATH = "data/audit_logs.txt";

    public static void addLog(String username, String action) {

        try {
            new java.io.File("data").mkdirs();

            java.io.PrintWriter writer =
                    new java.io.PrintWriter(
                            new java.io.FileWriter(FILE_PATH, true)
                    );

            String time = LocalDateTime.now().format(
                    DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss")
            );

            writer.println(time + " | " + username + " | " + action);

            writer.close();

        } catch (Exception e) {
            System.out.println("Error writing audit log: " + e.getMessage());
        }
    }
}