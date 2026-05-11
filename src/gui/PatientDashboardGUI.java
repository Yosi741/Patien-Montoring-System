package gui;

import ai.AIAnalysis;
import devices.SmartDeviceSimulator;
import models.Patient;
import models.VitalSign;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class PatientDashboardGUI extends JFrame {

    private JLabel tempLabel;
    private JLabel heartLabel;
    private JLabel pressureLabel;
    private JLabel oxygenLabel;
    private JLabel riskTitleLabel;

    public PatientDashboardGUI(Patient patient) {

        setTitle("Smart Patient Monitoring");
        setSize(1500, 850);
        setLocationRelativeTo(null);

        Color bg = new Color(243, 247, 251);

        JPanel main = new JPanel(new BorderLayout());
        main.setBackground(bg);

        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBackground(Color.WHITE);
        topBar.setBorder(new EmptyBorder(20, 30, 20, 30));

        JLabel patientName = new JLabel(patient.getName());
        patientName.setFont(new Font("Segoe UI", Font.BOLD, 34));
        patientName.setForeground(new Color(25, 35, 55));

        JLabel patientInfo = new JLabel(
                patient.getGender() + " | Age " + patient.getAge() + " | Room " + patient.getRoom()
        );
        patientInfo.setFont(new Font("Segoe UI", Font.PLAIN, 17));
        patientInfo.setForeground(new Color(110, 120, 130));

        JPanel leftTop = new JPanel();
        leftTop.setOpaque(false);
        leftTop.setLayout(new BoxLayout(leftTop, BoxLayout.Y_AXIS));
        leftTop.add(patientName);
        leftTop.add(Box.createVerticalStrut(8));
        leftTop.add(patientInfo);

        JButton backButton = styleButton("Back", new Color(30, 95, 150));
        backButton.addActionListener(e -> dispose());

        topBar.add(leftTop, BorderLayout.WEST);
        topBar.add(backButton, BorderLayout.EAST);
        main.add(topBar, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(20, 20));
        center.setBackground(bg);
        center.setBorder(new EmptyBorder(25, 25, 25, 25));

        JPanel vitalsBar = new JPanel(new GridLayout(1, 4, 20, 20));
        vitalsBar.setBackground(bg);

        VitalSign v = patient.getVitalSign();
        if (v == null) {
            v = new VitalSign(0, 0, 0, 0, 0);
        }

        tempLabel = new JLabel(v.getTemperature() + " °C");
        heartLabel = new JLabel(v.getHeartRate() + " bpm");
        pressureLabel = new JLabel(v.getSystolicPressure() + "/" + v.getDiastolicPressure());
        oxygenLabel = new JLabel(v.getOxygenLevel() + "%");

        vitalsBar.add(createVitalCard("Temperature", tempLabel, new Color(255, 245, 245), new Color(220, 70, 70)));
        vitalsBar.add(createVitalCard("Heart Rate", heartLabel, new Color(240, 248, 255), new Color(50, 110, 180)));
        vitalsBar.add(createVitalCard("Blood Pressure", pressureLabel, new Color(245, 250, 240), new Color(50, 140, 80)));
        vitalsBar.add(createVitalCard("SpO2", oxygenLabel, new Color(250, 245, 255), new Color(120, 70, 180)));

        center.add(vitalsBar, BorderLayout.NORTH);
        ECGPanel ecgPanel = new ECGPanel();

        ecgPanel.setPreferredSize(
                new Dimension(0, 180)
        );

        center.add(ecgPanel, BorderLayout.SOUTH);

        JPanel content = new JPanel(new GridLayout(1, 2, 20, 20));
        content.setBackground(bg);

        content.add(createPatientSummary(patient));
        content.add(createAlertsPanel(patient));

        center.add(content, BorderLayout.CENTER);
        main.add(center, BorderLayout.CENTER);

        add(main);

        startLiveUpdates(patient);
    }

    private JPanel createPatientSummary(Patient patient) {

        JPanel panel = modernPanel();

        panel.add(sectionTitle("Patient Summary"));

        panel.add(summaryLine("Patient ID", patient.getPatientId()));
        panel.add(summaryLine("Birth Date", patient.getBirthDate()));
        panel.add(summaryLine("Gender", patient.getGender()));
        panel.add(summaryLine("Room", patient.getRoom()));

        panel.add(Box.createVerticalStrut(25));

        JTextArea notes = new JTextArea();
        notes.setText(
                "AI Clinical Notes:\n\n" +
                        "Patient monitoring system is analyzing vital signs in real time.\n\n" +
                        "Current patient condition requires continuous observation."
        );
        notes.setLineWrap(true);
        notes.setWrapStyleWord(true);
        notes.setEditable(false);
        notes.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        notes.setBackground(new Color(248, 250, 252));
        notes.setBorder(new EmptyBorder(20, 20, 20, 20));

        panel.add(notes);

        return panel;
    }

    private JPanel createAlertsPanel(Patient patient) {

        JPanel panel = modernPanel();

        panel.add(sectionTitle("Clinical Alerts"));

        String risk = AIAnalysis.analyzeRisk(patient.getVitalSign());

        JPanel riskCard = new JPanel();
        riskCard.setLayout(new BoxLayout(riskCard, BoxLayout.Y_AXIS));
        riskCard.setBorder(new EmptyBorder(25, 25, 25, 25));

        riskTitleLabel = new JLabel(risk);
        riskTitleLabel.setFont(new Font("Segoe UI", Font.BOLD, 36));

        applyRiskStyle(riskCard, riskTitleLabel, risk);

        JLabel riskDesc = new JLabel("AI detected abnormal patient condition.");
        riskDesc.setFont(new Font("Segoe UI", Font.PLAIN, 17));
        riskDesc.setBorder(new EmptyBorder(10, 0, 0, 0));

        riskCard.add(riskTitleLabel);
        riskCard.add(riskDesc);

        panel.add(riskCard);
        panel.add(Box.createVerticalStrut(25));

        JButton emergencyButton = styleButton("Initialize Clinical Response", new Color(20, 90, 150));
        JButton connectButton = styleButton("Connect Monitor", new Color(40, 140, 90));
        JButton disconnectButton = styleButton("Disconnect Monitor", new Color(180, 60, 60));

        connectButton.addActionListener(e -> {
            SmartDeviceSimulator.startMonitoring(patient);
            JOptionPane.showMessageDialog(this, "Smart monitor connected successfully.");
        });

        disconnectButton.addActionListener(e -> {
            SmartDeviceSimulator.stopMonitoring();
            alerts.CriticalAlertManager.stopAlarm();
            JOptionPane.showMessageDialog(this, "Smart monitor disconnected.");
        });

        panel.add(emergencyButton);
        panel.add(Box.createVerticalStrut(20));
        panel.add(connectButton);
        panel.add(Box.createVerticalStrut(15));
        panel.add(disconnectButton);

        return panel;
    }

    private JPanel createVitalCard(String title, JLabel valueLabel, Color bg, Color textColor) {

        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(bg);
        card.setBorder(new EmptyBorder(25, 25, 25, 25));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        titleLabel.setForeground(new Color(110, 120, 130));

        valueLabel.setFont(new Font("Segoe UI", Font.BOLD, 34));
        valueLabel.setForeground(textColor);

        card.add(titleLabel);
        card.add(Box.createVerticalStrut(15));
        card.add(valueLabel);

        return card;
    }

    private void startLiveUpdates(Patient patient) {

        javax.swing.Timer timer = new javax.swing.Timer(2000, e -> {

            if (patient.getVitalSign() == null) {
                return;
            }

            VitalSign v = patient.getVitalSign();

            tempLabel.setText(v.getTemperature() + " °C");
            heartLabel.setText(v.getHeartRate() + " bpm");
            pressureLabel.setText(v.getSystolicPressure() + "/" + v.getDiastolicPressure());
            oxygenLabel.setText(v.getOxygenLevel() + "%");

            String risk = AIAnalysis.analyzeRisk(v);
            riskTitleLabel.setText(risk);

            JPanel riskCard = (JPanel) riskTitleLabel.getParent();
            applyRiskStyle(riskCard, riskTitleLabel, risk);
        });

        timer.start();
    }

    private void applyRiskStyle(JPanel riskCard, JLabel riskLabel, String risk) {

        if (risk.equals("Critical")) {
            riskCard.setBackground(new Color(255, 235, 235));
            riskLabel.setForeground(new Color(190, 40, 40));
        } else if (risk.equals("Warning")) {
            riskCard.setBackground(new Color(255, 245, 220));
            riskLabel.setForeground(new Color(180, 120, 0));
        } else {
            riskCard.setBackground(new Color(230, 255, 235));
            riskLabel.setForeground(new Color(40, 140, 70));
        }
    }

    private JButton styleButton(String text, Color color) {

        JButton button = new JButton(text);
        button.setFocusPainted(false);
        button.setBackground(color);
        button.setForeground(Color.WHITE);
        button.setFont(new Font("Segoe UI", Font.BOLD, 18));
        button.setBorder(new EmptyBorder(16, 25, 16, 25));

        return button;
    }

    private JPanel modernPanel() {

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(Color.WHITE);
        panel.setBorder(new EmptyBorder(25, 25, 25, 25));

        return panel;
    }

    private JLabel sectionTitle(String text) {

        JLabel label = new JLabel(text);
        label.setFont(new Font("Segoe UI", Font.BOLD, 26));
        label.setForeground(new Color(25, 40, 60));
        label.setBorder(new EmptyBorder(0, 0, 25, 0));

        return label;
    }

    private JPanel summaryLine(String title, String value) {

        JPanel line = new JPanel(new BorderLayout());
        line.setOpaque(false);
        line.setBorder(new EmptyBorder(10, 0, 10, 0));

        JLabel left = new JLabel(title);
        left.setFont(new Font("Segoe UI", Font.BOLD, 16));

        JLabel right = new JLabel(value);
        right.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        right.setForeground(new Color(80, 90, 100));

        line.add(left, BorderLayout.WEST);
        line.add(right, BorderLayout.EAST);

        return line;
    }
}