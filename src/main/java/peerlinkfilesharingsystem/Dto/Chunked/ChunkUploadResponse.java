package peerlinkfilesharingsystem.Dto.Chunked;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL) // Don't serialize null fields
public class ChunkUploadResponse {

    private Boolean success;

    private String message;

    private String uploadId;

    private String status;

    private String fileName;

    private Long fileSize;

    private String formattedFileSize;

    private String fileType;

    private Integer totalChunks;

    private Integer uploadedChunks;

    private List<Integer> receivedChunks;

    private Double progress;

    private Integer chunkNumber;

    private List<Integer> missingChunks;

    private Integer missingChunkCount;

    private Double currentSpeedMbps;

    private Double averageSpeedMbps;

    private Double minSpeedMbps;

    private Double maxSpeedMbps;

    private Double speedVariancePercent;

    private String networkCondition;

    private Long estimatedTimeRemaining;

    private String formattedTimeRemaining;

    private Long elapsedTimeSeconds;

    private Long totalDurationSeconds;

    private Integer recommendedChunkSize;

    private Boolean adaptedInThisRequest;

    private Integer newRecommendedChunkSize;

    private String adaptationReason;

    private String finalFilePath;

    private String downloadUrl;

    private String createdAt;

    private String completedAt;

    private String errorCode;

    private String errorDetails;

    private Boolean shouldRetry;

    private Integer retryAfterSeconds;
}
