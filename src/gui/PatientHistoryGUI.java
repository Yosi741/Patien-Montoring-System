package gui;

import database.FileStorage;
import database.HospitalData;
import database.MedicalFileStorage;
import models.MedicalFile;
import models.Patient;
import services.RolePermissionService;
import users.Session;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class PatientHistoryGUI extends JFrame {

    public PatientHistoryGUI(Patient patient) {
        setTitle("Sensitive Medical History - " + patient.getName());
        setSize(900, 720);
        setLocationRelativeTo(null);
        NavigationManager.configureChildWindow(this);

        if (!RolePermissionService.canViewSensitiveHistory(Session.getCurrentUser())) {
            JOptionPane.showMessageDialog(this, "You are not authorized to view sensitive patient history.");
            dispose();
            return;
        }

        JPanel main = UITheme.appPanel(new BorderLayout(14, 14));
        main.setBorder(new EmptyBorder(22, 22, 22, 22));

        JLabel title = UITheme.title("Patient Medical History - " + patient.getName(), 26);
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(title, BorderLayout.WEST);
        header.add(NavigationManager.homeButton(this), BorderLayout.EAST);

        JTabbedPane tabs = new JTabbedPane();
        JTextArea diagnosis = area(patient.getDiagnosis());
        JTextArea visits = area(patient.getMedicalHistory());
        JTextArea currentMeds = area(patient.getCurrentMedications());
        JTextArea pastMeds = area(patient.getPastMedications());
        JTextArea allergies = area(patient.getAllergies());
        JTextArea family = area(patient.getFamilyHistory());
        JTextArea files = area(filesText(patient));
        files.setEditable(false);

        tabs.addTab("Diagnosis", new JScrollPane(diagnosis));
        tabs.addTab("Visits/Reports", new JScrollPane(visits));
        tabs.addTab("Current Meds", new JScrollPane(currentMeds));
        tabs.addTab("Past Meds", new JScrollPane(pastMeds));
        tabs.addTab("Allergies", new JScrollPane(allergies));
        tabs.addTab("Family History", new JScrollPane(family));
        tabs.addTab("Uploaded Files", new JScrollPane(files));

        JButton saveButton = UITheme.button("Save History", UITheme.PRIMARY);
        saveButton.setEnabled(RolePermissionService.canEditPatient(Session.getCurrentUser()));
        saveButton.addActionListener(e -> {
            patient.setDiagnosis(diagnosis.getText().trim());
            patient.setMedicalHistory(visits.getText().trim());
            patient.setCurrentMedications(currentMeds.getText().trim());
            patient.setPastMedications(pastMeds.getText().trim());
            patient.setAllergies(allergies.getText().trim());
            patient.setFamilyHistory(family.getText().trim());
            FileStorage.savePatients(HospitalData.patientManager.getPatients());
            logs.AuditLog.addLog(Session.getUsername(), "Updated sensitive medical history for: " + patient.getName());
            JOptionPane.showMessageDialog(this, "Medical history saved.");
        });

        main.add(header, BorderLayout.NORTH);
        main.add(tabs, BorderLayout.CENTER);
        main.add(saveButton, BorderLayout.SOUTH);

        add(main);
    }

    private JTextArea area(String value) {
        JTextArea area = new JTextArea(value);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(UITheme.font(Font.PLAIN, 15));
        area.setBorder(new EmptyBorder(12, 12, 12, 12));
        return area;
    }

    private String filesText(Patient patient) {
        StringBuilder builder = new StringBuilder();
        for (MedicalFile file : MedicalFileStorage.getFilesForPatient(patient.getPatientId())) {
            builder.append(file.getUploadedAt()).append(" | ")
                    .append(file.getOriginalName()).append(" | ")
                    .append(file.getStoredPath()).append("\n");
        }
        if (builder.length() == 0) {
            builder.append("No uploaded files.");
        }
        return builder.toString();
    }
}
