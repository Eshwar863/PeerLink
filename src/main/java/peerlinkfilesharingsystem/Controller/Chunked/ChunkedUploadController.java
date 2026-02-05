package peerlinkfilesharingsystem.Controller.Chunked;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import peerlinkfilesharingsystem.Dto.Chunked.ChunkUploadInitRequest;
import peerlinkfilesharingsystem.Dto.Chunked.ChunkUploadResponse;
import peerlinkfilesharingsystem.Dto.Chunked.ErrorResponse;
import peerlinkfilesharingsystem.Model.Chunked.UploadSession;
import peerlinkfilesharingsystem.Service.Chunked.ChunkedUploadService;

import java.io.IOException;
import java.util.Collection;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/files/chunked")
@Slf4j
@CrossOrigin(origins = "*", maxAge = 3600)
public class ChunkedUploadController {
    
    @Autowired
    private ChunkedUploadService uploadService;
    

    @PostMapping(value = "/init", 
                 consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE})
    public ResponseEntity<?> initializeUpload(
            @Valid @ModelAttribute ChunkUploadInitRequest request,
            BindingResult bindingResult,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        
        log.info("========== POST /files/chunked/init ==========");
        log.info("File: {}, Size: {} bytes", request.getFileName(), request.getFileSize());

        if (bindingResult.hasErrors()) {
            String errors = bindingResult.getAllErrors()
                .stream()
                .map(error -> error.getDefaultMessage())
                .collect(Collectors.joining(", "));
            
            log.error("Validation failed: {}", errors);
            
            return ResponseEntity
                .badRequest()
                .body(ErrorResponse.builder()
                    .status(400)
                    .error("Bad Request")
                    .errorCode("VALIDATION_ERROR")
                    .message("Invalid request parameters")
                    .details(bindingResult.getAllErrors()
                        .stream()
                        .map(error -> error.getDefaultMessage())
                        .collect(Collectors.toList()))
                    .path(httpRequest.getRequestURI())
                    .shouldRetry(false)
                    .build());
        }

        String username = "anonymous";
        String userId = "anonymous";
        
        if (authentication != null && authentication.isAuthenticated()) {
            username = authentication.getName();
            userId = username;
            log.info("Authenticated user: {}", username);
        } else {
            log.warn("Anonymous upload (authentication not enabled)");
        }

        String clientIp = getClientIp(httpRequest);
        log.info("Client IP: {}", clientIp);

        try {
            UploadSession session = uploadService.initializeUpload(
                request.getFileName(),
                request.getFileSize(),
                request.getTotalChunks(),
                request.getChunkSize(),
                request.getNetworkSpeedMbps(),
                userId,
                username,
                clientIp
            );

            ChunkUploadResponse response = ChunkUploadResponse.builder()
                .success(true)
                .message("Upload session initialized")
                .uploadId(session.getUploadId())
                .status(session.getStatus())
                .fileName(session.getFileName())
                .fileSize(session.getFileSize())
                .formattedFileSize(session.getFormattedFileSize())
                .fileType(session.getFileType())
                .totalChunks(session.getTotalChunks())
                .uploadedChunks(0)
                .progress(0.0)
                .recommendedChunkSize(session.getChunkSize())
                .build();
            
            log.info("Upload session initialized: {}", session.getUploadId());
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            log.error("Invalid request: {}", e.getMessage());
            
            return ResponseEntity
                .badRequest()
                .body(ErrorResponse.builder()
                    .status(400)
                    .error("Bad Request")
                    .errorCode("INVALID_PARAMETERS")
                    .message(e.getMessage())
                    .path(httpRequest.getRequestURI())
                    .shouldRetry(false)
                    .suggestion("Check file size and name parameters")
                    .build());
            
        } catch (IOException e) {
            log.error("Failed to initialize upload", e);
            
            return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.builder()
                    .status(500)
                    .error("Internal Server Error")
                    .errorCode("INITIALIZATION_FAILED")
                    .message("Failed to initialize upload session")
                    .details(java.util.Arrays.asList(e.getMessage()))
                    .path(httpRequest.getRequestURI())
                    .shouldRetry(true)
                    .retryAfterSeconds(5)
                    .build());
        }
    }


    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadChunk(
            @RequestParam String uploadId,
            @RequestParam Integer chunkNumber,
            @RequestParam Integer totalChunks,
            @RequestPart("chunk") MultipartFile chunk,
            HttpServletRequest httpRequest) {
        
        long requestStartTime = System.currentTimeMillis();
        
        log.debug("========== POST /files/chunked/upload ==========");
        log.debug("Upload ID: {}, Chunk: {}/{}", uploadId, chunkNumber + 1, totalChunks);

        if (uploadId == null || uploadId.trim().isEmpty()) {
            return ResponseEntity
                .badRequest()
                .body(ErrorResponse.builder()
                    .status(400)
                    .error("Bad Request")
                    .errorCode("MISSING_UPLOAD_ID")
                    .message("Upload ID is required")
                    .path(httpRequest.getRequestURI())
                    .shouldRetry(false)
                    .build());
        }
        
        if (chunkNumber == null || chunkNumber < 0) {
            return ResponseEntity
                .badRequest()
                .body(ErrorResponse.builder()
                    .status(400)
                    .error("Bad Request")
                    .errorCode("INVALID_CHUNK_NUMBER")
                    .message("Invalid chunk number: " + chunkNumber)
                    .uploadId(uploadId)
                    .path(httpRequest.getRequestURI())
                    .shouldRetry(false)
                    .build());
        }
        
        if (chunk == null || chunk.isEmpty()) {
            return ResponseEntity
                .badRequest()
                .body(ErrorResponse.builder()
                    .status(400)
                    .error("Bad Request")
                    .errorCode("EMPTY_CHUNK")
                    .message("Chunk data is empty")
                    .uploadId(uploadId)
                    .chunkNumber(chunkNumber)
                    .path(httpRequest.getRequestURI())
                    .shouldRetry(false)
                    .build());
        }
        
        log.debug("Chunk size: {} bytes ({} KB)", chunk.getSize(), chunk.getSize() / 1024);

        try {
            ChunkUploadResponse response = uploadService.uploadChunk(
                uploadId,
                chunkNumber,
                totalChunks,
                chunk
            );
            
            long requestDuration = System.currentTimeMillis() - requestStartTime;
            
            log.debug("Chunk {}/{} uploaded in {}ms",
                chunkNumber + 1, totalChunks, requestDuration);
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            log.error("[{}] Invalid request: {}", uploadId, e.getMessage());
            
            return ResponseEntity
                .badRequest()
                .body(ErrorResponse.builder()
                    .status(400)
                    .error("Bad Request")
                    .errorCode("INVALID_PARAMETERS")
                    .message(e.getMessage())
                    .uploadId(uploadId)
                    .chunkNumber(chunkNumber)
                    .path(httpRequest.getRequestURI())
                    .shouldRetry(false)
                    .build());
            
        } catch (IllegalStateException e) {
            log.error("[{}] Invalid state: {}", uploadId, e.getMessage());
            
            boolean isNotFound = e.getMessage().contains("not found");
            
            return ResponseEntity
                .status(isNotFound ? HttpStatus.NOT_FOUND : HttpStatus.CONFLICT)
                .body(ErrorResponse.builder()
                    .status(isNotFound ? 404 : 409)
                    .error(isNotFound ? "Not Found" : "Conflict")
                    .errorCode(isNotFound ? "SESSION_NOT_FOUND" : "INVALID_STATE")
                    .message(e.getMessage())
                    .uploadId(uploadId)
                    .chunkNumber(chunkNumber)
                    .path(httpRequest.getRequestURI())
                    .shouldRetry(!isNotFound)
                    .retryAfterSeconds(isNotFound ? 0 : 3)
                    .suggestion(isNotFound ? 
                        "Call POST /files/chunked/init to create a new session" : 
                        "Check upload status with GET /files/chunked/status/" + uploadId)
                    .build());
            
        } catch (IOException e) {
            log.error("[{}] Failed to save chunk {}", uploadId, chunkNumber, e);
            
            boolean isDiskFull = e.getMessage().contains("disk space") ||
                                e.getMessage().contains("No space");
            
            return ResponseEntity
                .status(isDiskFull ? HttpStatus.INSUFFICIENT_STORAGE : HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.builder()
                    .status(isDiskFull ? 507 : 500)
                    .error(isDiskFull ? "Insufficient Storage" : "Internal Server Error")
                    .errorCode(isDiskFull ? "DISK_FULL" : "CHUNK_SAVE_FAILED")
                    .message("Failed to save chunk")
                    .details(java.util.Arrays.asList(e.getMessage()))
                    .uploadId(uploadId)
                    .chunkNumber(chunkNumber)
                    .path(httpRequest.getRequestURI())
                    .shouldRetry(!isDiskFull)
                    .retryAfterSeconds(isDiskFull ? 0 : 5)
                    .suggestion(isDiskFull ? 
                        "Server disk is full. Contact administrator." : 
                        "Retry this chunk")
                    .build());
        } catch (Exception e) {
            log.error("[{}] Unexpected error uploading chunk {}", uploadId, chunkNumber, e);
            
            return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.builder()
                    .status(500)
                    .error("Internal Server Error")
                    .errorCode("UNEXPECTED_ERROR")
                    .message("Unexpected error occurred")
                    .details(java.util.Arrays.asList(e.getMessage()))
                    .uploadId(uploadId)
                    .chunkNumber(chunkNumber)
                    .path(httpRequest.getRequestURI())
                    .shouldRetry(true)
                    .retryAfterSeconds(5)
                    .build());
        }
    }
    
    @GetMapping("/status/{uploadId}")
    public ResponseEntity<?> getUploadStatus(
            @PathVariable String uploadId,
            HttpServletRequest httpRequest) {
        
        log.debug("========== GET /files/chunked/status/{} ==========", uploadId);
        
        try {
            ChunkUploadResponse response = uploadService.getUploadStatus(uploadId);
            
            log.debug("Status: {}, Progress: {:.1f}%", 
                response.getStatus(), response.getProgress());
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalStateException e) {
            log.error("Session not found: {}", uploadId);
            
            return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.builder()
                    .status(404)
                    .error("Not Found")
                    .errorCode("SESSION_NOT_FOUND")
                    .message("Upload session not found")
                    .uploadId(uploadId)
                    .path(httpRequest.getRequestURI())
                    .shouldRetry(false)
                    .suggestion("Session may have expired or been cancelled")
                    .build());
        }
    }
    

    @DeleteMapping("/cancel/{uploadId}")
    public ResponseEntity<?> cancelUpload(
            @PathVariable String uploadId,
            HttpServletRequest httpRequest) {
        
        log.info("========== DELETE /files/chunked/cancel/{} ==========", uploadId);
        
        try {
            uploadService.cancelUpload(uploadId);
            
            log.info("Upload cancelled: {}", uploadId);
            
            return ResponseEntity.ok(
                ChunkUploadResponse.builder()
                    .success(true)
                    .message("Upload cancelled and cleaned up")
                    .uploadId(uploadId)
                    .status("CANCELLED")
                    .build());
            
        } catch (IOException e) {
            log.error("Failed to cleanup upload: {}", uploadId, e);
            
            return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.builder()
                    .status(500)
                    .error("Internal Server Error")
                    .errorCode("CLEANUP_FAILED")
                    .message("Failed to cleanup upload")
                    .details(java.util.Arrays.asList(e.getMessage()))
                    .uploadId(uploadId)
                    .path(httpRequest.getRequestURI())
                    .shouldRetry(true)
                    .build());
        }
    }

    @GetMapping("/sessions")
    public ResponseEntity<?> listActiveSessions() {
        
        log.debug("========== GET /files/chunked/sessions ==========");
        
        Collection<UploadSession> sessions = uploadService.getActiveSessions();
        
        return ResponseEntity.ok(
            java.util.Map.of(
                "success", true,
                "totalSessions", sessions.size(),
                "sessions", sessions.stream()
                    .map(session -> java.util.Map.of(
                        "uploadId", session.getUploadId(),
                        "fileName", session.getFileName(),
                        "status", session.getStatus(),
                        "progress", session.getProgressPercent(),
                        "uploadedChunks", session.getReceivedChunks().size(),
                        "totalChunks", session.getTotalChunks(),
                        "username", session.getUsername(),
                        "createdAt", session.getCreatedAt().toString()
                    ))
                    .collect(Collectors.toList())
            )
        );
    }
    

    private String getClientIp(HttpServletRequest request) {
        
        // Check X-Forwarded-For header (for proxied requests)
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // X-Forwarded-For can contain multiple IPs, take the first one
            return xForwardedFor.split(",")[0].trim();
        }
        
        // Check X-Real-IP header
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        // Fallback to remote address
        return request.getRemoteAddr();
    }

    private String getUserAgent(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        return userAgent != null ? userAgent : "Unknown";
    }
}
