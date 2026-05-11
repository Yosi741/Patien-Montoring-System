package gui;

import database.FileStorage;
import database.HospitalData;
import models.Patient;

import javax.swing.*;
import java.awt.*;

public class EditPatientGUI extends JFrame {

    private JTextField firstNameField;
    private JTextField lastNameField;
    private JTextField birthDateField;
    private JComboBox<String> genderBox;
    private JTextField roomField;

    public EditPatientGUI(Patient patient) {

        setTitle("Edit Patient");
        setSize(450, 420);
        setLocationRelativeTo(null);

        JPanel panel = new JPanel(new GridLayout(6, 2, 10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        firstNameField = new JTextField(patient.getFirstName());
        lastNameField = new JTextField(patient.getLastName());
        birthDateField = new JTextField(patient.getBirthDate());
        genderBox = new JComboBox<>(new String[]{"Male", "Female"});
        genderBox.setSelectedItem(patient.getGender());
        roomField = new JTextField(patient.getRoom());

        JButton saveButton = new JButton("Save Changes");

        panel.add(new JLabel("First Name:"));
        panel.add(firstNameField);

        panel.add(new JLabel("Last Name:"));
        panel.add(lastNameField);

        panel.add(new JLabel("Birth Date (DD-MM-YYYY):"));
        panel.add(birthDateField);

        panel.add(new JLabel("Gender:"));
        panel.add(genderBox);

        panel.add(new JLabel("Room:"));
        panel.add(roomField);

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
            patient.setRoom(roomField.getText().trim());

            FileStorage.savePatients(
                    HospitalData.patientManager.getPatients()

            );
            logs.AuditLog.addLog(
                    "Admin",
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
}