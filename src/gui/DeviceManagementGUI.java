package gui;

import database.DeviceStorage;
import models.MedicalDevice;
import models.Patient;
import services.DeviceService;
import services.RolePermissionService;
import users.Session;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

public class DeviceManagementGUI extends JFrame {

    private Patient patient;
    private DefaultTableModel model;

    public DeviceManagementGUI() {
        this(null);
    }

    public DeviceManagementGUI(Patient patient) {
        this.patient = patient;

        setTitle("Smart Devices");
        setSize(1050, 620);
        setLocationRelativeTo(null);
        NavigationManager.configureChildWindow(this);

        JPanel main = UITheme.appPanel(new BorderLayout(14, 14));
        main.setBorder(new EmptyBorder(22, 22, 22, 22));

        JLabel title = UITheme.title(patient == null ? "Smart Devices" : "Smart Devices - " + patient.getName(), 26);

        model = new DefaultTableModel(new String[]{"Device ID", "Name", "Type", "Serial", "Status", "Last Connection", "Patient ID"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        JTable table = new JTable(model);
        UITheme.styleTable(table);

        JButton simulationButton = UITheme.button("Connect Simulated Bluetooth Monitor", UITheme.SUCCESS);
        JButton disconnectButton = UITheme.button("Disconnect Patient Device", UITheme.DANGER);
        JButton refreshButton = UITheme.secondaryButton("Refresh");
        JButton homeButton = NavigationManager.homeButton(this);

        simulationButton.setEnabled(patient != null && RolePermissionService.canManageDevices(Session.getCurrentUser()));
        disconnectButton.setEnabled(patient != null && RolePermissionService.canManageDevices(Session.getCurrentUser()));

        simulationButton.addActionListener(e -> {
            try {
                DeviceService.connectSimulationMonitor(patient);
                JOptionPane.showMessageDialog(this, "Simulated Bluetooth monitor connected.");
                loadDevices();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage());
            }
        });

        disconnectButton.addActionListener(e -> {
            DeviceService.disconnectPatientDevice(patient);
            alerts.CriticalAlertManager.muteAlarm();
            JOptionPane.showMessageDialog(this, "Patient device disconnected.");
            loadDevices();
        });

        refreshButton.addActionListener(e -> loadDevices());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        buttons.setOpaque(false);
        buttons.add(simulationButton);
        buttons.add(disconnectButton);
        buttons.add(refreshButton);
        buttons.add(homeButton);

        main.add(new HospitalHeaderPanel(patient == null ? "Smart Devices" : "Smart Devices - " + patient.getName()), BorderLayout.NORTH);
        main.add(new JScrollPane(table), BorderLayout.CENTER);
        main.add(buttons, BorderLayout.SOUTH);

        add(main);
        loadDevices();
    }

    private void loadDevices() {
        model.setRowCount(0);
        for (MedicalDevice device : DeviceStorage.loadDevices()) {
            model.addRow(new Object[]{
                    device.getDeviceId(),
                    device.getDeviceName(),
                    device.getDeviceType(),
                    device.getSerialNumber(),
                    device.getConnectionStatus(),
                    device.getLastConnectionTime(),
                    device.getPatientId()
            });
        }
    }
}
