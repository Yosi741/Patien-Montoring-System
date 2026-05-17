package services;

import models.Patient;
import models.VitalSign;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.awt.*;
import java.io.File;
import java.io.IOException;

public class PdfTemplateOverlayWriter {

    private static final float LEFT = 72f;
    private static final float VALUE_LEFT = 230f;
    private static final float LINE_HEIGHT = 18f;
    private static final int VALUE_MAX_CHARS = 58;

    private PdfTemplateOverlayWriter() {
    }

    public static void writeBirthCertificate(File template, File output, String certificateNumber,
                                             String babyName, String motherId, String motherFirstName,
                                             String motherLastName, String fatherFirstName,
                                             String fatherLastName, String parentInfo,
                                             String birthDateTime, String gender, String birthWeight,
                                             String deliveryType, String staffName, String staffId,
                                             String notes, String signature, String signatureImagePath,
                                             String generatedAt) throws IOException {
        output.getParentFile().mkdirs();
        try (PDDocument document = PDDocument.load(template)) {
            PDPage page = ensureFirstPage(document);
            try (PDPageContentStream content = appendContent(document, page)) {
                PDRectangle box = page.getMediaBox();
                float y = box.getHeight() - 110f;

                drawTitle(content, "Birth Certificate", y);
                y -= 42f;
                y = drawField(content, "Certificate Number", certificateNumber, y);
                y = drawField(content, "Newborn Name", babyName, y);
                y = drawField(content, "Mother ID", motherId, y);
                y = drawField(content, "Mother Name", combine(motherFirstName, motherLastName), y);
                y = drawField(content, "Father Name", combine(fatherFirstName, fatherLastName), y);
                y = drawField(content, "Parent Contact", parentInfo, y);
                y = drawField(content, "Birth Date/Time", birthDateTime, y);
                y = drawField(content, "Gender", gender, y);
                y = drawField(content, "Birth Weight", birthWeight, y);
                y = drawField(content, "Delivery Type", deliveryType, y);
                y = drawField(content, "Doctor/Nurse", staffName + " / ID: " + staffId, y);
                y = drawField(content, "Notes", notes, y);
                y = drawField(content, "Signature", signature, y);
                drawSmallText(content, "Generated: " + safe(generatedAt), LEFT, 64f);
                drawSignatureImage(document, content, signatureImagePath, box.getWidth() - 250f, 72f, 170f, 58f);
            }
            document.save(output);
        }
    }

    public static void writeDeathCertificate(File template, File output, String certificateNumber,
                                             Patient patient, String doctorName, String doctorId,
                                             String dateTimeOfDeath, String cause, String summary,
                                             String notes, String signature, String signatureImagePath,
                                             String generatedAt) throws IOException {
        output.getParentFile().mkdirs();
        try (PDDocument document = PDDocument.load(template)) {
            PDPage page = ensureFirstPage(document);
            try (PDPageContentStream content = appendContent(document, page)) {
                PDRectangle box = page.getMediaBox();
                float y = box.getHeight() - 110f;

                drawTitle(content, "Death Certificate", y);
                y -= 42f;
                y = drawField(content, "Certificate Number", certificateNumber, y);
                y = drawField(content, "Patient ID", patient.getPatientId(), y);
                y = drawField(content, "Patient Name", patient.getName(), y);
                y = drawField(content, "Date of Birth", patient.getBirthDate(), y);
                y = drawField(content, "Section / Room", patient.getSection() + " / " + patient.getRoom(), y);
                y = drawField(content, "Death Date/Time", dateTimeOfDeath, y);
                y = drawField(content, "Cause of Death", cause, y);
                y = drawField(content, "Clinical Summary", summary, y);
                y = drawField(content, "Last Known Vitals", formatVitals(patient.getVitalSign()), y);
                y = drawField(content, "Pronouncing Doctor", doctorName + " / ID: " + doctorId, y);
                y = drawField(content, "Doctor Notes", notes, y);
                y = drawField(content, "Signature", signature, y);
                drawSmallText(content, "Generated: " + safe(generatedAt), LEFT, 64f);
                drawSignatureImage(document, content, signatureImagePath, box.getWidth() - 250f, 72f, 170f, 58f);
            }
            document.save(output);
        }
    }

