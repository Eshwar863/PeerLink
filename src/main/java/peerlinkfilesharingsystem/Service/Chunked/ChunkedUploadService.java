package peerlinkfilesharingsystem.Service.Chunked;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import peerlinkfilesharingsystem.Dto.Chunked.ChunkUploadResponse;
import peerlinkfilesharingsystem.Enums.UploadStatus;
import peerlinkfilesharingsystem.Model.Chunked.UploadSession;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class ChunkedUploadService {

    @Autowired
    private FileStorageService filesStorageService;

    @Autowired
    private ChunkAssemblyService chunkAssemblyService;

    @Autowired
    private ChunkSizeCalculator chunkSizeCalculator;

    @Value("${file.upload.temp-dir:./uploads/temp-chunks}")
    private String tempDirectory;

    @Value("${file.upload.final-dir:./uploads/files}")
    private String finalDirectory;

    @Value("${file.upload.max-file-size:10737418240}")
    private Long maxFileSize;

    private final Map<String, UploadSession> sessions = new ConcurrentHashMap<>();

    public UploadSession initializeUpload(
            String fileName,
            Long fileSize,
            Integer totalChunks,
            Integer chunkSize,
            Double networkSpeedMbps,
            String userId,
            String username,
            String clientIp) throws IOException {

        log.info("========== INITIALIZING CHUNKED UPLOAD ==========");
        log.info("User: {} ({})", username, userId);
        log.info("File: {}", fileName);
        log.info("Size: {} bytes ({} MB)", fileSize, fileSize / 1024 / 1024);
        log.info("Network Speed: {} Mbps", networkSpeedMbps);

        if (fileName == null || fileName.trim().isEmpty()) {
            throw new IllegalArgumentException("File name cannot be empty");
        }

        if (fileSize == null || fileSize <= 0) {
            throw new IllegalArgumentException("File size must be greater than 0");
        }

        if (fileSize > maxFileSize) {
            throw new IllegalArgumentException(
                    String.format("File size exceeds maximum allowed (%d GB)",
                            maxFileSize / 1024 / 1024 / 1024));
        }

        if (chunkSize == null) {
            chunkSize = chunkSizeCalculator.calculateOptimalChunkSize(
                    networkSpeedMbps != null ? networkSpeedMbps : 50.0,
                    0.0,
                    "DESKTOP");
            log.info("Calculated optimal chunk size: {} KB", chunkSize / 1024);
        } else {
            log.info("Using provided chunk size: {} KB", chunkSize / 1024);
        }

        if (totalChunks == null) {
            totalChunks = (int) Math.ceil((double) fileSize / chunkSize);
            log.info("Calculated total chunks: {}", totalChunks);
        } else {
            int calculatedChunks = (int) Math.ceil((double) fileSize / chunkSize);
            if (Math.abs(totalChunks - calculatedChunks) > 1) {
                log.warn("Provided totalChunks ({}) differs from calculated ({}). Using calculated.",
                        totalChunks, calculatedChunks);
                totalChunks = calculatedChunks;
            }
        }

        String uploadId = UUID.randomUUID().toString();
        log.info("Generated upload ID: {}", uploadId);

        Path tempDir = filesStorageService.createTempDirectory(uploadId);
        log.info("Created temp directory: {}", tempDir);

        String fileType = "unknown";
        String mimeType = "application/octet-stream";

        if (fileName.contains(".")) {
            fileType = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();

            mimeType = getMimeType(fileType);
        }

        UploadSession session = UploadSession.builder()
                .uploadId(uploadId)
                .userId(userId)
                .username(username)
                .fileName(fileName)
                .fileSize(fileSize)
                .fileType(fileType)
                .mimeType(mimeType)
                .totalChunks(totalChunks)
                .chunkSize(chunkSize)
                .receivedChunks(Collections.newSetFromMap(new ConcurrentHashMap<>()))
                .status(UploadStatus.INITIALIZED.name())
                .progressPercent(0.0)
                .speedHistory(Collections.synchronizedList(new ArrayList<>()))
                .startTime(System.currentTimeMillis())
                .createdAt(LocalDateTime.now())
                .tempDirectory(tempDir.toString())
                .clientIp(clientIp)
                .metadata(new ConcurrentHashMap<>())
                .build();

        sessions.put(uploadId, session);

        log.info("Upload session initialized successfully");
        log.info("Upload ID: {}", uploadId);
        log.info("Total chunks: {}", totalChunks);
        log.info("Chunk size: {} KB", chunkSize / 1024);
        log.info("Estimated upload size: {} MB", (totalChunks * chunkSize) / 1024 / 1024);
        log.info("================================================");

        return session;
    }

    public ChunkUploadResponse uploadChunk(
            String uploadId,
            Integer chunkNumber,
            Integer totalChunks,
            MultipartFile chunk) throws IOException {

        long uploadStartTime = System.currentTimeMillis();

        log.debug("[{}] Uploading chunk {}/{}", uploadId, chunkNumber + 1, totalChunks);

        try {
            UUID.fromString(uploadId);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid upload ID format");
        }

        UploadSession session = sessions.get(uploadId);
        if (session == null) {
            throw new IllegalStateException(
                    "Upload session not found. Please call /init first.");
        }

        if (!UploadStatus.valueOf(session.getStatus()).isActive()) {
            throw new IllegalStateException(
                    "Upload session is not active. Status: " + session.getStatus());
        }

        if (chunkNumber < 0 || chunkNumber >= totalChunks) {
            throw new IllegalArgumentException(
                    String.format("Invalid chunk number: %d (must be 0-%d)",
                            chunkNumber, totalChunks - 1));
        }

        if (!totalChunks.equals(session.getTotalChunks())) {
            throw new IllegalArgumentException(
                    String.format("Total chunks mismatch: expected %d, got %d",
                            session.getTotalChunks(), totalChunks));
        }

        if (chunk == null || chunk.isEmpty()) {
            throw new IllegalArgumentException("Chunk data is empty");
        }

        if (session.hasChunk(chunkNumber)) {
            log.debug("[{}] Chunk {} already received (idempotent request)",
                    uploadId, chunkNumber);

            return buildProgressResponse(session, chunkNumber, false);
        }

        Path chunkPath = filesStorageService.saveChunk(
                uploadId,
                chunkNumber,
                chunk);

        log.debug("[{}] Chunk {} saved to: {}", uploadId, chunkNumber, chunkPath);

        long uploadDuration = System.currentTimeMillis() - uploadStartTime;

        synchronized (session) {
            session.markChunkReceived(chunkNumber);

            session.recordChunkSpeed(chunk.getSize(), uploadDuration);

            if (session.getStatus().equals(UploadStatus.INITIALIZED.name())) {
                session.setStatus(UploadStatus.UPLOADING.name());
            }
        }

        if ((chunkNumber + 1) % 100 == 0 || chunkNumber == 0) {
            log.info("[{}] Progress: {}/{} chunks ({:.1f}%) | Speed: {:.2f} Mbps | Avg: {:.2f} Mbps",
                    uploadId,
                    session.getReceivedChunks().size(),
                    session.getTotalChunks(),
                    session.getProgressPercent(),
                    session.getCurrentSpeedMbps() != null ? session.getCurrentSpeedMbps() : 0.0,
                    session.getAverageSpeedMbps() != null ? session.getAverageSpeedMbps() : 0.0);
        }

        if (session.isComplete()) {
            log.info("[{}] All {} chunks received! Starting assembly...",
                    uploadId, totalChunks);
            return handleUploadCompletion(session);
        }

        return buildProgressResponse(session, chunkNumber, false);
    }

    private ChunkUploadResponse handleUploadCompletion(UploadSession session) {

        String uploadId = session.getUploadId();

        try {
            session.setStatus(UploadStatus.ASSEMBLING.name());

            Path finalFile = chunkAssemblyService.assembleChunks(session);

            session.setStatus(UploadStatus.COMPLETED.name());
            session.setProgressPercent(100.0);
            session.setCompletedAt(LocalDateTime.now());
            session.setFinalFilePath(finalFile.toString());

            long totalDuration = System.currentTimeMillis() - session.getStartTime();
            session.setTotalDurationSeconds(totalDuration / 1000);

            log.info("    [{}] Upload COMPLETED successfully!", uploadId);
            log.info("    File: {}", session.getFileName());
            log.info("    Size: {} MB", session.getFileSize() / 1024 / 1024);
            log.info("    Duration: {} seconds", session.getTotalDurationSeconds());
            log.info("    Avg Speed: {:.2f} Mbps", session.getAverageSpeedMbps());
            log.info("    Final Path: {}", finalFile);

            return ChunkUploadResponse.builder()
                    .success(true)
                    .message("Upload completed successfully!")
                    .uploadId(uploadId)
                    .status(session.getStatus())
                    .fileName(session.getFileName())
                    .fileSize(session.getFileSize())
                    .formattedFileSize(session.getFormattedFileSize())
                    .totalChunks(session.getTotalChunks())
                    .uploadedChunks(session.getReceivedChunks().size())
                    .progress(100.0)
                    .averageSpeedMbps(session.getAverageSpeedMbps())
                    .totalDurationSeconds(session.getTotalDurationSeconds())
                    .finalFilePath(finalFile.toString())
                    .completedAt(session.getCompletedAt().toString())
                    .build();

        } catch (Exception e) {
            log.error("[{}] Assembly failed", uploadId, e);

            session.setStatus(UploadStatus.FAILED.name());
            session.setErrorMessage("Assembly failed: " + e.getMessage());

            return ChunkUploadResponse.builder()
                    .success(false)
                    .message("Assembly failed")
                    .uploadId(uploadId)
                    .status(session.getStatus())
                    .errorCode("ASSEMBLY_FAILED")
                    .errorDetails(e.getMessage())
                    .shouldRetry(false)
                    .build();
        }
    }

    public ChunkUploadResponse getUploadStatus(String uploadId) {

        UploadSession session = sessions.get(uploadId);

        if (session == null) {
            throw new IllegalStateException("Upload session not found: " + uploadId);
        }

        return buildProgressResponse(session, null, false);
    }

    public void cancelUpload(String uploadId) throws IOException {

        log.info("[{}] Cancelling upload...", uploadId);

        UploadSession session = sessions.remove(uploadId);

        if (session != null) {
            session.setStatus(UploadStatus.CANCELLED.name());

            // Cleanup temp files
            chunkAssemblyService.cleanupTempFiles(session.getTempDirectory());

            log.info("[{}] Upload cancelled and cleaned up", uploadId);
        }
    }

    private ChunkUploadResponse buildProgressResponse(
            UploadSession session,
            Integer lastChunkNumber,
            boolean adapted) {

        return ChunkUploadResponse.builder()
                .success(true)
                .message(String.format("Uploaded %d/%d chunks (%.1f%%)",
                        session.getReceivedChunks().size(),
                        session.getTotalChunks(),
                        session.getProgressPercent()))
                .uploadId(session.getUploadId())
                .status(session.getStatus())
                .fileName(session.getFileName())
                .fileSize(session.getFileSize())
                .formattedFileSize(session.getFormattedFileSize())
                .totalChunks(session.getTotalChunks())
                .uploadedChunks(session.getReceivedChunks().size())
                .receivedChunks(new ArrayList<>(session.getReceivedChunks()))
                .progress(session.getProgressPercent())
                .chunkNumber(lastChunkNumber)
                .currentSpeedMbps(session.getCurrentSpeedMbps())
                .averageSpeedMbps(session.getAverageSpeedMbps())
                .minSpeedMbps(session.getMinSpeedMbps())
                .maxSpeedMbps(session.getMaxSpeedMbps())
                .speedVariancePercent(session.getSpeedVariancePercent())
                .networkCondition(session.getNetworkCondition())
                .estimatedTimeRemaining(session.estimateTimeRemaining())
                .formattedTimeRemaining(session.getFormattedTimeRemaining())
                .elapsedTimeSeconds(session.getElapsedTimeSeconds())
                .missingChunkCount(session.getMissingChunkCount())
                .adaptedInThisRequest(adapted)
                .build();
    }

    private String getMimeType(String fileType) {
        switch (fileType.toLowerCase()) {
            case "pdf":
                return "application/pdf";
            case "iso":
                return "application/x-iso9660-image";
            case "zip":
                return "application/zip";
            case "mp4":
                return "video/mp4";
            case "mp3":
                return "audio/mpeg";
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            case "png":
                return "image/png";
            case "txt":
                return "text/plain";
            case "doc":
            case "docx":
                return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            default:
                return "application/octet-stream";
        }
    }

    public Collection<UploadSession> getActiveSessions() {
        return sessions.values();
    }

    public UploadSession getSession(String uploadId) {
        return sessions.get(uploadId);
    }
}
