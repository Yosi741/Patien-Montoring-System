package gui;

import database.FileStorage;
import database.HospitalData;
import models.Patient;
import services.RoomService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;

public class EditPatientGUI extends JFrame {

    private JTextField firstNameField;
    private JTextField lastNameField;
    private JTextField birthDateField;
    private JComboBox<String> genderBox;
    private JComboBox<String> sectionBox;
    private JComboBox<String> roomBox;

    public EditPatientGUI(Patient patient) {

        setTitle("Edit Patient");
        setSize(520, 480);
        setLocationRelativeTo(null);
        NavigationManager.configureChildWindow(this);

        JPanel panel = UITheme.appPanel(new GridLayout(7, 2, 12, 12));
        panel.setBorder(new EmptyBorder(24, 24, 24, 24));

        firstNameField = new JTextField(patient.getFirstName());
        lastNameField = new JTextField(patient.getLastName());
        birthDateField = new JTextField(patient.getBirthDate());
        genderBox = new JComboBox<>(new String[]{"Male", "Female", "Other"});
        genderBox.setSelectedItem(patient.getGender());
        sectionBox = new JComboBox<>(RoomService.getSections());
        sectionBox.setSelectedItem(patient.getSection());
        roomBox = new JComboBox<>();
        loadRooms(patient.getRoom());

        JButton saveButton = UITheme.button("Save Changes", UITheme.PRIMARY);
        UITheme.styleTextField(firstNameField);
        UITheme.styleTextField(lastNameField);
        UITheme.styleTextField(birthDateField);
        sectionBox.addActionListener(e -> loadRooms(null));

        panel.add(new JLabel("First Name:"));
        panel.add(firstNameField);

        panel.add(new JLabel("Last Name:"));
        panel.add(lastNameField);

        panel.add(new JLabel("Birth Date (DD-MM-YYYY):"));
        panel.add(birthDateField);

        panel.add(new JLabel("Gender:"));
        panel.add(genderBox);

        panel.add(new JLabel("Hospital Section:"));
        panel.add(sectionBox);

        panel.add(new JLabel("Room:"));
        panel.add(roomBox);

        panel.add(new JLabel());
        panel.add(saveButton);

        saveButton.addActionListener(e -> {

            String birthDate = birthDateField.getText().trim();

            if (!birthDate.matches("\\d{2}-\\d{2}-\\d{4}")) {
                JOptionPane.showMessageDialog(
                        this,
                        "Birth date must be DD-MM-YYYY\nExample: 03-05-2005"
                );
                return;
            }

            patient.setFirstName(firstNameField.getText().trim());
            patient.setLastName(lastNameField.getText().trim());
            patient.setBirthDate(birthDate);
            patient.setGender(genderBox.getSelectedItem().toString());
            patient.setSection(sectionBox.getSelectedItem().toString());
            patient.setRoom(roomBox.getSelectedItem().toString());

            if (RoomService.isRoomFull(patient.getSection(), patient.getRoom(), patient.getPatientId())) {
                JOptionPane.showMessageDialog(this, "Room is full, please choose another room.");
                return;
            }

            FileStorage.savePatients(
                    HospitalData.patientManager.getPatients()

            );
            logs.AuditLog.addLog(
                    users.Session.getUsername(),
                    "Edited patient: " + patient.getName()
            );

            JOptionPane.showMessageDialog(
                    this,
                    "Patient Updated Successfully"
            );

            dispose();
        });

        add(panel);
    }

    private void loadRooms(String selectedRoom) {
        roomBox.removeAllItems();
        ArrayList<String> rooms = RoomService.getRoomsForSection(sectionBox.getSelectedItem().toString());
        for (String room : rooms) {
            roomBox.addItem(room);
        }
        if (selectedRoom != null) {
            roomBox.setSelectedItem(selectedRoom);
        }
    }
}
