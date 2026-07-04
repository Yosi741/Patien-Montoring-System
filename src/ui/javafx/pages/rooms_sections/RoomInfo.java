package ui.javafx.pages.rooms_sections;

public class RoomInfo {

    private String sectionName;
    private String roomNumber;
    private int capacity;

    public RoomInfo(String sectionName, String roomNumber, int capacity) {
        this.sectionName = sectionName;
        this.roomNumber = roomNumber;
        this.capacity = capacity;
    }

    public String getSectionName() {
        return sectionName;
    }

    public String getRoomNumber() {
        return roomNumber;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setSectionName(String sectionName) {
        this.sectionName = sectionName;
    }

    public void setRoomNumber(String roomNumber) {
        this.roomNumber = roomNumber;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }
}
