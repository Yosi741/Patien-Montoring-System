package pages.patient.services;

import pages.patient.MedicalFile;

import java.util.ArrayList;

public class FileAnalysisService {

    public static ArrayList<String> analyzeAdvice(MedicalFile medicalFile) {
        ArrayList<String> advice = new ArrayList<>();
        if (medicalFile == null) {
            return advice;
        }
        advice.add("Review uploaded file metadata and extracted text in Medical Files.");
        return advice;
    }

    public static ArrayList<String> extractPatientRecordItems(MedicalFile medicalFile) {
        ArrayList<String> items = new ArrayList<>();
        if (medicalFile == null) {
            return items;
        }
        items.add("Original file: " + medicalFile.getOriginalName());
        items.add("Category: " + medicalFile.getFileType());
        items.add("Uploaded by: " + medicalFile.getUploadedBy());
        return items;
    }
}
