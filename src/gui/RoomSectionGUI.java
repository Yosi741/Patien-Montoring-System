package gui;

import logs.AuditLog;
import models.RoomInfo;
import services.RolePermissionService;
import services.RoomService;
import users.Session;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

public class RoomSectionGUI extends JFrame {

    private DefaultTableModel model;
    private JTable table;

    public RoomSectionGUI() {
        setTitle("Sections & Rooms Management");
        setSize(1050, 720);
        setLocationRelativeTo(null);
        NavigationManager.configureChildWindow(this);

        JPanel main = UITheme.appPanel(new BorderLayout(14, 14));
        main.setBorder(new EmptyBorder(22, 22, 22, 22));

        JLabel title = UITheme.title("Sections & Rooms Management", 26);

        model = new DefaultTableModel(
                new String[]{"Section", "Room", "Capacity", "Current Patients", "Available Beds", "Status"}, 0
        ) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        table = new JTable(model);
        UITheme.styleTable(table);

        JButton addSection = UITheme.button("Add Section", UITheme.PRIMARY);
        JButton editSection = UITheme.button("Edit Section", UITheme.WARNING);
        JButton deleteSection = UITheme.button("Delete Section", UITheme.DANGER);
        JButton addRoom = UITheme.button("Add Room", UITheme.SUCCESS);
        JButton editRoom = UITheme.button("Edit Room", UITheme.WARNING);
        JButton deleteRoom = UITheme.button("Delete Room", UITheme.DANGER);
        JButton refresh = UITheme.secondaryButton("Refresh");
        JButton homeButton = NavigationManager.homeButton(this);

        boolean canManage = RolePermissionService.canManageRooms(Session.getCurrentUser());
        addSection.setEnabled(canManage);
        editSection.setEnabled(canManage);
        deleteSection.setEnabled(canManage);
        addRoom.setEnabled(canManage);
        editRoom.setEnabled(canManage);
        deleteRoom.setEnabled(canManage);

        addSection.addActionListener(e -> addSection());
        editSection.addActionListener(e -> editSection());
        deleteSection.addActionListener(e -> deleteSection());
        addRoom.addActionListener(e -> addRoom());
        editRoom.addActionListener(e -> editRoom());
        deleteRoom.addActionListener(e -> deleteRoom());
        refresh.addActionListener(e -> loadRooms());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        buttons.setOpaque(false);
        buttons.add(addSection);
        buttons.add(editSection);
        buttons.add(deleteSection);
        buttons.add(addRoom);
        buttons.add(editRoom);
        buttons.add(deleteRoom);
        buttons.add(refresh);
        buttons.add(homeButton);

        JPanel northStack = new JPanel(new BorderLayout());
        northStack.setOpaque(false);
        northStack.add(new HospitalHeaderPanel("Sections & Rooms Management"), BorderLayout.NORTH);
        northStack.add(title, BorderLayout.SOUTH);
        main.add(northStack, BorderLayout.NORTH);
        main.add(new JScrollPane(table), BorderLayout.CENTER);
        main.add(buttons, BorderLayout.SOUTH);

        add(main);
        loadRooms();
    }

    private void loadRooms() {
        model.setRowCount(0);
        for (RoomInfo room : RoomService.getAllRooms()) {
            int occupancy = RoomService.getRoomOccupancy(room.getSectionName(), room.getRoomNumber(), null);
            int available = room.getCapacity() - occupancy;
            model.addRow(new Object[]{
                    room.getSectionName(),
                    room.getRoomNumber(),
                    room.getCapacity(),
                    occupancy,
                    available,
                    available <= 0 ? "Full" : "Available"
            });
        }
    }

    private void addSection() {
        String section = JOptionPane.showInputDialog(this, "New section name:");
        if (section == null) return;

        String error = RoomService.addSection(section);
        showResult(error, "Section added.");
        if (error == null) AuditLog.addLog(Session.getUsername(), "Added hospital section: " + section);
    }

