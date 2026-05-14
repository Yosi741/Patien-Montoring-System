package ai;

import models.MedicalFile;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MedicalFileAnalyzer {

    public static ArrayList<String> analyze(MedicalFile medicalFile) {
        ArrayList<String> notes = new ArrayList<>();

        if (!canReadContent(medicalFile.getFileType())) {
            notes.add("File uploaded for clinical review. Automated text analysis is available for TXT and CSV files.");
            return notes;
        }

        String content = readFile(medicalFile.getStoredPath()).toLowerCase(Locale.ROOT);
        if (content.isBlank()) {
            notes.add("Uploaded file is empty or could not be read. Recommend manual review.");
            return notes;
        }

        detectKeywords(content, notes);
        detectNumericValues(content, notes);

        if (notes.isEmpty()) {
            notes.add("No obvious abnormal keywords detected. Continue routine monitoring.");
        }

        return notes;
    }

    public static ArrayList<String> extractStructuredInfo(MedicalFile medicalFile) {
        ArrayList<String> extracted = new ArrayList<>();

        if (!canReadContent(medicalFile.getFileType())) {
            return extracted;
        }

        String content = readFile(medicalFile.getStoredPath());
        String[] lines = content.split("\\R");

        for (String line : lines) {
            String cleanLine = line.trim();
            String lower = cleanLine.toLowerCase(Locale.ROOT);

            if (lower.startsWith("diagnosis") || lower.contains("diagnosis:")) {
                extracted.add("Diagnosis: " + valueAfterColon(cleanLine));
            } else if (lower.startsWith("medication") || lower.contains("medication:")) {
                extracted.add("Medication: " + valueAfterColon(cleanLine));
            } else if (lower.startsWith("allergy") || lower.contains("allergy:")) {
                extracted.add("Allergy: " + valueAfterColon(cleanLine));
            } else if (lower.startsWith("family history") || lower.contains("family history:")) {
                extracted.add("Family history: " + valueAfterColon(cleanLine));
            } else if (lower.startsWith("doctor note") || lower.contains("doctor note:")) {
                extracted.add("Doctor note: " + valueAfterColon(cleanLine));
            } else if (lower.startsWith("visit summary") || lower.contains("visit summary:")) {
                extracted.add("Visit summary: " + valueAfterColon(cleanLine));
            } else if (lower.contains("glucose") || lower.contains("crp") || lower.contains("wbc")
                    || lower.contains("hemoglobin") || lower.contains("spo2")) {
                extracted.add("Blood test / observation: " + cleanLine);
            }
        }

        return extracted;
    }

    private static boolean canReadContent(String type) {
        return type.equals("txt") || type.equals("csv");
    }

    private static String readFile(String path) {
        StringBuilder builder = new StringBuilder();

        try {
            BufferedReader reader = new BufferedReader(new FileReader(new File(path)));
            String line;

            while ((line = reader.readLine()) != null) {
                builder.append(line).append("\n");
            }

            reader.close();
        } catch (Exception e) {
            System.out.println("Error reading medical file: " + e.getMessage());
        }

        return builder.toString();
    }

    private static void detectKeywords(String content, ArrayList<String> notes) {
        if (content.contains("low oxygen") || content.contains("spo2 low") || content.contains("hypoxia")) {
            notes.add("Check oxygen saturation again and assess respiratory status.");
        }
        if (content.contains("high glucose") || content.contains("hyperglycemia")) {
            notes.add("High glucose indicator detected. Recommend doctor review.");
        }
        if (content.contains("high crp") || content.contains("crp elevated")) {
            notes.add("Possible infection or inflammation indicator. Monitor patient closely.");
        }
        if (content.contains("high wbc") || content.contains("wbc elevated") || content.contains("leukocytosis")) {
            notes.add("Possible infection indicator from elevated WBC. Recommend doctor review.");
        }
        if (content.contains("fever") || content.contains("pyrexia")) {
            notes.add("Possible fever, monitor infection signs.");
        }
        if (content.contains("high blood pressure") || content.contains("hypertension")) {
            notes.add("High blood pressure indicator detected. Recheck BP and notify doctor if persistent.");
        }
        if (content.contains("low hemoglobin") || content.contains("anemia") || content.contains("anaemia")) {
            notes.add("Low hemoglobin indicator detected. Recommend clinical review.");
        }
        if (content.contains("critical")) {
            notes.add("Critical values detected, immediate review recommended.");
        }
    }

    private static void detectNumericValues(String content, ArrayList<String> notes) {
        addIfAbove(content, "glucose", 180, "High glucose value detected. Recommend doctor review.", notes);
        addIfBelow(content, "oxygen|spo2|o2", 90, "Critical oxygen value detected. Immediate respiratory assessment recommended.", notes);
        addIfAbove(content, "crp", 10, "High CRP value detected. Possible infection indicator.", notes);
        addIfAbove(content, "wbc", 11000, "High WBC value detected. Possible infection indicator.", notes);
        addIfAbove(content, "temperature|temp", 38, "High temperature value detected. Monitor infection signs.", notes);
        addIfBelow(content, "hemoglobin|hb|hgb", 12, "Low hemoglobin value detected. Recommend doctor review.", notes);
        addIfAbove(content, "systolic|bp", 140, "High blood pressure value detected. Recheck BP and monitor trends.", notes);
    }

    private static void addIfAbove(String content, String labelPattern, double threshold, String note, ArrayList<String> notes) {
        Pattern pattern = Pattern.compile("(" + labelPattern + ")\\s*[:=, ]\\s*(\\d+(\\.\\d+)?)");
        Matcher matcher = pattern.matcher(content);

        while (matcher.find()) {
            double value = Double.parseDouble(matcher.group(2));
            if (value >= threshold && !notes.contains(note)) {
                notes.add(note);
            }
        }
    }

    private static void addIfBelow(String content, String labelPattern, double threshold, String note, ArrayList<String> notes) {
        Pattern pattern = Pattern.compile("(" + labelPattern + ")\\s*[:=, ]\\s*(\\d+(\\.\\d+)?)");
        Matcher matcher = pattern.matcher(content);

        while (matcher.find()) {
            double value = Double.parseDouble(matcher.group(2));
            if (value < threshold && !notes.contains(note)) {
                notes.add(note);
            }
        }
    }

    private static String valueAfterColon(String line) {
        int colon = line.indexOf(":");
        if (colon >= 0 && colon + 1 < line.length()) {
            return line.substring(colon + 1).trim();
        }
        return line;
    }
}
