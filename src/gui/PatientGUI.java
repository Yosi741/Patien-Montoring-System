package gui;

import ai.AIAnalysis;
import database.HospitalData;
import models.Patient;
import services.RolePermissionService;
import users.User;

import javax.swing.*;
import javax.swing.RowFilter;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.util.ArrayList;

public class PatientGUI extends JFrame {

    private JTable patientTable;
    private DefaultTableModel tableModel;
    private TableRowSorter<DefaultTableModel> sorter;
    private JTextField searchField;
    private JComboBox<String> riskFilter;
    private User currentUser;
    private ArrayList<Patient> visiblePatients = new ArrayList<>();

    public PatientGUI(JFrame previousWindow, User currentUser) {

        this.currentUser = currentUser;
        NavigationManager.configureDashboardReturnOnClose(this);

        setTitle("Hospital Patient Management");
        setSize(1400, 850);
        setLocationRelativeTo(null);

        Color bg = UITheme.BACKGROUND;

        JPanel main = UITheme.appPanel(new BorderLayout());

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(UITheme.SURFACE);
        header.setBorder(new EmptyBorder(20, 25, 20, 25));

        JLabel title = new JLabel("Hospital Patient Management", SwingConstants.CENTER);
        title.setFont(UITheme.font(Font.BOLD, 34));
        title.setForeground(UITheme.TEXT);

        searchField = new JTextField();
        searchField.setPreferredSize(new Dimension(250, 40));
        UITheme.styleTextField(searchField);

        riskFilter = new JComboBox<>(new String[]{
                "All", "Normal", "Warning", "Critical", "No Data"
        });
        riskFilter.setPreferredSize(new Dimension(150, 40));
        riskFilter.setFont(UITheme.font(Font.PLAIN, 15));

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
        JPanel northStack = new JPanel(new BorderLayout());
        northStack.setOpaque(false);
        northStack.add(new HospitalHeaderPanel("Hospital Patient Management"), BorderLayout.NORTH);
        northStack.add(header, BorderLayout.SOUTH);
        main.add(northStack, BorderLayout.NORTH);

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 15));
        toolbar.setBackground(bg);

        JButton addPatientButton = UITheme.button("Add Patient", UITheme.PRIMARY);
        JButton editPatientButton = UITheme.button("Edit Patient", UITheme.WARNING);
        JButton deletePatientButton = UITheme.button("Delete Patient", UITheme.DANGER);
        JButton vitalsButton = UITheme.button("Add Vitals", UITheme.SUCCESS);
        JButton monitorButton = UITheme.button("Monitor Patient", new Color(103, 83, 170));
        JButton uploadButton = UITheme.button("Upload Medical File", new Color(32, 132, 122));
        JButton historyButton = UITheme.button("Medical History", new Color(72, 111, 146));
        JButton vitalsHistoryButton = UITheme.button("Vitals History", new Color(72, 126, 112));
        JButton deathButton = UITheme.button("Pronounce Death", UITheme.DANGER);
        JButton refreshButton = UITheme.secondaryButton("Refresh");
        JButton homeButton = UITheme.button("Back to Dashboard", UITheme.PRIMARY_DARK);

        applyPermissions(addPatientButton, editPatientButton, deletePatientButton, vitalsButton, monitorButton, uploadButton, historyButton, deathButton);

        toolbar.add(addPatientButton);
        toolbar.add(editPatientButton);
        toolbar.add(deletePatientButton);
        toolbar.add(vitalsButton);
        toolbar.add(monitorButton);
        toolbar.add(uploadButton);
        toolbar.add(historyButton);
        toolbar.add(vitalsHistoryButton);
        toolbar.add(deathButton);
        toolbar.add(refreshButton);
        toolbar.add(homeButton);

        String[] columns = {
                "Patient ID", "Full Name", "Birth Date", "Age", "Gender", "Section", "Room", "Status", "Risk Level"
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
        patientTable.setDefaultRenderer(Object.class, new RiskRowRenderer());

        UITheme.styleTable(patientTable);

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

        uploadButton.addActionListener(e -> {
            Patient selectedPatient = getSelectedPatient();
            if (selectedPatient == null) return;
            new FileUploadGUI(selectedPatient, this::refreshPatients).setVisible(true);
        });

        historyButton.addActionListener(e -> {
            Patient selectedPatient = getSelectedPatient();
            if (selectedPatient == null) return;
            new PatientHistoryGUI(selectedPatient).setVisible(true);
        });

        vitalsHistoryButton.addActionListener(e -> {
            Patient selectedPatient = getSelectedPatient();
            if (selectedPatient == null) return;
            new VitalsHistoryGUI(selectedPatient).setVisible(true);
        });

        deathButton.addActionListener(e -> {
            Patient selectedPatient = getSelectedPatient();
            if (selectedPatient == null) return;
            new DeathPronouncementGUI(selectedPatient).setVisible(true);
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

        homeButton.addActionListener(e -> NavigationManager.returnHome(this));

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
        return visiblePatients.get(modelRow);
    }

    private void applyPermissions(
            JButton addPatientButton,
            JButton editPatientButton,
            JButton deletePatientButton,
            JButton vitalsButton,
            JButton monitorButton,
            JButton uploadButton,
            JButton historyButton,
            JButton deathButton
    ) {
        addPatientButton.setEnabled(RolePermissionService.canEditPatient(currentUser));
        editPatientButton.setEnabled(RolePermissionService.canEditPatient(currentUser));
        deletePatientButton.setEnabled(RolePermissionService.canDeletePatient(currentUser));
        vitalsButton.setEnabled(RolePermissionService.canAddVitals(currentUser));
        monitorButton.setEnabled(RolePermissionService.canAddVitals(currentUser));
        uploadButton.setEnabled(RolePermissionService.canEditPatient(currentUser) || RolePermissionService.canAddVitals(currentUser));
        historyButton.setEnabled(RolePermissionService.canViewSensitiveHistory(currentUser));
        deathButton.setEnabled(RolePermissionService.canPronounceDeath(currentUser));
    }

    private void refreshPatients() {
        tableModel.setRowCount(0);
        visiblePatients.clear();

        for (Patient patient : HospitalData.patientManager.getPatients()) {
            if (patient.isDeceased()) {
                continue;
            }
            if (!RolePermissionService.canAccessPatient(currentUser, patient)) {
                continue;
            }

            String risk = AIAnalysis.analyzeRisk(patient.getVitalSign());
            visiblePatients.add(patient);

            Object[] row = {
                    patient.getPatientId(),
                    patient.getName(),
                    patient.getBirthDate(),
                    patient.getAge(),
                    patient.getGender(),
                    patient.getSection(),
                    patient.getRoom(),
                    patient.getStatus(),
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
                        RowFilter.regexFilter(risk, 8);

                sorter.setRowFilter(RowFilter.andFilter(
                        java.util.List.of(searchFilter, riskOnlyFilter)
                ));
            }

        } catch (Exception e) {
            sorter.setRowFilter(null);
        }
    }

    private class RiskRowRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            Component component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            int modelRow = table.convertRowIndexToModel(row);
            String risk = tableModel.getValueAt(modelRow, 8).toString();

            if (!isSelected) {
                if (risk.equals("Critical")) {
                    component.setBackground(new Color(255, 220, 220));
                    component.setForeground(new Color(120, 20, 20));
                } else if (risk.equals("Warning")) {
                    component.setBackground(new Color(255, 246, 218));
                    component.setForeground(new Color(120, 84, 10));
                } else {
                    component.setBackground(Color.WHITE);
                    component.setForeground(UITheme.TEXT);
                }
            }

            if (column == 8 && risk.equals("Normal")) {
                setText("STABLE");
            } else if (column == 8) {
                setText(risk.toUpperCase());
            }

            return component;
        }
    }

}
