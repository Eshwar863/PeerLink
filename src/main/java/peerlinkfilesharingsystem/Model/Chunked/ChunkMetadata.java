package peerlinkfilesharingsystem.Model.Chunked;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChunkMetadata implements Serializable {
    
    private static final long serialVersionUID = 1L;

    private Integer chunkNumber;

    private Long chunkSize;

    private Long uploadStartTime;

    private Long uploadEndTime;

    private Long uploadDurationMs;

    private Double speedMbps;

    @Builder.Default
    private Integer retryCount = 0;

    private String checksum;

    private String clientIp;

    public void calculateSpeed() {
        if (chunkSize != null && uploadDurationMs != null && uploadDurationMs > 0) {
            this.speedMbps = (chunkSize * 8.0) / (uploadDurationMs * 1000.0);
        }
    }
}
