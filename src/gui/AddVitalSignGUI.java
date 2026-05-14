package gui;

import models.Patient;
import models.VitalSign;
import services.VitalService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class AddVitalSignGUI extends JFrame {

    public AddVitalSignGUI(Patient patient) {

        setTitle("Add Vital Signs - " + patient.getName());
        setSize(450, 420);
        setLocationRelativeTo(null);
        NavigationManager.configureChildWindow(this);

        if (patient.isDeceased()) {
            JOptionPane.showMessageDialog(this, "Cannot add new vital signs for a deceased patient.");
            dispose();
            return;
        }

        JPanel panel = UITheme.appPanel(new GridLayout(7, 2, 12, 12));
        panel.setBorder(new EmptyBorder(25, 25, 25, 25));

        JTextField tempField = new JTextField();
        JTextField heartField = new JTextField();
        JTextField systolicField = new JTextField();
        JTextField diastolicField = new JTextField();
        JTextField oxygenField = new JTextField();

        panel.add(new JLabel("Temperature °C:"));
        panel.add(tempField);

        panel.add(new JLabel("Heart Rate bpm:"));
        panel.add(heartField);

        panel.add(new JLabel("Systolic Pressure:"));
        panel.add(systolicField);

        panel.add(new JLabel("Diastolic Pressure:"));
        panel.add(diastolicField);

        panel.add(new JLabel("Oxygen Level %:"));
        panel.add(oxygenField);

        panel.add(new JLabel("Example:"));
        panel.add(new JLabel("37.0 | 80 | 120 | 80 | 98"));

        JButton saveButton = UITheme.button("Save Vitals", UITheme.SUCCESS);
        UITheme.styleTextField(tempField);
        UITheme.styleTextField(heartField);
        UITheme.styleTextField(systolicField);
        UITheme.styleTextField(diastolicField);
        UITheme.styleTextField(oxygenField);

        panel.add(new JLabel());
        panel.add(saveButton);

        add(panel);

        saveButton.addActionListener(e -> {
            try {
                double temperature = Double.parseDouble(tempField.getText().trim());
                int heartRate = Integer.parseInt(heartField.getText().trim());
                int systolic = Integer.parseInt(systolicField.getText().trim());
                int diastolic = Integer.parseInt(diastolicField.getText().trim());
                int oxygen = Integer.parseInt(oxygenField.getText().trim());

                String validationError = VitalService.validateVitals(temperature, heartRate, systolic, diastolic, oxygen);
                if (validationError != null) {
                    JOptionPane.showMessageDialog(this, validationError);
                    return;
                }

                VitalSign vitalSign = new VitalSign(
                        temperature,
                        heartRate,
                        systolic,
                        diastolic,
                        oxygen
                );

                VitalService.recordManualVitals(patient, vitalSign);

                JOptionPane.showMessageDialog(this, "Vital Signs Added Successfully");
                dispose();

            } catch (Exception ex) {
                JOptionPane.showMessageDialog(
                        this,
                        "Invalid input. Please enter numbers only.\nExample: 37.0, 80, 120, 80, 98"
                );
            }
        });
    }
}
