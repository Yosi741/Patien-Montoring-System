package gui;

import models.Patient;
import models.VitalSign;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;

public class DeceasedPatientDetailGUI extends JFrame {

    private final Patient patient;

    public DeceasedPatientDetailGUI(Patient patient) {
        this.patient = patient;
        setTitle("Deceased Patient Details - " + patient.getName());
        setSize(900, 760);
        setLocationRelativeTo(null);
        NavigationManager.configureChildWindow(this);

        JPanel main = UITheme.appPanel(new BorderLayout(14, 14));
        main.setBorder(new EmptyBorder(22, 22, 22, 22));

        JLabel title = UITheme.title("Deceased Patient Details", 26);
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(title, BorderLayout.WEST);
        header.add(NavigationManager.homeButton(this), BorderLayout.EAST);

        JTextArea details = new JTextArea(buildDetails());
        details.setEditable(false);
        details.setLineWrap(true);
        details.setWrapStyleWord(true);
        details.setFont(UITheme.font(Font.PLAIN, 15));
        details.setBorder(new EmptyBorder(16, 16, 16, 16));

        JButton openPdf = UITheme.secondaryButton("Open Death Certificate");
        openPdf.addActionListener(e -> openPdf());

        main.add(header, BorderLayout.NORTH);
        main.add(new JScrollPane(details), BorderLayout.CENTER);
        main.add(openPdf, BorderLayout.SOUTH);

        add(main);
    }

    private String buildDetails() {
        StringBuilder builder = new StringBuilder();
        builder.append("Patient ID: ").append(patient.getPatientId()).append("\n");
        builder.append("Name: ").append(patient.getName()).append("\n");
        builder.append("Birth Date: ").append(patient.getBirthDate()).append("\n");
        builder.append("Gender: ").append(patient.getGender()).append("\n");
        builder.append("Section: ").append(patient.getSection()).append("\n");
        builder.append("Room: ").append(patient.getRoom()).append("\n");
        builder.append("Status: ").append(patient.getStatus()).append("\n\n");
        builder.append("Last Recorded Vitals:\n").append(formatVitals(patient.getVitalSign())).append("\n\n");
        builder.append("Medical History Summary:\n");
        builder.append("Diagnosis: ").append(patient.getDiagnosis()).append("\n");
        builder.append("History: ").append(patient.getMedicalHistory()).append("\n");
        builder.append("Medications: ").append(patient.getCurrentMedications()).append("\n");
        builder.append("Allergies: ").append(patient.getAllergies()).append("\n\n");
        builder.append("Death Time: ").append(patient.getDeathDateTime()).append("\n");
        builder.append("Cause of Death: ").append(patient.getDeathCause()).append("\n");
        builder.append("Clinical Summary: ").append(patient.getDeathClinicalSummary()).append("\n");
        builder.append("Doctor Notes: ").append(patient.getDeathNotes()).append("\n");
        builder.append("Pronouncing Doctor: ").append(patient.getPronouncingDoctorName())
                .append(" / ID: ").append(patient.getPronouncingDoctorId()).append("\n");
        builder.append("Certificate Number: ").append(patient.getDeathCertificateNumber()).append("\n");
        builder.append("Certificate Path: ").append(patient.getDeathCertificatePath()).append("\n");
        return builder.toString();
    }

    private String formatVitals(VitalSign vitalSign) {
        if (vitalSign == null) {
            return "No vitals recorded.";
        }
        return String.format("Temperature: %.1f C\nHeart Rate: %d bpm\nBlood Pressure: %d/%d mmHg\nOxygen: %d%%",
                vitalSign.getTemperature(),
                vitalSign.getHeartRate(),
                vitalSign.getSystolicPressure(),
                vitalSign.getDiastolicPressure(),
                vitalSign.getOxygenLevel());
    }

    private void openPdf() {
        if (patient.getDeathCertificatePath().isBlank()) {
            JOptionPane.showMessageDialog(this, "No death certificate PDF has been generated yet.");
            return;
        }
        try {
            Desktop.getDesktop().open(new File(patient.getDeathCertificatePath()));
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Could not open PDF. Saved at:\n" + patient.getDeathCertificatePath());
        }
    }
}
