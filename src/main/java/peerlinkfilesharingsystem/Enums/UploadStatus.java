package peerlinkfilesharingsystem.Enums;


public enum UploadStatus {
    
    INITIALIZED("Session initialized, ready to upload"),

    UPLOADING("Uploading chunks"),

    ASSEMBLING("Assembling final file"),

    COMPLETED("Upload completed successfully"),

    FAILED("Upload failed"),

    CANCELLED("Upload cancelled by user"),

    EXPIRED("Session expired");
    
    private final String description;
    
    UploadStatus(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }

    public boolean isTerminal() {
        return this == COMPLETED || 
               this == FAILED || 
               this == CANCELLED || 
               this == EXPIRED;
    }
    
    public boolean isActive() {
        return this == INITIALIZED || this == UPLOADING;
    }
}
