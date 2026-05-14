package gui;

import database.HospitalData;
import models.Patient;
import services.RoomService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;

public class AddPatientGUI extends JFrame {

    private final JTextField idField;
    private final JTextField firstNameField;
    private final JTextField lastNameField;
    private final JTextField birthDateField;
    private final JComboBox<String> genderBox;
    private final JComboBox<String> sectionBox;
    private final JComboBox<String> roomBox;

    public AddPatientGUI() {

        setTitle("Add Patient");
        setSize(520, 480);
        setLocationRelativeTo(null);
        NavigationManager.configureChildWindow(this);

        JPanel panel = UITheme.appPanel(new GridLayout(8, 2, 12, 12));
        panel.setBorder(new EmptyBorder(24, 24, 24, 24));

        idField = new JTextField();
        firstNameField = new JTextField();
        lastNameField = new JTextField();
        birthDateField = new JTextField();
        genderBox = new JComboBox<>(new String[]{"Male", "Female", "Other"});
        sectionBox = new JComboBox<String>(RoomService.getSections());
        roomBox = new JComboBox<String>();

        JButton saveButton = UITheme.button("Save Patient", UITheme.PRIMARY);
        UITheme.styleTextField(idField);
        UITheme.styleTextField(firstNameField);
        UITheme.styleTextField(lastNameField);
        UITheme.styleTextField(birthDateField);

        sectionBox.addActionListener(e -> loadRooms());
        loadRooms();

        panel.add(new JLabel("Patient ID (9 digits):"));
        panel.add(idField);

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

        saveButton.addActionListener(e -> savePatient());

        add(panel);
    }

    private void savePatient() {
        try {
            String id = idField.getText().trim();
            String birthDate = birthDateField.getText().trim();

            if (!birthDate.matches("\\d{2}-\\d{2}-\\d{4}")) {
                JOptionPane.showMessageDialog(
                        this,
                        "Birth date must be DD-MM-YYYY\nExample: 03-05-2005"
                );
                return;
            }

            if (!id.matches("\\d{9}")) {
                JOptionPane.showMessageDialog(this, "Patient ID must be exactly 9 digits");
                return;
            }

            for (Patient existingPatient : HospitalData.patientManager.getPatients()) {
                if (existingPatient.getPatientId().equals(id)) {
                    JOptionPane.showMessageDialog(
                            this,
                            "This Patient ID is already used.\nPlease enter a unique ID."
                    );
                    return;
                }
            }

            String selectedGender = selectedValue(genderBox);
            String selectedSection = selectedValue(sectionBox);
            String selectedRoom = selectedValue(roomBox);

            if (selectedSection.isEmpty() || selectedRoom.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please choose a valid section and room.");
                return;
            }

            Patient patient = new Patient(
                    id,
                    firstNameField.getText().trim(),
                    lastNameField.getText().trim(),
                    birthDate,
                    selectedGender,
                    selectedRoom
            );
            patient.setSection(selectedSection);

            if (RoomService.isRoomFull(patient.getSection(), patient.getRoom(), null)) {
                JOptionPane.showMessageDialog(this, "Room is full, please choose another room.");
                return;
            }

            HospitalData.patientManager.addPatient(patient);
            logs.AuditLog.addLog(
                    users.Session.getUsername(),
                    "Added patient: " + patient.getName()
            );

            JOptionPane.showMessageDialog(this, "Patient Added Successfully");
            dispose();

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Invalid Input. Birth date must be YYYY-MM-DD");
        }
    }

    private void loadRooms() {
        roomBox.removeAllItems();
        ArrayList<String> rooms = RoomService.getRoomsForSection(selectedValue(sectionBox));
        for (String room : rooms) {
            roomBox.addItem(room);
        }
    }

    private String selectedValue(JComboBox<String> comboBox) {
        Object value = comboBox.getSelectedItem();
        if (value == null) {
            return "";
        }
        return value.toString();
    }
}
