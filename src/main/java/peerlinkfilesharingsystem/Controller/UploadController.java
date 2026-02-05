package peerlinkfilesharingsystem.Controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import peerlinkfilesharingsystem.Dto.FileUploadResponse;
import peerlinkfilesharingsystem.Service.FileUploadService.FileUploadService;

import java.util.UUID;

@RestController
@RequestMapping("/files/v1")
@Slf4j
public class UploadController {

    private FileUploadService fileUploadService;
    public UploadController(FileUploadService fileUploadService) {
        this.fileUploadService = fileUploadService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public FileUploadResponse uploadFile(
            @RequestPart(value = "file", required = true) MultipartFile file,
            @RequestHeader(value = "X-Network-Speed", defaultValue = "50.0") Double networkSpeedMbps,
            @RequestHeader(value = "X-Latency-Ms", defaultValue = "50") Integer latencyMs,
            @RequestHeader(value = "X-Device-Type", defaultValue = "DESKTOP") String deviceType,
            HttpServletRequest request) {

        String clientIp = request.getRemoteAddr();
        String correlationId = UUID.randomUUID().toString();

        log.info("[{}] Upload started - File: {}, Size: {}MB, Speed: {}Mbps, Latency: {}ms, IP: {}",
                correlationId, file.getOriginalFilename(),
                file.getSize() / 1024 / 1024, networkSpeedMbps, latencyMs, clientIp);

        try {
            if (file.isEmpty() || file.getSize() == 0L || clientIp.isEmpty()) {
                return ResponseEntity.status(400).body(
                        FileUploadResponse.builder()
                                .success(false)
                                .message("Upload failed: File Cant be Empty,Client IP Can't be Empty")
                                .build()
                ).getBody();
            }

            if (file.getSize() > 10 * 1024 * 1024 * 1024L) {
                return ResponseEntity.status(413).body(
                        FileUploadResponse.builder()
                                .success(false)
                                .message("Upload failed: File Size Exceeded")
                                .build()
                ).getBody();
            }
                log.info("=== NEW UPLOAD DETECTED ===");
            FileUploadResponse fileUploadResponse  = fileUploadService.handleFile(
                    file, latencyMs, networkSpeedMbps, deviceType, clientIp);
            if (fileUploadResponse ==  null) {
                return (FileUploadResponse) ResponseEntity.status(HttpStatus.UNAUTHORIZED);
            }
                return ResponseEntity.ok(fileUploadResponse).getBody();
        }catch (Exception e){
            log.error("Error uploading file", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    FileUploadResponse.builder()
                            .success(false)
                            .message("Upload failed: " + e.getMessage())
                            .build()
            ).getBody();
        }

    }

    @GetMapping("/history")
    public ResponseEntity<?> getTransferHistory(
            @RequestParam(defaultValue = "10") Integer limit) {
        try {
            return ResponseEntity.ok(fileUploadService.getRecentTransfers(limit));
        } catch (Exception e) {
            log.error("Error getting history", e);
            return new ResponseEntity<>("History error: Failed to Fetch Files",HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }








}

