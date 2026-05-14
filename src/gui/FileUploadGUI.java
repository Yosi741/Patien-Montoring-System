package gui;

import ai.AIAdviceEngine;
import database.MedicalFileStorage;
import logs.AuditLog;
import models.MedicalFile;
import models.Patient;
import services.FileAnalysisService;
import services.PatientService;
import users.Session;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;

public class FileUploadGUI extends JFrame {

    private Patient patient;
    private Runnable onUploadComplete;
    private JLabel selectedFileLabel;
    private JTextArea resultArea;
    private File selectedFile;

    public FileUploadGUI(Patient patient, Runnable onUploadComplete) {
        this.patient = patient;
        this.onUploadComplete = onUploadComplete;

        setTitle("Upload Medical File - " + patient.getName());
        setSize(620, 460);
        setLocationRelativeTo(null);
        NavigationManager.configureChildWindow(this);

        JPanel main = UITheme.appPanel(new BorderLayout(18, 18));
        main.setBorder(new EmptyBorder(24, 24, 24, 24));

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JLabel title = UITheme.title("Upload Medical File", 26);
        JLabel subtitle = new JLabel(patient.getName() + " | Patient ID " + patient.getPatientId());
        subtitle.setFont(UITheme.font(Font.PLAIN, 15));
        subtitle.setForeground(UITheme.MUTED);

        JPanel titleBox = new JPanel();
        titleBox.setOpaque(false);
        titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));
        titleBox.add(title);
        titleBox.add(Box.createVerticalStrut(6));
        titleBox.add(subtitle);

        header.add(titleBox, BorderLayout.WEST);
        header.add(NavigationManager.homeButton(this), BorderLayout.EAST);

        JPanel content = UITheme.cardPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        selectedFileLabel = new JLabel("No file selected");
        selectedFileLabel.setFont(UITheme.font(Font.PLAIN, 15));
        selectedFileLabel.setForeground(UITheme.MUTED);

        JButton chooseButton = UITheme.button("Choose TXT, CSV, PDF, or report", UITheme.PRIMARY);
        JButton uploadButton = UITheme.button("Upload Medical File", UITheme.SUCCESS);

        chooseButton.addActionListener(e -> chooseFile());
        uploadButton.addActionListener(e -> uploadFile());

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        actions.setOpaque(false);
        actions.add(chooseButton);
        actions.add(uploadButton);

        resultArea = new JTextArea();
        resultArea.setEditable(false);
        resultArea.setLineWrap(true);
        resultArea.setWrapStyleWord(true);
        resultArea.setFont(UITheme.font(Font.PLAIN, 14));
        resultArea.setBackground(new Color(248, 251, 253));
        resultArea.setBorder(new EmptyBorder(14, 14, 14, 14));
        resultArea.setText("Uploaded TXT and CSV files will be scanned for rule-based advice. PDF files are stored and listed for manual review.");

        content.add(new JLabel("Supported examples: blood tests, reports, medication notes, doctor notes, TXT, CSV, PDF."));
        content.add(Box.createVerticalStrut(16));
        content.add(selectedFileLabel);
        content.add(Box.createVerticalStrut(18));
        content.add(actions);
        content.add(Box.createVerticalStrut(18));
        content.add(new JScrollPane(resultArea));

        main.add(header, BorderLayout.NORTH);
        main.add(content, BorderLayout.CENTER);

        add(main);
    }

    private void chooseFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter(
                "Medical files (txt, csv, pdf, doc, docx)",
                "txt", "csv", "pdf", "doc", "docx"
        ));

        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            selectedFile = chooser.getSelectedFile();
            selectedFileLabel.setText(selectedFile.getName());
        }
    }

    private void uploadFile() {
        if (selectedFile == null) {
            JOptionPane.showMessageDialog(this, "Please choose a file first.");
            return;
        }

        try {
            MedicalFile medicalFile = MedicalFileStorage.uploadFile(patient, selectedFile);
            ArrayList<String> notes = FileAnalysisService.analyzeAdvice(medicalFile);
            ArrayList<String> extractedItems = FileAnalysisService.extractPatientRecordItems(medicalFile);
            AIAdviceEngine.saveAdvice(patient.getPatientId(), medicalFile.getOriginalName(), notes);

            AuditLog.addLog(Session.getUsername(), "Uploaded file for patient " + patient.getName() + ": " + medicalFile.getOriginalName());
            AuditLog.addLog(Session.getUsername(), "AI generated advice for patient " + patient.getName() + " from: " + medicalFile.getOriginalName());

            StringBuilder message = new StringBuilder();
            message.append("File uploaded successfully.\n\nGenerated advice:\n");
            for (String note : notes) {
                message.append("- ").append(note).append("\n");
            }

            if (!extractedItems.isEmpty()) {
                message.append("\nExtracted possible patient-record information:\n");
                for (String item : extractedItems) {
                    message.append("- ").append(item).append("\n");
                }

                int confirm = JOptionPane.showConfirmDialog(
                        this,
                        "AI extracted possible medical-record information.\nSave it to the patient record after staff review?",
                        "Confirm Extracted Information",
                        JOptionPane.YES_NO_OPTION
                );

                if (confirm == JOptionPane.YES_OPTION) {
                    PatientService.applyExtractedMedicalInfo(patient, extractedItems);
                    message.append("\nExtracted information was confirmed and saved to patient history.");
                } else {
                    message.append("\nExtracted information was not saved to patient history.");
                }
            } else {
                message.append("\nNo structured diagnosis/medication/allergy/history fields were extracted.");
            }

            resultArea.setText(message.toString());

            if (onUploadComplete != null) {
                onUploadComplete.run();
            }

            JOptionPane.showMessageDialog(this, "Medical file uploaded and analyzed.");

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Could not upload file: " + ex.getMessage());
        }
    }
}
