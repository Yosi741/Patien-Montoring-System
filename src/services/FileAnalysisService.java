package services;

import ai_Prototype.MedicalFileAnalyzer;
import models.MedicalFile;

import java.util.ArrayList;

public class FileAnalysisService {

    public static ArrayList<String> analyzeAdvice(MedicalFile medicalFile) {
        return MedicalFileAnalyzer.analyze(medicalFile);
    }

    public static ArrayList<String> extractPatientRecordItems(MedicalFile medicalFile) {
        return MedicalFileAnalyzer.extractStructuredInfo(medicalFile);
    }
}
