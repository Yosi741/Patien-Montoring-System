package gui;

import database.FileStorage;
import database.HospitalData;
import database.NotificationStorage;
import logs.AuditLog;
import models.Patient;
import services.CertificateService;
import services.RolePermissionService;
import users.Session;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class DeathPronouncementGUI extends JFrame {

    private final Patient patient;
    private JTextField doctorName;
    private JTextField doctorId;
    private JTextField deathTime;
    private JTextField cause;
    private JTextArea summary;
    private JTextArea notes;
    private JTextField signature;
    private JTextField signatureImagePath;
    private JTextField pdfPath;

    public DeathPronouncementGUI(Patient patient) {
        this.patient = patient;
        setTitle("Death Pronouncement - " + patient.getName());
        setSize(820, 760);
        setLocationRelativeTo(null);
        NavigationManager.configureChildWindow(this);

        if (!RolePermissionService.canPronounceDeath(Session.getCurrentUser())
                || !RolePermissionService.canAccessPatient(Session.getCurrentUser(), patient)) {
            JOptionPane.showMessageDialog(this, "You are not authorized to pronounce or edit death information for this patient.");
            dispose();
            return;
        }

        JPanel main = UITheme.appPanel(new BorderLayout(14, 14));
        main.setBorder(new EmptyBorder(22, 22, 22, 22));

        JLabel title = UITheme.title(patient.isDeceased() ? "Edit Death Information" : "Secure Death Pronouncement", 26);

        JPanel form = UITheme.cardPanel();
        form.setLayout(new GridLayout(11, 2, 12, 12));

        JTextField patientId = field(patient.getPatientId());
        doctorName = field(valueOrDefault(patient.getPronouncingDoctorName(), Session.getUsername()));
        doctorId = field(patient.getPronouncingDoctorId());
        JTextField section = field(patient.getSection());
        deathTime = field(valueOrDefault(patient.getDeathDateTime(), LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"))));
        cause = field(patient.getDeathCause());
        summary = area(patient.getDeathClinicalSummary());
        notes = area(patient.getDeathNotes());
        signature = field(valueOrDefault(patient.getPronouncingDoctorName(), ""));
        signatureImagePath = field("");
        pdfPath = field(patient.getDeathCertificatePath());

        patientId.setEditable(false);
        section.setEditable(false);
        signatureImagePath.setEditable(false);
        pdfPath.setEditable(false);

        form.add(new JLabel("Patient ID:")); form.add(patientId);
        form.add(new JLabel("Doctor Name:")); form.add(doctorName);
        form.add(new JLabel("Doctor ID:")); form.add(doctorId);
        form.add(new JLabel("Section:")); form.add(section);
        form.add(new JLabel("Date/Time of Death:")); form.add(deathTime);
        form.add(new JLabel("Cause of Death:")); form.add(cause);
        form.add(new JLabel("Clinical Summary:")); form.add(new JScrollPane(summary));
        form.add(new JLabel("Notes:")); form.add(new JScrollPane(notes));
        form.add(new JLabel("Doctor Signature:")); form.add(signature);
        form.add(new JLabel("Signature Image:")); form.add(signatureImagePath);
        form.add(new JLabel("PDF Path:")); form.add(pdfPath);

        JCheckBox confirm = new JCheckBox("I confirm this sensitive legal medical action.");

        JButton generateButton = UITheme.button(patient.isDeceased() ? "Regenerate PDF" : "Generate PDF", UITheme.DANGER);
        JButton openButton = UITheme.secondaryButton("Open PDF");
        JButton saveButton = UITheme.button(patient.isDeceased() ? "Save Corrections" : "Confirm Death", UITheme.PRIMARY);
        JButton homeButton = NavigationManager.homeButton(this);
        JButton importSignatureButton = UITheme.secondaryButton("Import Signature");

        generateButton.setEnabled(RolePermissionService.canGenerateCertificates(Session.getCurrentUser()));
        saveButton.setEnabled(!patient.isDeceased() || RolePermissionService.canEditDeathRecord(Session.getCurrentUser()));
        openButton.addActionListener(e -> openPdf());
        generateButton.addActionListener(e -> generatePdf());
        saveButton.addActionListener(e -> saveDeathInfo(confirm.isSelected()));
        importSignatureButton.addActionListener(e -> importSignature());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttons.setOpaque(false);
        buttons.add(importSignatureButton);
        buttons.add(openButton);
        buttons.add(generateButton);
        buttons.add(saveButton);
        buttons.add(homeButton);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setOpaque(false);
        bottom.add(confirm, BorderLayout.WEST);
        bottom.add(buttons, BorderLayout.EAST);

        main.add(title, BorderLayout.NORTH);
        main.add(form, BorderLayout.CENTER);
        main.add(bottom, BorderLayout.SOUTH);

        add(main);
    }

    private void saveDeathInfo(boolean confirmed) {
        if (!confirmed || doctorId.getText().trim().isEmpty()
                || cause.getText().trim().isEmpty() || signature.getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Doctor ID, cause of death, signature, and confirmation are required.");
            return;
        }

        boolean correction = patient.isDeceased();
        patient.setStatus("DECEASED");
        patient.setDeathDateTime(deathTime.getText().trim());
        patient.setDeathCause(cause.getText().trim());
        patient.setDeathClinicalSummary(summary.getText().trim());
        patient.setDeathNotes(notes.getText().trim());
        patient.setPronouncingDoctorName(doctorName.getText().trim());
        patient.setPronouncingDoctorId(doctorId.getText().trim());

        if (patient.getDeathCertificatePath().isBlank()) {
            generatePdf();
        }

        FileStorage.savePatients(HospitalData.patientManager.getPatients());
        AuditLog.addLog(Session.getUsername(), correction
                ? "Edited death information for: " + patient.getName()
                : "Pronounced patient DECEASED: " + patient.getName());
        JOptionPane.showMessageDialog(this, correction ? "Death information updated." : "Patient marked DECEASED.");
    }

    private void generatePdf() {
        try {
            patient.setDeathDateTime(deathTime.getText().trim());
            patient.setDeathCause(cause.getText().trim());
            patient.setDeathClinicalSummary(summary.getText().trim());
            patient.setDeathNotes(notes.getText().trim());
            patient.setPronouncingDoctorName(doctorName.getText().trim());
            patient.setPronouncingDoctorId(doctorId.getText().trim());

            String path = CertificateService.generateDeathCertificate(
                    patient,
                    doctorName.getText().trim(),
                    doctorId.getText().trim(),
                    deathTime.getText().trim(),
                    cause.getText().trim(),
                    summary.getText().trim(),
                    notes.getText().trim(),
                    signature.getText().trim(),
                    signatureImagePath.getText().trim()
            );
            patient.setDeathCertificatePath(path);
            pdfPath.setText(path);
            FileStorage.savePatients(HospitalData.patientManager.getPatients());
            AuditLog.addLog(Session.getUsername(), "Generated/regenerated death certificate PDF for: " + patient.getName());
            NotificationStorage.addNotification("ALL", "INFO", "Death certificate generated for patient: " + patient.getName());
            JOptionPane.showMessageDialog(this, "Death certificate PDF generated:\n" + path);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Could not generate PDF: " + ex.getMessage());
        }
    }

    private void openPdf() {
        if (pdfPath.getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "No PDF has been generated yet.");
            return;
        }
        try {
            Desktop.getDesktop().open(new File(pdfPath.getText().trim()));
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Could not open PDF. Saved at:\n" + pdfPath.getText().trim());
        }
    }

    private void importSignature() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Signature images (png, jpg, jpeg)", "png", "jpg", "jpeg"));
        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            signatureImagePath.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private JTextField field(String value) {
        JTextField field = new JTextField(value);
        UITheme.styleTextField(field);
        return field;
    }

    private JTextArea area(String value) {
        JTextArea area = new JTextArea(value);
        area.setRows(3);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        return area;
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