    private void editSection() {
        String section = selectedSection();
        if (section == null) return;

        String newName = JOptionPane.showInputDialog(this, "New section name:", section);
        if (newName == null) return;

        String error = RoomService.renameSection(section, newName);
        showResult(error, "Section updated.");
        if (error == null) AuditLog.addLog(Session.getUsername(), "Renamed section: " + section + " -> " + newName);
    }

    private void deleteSection() {
        String section = selectedSection();
        if (section == null) return;

        int confirm = JOptionPane.showConfirmDialog(this, "Delete section " + section + " and all empty rooms?", "Confirm", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;

        String error = RoomService.deleteSection(section);
        showResult(error, "Section deleted.");
        if (error == null) AuditLog.addLog(Session.getUsername(), "Deleted hospital section: " + section);
    }

    private void addRoom() {
        String section = chooseSection();
        if (section == null) return;

        JTextField roomField = new JTextField();
        JTextField capacityField = new JTextField("1");
        Object[] fields = {"Room number:", roomField, "Capacity:", capacityField};

        int result = JOptionPane.showConfirmDialog(this, fields, "Add Room to " + section, JOptionPane.OK_CANCEL_OPTION);
        if (result != JOptionPane.OK_OPTION) return;

        try {
            String error = RoomService.addRoom(section, roomField.getText(), Integer.parseInt(capacityField.getText().trim()));
            showResult(error, "Room added.");
            if (error == null) AuditLog.addLog(Session.getUsername(), "Added room " + roomField.getText() + " to section " + section);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Capacity must be a positive number.");
        }
    }

    private void editRoom() {
        int row = selectedModelRow();
        if (row == -1) return;

        String section = model.getValueAt(row, 0).toString();
        String oldRoom = model.getValueAt(row, 1).toString();
        JTextField roomField = new JTextField(oldRoom);
        JTextField capacityField = new JTextField(model.getValueAt(row, 2).toString());
        Object[] fields = {"Room number:", roomField, "Capacity:", capacityField};

        int result = JOptionPane.showConfirmDialog(this, fields, "Edit Room", JOptionPane.OK_CANCEL_OPTION);
        if (result != JOptionPane.OK_OPTION) return;

        try {
            String error = RoomService.updateRoom(section, oldRoom, roomField.getText(), Integer.parseInt(capacityField.getText().trim()));
            showResult(error, "Room updated.");
            if (error == null) AuditLog.addLog(Session.getUsername(), "Edited room " + oldRoom + " in section " + section);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Capacity must be a positive number.");
        }
    }

    private void deleteRoom() {
        int row = selectedModelRow();
        if (row == -1) return;

        String section = model.getValueAt(row, 0).toString();
        String room = model.getValueAt(row, 1).toString();
        int confirm = JOptionPane.showConfirmDialog(this, "Delete room " + room + " from " + section + "?", "Confirm", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;

        String error = RoomService.deleteRoom(section, room);
        showResult(error, "Room deleted.");
        if (error == null) AuditLog.addLog(Session.getUsername(), "Deleted room " + room + " from section " + section);
    }

    private void showResult(String error, String success) {
        if (error != null) {
            JOptionPane.showMessageDialog(this, error);
        } else {
            JOptionPane.showMessageDialog(this, success);
            loadRooms();
        }
    }

    private String chooseSection() {
        String[] sections = RoomService.getSections();
        if (sections.length == 0) {
            JOptionPane.showMessageDialog(this, "Please add a section first.");
            return null;
        }
        return (String) JOptionPane.showInputDialog(this, "Section:", "Choose Section",
                JOptionPane.PLAIN_MESSAGE, null, sections, sections[0]);
    }

    private String selectedSection() {
        int row = selectedModelRow();
        if (row == -1) return null;
        return model.getValueAt(row, 0).toString();
    }

    private int selectedModelRow() {
        int row = table.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Please select a row first.");
            return -1;
        }
        return table.convertRowIndexToModel(row);
    }
}
