package peerlinkfilesharingsystem.Service.Chunked;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import peerlinkfilesharingsystem.Enums.NetworkCondition;

/**
 * Chunk Size Calculator
 * 
 * Calculates optimal chunk size based on:
 * - Network speed (Mbps)
 * - Network stability (variance)
 * - Device type (desktop, mobile)
 * 
 * Goal: Each chunk should take ~2-3 seconds to upload
 * - Too small: Too many HTTP requests (overhead)
 * - Too large: Timeout risk, wasted bandwidth on retry
 * 
 * @author PeerLink Team
 * @version 1.0
 */
@Service
@Slf4j
public class ChunkSizeCalculator {
    
    // ==================== CONSTANTS ====================
    
    // Chunk size limits (bytes)
    private static final int MIN_CHUNK_SIZE = 256 * 1024;      // 256 KB
    private static final int MAX_CHUNK_SIZE = 5 * 1024 * 1024; // 5 MB
    private static final int DEFAULT_CHUNK_SIZE = 640 * 1024;  // 640 KB
    
    // Target upload time per chunk (seconds)
    private static final double TARGET_UPLOAD_TIME = 2.5;
    
    // Variance thresholds
    private static final double LOW_VARIANCE_THRESHOLD = 10.0;    // < 10% = stable
    private static final double HIGH_VARIANCE_THRESHOLD = 30.0;   // > 30% = unstable
    
    // Speed thresholds (Mbps)
    private static final double EXCELLENT_SPEED = 100.0;
    private static final double GOOD_SPEED = 50.0;
    private static final double FAIR_SPEED = 20.0;
    
    /**
     * Calculate optimal chunk size based on network conditions
     * 
     * Formula:
     * chunkSize = networkSpeedMbps × targetTime × 1024 × 1024 / 8
     * 
     * Then apply:
     * - Stability adjustments (reduce size if unstable)
     * - Device adjustments (reduce size for mobile)
     * - Min/max clamping
     * 
     * @param networkSpeedMbps Network speed in Mbps
     * @param variancePercent Speed variance percentage (0-100)
     * @param deviceType Device type ("DESKTOP", "MOBILE", "TABLET")
     * @return Optimal chunk size in bytes
     */
    public int calculateOptimalChunkSize(
            Double networkSpeedMbps,
            Double variancePercent,
            String deviceType) {
        
        // Handle null inputs
        if (networkSpeedMbps == null || networkSpeedMbps <= 0) {
            log.debug("No network speed provided, using default chunk size: {} KB",
                DEFAULT_CHUNK_SIZE / 1024);
            return DEFAULT_CHUNK_SIZE;
        }
        
        if (variancePercent == null) {
            variancePercent = 0.0;
        }
        
        if (deviceType == null) {
            deviceType = "DESKTOP";
        }
        
        log.debug("Calculating optimal chunk size: speed={} Mbps, variance={:.1f}%, device={}",
            networkSpeedMbps, variancePercent, deviceType);
        
        // ==================== BASE CALCULATION ====================
        
        // Calculate chunk size for target upload time
        // networkSpeedMbps * TARGET_TIME * (1024 * 1024 bits/MB) / 8 bits/byte
        double baseChunkSize = networkSpeedMbps * TARGET_UPLOAD_TIME * 1024 * 1024 / 8;
        
        log.trace("Base chunk size (before adjustments): {} KB", (int) baseChunkSize / 1024);
        
        // ==================== STABILITY ADJUSTMENT ====================
        
        double stabilityFactor = 1.0;
        
        if (variancePercent < LOW_VARIANCE_THRESHOLD) {
            // Very stable: can use larger chunks
            stabilityFactor = 1.2;
            log.trace("Network stable (variance {:.1f}%), increasing size by 20%", variancePercent);
            
        } else if (variancePercent > HIGH_VARIANCE_THRESHOLD) {
            // Unstable: use smaller chunks to reduce retry penalty
            stabilityFactor = 0.6;
            log.trace("Network unstable (variance {:.1f}%), decreasing size by 40%", variancePercent);
            
        } else {
            // Moderate variance: use base size
            log.trace("Network moderate (variance {:.1f}%), using base size", variancePercent);
        }
        
        baseChunkSize *= stabilityFactor;
        
        // ==================== DEVICE ADJUSTMENT ====================
        
        double deviceFactor = 1.0;
        
        switch (deviceType.toUpperCase()) {
            case "MOBILE":
                // Mobile: smaller chunks due to:
                // - Less stable connections
                // - Background interruptions
                // - Battery considerations
                deviceFactor = 0.7;
                log.trace("Mobile device: reducing size by 30%");
                break;
                
            case "TABLET":
                deviceFactor = 0.85;
                log.trace("Tablet device: reducing size by 15%");
                break;
                
            case "DESKTOP":
            default:
                deviceFactor = 1.0;
                log.trace("Desktop device: using base size");
                break;
        }
        
        baseChunkSize *= deviceFactor;
        
        // ==================== CLAMP TO LIMITS ====================
        
        int finalChunkSize = (int) baseChunkSize;
        
        if (finalChunkSize < MIN_CHUNK_SIZE) {
            finalChunkSize = MIN_CHUNK_SIZE;
            log.trace("Clamped to minimum: {} KB", finalChunkSize / 1024);
            
        } else if (finalChunkSize > MAX_CHUNK_SIZE) {
            finalChunkSize = MAX_CHUNK_SIZE;
            log.trace("Clamped to maximum: {} KB", finalChunkSize / 1024);
        }
        
        // ==================== ROUND TO NEAREST 64 KB ====================
        
        // Makes chunk sizes more predictable and cache-friendly
        int roundingFactor = 64 * 1024;  // 64 KB
        finalChunkSize = (finalChunkSize / roundingFactor) * roundingFactor;
        
        log.info("✅ Optimal chunk size: {} KB ({} bytes) for {} Mbps network",
            finalChunkSize / 1024, finalChunkSize, networkSpeedMbps);
        
        return finalChunkSize;
    }
    
