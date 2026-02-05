package peerlinkfilesharingsystem.Model.Chunked;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadSession implements Serializable {
    
    private static final long serialVersionUID = 1L;

    private String uploadId;

    private String userId;

    private String username;
    
    private String fileName;

    private Long fileSize;

    private String fileType;

    private String mimeType;

    private Integer totalChunks;

    private Integer chunkSize;

    @Builder.Default
    private Set<Integer> receivedChunks = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private String status;

    private Double progressPercent;

    @Builder.Default
    private List<Double> speedHistory = Collections.synchronizedList(new ArrayList<>());

    private Double currentSpeedMbps;

    private Double averageSpeedMbps;

    private Double minSpeedMbps;

    private Double maxSpeedMbps;

    private Double speedVariancePercent;

    private Long startTime;

    private Long lastChunkTime;

    private LocalDateTime createdAt;

    private LocalDateTime completedAt;

    private Long totalDurationSeconds;

    private String tempDirectory;

    private String finalFilePath;

    private String errorMessage;

    @Builder.Default
    private Integer retryCount = 0;

    @Builder.Default
    private Integer maxRetries = 3;
    
    private String clientIp;

    private String userAgent;

    /**
     * Additional metadata (flexible key-value storage)
     */
    @Builder.Default
    private Map<String, String> metadata = new ConcurrentHashMap<>();

    // ==================== METHODS ====================

    /**
     * Record speed measurement for a chunk upload
     *
     * @param chunkSizeBytes Size of the chunk in bytes
     * @param durationMs Upload duration in milliseconds
     */
    public synchronized void recordChunkSpeed(long chunkSizeBytes, long durationMs) {
        if (durationMs <= 0) {
            return;
        }

        // Calculate speed in Mbps
        // Formula: (bytes * 8 bits/byte) / (milliseconds * 1000 ms/s * 1024 * 1024 bits/Mb)
        double speedMbps = (chunkSizeBytes * 8.0) / (durationMs * 1000.0);

        // Update current speed
        this.currentSpeedMbps = speedMbps;
        
        // Add to history
        speedHistory.add(speedMbps);
        
        // Update min/max
        if (minSpeedMbps == null || speedMbps < minSpeedMbps) {
            minSpeedMbps = speedMbps;
        }
        if (maxSpeedMbps == null || speedMbps > maxSpeedMbps) {
            maxSpeedMbps = speedMbps;
        }
        
        // Calculate rolling average (last 10 chunks)
        int windowSize = Math.min(speedHistory.size(), 10);
        double sum = 0;
        for (int i = speedHistory.size() - windowSize; i < speedHistory.size(); i++) {
            sum += speedHistory.get(i);
        }
        this.averageSpeedMbps = sum / windowSize;
        
        // Calculate variance
        if (speedHistory.size() >= 5) {
            calculateSpeedVariance();
        }
    }
    
    /**
     * Calculate speed variance percentage
     * High variance indicates unstable network
     */
    private void calculateSpeedVariance() {
        if (speedHistory.isEmpty() || averageSpeedMbps == null) {
            return;
        }
        
        // Use last 10 samples
        int windowSize = Math.min(speedHistory.size(), 10);
        List<Double> recentSpeeds = speedHistory.subList(
            speedHistory.size() - windowSize, 
            speedHistory.size()
        );
        
        // Calculate standard deviation
        double sumSquaredDiff = 0;
        for (Double speed : recentSpeeds) {
            double diff = speed - averageSpeedMbps;
            sumSquaredDiff += diff * diff;
        }
        
        double standardDeviation = Math.sqrt(sumSquaredDiff / windowSize);
        
        // Variance as percentage of average
        this.speedVariancePercent = (standardDeviation / averageSpeedMbps) * 100;
    }
    
    /**
     * Update progress percentage based on received chunks
     */
    public synchronized void updateProgress() {
        if (totalChunks == null || totalChunks == 0) {
            this.progressPercent = 0.0;
            return;
        }
        
        this.progressPercent = (receivedChunks.size() * 100.0) / totalChunks;
    }
    
    /**
     * Check if upload is complete
     * 
     * @return true if all chunks received
     */
    public boolean isComplete() {
        return receivedChunks.size() >= totalChunks;
    }
    
    /**
     * Get list of missing chunk numbers
     * 
     * @return List of chunk numbers not yet received
     */
    public List<Integer> getMissingChunks() {
        List<Integer> missing = new ArrayList<>();
        for (int i = 0; i < totalChunks; i++) {
            if (!receivedChunks.contains(i)) {
                missing.add(i);
            }
        }
        return missing;
    }
    
    /**
     * Get count of missing chunks
     * 
     * @return Number of chunks not yet received
     */
    public int getMissingChunkCount() {
        return totalChunks - receivedChunks.size();
    }
    
    /**
     * Estimate time remaining in seconds
     * Based on average speed and remaining bytes
     * 
     * @return Estimated seconds remaining, or null if cannot estimate
     */
    public Long estimateTimeRemaining() {
        if (averageSpeedMbps == null || averageSpeedMbps <= 0) {
            return null;
        }
        
        int remainingChunks = getMissingChunkCount();
        if (remainingChunks == 0) {
            return 0L;
        }
        
        // Estimate remaining bytes (last chunk may be smaller, but close enough)
        long remainingBytes = (long) remainingChunks * chunkSize;
        
        // Convert speed from Mbps to bytes/second
        // Mbps = megabits per second
        // bytes/sec = (Mbps * 1024 * 1024) / 8
        double speedBytesPerSecond = (averageSpeedMbps * 1024 * 1024) / 8;
        
        return (long) (remainingBytes / speedBytesPerSecond);
    }
    
    /**
     * Format estimated time remaining as human-readable string
     * 
     * @return String like "5m 23s" or "1h 15m 30s"
     */
    public String getFormattedTimeRemaining() {
        Long seconds = estimateTimeRemaining();
        if (seconds == null) {
            return "Calculating...";
        }
        
        if (seconds < 60) {
            return seconds + "s";
        } else if (seconds < 3600) {
            long minutes = seconds / 60;
            long secs = seconds % 60;
            return minutes + "m " + secs + "s";
        } else {
            long hours = seconds / 3600;
            long minutes = (seconds % 3600) / 60;
            long secs = seconds % 60;
            return hours + "h " + minutes + "m " + secs + "s";
        }
    }
    
    /**
     * Check if chunk is already received
     * 
     * @param chunkNumber Chunk number to check
     * @return true if chunk already received
     */
    public boolean hasChunk(Integer chunkNumber) {
        return receivedChunks.contains(chunkNumber);
    }
    
    /**
     * Mark chunk as received
     * Thread-safe operation
     * 
     * @param chunkNumber Chunk number to mark as received
     */
    public synchronized void markChunkReceived(Integer chunkNumber) {
        receivedChunks.add(chunkNumber);
        updateProgress();
        this.lastChunkTime = System.currentTimeMillis();
    }
    
    /**
     * Get network condition assessment based on speed metrics
     * 
     * @return "EXCELLENT", "GOOD", "FAIR", "POOR", or "UNSTABLE"
     */
    public String getNetworkCondition() {
        if (averageSpeedMbps == null) {
            return "UNKNOWN";
        }
        
        // Check stability first
        if (speedVariancePercent != null && speedVariancePercent > 30) {
            return "UNSTABLE";
        }
        
        // Assess based on speed
        if (averageSpeedMbps >= 100) {
            return "EXCELLENT";
        } else if (averageSpeedMbps >= 50) {
            return "GOOD";
        } else if (averageSpeedMbps >= 20) {
            return "FAIR";
        } else {
            return "POOR";
        }
    }
    
    /**
     * Calculate elapsed time since start
     * 
     * @return Elapsed time in seconds
     */
    public long getElapsedTimeSeconds() {
        if (startTime == null) {
            return 0;
        }
        return (System.currentTimeMillis() - startTime) / 1000;
    }
    
    /**
     * Get formatted file size
     * 
     * @return String like "6.0 GB", "500 MB", "1.5 KB"
     */
    public String getFormattedFileSize() {
        if (fileSize == null) {
            return "Unknown";
        }
        
        if (fileSize >= 1024L * 1024 * 1024) {
            return String.format("%.1f GB", fileSize / (1024.0 * 1024 * 1024));
        } else if (fileSize >= 1024L * 1024) {
            return String.format("%.1f MB", fileSize / (1024.0 * 1024));
        } else if (fileSize >= 1024L) {
            return String.format("%.1f KB", fileSize / 1024.0);
        } else {
            return fileSize + " bytes";
        }
    }
    
    /**
     * Get summary string for logging
     * 
     * @return Summary string
     */
    @Override
    public String toString() {
        return String.format(
            "UploadSession[id=%s, file=%s, progress=%.1f%%, status=%s, speed=%.1f Mbps]",
            uploadId != null ? uploadId.substring(0, 8) + "..." : "null",
            fileName,
            progressPercent != null ? progressPercent : 0.0,
            status,
            averageSpeedMbps != null ? averageSpeedMbps : 0.0
        );
    }
}
