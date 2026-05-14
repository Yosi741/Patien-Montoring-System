package gui;

import logs.AuditLog;
import database.MotherStorage;
import database.NewbornStorage;
import database.NewbornMeasurementStorage;
import database.NotificationStorage;
import models.MotherInfo;
import models.NewbornRecord;
import services.CertificateService;
import services.RolePermissionService;
import users.Session;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class BirthCertificateGUI extends JFrame {

    private String lastPdfPath = "";
    private JTextField signatureImagePath;

    public BirthCertificateGUI() {
        setTitle("Newborn Birth Certificate");
        setSize(760, 680);
        setLocationRelativeTo(null);
        NavigationManager.configureChildWindow(this);

        if (!RolePermissionService.canCreateBirthCertificate(Session.getCurrentUser())) {
            JOptionPane.showMessageDialog(this, "You are not authorized to create birth certificates.");
            dispose();
            return;
        }

        JPanel main = UITheme.appPanel(new BorderLayout(14, 14));
        main.setBorder(new EmptyBorder(22, 22, 22, 22));

        JLabel title = UITheme.title("Newborn Registration / Birth Certificate", 26);
        JPanel form = UITheme.cardPanel();
        form.setLayout(new GridLayout(11, 2, 12, 12));

        JTextField babyName = field("");
        JTextField motherId = field("");
        JTextField motherFirstName = field("");
        JTextField motherLastName = field("");
        JTextField fatherFirstName = field("");
        JTextField fatherLastName = field("");
        JTextField parentInfo = field("");
        JTextField birthTime = field(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss")));
        JComboBox<String> gender = new JComboBox<>(new String[]{"Male", "Female", "Other"});
        JTextField weight = field("");
        JComboBox<String> deliveryType = new JComboBox<>(new String[]{"Natural", "C-Section", "Assisted"});
        JTextField staffName = field(Session.getUsername());
        JTextField staffId = field("");
        JTextArea notes = new JTextArea();
        JTextField signature = field("");
        signatureImagePath = field("");
        JTextField pdfPath = field("");
        pdfPath.setEditable(false);
        signatureImagePath.setEditable(false);

        form.setLayout(new GridLayout(17, 2, 12, 12));

        form.add(new JLabel("Baby Name:")); form.add(babyName);
        form.add(new JLabel("Mother ID:")); form.add(motherId);
        JButton lookupMother = UITheme.secondaryButton("Lookup Mother ID");
        lookupMother.addActionListener(e -> {
            MotherInfo mother = MotherStorage.findById(motherId.getText().trim());
            if (mother == null) {
                JOptionPane.showMessageDialog(this, "Mother ID not found.");
                return;
            }
            motherFirstName.setText(mother.getFirstName());
            motherLastName.setText(mother.getLastName());
            parentInfo.setText(mother.getContactInfo());
        });
        form.add(new JLabel("Mother Lookup:")); form.add(lookupMother);
        form.add(new JLabel("Mother First Name:")); form.add(motherFirstName);
        form.add(new JLabel("Mother Last Name:")); form.add(motherLastName);
        form.add(new JLabel("Father First Name:")); form.add(fatherFirstName);
        form.add(new JLabel("Father Last Name:")); form.add(fatherLastName);
        form.add(new JLabel("Parent Contact Info:")); form.add(parentInfo);
        form.add(new JLabel("Date/Time of Birth:")); form.add(birthTime);
        form.add(new JLabel("Gender:")); form.add(gender);
        form.add(new JLabel("Birth Weight:")); form.add(weight);
        form.add(new JLabel("Delivery Type:")); form.add(deliveryType);
        form.add(new JLabel("Doctor/Nurse Name:")); form.add(staffName);
        form.add(new JLabel("Doctor/Nurse ID:")); form.add(staffId);
        form.add(new JLabel("Notes:")); form.add(new JScrollPane(notes));
        form.add(new JLabel("Signature:")); form.add(signature);
        form.add(new JLabel("Signature Image:")); form.add(signatureImagePath);
        form.add(new JLabel("PDF Path:")); form.add(pdfPath);

        JButton generateButton = UITheme.button("Generate PDF", UITheme.SUCCESS);
        JButton openButton = UITheme.secondaryButton("Open PDF");
        JButton homeButton = NavigationManager.homeButton(this);
        JButton importSignatureButton = UITheme.secondaryButton("Import Signature");
        generateButton.setEnabled(RolePermissionService.canGenerateCertificates(Session.getCurrentUser()));

        generateButton.addActionListener(e -> {
            if (motherId.getText().trim().isEmpty() || staffId.getText().trim().isEmpty()) {
                JOptionPane.showMessageDialog(this, "Mother ID and staff ID are required.");
                return;
            }

            if (signature.getText().trim().isEmpty()) {
                JOptionPane.showMessageDialog(this, "Signature is required.");
                return;
            }
            double parsedWeight;
            try {
                parsedWeight = Double.parseDouble(weight.getText().trim());
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Birth weight must be a number in kg.");
                return;
            }
            if (parsedWeight < 0.4 || parsedWeight > 7.0) {
                JOptionPane.showMessageDialog(this, "Birth weight must be realistic, between 0.4kg and 7.0kg.");
                return;
            }

            try {
                String path = CertificateService.generateBirthCertificate(
                        babyName.getText().trim(),
                        motherId.getText().trim(),
                        motherFirstName.getText().trim(),
                        motherLastName.getText().trim(),
                        fatherFirstName.getText().trim(),
                        fatherLastName.getText().trim(),
                        parentInfo.getText().trim(),
                        birthTime.getText().trim(),
                        gender.getSelectedItem().toString(),
                        weight.getText().trim(),
                        deliveryType.getSelectedItem().toString(),
                        staffName.getText().trim(),
                        staffId.getText().trim(),
                        notes.getText().trim(),
                        signature.getText().trim(),
                        signatureImagePath.getText().trim()
                );

                lastPdfPath = path;
                pdfPath.setText(path);
                String babyId = "BABY-" + System.currentTimeMillis();
                NewbornStorage.addNewborn(new NewbornRecord(
                        babyId,
                        babyName.getText().trim(),
                        motherId.getText().trim(),
                        motherFirstName.getText().trim(),
                        motherLastName.getText().trim(),
                        fatherFirstName.getText().trim(),
                        fatherLastName.getText().trim(),
                        birthTime.getText().trim(),
                        gender.getSelectedItem().toString(),
                        parsedWeight,
                        deliveryType.getSelectedItem().toString(),
                        "Newborn",
                        "",
                        "Admitted",
                        parsedWeight < 2.5,
                        "Birth weight: " + parsedWeight + " kg",
                        notes.getText().trim(),
                        path
                ));
                NewbornMeasurementStorage.addMeasurement(babyId, "Birth Weight", parsedWeight + " kg", "Initial birth measurement");
                NotificationStorage.addNotification("ALL", "INFO", "Birth certificate generated for newborn: " + babyName.getText().trim());
                AuditLog.addLog(Session.getUsername(), "Generated birth certificate for newborn: " + babyName.getText().trim());
                JOptionPane.showMessageDialog(this, "Birth certificate PDF generated:\n" + path);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Could not generate certificate: " + ex.getMessage());
            }
        });

        importSignatureButton.addActionListener(e -> importSignature());

        openButton.addActionListener(e -> {
            if (lastPdfPath.isBlank()) {
                JOptionPane.showMessageDialog(this, "Please generate the PDF first.");
                return;
            }
            try {
                Desktop.getDesktop().open(new File(lastPdfPath));
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Could not open PDF. Saved at:\n" + lastPdfPath);
            }
        });

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttons.setOpaque(false);
        buttons.add(importSignatureButton);
        buttons.add(openButton);
        buttons.add(generateButton);
        buttons.add(homeButton);

        main.add(title, BorderLayout.NORTH);
        main.add(form, BorderLayout.CENTER);
        main.add(buttons, BorderLayout.SOUTH);

        add(main);
    }

    private JTextField field(String value) {
        JTextField field = new JTextField(value);
        UITheme.styleTextField(field);
        return field;
    }

    private void importSignature() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Signature images (png, jpg, jpeg)", "png", "jpg", "jpeg"));
        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            signatureImagePath.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }
}
