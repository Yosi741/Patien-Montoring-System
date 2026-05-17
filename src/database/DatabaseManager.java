package database;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {

    private static final String DATABASE_DIR = "data";
    private static final String DATABASE_FILE = DATABASE_DIR + "/smart_patient_monitoring.db";
    private static final String JDBC_URL = "jdbc:sqlite:" + DATABASE_FILE;

    private DatabaseManager() {
    }

    public static Connection getConnection() throws SQLException {
        ensureDatabaseDirectory();
        Connection connection = DriverManager.getConnection(JDBC_URL);
        configure(connection);
        return connection;
    }

    public static String getDatabasePath() {
        return DATABASE_FILE;
    }

    public static boolean testConnection() {
        try (Connection connection = getConnection()) {
            return connection.isValid(2);
        } catch (SQLException e) {
            System.out.println("SQLite connection test failed: " + e.getMessage());
            return false;
        }
    }

    private static void ensureDatabaseDirectory() {
        File directory = new File(DATABASE_DIR);
        if (!directory.exists()) {
            directory.mkdirs();
        }
    }

    private static void configure(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA journal_mode = WAL");
            statement.execute("PRAGMA busy_timeout = 5000");
        }
    }
}
