package pages.patient.patient_directory;

/**
 * Holds the search and status criteria used by the Patient Management table.
 */
public class PatientListFilter {
    private String search = "";
    private String displayStatus = "All";

    /**
     * Returns the free-text search value used for patient directory filtering.
     */
    public String getSearch() {
        return search;
    }

    /**
     * Updates the free-text search value used for patient directory filtering.
     */
    public void setSearch(String search) {
        this.search = search == null ? "" : search;
    }

    /**
     * Returns the visible status filter selected in the patient directory.
     */
    public String getDisplayStatus() {
        return displayStatus;
    }

    /**
     * Updates the visible status filter selected in the patient directory.
     */
    public void setDisplayStatus(String displayStatus) {
        this.displayStatus = displayStatus == null ? "All" : displayStatus;
    }
}
