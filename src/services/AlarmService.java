package services;

import ai.AIAnalysis;
import database.NotificationStorage;
import logs.AuditLog;
import models.Patient;

import javax.sound.sampled.*;
import javax.swing.*;
import java.io.File;

public class AlarmService {

    public enum AlarmState {
        ACTIVE,
        ACKNOWLEDGED,
        STOPPED,
        RESOLVED
    }

    private static Clip currentClip;
    private static AlarmState state = AlarmState.STOPPED;
    private static boolean alertDialogOpen = false;
    private static boolean alertShownForActiveAlarm = false;
    private static String activePatientId = "";

    public static synchronized void checkPatient(Patient patient) {
        String risk = AIAnalysis.analyzeRisk(patient.getVitalSign());

        if (!risk.equals("Critical")) {
            if (activePatientId.equals(patient.getPatientId())) {
                resolveAlarm();
            }
            return;
        }

        if (state == AlarmState.ACKNOWLEDGED && activePatientId.equals(patient.getPatientId())) {
            return;
        }

        if (state != AlarmState.ACTIVE || !activePatientId.equals(patient.getPatientId())) {
            activePatientId = patient.getPatientId();
            state = AlarmState.ACTIVE;
            alertShownForActiveAlarm = false;
            startAlarm();
            AuditLog.addLog("System", "Alarm ACTIVE for patient: " + patient.getName());
            NotificationStorage.addNotification("ALL", "CRITICAL", "Critical alert active for patient " + patient.getName() + " in " + patient.getSection() + " room " + patient.getRoom());
        }

        if (!alertDialogOpen && !alertShownForActiveAlarm) {
            SwingUtilities.invokeLater(() -> showCriticalDialog(patient));
        }
    }

    public static synchronized void acknowledgeAlarm() {
        stopSoundOnly();
        state = AlarmState.ACKNOWLEDGED;
        alertShownForActiveAlarm = true;
        AuditLog.addLog("System", "Alarm ACKNOWLEDGED");
    }

    public static synchronized void stopAlarm() {
        stopSoundOnly();
        state = AlarmState.STOPPED;
        alertShownForActiveAlarm = false;
        AuditLog.addLog("System", "Alarm STOPPED");
    }

    public static synchronized void resolveAlarm() {
        stopSoundOnly();
        state = AlarmState.RESOLVED;
        activePatientId = "";
        alertShownForActiveAlarm = false;
        AuditLog.addLog("System", "Alarm RESOLVED");
    }

    public static synchronized AlarmState getState() {
        return state;
    }

    private static synchronized void startAlarm() {
        if (currentClip != null && currentClip.isRunning()) {
            return;
        }

        try {
            File soundFile = new File("resources/sounds/alarm.wav");
            AudioInputStream audioStream = AudioSystem.getAudioInputStream(soundFile);
            currentClip = AudioSystem.getClip();
            currentClip.open(audioStream);
            currentClip.loop(Clip.LOOP_CONTINUOUSLY);
            currentClip.start();
        } catch (Exception e) {
            System.out.println("Alarm sound error: " + e.getMessage());
        }
    }

    private static synchronized void stopSoundOnly() {
        try {
            if (currentClip != null) {
                currentClip.stop();
                currentClip.close();
                currentClip = null;
            }
        } catch (Exception e) {
            System.out.println("Stop alarm error: " + e.getMessage());
        }
    }

    private static void showCriticalDialog(Patient patient) {
        alertDialogOpen = true;
        alertShownForActiveAlarm = true;

        JDialog dialog = new JDialog((java.awt.Frame) null, "Critical Patient Alert", false);
        dialog.setSize(680, 460);
        dialog.setMinimumSize(new java.awt.Dimension(620, 380));
        dialog.setResizable(true);
        dialog.setLocationRelativeTo(null);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JPanel panel = new JPanel(new java.awt.BorderLayout(15, 15));
        panel.setBorder(BorderFactory.createEmptyBorder(22, 24, 22, 24));
        panel.setBackground(new java.awt.Color(255, 235, 235));

        JLabel title = new JLabel("CRITICAL ALERT");
        title.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 28));
        title.setForeground(new java.awt.Color(180, 35, 35));

        JTextArea message = new JTextArea(
                "Patient: " + patient.getName()
                        + "\nRoom: " + patient.getRoom()
                        + "\nSection: " + patient.getSection()
                        + "\nRisk Level: CRITICAL"
                        + "\n\nImmediate medical attention required."
                        + "\n\nActions:"
                        + "\n- Review current vitals immediately."
                        + "\n- Check device ID/history if device error is possible."
                        + "\n- Press Stop Alarm to acknowledge and silence the alarm."
                        + "\n\nClosing this window will not restart the alarm."
        );
        message.setEditable(false);
        message.setLineWrap(true);
        message.setWrapStyleWord(true);
        message.setBackground(new java.awt.Color(255, 245, 245));
        message.setFont(new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 18));
        JScrollPane messageScroll = new JScrollPane(message);
        messageScroll.setBorder(BorderFactory.createLineBorder(new java.awt.Color(245, 180, 180)));

        JButton stopButton = new JButton("Stop Alarm");
        stopButton.setBackground(new java.awt.Color(190, 55, 55));
        stopButton.setForeground(java.awt.Color.WHITE);
        stopButton.setFocusPainted(false);
        stopButton.addActionListener(e -> {
            acknowledgeAlarm();
            stopButton.setEnabled(false);
            stopButton.setText("Alarm Acknowledged");
        });

        JButton closeButton = new JButton("Close Alert");
        closeButton.setFocusPainted(false);
        closeButton.addActionListener(e -> dialog.dispose());

        JPanel buttons = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 10, 0));
        buttons.setOpaque(false);
        buttons.add(stopButton);
        buttons.add(closeButton);

        panel.add(title, java.awt.BorderLayout.NORTH);
        panel.add(messageScroll, java.awt.BorderLayout.CENTER);
        panel.add(buttons, java.awt.BorderLayout.SOUTH);

        dialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                alertDialogOpen = false;
            }

            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                alertDialogOpen = false;
            }
        });

        dialog.add(panel);
        dialog.setVisible(true);
    }
}
