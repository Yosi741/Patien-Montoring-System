package models;

public class Medication {

    private String name;
    private String dosage;
    private String frequency;

    public Medication(String name, String dosage, String frequency) {
        this.name = name;
        this.dosage = dosage;
        this.frequency = frequency;
    }

    public String getName() {
        return name;
    }

    public String getDosage() {
        return dosage;
    }

    public String getFrequency() {
        return frequency;
    }

    public void displayMedication() {
        System.out.println("Medication: " + name);
        System.out.println("Dosage: " + dosage);
        System.out.println("Frequency: " + frequency);
    }
}