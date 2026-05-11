package gui;

import users.User;

import javax.swing.*;
import java.awt.*;

public class DashboardGUI extends JFrame {

    private User currentUser;

    public DashboardGUI(User user) {

        this.currentUser = user;

        setTitle("Hospital Dashboard - " + currentUser.getRole());
        setSize(700, 500);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        JPanel panel = new JPanel();
        panel.setLayout(new GridLayout(3, 2, 20, 20));
        panel.setBorder(BorderFactory.createEmptyBorder(30, 30, 30, 30));

        JButton patientsButton = new JButton("Patients");
        JButton alertsButton = new JButton("Alerts");
        JButton vitalsButton = new JButton("Vital Signs");
        JButton aiButton = new JButton("AI Analysis");
        JButton logsButton = new JButton("Audit Logs");
        JButton devicesButton = new JButton("Smart Devices");
        JButton usersButton = new JButton("User Management");

        patientsButton.addActionListener(e -> {
            PatientGUI patientGUI = new PatientGUI(this, currentUser);
            patientGUI.setVisible(true);
            this.setVisible(false);
        });

        logsButton.addActionListener(e -> {
            AuditLogGUI auditLogGUI = new AuditLogGUI();
            auditLogGUI.setVisible(true);
        });

        usersButton.addActionListener(e -> {

            UserManagementGUI userManagementGUI =
                    new UserManagementGUI();

            userManagementGUI.setVisible(true);

        });
        if (!currentUser.getRole().equals("Admin")) {
            usersButton.setEnabled(false);
        }

        if (!currentUser.getRole().equals("Admin")) {
            logsButton.setEnabled(false);
        }

        panel.add(patientsButton);
        panel.add(alertsButton);
        panel.add(vitalsButton);
        panel.add(aiButton);
        panel.add(logsButton);
        panel.add(devicesButton);
        panel.add(usersButton);

        add(panel);
    }
}