    /**
     * Assess network condition from speed and variance
     * 
     * @param averageSpeedMbps Average speed
     * @param variancePercent Variance percentage
     * @return NetworkCondition assessment
     */
    public NetworkCondition assessNetworkCondition(
            Double averageSpeedMbps,
            Double variancePercent) {
        
        return NetworkCondition.fromMetrics(averageSpeedMbps, variancePercent);
    }
    
    /**
     * Determine if chunk size should be adapted
     * 
     * Only adapt if difference is significant (> 25%)
     * Prevents frequent small adjustments
     * 
     * @param currentChunkSize Current chunk size
     * @param recommendedChunkSize Recommended chunk size
     * @return true if should adapt
     */
    public boolean shouldAdaptChunkSize(int currentChunkSize, int recommendedChunkSize) {
        
        double difference = Math.abs(currentChunkSize - recommendedChunkSize);
        double percentDifference = (difference / currentChunkSize) * 100;
        
        boolean shouldAdapt = percentDifference > 25.0;
        
        log.debug("Chunk size adaptation check: current={} KB, recommended={} KB, diff={:.1f}%, adapt={}",
            currentChunkSize / 1024,
            recommendedChunkSize / 1024,
            percentDifference,
            shouldAdapt);
        
        return shouldAdapt;
    }
    
    /**
     * Calculate expected upload time for given chunk size and speed
     * 
     * @param chunkSizeBytes Chunk size in bytes
     * @param speedMbps Upload speed in Mbps
     * @return Expected upload time in seconds
     */
    public double calculateExpectedUploadTime(long chunkSizeBytes, double speedMbps) {
        
        if (speedMbps <= 0) {
            return -1;
        }
        
        // Convert speed from Mbps to bytes/second
        double speedBytesPerSecond = (speedMbps * 1024 * 1024) / 8;
        
        return chunkSizeBytes / speedBytesPerSecond;
    }
    
    /**
     * Get recommended chunk size for specific network condition
     * 
     * @param condition Network condition
     * @return Recommended chunk size in bytes
     */
    public int getRecommendedChunkSizeForCondition(NetworkCondition condition) {
        return condition.getRecommendedChunkSize();
    }
}
