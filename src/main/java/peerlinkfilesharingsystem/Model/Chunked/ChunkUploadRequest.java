package peerlinkfilesharingsystem.Model.Chunked;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.constraints.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChunkUploadRequest {

    @NotBlank(message = "Upload ID is required")
    @Pattern(
        regexp = "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$",
        message = "Invalid upload ID format"
    )
    private String uploadId;

    @NotNull(message = "Chunk number is required")
    @Min(value = 0, message = "Chunk number must be >= 0")
    private Integer chunkNumber;

    @NotNull(message = "Total chunks is required")
    @Min(value = 1, message = "Total chunks must be at least 1")
    private Integer totalChunks;

    @NotNull(message = "Chunk file is required")
    private MultipartFile chunk;

    private Long uploadStartTime;

    @Size(max = 64, message = "Checksum must not exceed 64 characters")
    private String checksum;

    @Min(value = 0, message = "Retry count cannot be negative")
    private Integer retryCount;
}
