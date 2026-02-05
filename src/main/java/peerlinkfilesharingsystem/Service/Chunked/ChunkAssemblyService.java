package peerlinkfilesharingsystem.Service.Chunked;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import peerlinkfilesharingsystem.Model.Chunked.UploadSession;

import java.io.*;
import java.nio.file.*;
import java.util.Comparator;


@Service
@Slf4j
public class ChunkAssemblyService {
    
    @Value("${file.upload.final-dir:./uploads/files}")
    private String finalDirectory;
    
    @Value("${file.upload.buffer-size:32768}")  // 32 KB buffer
    private Integer bufferSize;
    

    public Path assembleChunks(UploadSession session) throws IOException {
        
        String uploadId = session.getUploadId();
        
        log.info("🔧 [{}] Starting chunk assembly...", uploadId);
        log.info("    Total chunks: {}", session.getTotalChunks());
        log.info("    Expected size: {} MB", session.getFileSize() / 1024 / 1024);
        
        long assemblyStartTime = System.currentTimeMillis();

        Path tempDir = Paths.get(session.getTempDirectory());
        
        if (!Files.exists(tempDir)) {
            throw new IOException("Temp directory not found: " + tempDir);
        }
        
        log.debug("[{}] Validating all chunks exist...", uploadId);
        
        for (int i = 0; i < session.getTotalChunks(); i++) {
            Path chunkPath = tempDir.resolve("chunk_" + i);
            
            if (!Files.exists(chunkPath)) {
                throw new IOException(
                    String.format("Missing chunk: %d (expected at %s)", i, chunkPath)
                );
            }
        }
        
        log.info("[{}] ✅ All {} chunks validated", uploadId, session.getTotalChunks());
        
        Path finalDir = Paths.get(finalDirectory);
        Files.createDirectories(finalDir);
        
        String sanitizedFileName = sanitizeFileName(session.getFileName());
        Path finalFile = finalDir.resolve(uploadId + "_" + sanitizedFileName);
        
        log.info("[{}] Creating final file: {}", uploadId, finalFile);
        
        long totalBytesWritten = 0;
        int chunksAssembled = 0;
        
        try (FileOutputStream fos = new FileOutputStream(finalFile.toFile());
             BufferedOutputStream bos = new BufferedOutputStream(fos, bufferSize)) {
            
            for (int i = 0; i < session.getTotalChunks(); i++) {
                Path chunkPath = tempDir.resolve("chunk_" + i);
                
                // Read chunk and write to final file
                try (FileInputStream fis = new FileInputStream(chunkPath.toFile());
                     BufferedInputStream bis = new BufferedInputStream(fis, bufferSize)) {
                    
                    byte[] buffer = new byte[bufferSize];
                    int bytesRead;
                    
                    while ((bytesRead = bis.read(buffer)) != -1) {
                        bos.write(buffer, 0, bytesRead);
                        totalBytesWritten += bytesRead;
                    }
                }
                
                chunksAssembled++;
                
                // Log progress every 100 chunks or at completion
                if (chunksAssembled % 100 == 0 || chunksAssembled == session.getTotalChunks()) {
                    double assemblyProgress = (chunksAssembled * 100.0) / session.getTotalChunks();
                    log.info("[{}] Assembly progress: {}/{} chunks ({:.1f}%)",
                        uploadId, chunksAssembled, session.getTotalChunks(), assemblyProgress);
                }
            }
            
            // Flush to ensure all data is written
            bos.flush();
        }
        
        long assemblyDuration = System.currentTimeMillis() - assemblyStartTime;
        
        log.info("[{}] All chunks assembled in {} seconds",
            uploadId, assemblyDuration / 1000);
        

        long actualFileSize = Files.size(finalFile);
        long expectedFileSize = session.getFileSize();
        
        log.info("[{}] Verifying file size...", uploadId);
        log.info("    Expected: {} bytes", expectedFileSize);
        log.info("    Actual:   {} bytes", actualFileSize);
        
        if (actualFileSize != expectedFileSize) {
            // Size mismatch - this is critical!
            long difference = Math.abs(actualFileSize - expectedFileSize);
            double percentDiff = (difference * 100.0) / expectedFileSize;
            
            log.error("[{}]  SIZE MISMATCH!", uploadId);
            log.error("    Difference: {} bytes ({:.2f}%)", difference, percentDiff);
            
            // Delete corrupted file
            Files.deleteIfExists(finalFile);
            
            throw new IOException(
                String.format("File size mismatch: expected %d bytes, got %d bytes (diff: %d bytes, %.2f%%)",
                    expectedFileSize, actualFileSize, difference, percentDiff)
            );
        }
        
        log.info("[{}]  File size verified: {} bytes", uploadId, actualFileSize);
        
        // ==================== CLEANUP TEMP CHUNKS ====================
        
        log.info("[{}] Cleaning up temp chunks...", uploadId);
        cleanupTempFiles(tempDir.toString());
        log.info("[{}]  Temp chunks deleted", uploadId);
        
        // ==================== SUCCESS ====================
        
        log.info("    [{}] Assembly completed successfully!", uploadId);
        log.info("    Final file: {}", finalFile);
        log.info("    Size: {} MB", actualFileSize / 1024 / 1024);
        log.info("    Assembly time: {} seconds", assemblyDuration / 1000);
        log.info("    Assembly speed: {} MB/s", 
            (actualFileSize / 1024.0 / 1024.0) / (assemblyDuration / 1000.0));
        
        return finalFile;
    }
    

