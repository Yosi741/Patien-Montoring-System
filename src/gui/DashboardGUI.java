package gui;

import services.RolePermissionService;
import users.User;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class DashboardGUI extends JFrame {

    private User currentUser;

    public DashboardGUI(User user) {
        this.currentUser = user;
        NavigationManager.registerDashboard(this);

        setTitle("Hospital Dashboard - " + currentUser.getRole());
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        WindowSizing.apply(this, 1180, 760, 980, 640);

        JPanel main = UITheme.appPanel(new BorderLayout(20, 20));
        main.setBorder(new EmptyBorder(22, 24, 24, 24));

        JPanel grid = UITheme.appPanel(new GridLayout(4, 3, 18, 18));

        JButton patientsButton = dashboardButton("Patient Management", "Patients, vitals, files, history, death forms", UITheme.PRIMARY);
        JButton devicesButton = dashboardButton("Devices", "Connected monitors and simulated Bluetooth adapter", UITheme.SUCCESS);
        JButton aiButton = dashboardButton("AI Advice", "Rule-based advice notes requiring staff review", new Color(32, 132, 122));
        JButton roomsButton = dashboardButton("Rooms / Sections", "Capacity, room ranges, and occupancy", new Color(72, 111, 146));
        JButton deceasedButton = dashboardButton("Deceased Patients", "Death records, details, and certificates", UITheme.DANGER);
        JButton mothersButton = dashboardButton("Mothers", "Mother lookup and birth management", new Color(72, 126, 112));
        JButton newbornsButton = dashboardButton("Newborn / Babies", "Baby records, stats, and certificates", new Color(32, 132, 122));
        JButton reportsButton = dashboardButton("Birth Certificate", "Newborn registration and certificate report", new Color(103, 83, 170));
        JButton logsButton = dashboardButton("Audit Logs", "Admin/director activity history", new Color(88, 105, 124));
        JButton usersButton = dashboardButton("User Management", "Accounts, roles, and assigned section", UITheme.WARNING);

        patientsButton.addActionListener(e -> openPatients());
        devicesButton.addActionListener(e -> new DeviceManagementGUI().setVisible(true));
        aiButton.addActionListener(e -> new AIAdviceGUI().setVisible(true));
        roomsButton.addActionListener(e -> new RoomSectionGUI().setVisible(true));
        deceasedButton.addActionListener(e -> new DeceasedPatientsGUI().setVisible(true));
        mothersButton.addActionListener(e -> new MotherManagementGUI().setVisible(true));
        newbornsButton.addActionListener(e -> new NewbornManagementGUI().setVisible(true));
        reportsButton.addActionListener(e -> new BirthCertificateGUI().setVisible(true));
        logsButton.addActionListener(e -> new AuditLogGUI().setVisible(true));
        usersButton.addActionListener(e -> new UserManagementGUI().setVisible(true));

        devicesButton.setEnabled(RolePermissionService.canManageDevices(currentUser));
        aiButton.setEnabled(RolePermissionService.canViewAIAdvice(currentUser));
        roomsButton.setEnabled(RolePermissionService.canManageRooms(currentUser));
        deceasedButton.setEnabled(RolePermissionService.canViewDeceasedPatients(currentUser));
        mothersButton.setEnabled(RolePermissionService.canCreateBirthCertificate(currentUser));
        newbornsButton.setEnabled(RolePermissionService.canCreateBirthCertificate(currentUser));
        reportsButton.setEnabled(RolePermissionService.canCreateBirthCertificate(currentUser));
        logsButton.setEnabled(RolePermissionService.canViewAuditLogs(currentUser));
        usersButton.setEnabled(RolePermissionService.canManageUsers(currentUser));

        grid.add(patientsButton);
        grid.add(devicesButton);
        grid.add(aiButton);
        grid.add(roomsButton);
        grid.add(deceasedButton);
        grid.add(mothersButton);
        grid.add(newbornsButton);
        grid.add(reportsButton);
        grid.add(logsButton);
        grid.add(usersButton);

        main.add(new AppHeader("Smart Patient Monitoring System"), BorderLayout.NORTH);
        main.add(grid, BorderLayout.CENTER);

        add(main);
    }

    private void openPatients() {
        PatientGUI patientGUI = new PatientGUI(this, currentUser);
        patientGUI.setVisible(true);
        this.setVisible(false);
    }

    private JButton dashboardButton(String title, String subtitle, Color color) {
        JButton button = UITheme.button("<html><b>" + title + "</b><br><span style='font-weight:normal;font-size:11px;'>" + subtitle + "</span></html>", color);
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setFont(UITheme.font(Font.BOLD, 18));
        button.setBorder(new EmptyBorder(22, 22, 22, 22));
        return button;
    }
}
