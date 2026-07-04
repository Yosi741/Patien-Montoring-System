package pages.patient;

public class VitalSign {

    private double temperature;
    private int heartRate;
    private int systolicPressure;
    private int diastolicPressure;
    private int oxygenLevel;

    public VitalSign(double temperature,
                     int heartRate,
                     int systolicPressure,
                     int diastolicPressure,
                     int oxygenLevel) {

        this.temperature = temperature;
        this.heartRate = heartRate;
        this.systolicPressure = systolicPressure;
        this.diastolicPressure = diastolicPressure;
        this.oxygenLevel = oxygenLevel;

    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public int getHeartRate() {
        return heartRate;
    }

    public void setHeartRate(int heartRate) {
        this.heartRate = heartRate;
    }

    public int getSystolicPressure() {
        return systolicPressure;
    }

    public void setSystolicPressure(int systolicPressure) {
        this.systolicPressure = systolicPressure;
    }

    public int getDiastolicPressure() {
        return diastolicPressure;
    }

    public void setDiastolicPressure(int diastolicPressure) {
        this.diastolicPressure = diastolicPressure;
    }

    public int getOxygenLevel() {
        return oxygenLevel;
    }

    public void setOxygenLevel(int oxygenLevel) {
        this.oxygenLevel = oxygenLevel;
    }

    public void displayVitalSigns() {

        System.out.println("Temperature: " + temperature);
        System.out.println("Heart Rate: " + heartRate);
        System.out.println("Blood Pressure: "
                + systolicPressure + "/" + diastolicPressure);

        System.out.println("Oxygen Level: " + oxygenLevel + "%");

    }

}
