    package peerlinkfilesharingsystem.Dto.Chunked;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChunkUploadInitRequest {

    @NotBlank(message = "File name is required")
    @Size(min = 1, max = 255, message = "File name must be between 1 and 255 characters")
    private String fileName;
    

    @NotNull(message = "File size is required")
    @Min(value = 1, message = "File size must be greater than 0")
    @Max(value = 10737418240L, message = "File size must not exceed 10 GB")
    private Long fileSize;
    
    @Min(value = 1, message = "Total chunks must be at least 1")
    private Integer totalChunks;
    
    @Min(value = 262144, message = "Chunk size must be at least 256 KB")
    @Max(value = 5242880, message = "Chunk size must not exceed 5 MB")
    private Integer chunkSize;

    @Min(value = 0, message = "Network speed cannot be negative")
    private Double networkSpeedMbps;
    

    @Size(max = 50, message = "File type must not exceed 50 characters")
    private String fileType;

    @Size(max = 100, message = "MIME type must not exceed 100 characters")
    private String mimeType;
    

    private String clientIp;

    private String userAgent;
}