    private static PDPage ensureFirstPage(PDDocument document) {
        if (document.getNumberOfPages() == 0) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);
            return page;
        }
        return document.getPage(0);
    }

    private static PDPageContentStream appendContent(PDDocument document, PDPage page) throws IOException {
        return new PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true);
    }

    private static void drawTitle(PDPageContentStream content, String title, float y) throws IOException {
        content.setNonStrokingColor(new Color(20, 70, 115));
        content.beginText();
        content.setFont(PDType1Font.HELVETICA_BOLD, 20);
        content.newLineAtOffset(LEFT, y);
        content.showText(title);
        content.endText();
    }

    private static float drawField(PDPageContentStream content, String label, String value, float y) throws IOException {
        String safeValue = safe(value);
        content.setNonStrokingColor(new Color(24, 38, 58));
        content.beginText();
        content.setFont(PDType1Font.HELVETICA_BOLD, 11);
        content.newLineAtOffset(LEFT, y);
        content.showText(safe(label) + ":");
        content.endText();

        String remaining = safeValue;
        boolean firstLine = true;
        do {
            String line = takeLine(remaining);
            remaining = remaining.substring(line.length()).trim();
            content.setNonStrokingColor(Color.BLACK);
            content.beginText();
            content.setFont(PDType1Font.HELVETICA, 11);
            content.newLineAtOffset(firstLine ? VALUE_LEFT : VALUE_LEFT + 16f, y);
            content.showText(line);
            content.endText();
            y -= LINE_HEIGHT;
            firstLine = false;
        } while (!remaining.isEmpty() && y > 120f);

        return y - 2f;
    }

    private static void drawSmallText(PDPageContentStream content, String text, float x, float y) throws IOException {
        content.setNonStrokingColor(new Color(102, 116, 130));
        content.beginText();
        content.setFont(PDType1Font.HELVETICA, 9);
        content.newLineAtOffset(x, y);
        content.showText(safe(text));
        content.endText();
    }

    private static void drawSignatureImage(PDDocument document, PDPageContentStream content, String path,
                                           float x, float y, float maxWidth, float maxHeight) {
        try {
            if (path == null || path.isBlank()) {
                return;
            }
            File file = new File(path);
            if (!file.exists()) {
                return;
            }
            PDImageXObject image = PDImageXObject.createFromFileByContent(file, document);
            float width = maxWidth;
            float height = image.getHeight() * (width / image.getWidth());
            if (height > maxHeight) {
                height = maxHeight;
                width = image.getWidth() * (height / image.getHeight());
            }
            content.drawImage(image, x, y, width, height);
        } catch (Exception ignored) {
            // Keep certificate generation reliable even if a signature image cannot be decoded.
        }
    }

    private static String takeLine(String value) {
        if (value.length() <= VALUE_MAX_CHARS) {
            return value;
        }
        int split = value.lastIndexOf(' ', VALUE_MAX_CHARS);
        if (split < 24) {
            split = VALUE_MAX_CHARS;
        }
        return value.substring(0, split).trim();
    }

    private static String combine(String first, String last) {
        return (safe(first) + " " + safe(last)).trim();
    }

    private static String formatVitals(VitalSign vitalSign) {
        if (vitalSign == null) {
            return "No vitals recorded";
        }
        return String.format("%.1f C, HR %d bpm, BP %d/%d mmHg, SpO2 %d%%",
                vitalSign.getTemperature(),
                vitalSign.getHeartRate(),
                vitalSign.getSystolicPressure(),
                vitalSign.getDiastolicPressure(),
                vitalSign.getOxygenLevel());
    }

    private static String safe(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "/")
                .replace("\n", " ")
                .replace("\r", " ")
                .replace("\t", " ")
                .replaceAll("[^\\x20-\\x7E]", "?")
                .trim();
    }
}