    public void cleanupTempFiles(String tempDirectoryPath) throws IOException {
        
        Path tempDir = Paths.get(tempDirectoryPath);
        
        if (!Files.exists(tempDir)) {
            log.debug("Temp directory does not exist, nothing to clean: {}", tempDir);
            return;
        }
        
        log.debug("Deleting temp directory: {}", tempDir);
        
        try {
            Files.walk(tempDir)
                .sorted(Comparator.reverseOrder())  // Delete files before directories
                .forEach(path -> {
                    try {
                        Files.delete(path);
                        log.trace("Deleted: {}", path);
                    } catch (IOException e) {
                        log.error("Failed to delete: {}", path, e);
                    }
                });
            
            log.debug(" Temp directory deleted: {}", tempDir);
            
        } catch (IOException e) {
            log.error("Error during cleanup of {}", tempDir, e);
            throw e;
        }
    }
    

//    public boolean verifyFileIntegrity(Path filePath, String expectedChecksum) throws IOException {
//
//        log.info("Verifying file integrity: {}", filePath);
//
//        // TODO: Implement checksum verification
//        // This would calculate MD5/SHA256 of final file and compare with expected
//
//        log.warn("Checksum verification not yet implemented");
//        return true;
//    }
    
    /**
     * Sanitize filename to prevent security issues
     * 
     * Rules:
     * - Remove path traversal attempts (../)
     * - Remove dangerous characters
     * - Limit length
     * - Preserve extension
     * 
     * @param fileName Original filename
     * @return Sanitized filename
     */
    private String sanitizeFileName(String fileName) {
        
        if (fileName == null || fileName.trim().isEmpty()) {
            return "unnamed_file";
        }
        
        // Remove path separators and parent directory references
        String sanitized = fileName
            .replaceAll("[/\\\\]", "_")          // Replace / and \
            .replaceAll("\\.\\.", "_")           // Replace ..
            .replaceAll("[<>:\"|?*]", "_")       // Replace Windows invalid chars
            .replaceAll("[\\x00-\\x1F]", "_")    // Replace control characters
            .replaceAll("\\s+", "_")             // Replace multiple spaces with single underscore
            .trim();
        
        // Limit length while preserving extension
        if (sanitized.length() > 200) {
            String extension = "";
            int lastDotIndex = sanitized.lastIndexOf(".");
            
            if (lastDotIndex > 0) {
                extension = sanitized.substring(lastDotIndex);
                sanitized = sanitized.substring(0, 200 - extension.length()) + extension;
            } else {
                sanitized = sanitized.substring(0, 200);
            }
        }
        
        // If empty after sanitization, use default
        if (sanitized.isEmpty()) {
            sanitized = "unnamed_file";
        }
        
        log.trace("Sanitized filename: {} -> {}", fileName, sanitized);
        
        return sanitized;
    }
    
//    /**
//     * Get estimated assembly time based on file size
//     *
//     * @param fileSize File size in bytes
//     * @return Estimated seconds
//     */
//    public long estimateAssemblyTime(long fileSize) {
//        // Assume ~200 MB/s assembly speed (sequential disk I/O)
//        double assemblySpeedBytesPerSec = 200 * 1024 * 1024;
//        return (long) (fileSize / assemblySpeedBytesPerSec);
//    }
}
