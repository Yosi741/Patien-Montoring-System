package gui;

import models.NewbornRecord;
import database.NewbornMeasurementStorage;
import models.NewbornMeasurement;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class NewbornDetailGUI extends JFrame {

    public NewbornDetailGUI(NewbornRecord newborn) {
        setTitle("Newborn Details - " + newborn.getBabyName());
        setSize(850, 620);
        setLocationRelativeTo(null);
        NavigationManager.configureChildWindow(this);

        JPanel main = UITheme.appPanel(new BorderLayout(14, 14));
        main.setBorder(new EmptyBorder(22, 22, 22, 22));

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(UITheme.title("Newborn Details", 26), BorderLayout.WEST);
        top.add(NavigationManager.homeButton(this), BorderLayout.EAST);

        JTextArea details = new JTextArea();
        details.setEditable(false);
        details.setLineWrap(true);
        details.setWrapStyleWord(true);
        details.setFont(UITheme.font(Font.PLAIN, 15));
        details.setText(
                "Baby ID: " + newborn.getBabyId() + "\n" +
                        "Baby Name: " + newborn.getBabyName() + "\n" +
                        "Mother: " + newborn.getMotherFirstName() + " " + newborn.getMotherLastName() + " / " + newborn.getMotherId() + "\n" +
                        "Father: " + newborn.getFatherFirstName() + " " + newborn.getFatherLastName() + "\n" +
                        "Birth Date/Time: " + newborn.getBirthDateTime() + "\n" +
                        "Gender: " + newborn.getGender() + "\n" +
                        "Birth Weight: " + newborn.getBirthWeightKg() + " kg\n" +
                        "Delivery Type: " + newborn.getDeliveryType() + "\n" +
                        "Section/Room: " + newborn.getSection() + " " + newborn.getRoom() + "\n" +
                        "Hospital Status: " + newborn.getHospitalStatus() + "\n" +
                        "Premature: " + (newborn.isPremature() ? "Yes" : "No") + "\n" +
                        "Vitals / Measurements: " + newborn.getVitals() + "\n" +
                        "Weight / Measurement History:\n" + measurementHistory(newborn) +
                        "Notes: " + newborn.getNotes() + "\n" +
                        "Certificate: " + newborn.getCertificatePath()
        );

        main.add(top, BorderLayout.NORTH);
        main.add(new JScrollPane(details), BorderLayout.CENTER);
        add(main);
    }

    private String measurementHistory(NewbornRecord newborn) {
        StringBuilder builder = new StringBuilder();
        for (NewbornMeasurement m : NewbornMeasurementStorage.getForBaby(newborn.getBabyId())) {
            builder.append("- ").append(m.getTimestamp()).append(" | ")
                    .append(m.getType()).append(": ").append(m.getValue())
                    .append(" | ").append(m.getNotes()).append("\n");
        }
        if (builder.length() == 0) builder.append("- No measurement history recorded.\n");
        return builder.toString();
    }
}
