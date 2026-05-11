package gui;

import database.HospitalData;
import models.Patient;

import javax.swing.*;
import java.awt.*;

public class AddPatientGUI extends JFrame {

    private JTextField idField;
    private JTextField firstNameField;
    private JTextField lastNameField;
    private JTextField birthDateField;
    private JComboBox<String> genderBox;
    private JTextField roomField;

    public AddPatientGUI() {

        setTitle("Add Patient");
        setSize(450, 420);
        setLocationRelativeTo(null);

        JPanel panel = new JPanel(new GridLayout(7, 2, 10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        idField = new JTextField();
        firstNameField = new JTextField();
        lastNameField = new JTextField();
        birthDateField = new JTextField();
        genderBox = new JComboBox<>(new String[]{"Male", "Female"});
        roomField = new JTextField();

        JButton saveButton = new JButton("Save Patient");

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

        panel.add(new JLabel("Room:"));
        panel.add(roomField);

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

            Patient patient = new Patient(
                    id,
                    firstNameField.getText().trim(),
                    lastNameField.getText().trim(),
                    birthDate,
                    genderBox.getSelectedItem().toString(),
                    roomField.getText().trim()
            );

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
}