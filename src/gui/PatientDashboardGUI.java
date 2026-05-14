package gui;

import ai.AIAdviceEngine;
import ai.AIAnalysis;
import database.MedicalFileStorage;
import devices.SmartDeviceSimulator;
import logs.AuditLog;
import models.MedicalFile;
import models.Patient;
import models.VitalSign;
import services.DeviceService;
import services.RolePermissionService;
import users.Session;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;

public class PatientDashboardGUI extends JFrame {

    private Patient patient;
    private JLabel tempLabel;
    private JLabel heartLabel;
    private JLabel pressureLabel;
    private JLabel oxygenLabel;
    private JLabel riskTitleLabel;
    private JLabel riskDescriptionLabel;
    private JTextArea recommendationArea;
    private JTextArea adviceArea;
    private DefaultTableModel filesModel;
    private JTable filesTable;
    private JPanel riskCard;
    private JPanel heartCard;
    private JPanel oxygenCard;
    private JPanel tempCard;
    private JPanel pressureCard;
    private ECGPanel ecgPanel;
    private JLabel deviceStatusLabel;
    private boolean pulseState = false;

    public PatientDashboardGUI(Patient patient) {
        this.patient = patient;

        setTitle("ICU Monitor - " + patient.getName());
        setSize(1500, 900);
        setLocationRelativeTo(null);
        NavigationManager.configureChildWindow(this);

        JPanel main = UITheme.appPanel(new BorderLayout());
        main.add(createHeader(), BorderLayout.NORTH);

        JPanel center = UITheme.appPanel(new BorderLayout(18, 18));
        center.setBorder(new EmptyBorder(22, 22, 22, 22));

        center.add(createVitalsBar(), BorderLayout.NORTH);
        center.add(createMonitorContent(), BorderLayout.CENTER);

        main.add(center, BorderLayout.CENTER);
        add(main);

        refreshClinicalContent();
        startLiveUpdates();
    }

    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(UITheme.SURFACE);
        header.setBorder(new EmptyBorder(18, 26, 18, 26));

        JLabel patientName = UITheme.title(patient.getName(), 32);
        JLabel patientInfo = new JLabel(patient.getGender() + " | Age " + patient.getAge() + " | " + patient.getSection() + " Room " + patient.getRoom() + " | ID " + patient.getPatientId() + " | " + patient.getStatus());
        patientInfo.setFont(UITheme.font(Font.PLAIN, 16));
        patientInfo.setForeground(UITheme.MUTED);

        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.add(patientName);
        left.add(Box.createVerticalStrut(6));
        left.add(patientInfo);

        JButton uploadButton = UITheme.button("Upload Medical File", UITheme.SUCCESS);
        JButton historyButton = UITheme.secondaryButton("History");
        JButton vitalsHistoryButton = UITheme.secondaryButton("Vitals");
        JButton backButton = UITheme.secondaryButton("Back");
        JButton homeButton = NavigationManager.homeButton(this);

        uploadButton.addActionListener(e -> new FileUploadGUI(patient, this::refreshClinicalContent).setVisible(true));
        historyButton.addActionListener(e -> new PatientHistoryGUI(patient).setVisible(true));
        vitalsHistoryButton.addActionListener(e -> new VitalsHistoryGUI(patient).setVisible(true));
        backButton.addActionListener(e -> dispose());

        uploadButton.setEnabled(RolePermissionService.canEditPatient(Session.getCurrentUser()) || RolePermissionService.canAddVitals(Session.getCurrentUser()));
        historyButton.setEnabled(RolePermissionService.canViewSensitiveHistory(Session.getCurrentUser()));

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actions.setOpaque(false);
        actions.add(uploadButton);
        actions.add(historyButton);
        actions.add(vitalsHistoryButton);
        actions.add(backButton);
        actions.add(homeButton);

        header.add(left, BorderLayout.WEST);
        header.add(actions, BorderLayout.EAST);

