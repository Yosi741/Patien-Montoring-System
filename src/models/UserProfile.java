package models;

public class UserProfile {
    private String username;
    private String displayName;
    private String phone;
    private String email;
    private String photoPath;

    public UserProfile(String username, String displayName, String phone, String email, String photoPath) {
        this.username = username;
        this.displayName = displayName;
        this.phone = phone;
        this.email = email;
        this.photoPath = photoPath;
    }

    public String getUsername() { return username; }
    public String getDisplayName() { return displayName; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public String getPhotoPath() { return photoPath; }
}
