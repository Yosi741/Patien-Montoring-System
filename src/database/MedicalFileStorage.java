package database;

import models.MedicalFile;
import models.Patient;
import users.Session;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.UUID;

public class MedicalFileStorage {

    private static final String FILE_INDEX = "data/medical_files.txt";
    private static final String FILES_DIR = "data/files";
    private static final String DELIMITER = "\\|";

    public static MedicalFile uploadFile(Patient patient, File sourceFile) throws IOException {
        new File("data").mkdirs();
        File patientFolder = new File(FILES_DIR, patient.getPatientId());
        patientFolder.mkdirs();

        String originalName = sourceFile.getName();
        String cleanName = originalName.replaceAll("[^a-zA-Z0-9._-]", "_");
        String fileId = UUID.randomUUID().toString();
        String storedName = fileId + "_" + cleanName;
        File destination = new File(patientFolder, storedName);

        Files.copy(sourceFile.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);

        String uploadedAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"));
        MedicalFile medicalFile = new MedicalFile(
                fileId,
                patient.getPatientId(),
                originalName,
                destination.getPath(),
                getExtension(originalName),
                Session.getUsername(),
                uploadedAt
        );

        appendMedicalFile(medicalFile);
        return medicalFile;
    }

    public static ArrayList<MedicalFile> getFilesForPatient(String patientId) {
        ArrayList<MedicalFile> results = new ArrayList<>();

        for (MedicalFile file : loadAllFiles()) {
            if (file.getPatientId().equals(patientId)) {
                results.add(file);
            }
        }

        return results;
    }

    public static ArrayList<MedicalFile> loadAllFiles() {
        ArrayList<MedicalFile> files = new ArrayList<>();

        try {
            File file = new File(FILE_INDEX);
            if (!file.exists()) {
                return files;
            }

            BufferedReader reader = new BufferedReader(new FileReader(file));
            String line;

            while ((line = reader.readLine()) != null) {
                String[] data = line.split(DELIMITER, -1);
                if (data.length == 7) {
                    files.add(new MedicalFile(
                            data[0],
                            data[1],
                            data[2],
                            data[3],
                            data[4],
                            data[5],
                            data[6]
                    ));
                }
            }

            reader.close();

        } catch (Exception e) {
            System.out.println("Error loading medical files: " + e.getMessage());
        }

        return files;
    }

    private static void appendMedicalFile(MedicalFile medicalFile) throws IOException {
        PrintWriter writer = new PrintWriter(new FileWriter(FILE_INDEX, true));
        writer.println(
                escape(medicalFile.getFileId()) + "|" +
                        escape(medicalFile.getPatientId()) + "|" +
                        escape(medicalFile.getOriginalName()) + "|" +
                        escape(medicalFile.getStoredPath()) + "|" +
                        escape(medicalFile.getFileType()) + "|" +
                        escape(medicalFile.getUploadedBy()) + "|" +
                        escape(medicalFile.getUploadedAt())
        );
        writer.close();
    }

    private static String getExtension(String name) {
        int dot = name.lastIndexOf('.');
        if (dot == -1 || dot == name.length() - 1) {
            return "unknown";
        }
        return name.substring(dot + 1).toLowerCase();
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("|", " ");
    }
}
