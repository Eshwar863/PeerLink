package peerlinkfilesharingsystem.Dto.Chunked;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UploadStatusResponse {
    
    private String uploadId;
    private String status;
    private String fileName;
    private Long fileSize;
    private String formattedFileSize;
    
    private Integer totalChunks;
    private Integer uploadedChunks;
    private Integer missingChunkCount;
    private Double progress;
    
    private List<Integer> missingChunks;
    
    private Double averageSpeedMbps;
    private Double minSpeedMbps;
    private Double maxSpeedMbps;
    private Double speedVariancePercent;
    private String networkCondition;
    
    private Long elapsedTimeSeconds;
    private Long estimatedTimeRemaining;
    private String formattedTimeRemaining;
    
    private String createdAt;
    private String lastChunkAt;

    private Boolean canResume;

    private Map<String, Object> metadata;
}