        return header;
    }

    private JPanel createVitalsBar() {
        JPanel vitalsBar = UITheme.appPanel(new GridLayout(1, 4, 16, 16));

        heartLabel = new JLabel();
        oxygenLabel = new JLabel();
        tempLabel = new JLabel();
        pressureLabel = new JLabel();

        heartCard = createVitalCard("Heart Rate", heartLabel, "bpm", new Color(229, 244, 255), UITheme.PRIMARY);
        oxygenCard = createVitalCard("Oxygen Level", oxygenLabel, "%", new Color(232, 248, 242), UITheme.SUCCESS);
        tempCard = createVitalCard("Temperature", tempLabel, "C", new Color(255, 244, 231), UITheme.WARNING);
        pressureCard = createVitalCard("Blood Pressure", pressureLabel, "mmHg", new Color(246, 242, 255), new Color(111, 77, 170));

        vitalsBar.add(heartCard);
        vitalsBar.add(oxygenCard);
        vitalsBar.add(tempCard);
        vitalsBar.add(pressureCard);

        return vitalsBar;
    }

    private JPanel createMonitorContent() {
        JPanel content = UITheme.appPanel(new GridLayout(1, 2, 18, 18));

        JPanel leftColumn = UITheme.appPanel(new BorderLayout(18, 18));
        ecgPanel = new ECGPanel();
        ecgPanel.setPreferredSize(new Dimension(0, 310));
        leftColumn.add(ecgPanel, BorderLayout.NORTH);
        leftColumn.add(createPatientSummary(), BorderLayout.CENTER);

        JPanel rightColumn = UITheme.appPanel(new BorderLayout(18, 18));
        rightColumn.add(createRiskPanel(), BorderLayout.NORTH);
        rightColumn.add(createAdvicePanel(), BorderLayout.CENTER);
        rightColumn.add(createFilesPanel(), BorderLayout.SOUTH);

        content.add(leftColumn);
        content.add(rightColumn);

        return content;
    }

    private JPanel createPatientSummary() {
        JPanel panel = UITheme.cardPanel();
        panel.setLayout(new BorderLayout(18, 18));

        JPanel details = new JPanel();
        details.setOpaque(false);
        details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
        details.add(sectionTitle("Patient Summary"));
        details.add(summaryLine("Patient ID", patient.getPatientId()));
        details.add(summaryLine("Birth Date", patient.getBirthDate()));
        details.add(summaryLine("Gender", patient.getGender()));
        details.add(summaryLine("Section", patient.getSection()));
        details.add(summaryLine("Room", patient.getRoom()));
        details.add(summaryLine("Status", patient.getStatus()));

        recommendationArea = new JTextArea();
        recommendationArea.setEditable(false);
        recommendationArea.setLineWrap(true);
        recommendationArea.setWrapStyleWord(true);
        recommendationArea.setFont(UITheme.font(Font.PLAIN, 15));
        recommendationArea.setBackground(new Color(248, 251, 253));
        recommendationArea.setBorder(new EmptyBorder(16, 16, 16, 16));

        panel.add(details, BorderLayout.NORTH);
        panel.add(new JScrollPane(recommendationArea), BorderLayout.CENTER);

        return panel;
    }

    private JPanel createRiskPanel() {
        JPanel panel = UITheme.cardPanel();
        panel.setLayout(new BorderLayout(15, 15));

        riskCard = new JPanel(new BorderLayout());
        riskCard.setBorder(new EmptyBorder(20, 20, 20, 20));

        riskTitleLabel = new JLabel();
        riskTitleLabel.setFont(UITheme.font(Font.BOLD, 34));

        riskDescriptionLabel = new JLabel();
        riskDescriptionLabel.setFont(UITheme.font(Font.PLAIN, 15));
        riskDescriptionLabel.setForeground(UITheme.MUTED);

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        text.add(riskTitleLabel);
        text.add(Box.createVerticalStrut(8));
        text.add(riskDescriptionLabel);

        riskCard.add(text, BorderLayout.WEST);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttons.setOpaque(false);

        JButton responseButton = UITheme.button("Clinical Response", UITheme.PRIMARY);
        JButton connectButton = UITheme.button("Connect Simulation Monitor", UITheme.SUCCESS);
        JButton disconnectButton = UITheme.button("Disconnect", UITheme.DANGER);
        JButton stopAlarmButton = UITheme.button("Stop Alarm", UITheme.DANGER);
        JButton deviceButton = UITheme.secondaryButton("Devices");
        JButton generateAdviceButton = UITheme.button("Generate AI Advice", new Color(32, 132, 122));
        deviceStatusLabel = new JLabel("No device connected");
        deviceStatusLabel.setFont(UITheme.font(Font.PLAIN, 14));
        deviceStatusLabel.setForeground(UITheme.MUTED);

        connectButton.addActionListener(e -> {
            SmartDeviceSimulator.startMonitoring(patient);
            AuditLog.addLog(Session.getUsername(), "Connected smart monitor for: " + patient.getName());
            JOptionPane.showMessageDialog(this, "Smart monitor connected successfully.");
            refreshClinicalContent();
        });

        disconnectButton.addActionListener(e -> {
            SmartDeviceSimulator.stopMonitoring();
            alerts.CriticalAlertManager.muteAlarm();
            AuditLog.addLog(Session.getUsername(), "Disconnected smart monitor for: " + patient.getName());
            JOptionPane.showMessageDialog(this, "Smart monitor disconnected.");
            refreshClinicalContent();
        });

        responseButton.addActionListener(e -> JOptionPane.showMessageDialog(this, "Clinical response initialized for " + patient.getName() + "."));
        stopAlarmButton.addActionListener(e -> {
            alerts.CriticalAlertManager.muteAlarm();
            JOptionPane.showMessageDialog(this, "Alarm acknowledged and sound stopped.");
        });
        deviceButton.addActionListener(e -> new DeviceManagementGUI(patient).setVisible(true));
        generateAdviceButton.addActionListener(e -> {
            AIAdviceEngine.generatePatientAdvice(patient);
            refreshAdviceNotes();
            JOptionPane.showMessageDialog(this, "AI advice generated. Staff review is required before clinical decisions.");
        });

        connectButton.setEnabled(RolePermissionService.canManageDevices(Session.getCurrentUser()));
        disconnectButton.setEnabled(RolePermissionService.canManageDevices(Session.getCurrentUser()));
        generateAdviceButton.setEnabled(RolePermissionService.canViewAIAdvice(Session.getCurrentUser()));

        buttons.add(responseButton);
        buttons.add(stopAlarmButton);
        buttons.add(connectButton);
        buttons.add(disconnectButton);
        buttons.add(deviceButton);
        buttons.add(generateAdviceButton);

        panel.add(sectionTitle("Clinical Status"), BorderLayout.NORTH);
        panel.add(riskCard, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setOpaque(false);
        bottom.add(deviceStatusLabel, BorderLayout.NORTH);
        bottom.add(buttons, BorderLayout.SOUTH);
        panel.add(bottom, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createAdvicePanel() {
        JPanel panel = UITheme.cardPanel();
        panel.setLayout(new BorderLayout(12, 12));

        adviceArea = new JTextArea();
        adviceArea.setEditable(false);
        adviceArea.setLineWrap(true);
        adviceArea.setWrapStyleWord(true);
        adviceArea.setFont(UITheme.font(Font.PLAIN, 15));
        adviceArea.setBackground(new Color(248, 251, 253));
        adviceArea.setBorder(new EmptyBorder(16, 16, 16, 16));

        panel.add(sectionTitle("AI Advice Notes"), BorderLayout.NORTH);
        panel.add(new JScrollPane(adviceArea), BorderLayout.CENTER);

        return panel;
    }

    private JPanel createFilesPanel() {
        JPanel panel = UITheme.cardPanel();
        panel.setLayout(new BorderLayout(12, 12));
        panel.setPreferredSize(new Dimension(0, 220));

        filesModel = new DefaultTableModel(new String[]{"File", "Type", "Uploaded By", "Uploaded At", "Path"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        filesTable = new JTable(filesModel);
        UITheme.styleTable(filesTable);
        filesTable.getColumnModel().getColumn(4).setMinWidth(0);
        filesTable.getColumnModel().getColumn(4).setMaxWidth(0);
        filesTable.getColumnModel().getColumn(4).setWidth(0);

        JButton openButton = UITheme.secondaryButton("Open Selected File");
        openButton.addActionListener(e -> openSelectedMedicalFile());

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(sectionTitle("Medical Files"), BorderLayout.WEST);
        top.add(openButton, BorderLayout.EAST);

        panel.add(top, BorderLayout.NORTH);
        panel.add(new JScrollPane(filesTable), BorderLayout.CENTER);

        return panel;
    }

    private JPanel createVitalCard(String title, JLabel valueLabel, String unit, Color bg, Color textColor) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(bg);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(218, 228, 238)),
                new EmptyBorder(18, 20, 18, 20)
        ));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(UITheme.font(Font.BOLD, 15));
        titleLabel.setForeground(UITheme.MUTED);

        valueLabel.setFont(UITheme.font(Font.BOLD, 34));
        valueLabel.setForeground(textColor);

        JLabel unitLabel = new JLabel(unit);
        unitLabel.setFont(UITheme.font(Font.PLAIN, 14));
        unitLabel.setForeground(UITheme.MUTED);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setOpaque(false);
        bottom.add(valueLabel, BorderLayout.WEST);
        bottom.add(unitLabel, BorderLayout.EAST);

        card.add(titleLabel, BorderLayout.NORTH);
        card.add(bottom, BorderLayout.CENTER);

        return card;
    }

    private void startLiveUpdates() {
        Timer timer = new Timer(1200, e -> {
            pulseState = !pulseState;
            updateDeviceStatus();
            refreshVitalLabels();
            refreshRiskStatus();
        });

        timer.start();
    }

    private void refreshClinicalContent() {
        refreshVitalLabels();
        refreshRiskStatus();
        refreshRecommendations();
        refreshAdviceNotes();
        refreshFiles();
        updateDeviceStatus();
    }

    private void refreshVitalLabels() {
        VitalSign v = patient.getVitalSign();
        if (v == null) {
            heartLabel.setText("--");
            oxygenLabel.setText("--");
            tempLabel.setText("--");
            pressureLabel.setText("--/--");
            return;
        }

        heartLabel.setText(String.valueOf(v.getHeartRate()));
        oxygenLabel.setText(String.valueOf(v.getOxygenLevel()));
        tempLabel.setText(String.format("%.1f", v.getTemperature()));
        pressureLabel.setText(v.getSystolicPressure() + "/" + v.getDiastolicPressure());

        applyVitalAccent(heartCard, v.getHeartRate() >= 120, v.getHeartRate() > 100);
        applyVitalAccent(oxygenCard, v.getOxygenLevel() < 90, v.getOxygenLevel() < 94);
        applyVitalAccent(tempCard, v.getTemperature() >= 39, v.getTemperature() >= 38);
        applyVitalAccent(pressureCard, v.getSystolicPressure() >= 160 || v.getDiastolicPressure() >= 100,
                v.getSystolicPressure() >= 140 || v.getDiastolicPressure() >= 90);
    }

    private void refreshRiskStatus() {
        String risk = AIAnalysis.analyzeRisk(patient.getVitalSign());
        riskTitleLabel.setText(risk.toUpperCase());

        if (risk.equals("Critical")) {
            riskDescriptionLabel.setText("Immediate review recommended. Check vitals history and device ID if device error is possible.");
            riskCard.setBackground(pulseState ? new Color(255, 214, 214) : new Color(255, 235, 235));
            riskTitleLabel.setForeground(UITheme.DANGER);
            alerts.CriticalAlertManager.checkPatient(patient);
        } else if (risk.equals("Warning")) {
            riskDescriptionLabel.setText("Abnormal values detected. Continue close observation.");
            riskCard.setBackground(new Color(255, 245, 220));
            riskTitleLabel.setForeground(UITheme.WARNING);
        } else if (risk.equals("No Data")) {
            riskDescriptionLabel.setText("No vital signs recorded yet.");
            riskCard.setBackground(new Color(235, 241, 247));
            riskTitleLabel.setForeground(UITheme.MUTED);
        } else {
            riskDescriptionLabel.setText("Vitals are currently within expected monitoring range.");
            riskCard.setBackground(new Color(229, 248, 238));
            riskTitleLabel.setForeground(UITheme.SUCCESS);
        }
    }

    private void refreshRecommendations() {
        ArrayList<String> recommendations = AIAnalysis.getRecommendations(patient.getVitalSign());
        StringBuilder builder = new StringBuilder("Vital Sign Recommendations:\n\n");

        for (String recommendation : recommendations) {
            builder.append("- ").append(recommendation).append("\n");
        }

        recommendationArea.setText(builder.toString());
    }

    private void refreshAdviceNotes() {
        ArrayList<String> notes = AIAdviceEngine.getLatestAdvice(patient.getPatientId(), 8);
        if (notes.isEmpty()) {
            adviceArea.setText("No AI advice notes yet. Upload TXT/CSV files or press Generate AI Advice.\nRule-based support only; staff review is required.");
            return;
        }

        StringBuilder builder = new StringBuilder();
        for (String note : notes) {
            builder.append("- ").append(note).append("\n");
        }
        adviceArea.setText(builder.toString());
    }

    private void updateDeviceStatus() {
        if (deviceStatusLabel == null || ecgPanel == null) {
            return;
        }

        if (DeviceService.hasConnectedDevice(patient.getPatientId())) {
            models.MedicalDevice device = DeviceService.getConnectedDevice(patient.getPatientId());
            deviceStatusLabel.setText("Connected device: " + device.getDeviceName() + " | ID " + device.getDeviceId() + " | Serial " + device.getSerialNumber());
        } else {
            deviceStatusLabel.setText("No device connected. ECG animation is paused until a real or simulated ECG monitor is connected.");
        }

        ecgPanel.setConnected(DeviceService.hasConnectedEcg(patient.getPatientId()));
    }

    private void refreshFiles() {
        filesModel.setRowCount(0);
        for (MedicalFile file : MedicalFileStorage.getFilesForPatient(patient.getPatientId())) {
            filesModel.addRow(new Object[]{
                    file.getOriginalName(),
                    file.getFileType().toUpperCase(),
                    file.getUploadedBy(),
                    file.getUploadedAt(),
                    file.getStoredPath()
            });
        }
    }

    private void openSelectedMedicalFile() {
        int selectedRow = filesTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Please select a medical file first.");
            return;
        }

        int modelRow = filesTable.convertRowIndexToModel(selectedRow);
        String path = filesModel.getValueAt(modelRow, 4).toString();

        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(new File(path));
            } else {
                JOptionPane.showMessageDialog(this, "File saved at:\n" + path);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Could not open file. Saved at:\n" + path);
        }
    }

    private void applyVitalAccent(JPanel card, boolean critical, boolean warning) {
        if (critical) {
            card.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(pulseState ? UITheme.DANGER : new Color(245, 170, 170), 2),
                    new EmptyBorder(17, 19, 17, 19)
            ));
        } else if (warning) {
            card.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(UITheme.WARNING, 2),
                    new EmptyBorder(17, 19, 17, 19)
            ));
        } else {
            card.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(218, 228, 238)),
                    new EmptyBorder(18, 20, 18, 20)
            ));
        }
    }

    private JLabel sectionTitle(String text) {
        JLabel label = UITheme.title(text, 22);
        label.setBorder(new EmptyBorder(0, 0, 8, 0));
        return label;
    }

    private JPanel summaryLine(String title, String value) {
        JPanel line = new JPanel(new BorderLayout());
        line.setOpaque(false);
        line.setBorder(new EmptyBorder(8, 0, 8, 0));

        JLabel left = new JLabel(title);
        left.setFont(UITheme.font(Font.BOLD, 15));
        left.setForeground(UITheme.TEXT);

        JLabel right = new JLabel(value);
        right.setFont(UITheme.font(Font.PLAIN, 15));
        right.setForeground(UITheme.MUTED);

        line.add(left, BorderLayout.WEST);
        line.add(right, BorderLayout.EAST);

        return line;
    }
}
