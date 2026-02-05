package peerlinkfilesharingsystem.Enums;


public enum NetworkCondition {
    

    EXCELLENT(100.0, "Excellent - Use large chunks"),

    GOOD(50.0, "Good - Use medium chunks"),

    FAIR(20.0, "Fair - Use default chunks"),

    POOR(0.0, "Poor - Use small chunks"),

    UNSTABLE(0.0, "Unstable - Use very small chunks");
    
    private final double minSpeedMbps;
    private final String recommendation;
    
    NetworkCondition(double minSpeedMbps, String recommendation) {
        this.minSpeedMbps = minSpeedMbps;
        this.recommendation = recommendation;
    }
    
    public double getMinSpeedMbps() {
        return minSpeedMbps;
    }
    
    public String getRecommendation() {
        return recommendation;
    }
    

    public static NetworkCondition fromMetrics(Double averageSpeedMbps, Double variancePercent) {
        if (averageSpeedMbps == null) {
            return FAIR; // Default
        }
        
        // Check stability first
        if (variancePercent != null && variancePercent > 30) {
            return UNSTABLE;
        }
        
        // Assess based on speed
        if (averageSpeedMbps >= 100) {
            return EXCELLENT;
        } else if (averageSpeedMbps >= 50) {
            return GOOD;
        } else if (averageSpeedMbps >= 20) {
            return FAIR;
        } else {
            return POOR;
        }
    }
    

    public int getRecommendedChunkSize() {
        switch (this) {
            case EXCELLENT:
                return 5 * 1024 * 1024;  // 5 MB
            case GOOD:
                return 2 * 1024 * 1024;  // 2 MB
            case FAIR:
                return 640 * 1024;       // 640 KB
            case POOR:
                return 384 * 1024;       // 384 KB
            case UNSTABLE:
                return 256 * 1024;       // 256 KB
            default:
                return 640 * 1024;       // Default 640 KB
        }
    }
}
