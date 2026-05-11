package gui;

import ai.AIAnalysis;
import database.HospitalData;
import models.Patient;
import users.User;

import javax.swing.*;
import javax.swing.RowFilter;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;

public class PatientGUI extends JFrame {

    private JTable patientTable;
    private DefaultTableModel tableModel;
    private TableRowSorter<DefaultTableModel> sorter;
    private JTextField searchField;
    private JComboBox<String> riskFilter;
    private User currentUser;

    public PatientGUI(JFrame previousWindow, User currentUser) {

        this.currentUser = currentUser;

        setTitle("Hospital Admin Panel");
        setSize(1400, 850);
        setLocationRelativeTo(null);

        Color bg = new Color(243, 247, 251);

        JPanel main = new JPanel(new BorderLayout());
        main.setBackground(bg);

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(Color.WHITE);
        header.setBorder(new EmptyBorder(20, 25, 20, 25));

        JLabel title = new JLabel("Hospital Patient Management", SwingConstants.CENTER);
        title.setFont(new Font("Segoe UI", Font.BOLD, 34));
        title.setForeground(new Color(20, 45, 80));

        searchField = new JTextField();
        searchField.setPreferredSize(new Dimension(250, 40));
        searchField.setFont(new Font("Segoe UI", Font.PLAIN, 16));

        riskFilter = new JComboBox<>(new String[]{
                "All", "Normal", "Warning", "Critical", "No Data"
        });
        riskFilter.setPreferredSize(new Dimension(150, 40));

        JPanel rightHeader = new JPanel();
        rightHeader.setOpaque(false);
        rightHeader.add(new JLabel("Search: "));
        rightHeader.add(searchField);
        rightHeader.add(Box.createHorizontalStrut(15));
        rightHeader.add(new JLabel("Risk: "));
        rightHeader.add(riskFilter);

        JPanel topRightWrapper = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        topRightWrapper.setOpaque(false);
        topRightWrapper.add(rightHeader);

        header.add(title, BorderLayout.CENTER);
        header.add(topRightWrapper, BorderLayout.EAST);
        main.add(header, BorderLayout.NORTH);

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 15));
        toolbar.setBackground(bg);

        JButton addPatientButton = modernButton("Add Patient", new Color(40, 120, 190));
        JButton editPatientButton = modernButton("Edit Patient", new Color(230, 145, 40));
        JButton deletePatientButton = modernButton("Delete Patient", new Color(180, 55, 55));
        JButton vitalsButton = modernButton("Add Vitals", new Color(50, 150, 90));
        JButton monitorButton = modernButton("Monitor Patient", new Color(120, 80, 180));
        JButton refreshButton = modernButton("Refresh", new Color(80, 90, 110));
        JButton backButton = modernButton("Back", new Color(200, 70, 70));

        applyPermissions(addPatientButton, editPatientButton, deletePatientButton, vitalsButton, monitorButton);

        toolbar.add(addPatientButton);
        toolbar.add(editPatientButton);
        toolbar.add(deletePatientButton);
        toolbar.add(vitalsButton);
        toolbar.add(monitorButton);
        toolbar.add(refreshButton);
        toolbar.add(backButton);

        String[] columns = {
                "Patient ID", "Full Name", "Birth Date", "Age", "Gender", "Room", "Risk Level"
        };

        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        patientTable = new JTable(tableModel);
        sorter = new TableRowSorter<>(tableModel);
        patientTable.setRowSorter(sorter);

        patientTable.setRowHeight(45);
        patientTable.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        patientTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 16));
        patientTable.getTableHeader().setBackground(new Color(30, 95, 150));
        patientTable.getTableHeader().setForeground(Color.WHITE);

        JScrollPane scrollPane = new JScrollPane(patientTable);
        scrollPane.setBorder(new EmptyBorder(0, 20, 20, 20));

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setBackground(bg);
        centerPanel.add(toolbar, BorderLayout.NORTH);
        centerPanel.add(scrollPane, BorderLayout.CENTER);

        main.add(centerPanel, BorderLayout.CENTER);
        add(main);

        addPatientButton.addActionListener(e -> new AddPatientGUI().setVisible(true));

        refreshButton.addActionListener(e -> refreshPatients());

        vitalsButton.addActionListener(e -> {
            Patient selectedPatient = getSelectedPatient();
            if (selectedPatient == null) return;
            new AddVitalSignGUI(selectedPatient).setVisible(true);
        });

        monitorButton.addActionListener(e -> {
            Patient selectedPatient = getSelectedPatient();
            if (selectedPatient == null) return;
            new PatientDashboardGUI(selectedPatient).setVisible(true);
        });

        editPatientButton.addActionListener(e -> {
            Patient selectedPatient = getSelectedPatient();
            if (selectedPatient == null) return;
            new EditPatientGUI(selectedPatient).setVisible(true);
        });

        deletePatientButton.addActionListener(e -> {
            Patient selectedPatient = getSelectedPatient();
            if (selectedPatient == null) return;

            int confirm = JOptionPane.showConfirmDialog(
                    this,
                    "Are you sure you want to delete this patient?",
                    "Delete Confirmation",
                    JOptionPane.YES_NO_OPTION
            );

            if (confirm == JOptionPane.YES_OPTION) {
                HospitalData.patientManager.deletePatient(selectedPatient);

                logs.AuditLog.addLog(
                        currentUser.getUsername(),
                        "Deleted patient: " + selectedPatient.getName()
                );

                refreshPatients();

                JOptionPane.showMessageDialog(this, "Patient deleted successfully");
            }
        });

        backButton.addActionListener(e -> {
            previousWindow.setVisible(true);
            dispose();
        });

        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { filterTable(); }
            public void removeUpdate(DocumentEvent e) { filterTable(); }
            public void changedUpdate(DocumentEvent e) { filterTable(); }
        });

        riskFilter.addActionListener(e -> filterTable());

        refreshPatients();
    }

    private Patient getSelectedPatient() {
        int selectedRow = patientTable.getSelectedRow();

        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Please select a patient first");
            return null;
        }

        int modelRow = patientTable.convertRowIndexToModel(selectedRow);
        return HospitalData.patientManager.getPatients().get(modelRow);
    }

    private void applyPermissions(
            JButton addPatientButton,
            JButton editPatientButton,
            JButton deletePatientButton,
            JButton vitalsButton,
            JButton monitorButton
    ) {
        String role = currentUser.getRole();

        if (role.equals("Admin")) {
            return;
        }

        if (role.equals("Doctor")) {
            deletePatientButton.setEnabled(false);
        }

        if (role.equals("Nurse")) {
            addPatientButton.setEnabled(false);
            editPatientButton.setEnabled(false);
            deletePatientButton.setEnabled(false);
        }
    }

    private void refreshPatients() {
        tableModel.setRowCount(0);

        for (Patient patient : HospitalData.patientManager.getPatients()) {
            String risk = AIAnalysis.analyzeRisk(patient.getVitalSign());

            Object[] row = {
                    patient.getPatientId(),
                    patient.getName(),
                    patient.getBirthDate(),
                    patient.getAge(),
                    patient.getGender(),
                    patient.getRoom(),
                    risk
            };

            tableModel.addRow(row);
        }

        filterTable();
    }

    private void filterTable() {
        try {
            String search = searchField.getText();
            String risk = riskFilter.getSelectedItem().toString();

            RowFilter<DefaultTableModel, Object> searchFilter =
                    RowFilter.regexFilter("(?i)" + search);

            if (risk.equals("All")) {
                sorter.setRowFilter(searchFilter);
            } else {
                RowFilter<DefaultTableModel, Object> riskOnlyFilter =
                        RowFilter.regexFilter(risk, 6);

                sorter.setRowFilter(RowFilter.andFilter(
                        java.util.List.of(searchFilter, riskOnlyFilter)
                ));
            }

        } catch (Exception e) {
            sorter.setRowFilter(null);
        }
    }

    private JButton modernButton(String text, Color color) {
        JButton button = new JButton(text);

        button.setFocusPainted(false);
        button.setBackground(color);
        button.setForeground(Color.WHITE);
        button.setFont(new Font("Segoe UI", Font.BOLD, 15));
        button.setBorder(new EmptyBorder(12, 18, 12, 18));

        return button;
    }
}