package gui;

import database.HospitalData;
import models.Patient;
import services.RolePermissionService;
import users.Session;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;

public class DeceasedPatientsGUI extends JFrame {

    private DefaultTableModel model;
    private JTable table;
    private ArrayList<Patient> deceasedPatients = new ArrayList<>();
    private JLabel counterLabel;

    public DeceasedPatientsGUI() {
        setTitle("Deceased Patients");
        setSize(1200, 700);
        setLocationRelativeTo(null);
        NavigationManager.configureChildWindow(this);

        if (!RolePermissionService.canViewDeceasedPatients(Session.getCurrentUser())) {
            JOptionPane.showMessageDialog(this, "You are not authorized to view deceased patient records.");
            dispose();
            return;
        }

        JPanel main = UITheme.appPanel(new BorderLayout(14, 14));
        main.setBorder(new EmptyBorder(22, 22, 22, 22));

        JLabel title = UITheme.title("Deceased Patients", 26);
        counterLabel = new JLabel();
        counterLabel.setFont(UITheme.font(Font.BOLD, 16));
        counterLabel.setForeground(UITheme.MUTED);

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(title, BorderLayout.WEST);
        top.add(counterLabel, BorderLayout.EAST);

        model = new DefaultTableModel(
                new String[]{"Patient ID", "Patient Name", "Section", "Room", "Death Time", "Cause", "Doctor", "Certificate Path"}, 0
        ) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        table = new JTable(model);
        UITheme.styleTable(table);

        JButton detailsButton = UITheme.button("View Details", UITheme.PRIMARY);
        JButton openButton = UITheme.secondaryButton("Open Death Certificate");
        JButton editButton = UITheme.button("Edit / Regenerate", UITheme.WARNING);
        JButton refreshButton = UITheme.secondaryButton("Refresh");
        JButton homeButton = NavigationManager.homeButton(this);

        detailsButton.addActionListener(e -> openDetails());
        openButton.addActionListener(e -> openCertificate());
        editButton.addActionListener(e -> {
            Patient patient = selectedPatient();
            if (patient != null) {
                new DeathPronouncementGUI(patient).setVisible(true);
            }
        });
        refreshButton.addActionListener(e -> loadPatients());
        editButton.setEnabled(RolePermissionService.canEditDeathRecord(Session.getCurrentUser()));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        buttons.setOpaque(false);
        buttons.add(detailsButton);
        buttons.add(openButton);
        buttons.add(editButton);
        buttons.add(refreshButton);
        buttons.add(homeButton);

        JPanel northStack = new JPanel(new BorderLayout());
        northStack.setOpaque(false);
        northStack.add(new HospitalHeaderPanel("Deceased Patients"), BorderLayout.NORTH);
        northStack.add(top, BorderLayout.SOUTH);
        main.add(northStack, BorderLayout.NORTH);
        main.add(new JScrollPane(table), BorderLayout.CENTER);
        main.add(buttons, BorderLayout.SOUTH);

        add(main);
        loadPatients();
    }

    private void loadPatients() {
        model.setRowCount(0);
        deceasedPatients.clear();

        for (Patient patient : HospitalData.patientManager.getPatients()) {
            if (patient.isDeceased() && RolePermissionService.canAccessPatient(Session.getCurrentUser(), patient)) {
                deceasedPatients.add(patient);
                model.addRow(new Object[]{
                        patient.getPatientId(),
                        patient.getName(),
                        patient.getSection(),
                        patient.getRoom(),
                        patient.getDeathDateTime(),
                        patient.getDeathCause(),
                        patient.getPronouncingDoctorName(),
                        patient.getDeathCertificatePath()
                });
            }
        }
        counterLabel.setText("Total deceased patients: " + deceasedPatients.size());
    }

    private void openDetails() {
        Patient patient = selectedPatient();
        if (patient != null) {
            new DeceasedPatientDetailGUI(patient).setVisible(true);
        }
    }

    private void openCertificate() {
        Patient patient = selectedPatient();
        if (patient == null) return;
        if (patient.getDeathCertificatePath().isBlank()) {
            JOptionPane.showMessageDialog(this, "No death certificate PDF has been generated yet.");
            return;
        }
        try {
            Desktop.getDesktop().open(new File(patient.getDeathCertificatePath()));
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Could not open PDF. Saved at:\n" + patient.getDeathCertificatePath());
        }
    }

    private Patient selectedPatient() {
        int row = table.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Please select a deceased patient first.");
            return null;
        }
        return deceasedPatients.get(table.convertRowIndexToModel(row));
    }
}
