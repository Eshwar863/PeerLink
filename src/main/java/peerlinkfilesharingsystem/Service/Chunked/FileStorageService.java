package peerlinkfilesharingsystem.Service.Chunked;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * File Storage Service
 *
 * Handles all disk I/O operations for chunked uploads
 * Responsible for:
 * - Creating directories
 * - Saving chunks to disk
 * - Generating file paths
 * - Checking disk space
 *
 * @author PeerLink Team
 * @version 1.0
 */
@Service
@Slf4j
public class FileStorageService {

    @Value("${file.upload.temp-chunks-dir:./uploads/temp-chunks}")
    private String tempChunksDir;

    @Value("${file.upload.final-dir:./uploads/files}")
    private String finalDir;

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(Paths.get(tempChunksDir));
            Files.createDirectories(Paths.get(finalDir));
            log.info("✅ Storage directories initialized");
            log.info("   Temp: {}", Paths.get(tempChunksDir).toAbsolutePath());
            log.info("   Final: {}", Paths.get(finalDir).toAbsolutePath());
        } catch (IOException e) {
            log.error("Failed to create storage directories", e);
            throw new RuntimeException("Could not initialize storage", e);
        }
    }

    /**
     * Create a temporary directory for an upload session
     *
     * @param uploadId The unique upload session ID
     * @return Path to the created directory
     * @throws RuntimeException if directory creation fails
     */
    public Path createTempDirectory(String uploadId) {
        try {
            Path uploadDir = Paths.get(tempChunksDir, uploadId);
            Files.createDirectories(uploadDir);
            log.info("[{}] ✅ Created temp directory: {}", uploadId, uploadDir.toAbsolutePath());
            return uploadDir;
        } catch (IOException e) {
            log.error("[{}] ❌ Failed to create temp directory", uploadId, e);
            throw new RuntimeException("Failed to create temp directory for upload: " + uploadId, e);
        }
    }

    /**
     * Save a chunk for a specific upload session
     */
    /**
     * Save a chunk for a specific upload session
     *
     * @param uploadId The upload session ID
     * @param chunkIndex The chunk index (0-based)
     * @param chunkData The chunk file data
     * @return Path to the saved chunk file
     * @throws IOException If saving fails
     */
    /**
     * Save a chunk for a specific upload session
     *
     * @param uploadId The upload session ID
     * @param chunkIndex The chunk index (0-based)
     * @param chunkData The chunk file data
     * @return Path to the saved chunk file
     * @throws IOException If saving fails
     */
    public Path saveChunk(String uploadId, int chunkIndex, MultipartFile chunkData) throws IOException {
        try {
            // Create upload session directory if it doesn't exist
            Path uploadDir = Paths.get(tempChunksDir, uploadId);
            Files.createDirectories(uploadDir);

            // Build chunk path
            Path chunkPath = uploadDir.resolve("chunk_" + chunkIndex);

            // ✅ FIX: Convert to ABSOLUTE path before saving
            Path absoluteChunkPath = chunkPath.toAbsolutePath().normalize();

            // Save the chunk using absolute path
            chunkData.transferTo(absoluteChunkPath.toFile());

            log.debug("[{}] ✅ Chunk {} saved ({} bytes) at: {}",
                    uploadId, chunkIndex, chunkData.getSize(), absoluteChunkPath);

            return chunkPath;  // Return original path for consistency

        } catch (IOException e) {
            log.error("[{}] Failed to save chunk {}", uploadId, chunkIndex, e);
            throw new IOException("Failed to save chunk: " + e.getMessage(), e);
        }
    }



    /**
     * Get the path to a specific chunk
     */
    public Path getChunkPath(String uploadId, int chunkIndex) {
        return Paths.get(tempChunksDir, uploadId, "chunk_" + chunkIndex);
    }

    /**
     * Get all chunk files for an upload session (sorted)
     */
    public List<Path> getChunks(String uploadId) throws IOException {
        Path uploadDir = Paths.get(tempChunksDir, uploadId);

        if (!Files.exists(uploadDir)) {
            return List.of();
        }

        try (Stream<Path> paths = Files.list(uploadDir)) {
            return paths
                    .filter(path -> path.getFileName().toString().startsWith("chunk_"))
                    .sorted(Comparator.comparingInt(path -> {
                        String filename = path.getFileName().toString();
                        return Integer.parseInt(filename.substring(6)); // "chunk_0" → 0
                    }))
                    .collect(Collectors.toList());
        }
    }

    /**
     * Assemble all chunks into final file
     */
    public Path assembleChunks(String uploadId, String finalFilename) throws IOException {
        Path outputPath = Paths.get(finalDir, finalFilename);
        List<Path> chunks = getChunks(uploadId);

        if (chunks.isEmpty()) {
            throw new IOException("No chunks found for upload: " + uploadId);
        }

        log.info("[{}] Assembling {} chunks into {}", uploadId, chunks.size(), finalFilename);

        try (FileOutputStream fos = new FileOutputStream(outputPath.toFile())) {
            for (Path chunk : chunks) {
                Files.copy(chunk, fos);
            }
        }

        log.info("[{}] ✅ File assembled: {} ({} bytes)",
                uploadId, finalFilename, Files.size(outputPath));

        return outputPath;
    }

    /**
     * Clean up temp chunks for an upload session
     */
    public void cleanupChunks(String uploadId) {
        try {
            Path uploadDir = Paths.get(tempChunksDir, uploadId);
            if (Files.exists(uploadDir)) {
                Files.walk(uploadDir)
                        .sorted(Comparator.reverseOrder())
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (IOException e) {
                                log.warn("Failed to delete: {}", path, e);
                            }
                        });
                log.debug("[{}] ✅ Temp chunks cleaned up", uploadId);
            }
        } catch (IOException e) {
            log.error("[{}] Failed to cleanup chunks", uploadId, e);
        }
    }

    /**
     * Check available disk space
     */
    public long getAvailableSpace() {
        try {
            return Files.getFileStore(Paths.get(finalDir))
                    .getUsableSpace();
        } catch (IOException e) {
            log.error("Failed to get available space", e);
            return 0;
        }
    }


}
