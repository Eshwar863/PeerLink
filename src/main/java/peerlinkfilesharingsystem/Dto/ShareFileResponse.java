package peerlinkfilesharingsystem.Dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder  // ✅ Add Builder pattern
@AllArgsConstructor
@NoArgsConstructor
public class ShareFileResponse {  // ✅ Renamed from ShareFileResponse

    private String fileName;
    private Long shareport;  // Share ID
    private String fileDownloadUri;

    // Additional fields for better response
    private Long fileSize;
    private String fileType;
    private LocalDateTime expiresAt;
}
