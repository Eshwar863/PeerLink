package peerlinkfilesharingsystem.Service.FileDownloadService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import peerlinkfilesharingsystem.Dto.FileShareDownloadDTO;
import peerlinkfilesharingsystem.Enums.MarkFileAs;
import peerlinkfilesharingsystem.Model.*;
import peerlinkfilesharingsystem.Repo.FileDownloadRepo;
import peerlinkfilesharingsystem.Repo.FileShareRepo;
import peerlinkfilesharingsystem.Repo.FileTransferRepo;
import peerlinkfilesharingsystem.Repo.UserRepo;
import peerlinkfilesharingsystem.Service.FileStorageService.FilesStorageService;
import peerlinkfilesharingsystem.Service.IntelligencePredictionService.IntelligencePredictionService;

import java.io.*;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.zip.GZIPInputStream;

@Service
@Slf4j
public class FileDownloadService {

    private final FileDownloadRepo fileDownloadRepo;
    private final FileShareRepo fileShareRepo;
    private final FilesStorageService filesStorageService;
    private FileTransferRepo fileTransferRepo;
    private IntelligencePredictionService intelligencePredictionService;
    private UserRepo userRepo;

    private static final int GZIP_MAGIC_BYTE_1 = 0x1f;
    private static final int GZIP_MAGIC_BYTE_2 = 0x8b;

    public FileDownloadService(
            FileTransferRepo fileTransferRepo,
            UserRepo userRepo,
            IntelligencePredictionService intelligencePredictionService, FileDownloadRepo fileDownloadRepo, FileShareRepo fileShareRepo, FilesStorageService filesStorageService) {
        this.fileTransferRepo = fileTransferRepo;
        this.intelligencePredictionService = intelligencePredictionService;
        this.fileDownloadRepo = fileDownloadRepo;
        this.fileShareRepo = fileShareRepo;
        this.filesStorageService = filesStorageService;
        this.userRepo = userRepo;
    }


    public FileTransferEntity getTransferById(String transferId) {
        log.info("Querying database for transferId: {}", transferId);

        try {
            Optional<FileTransferEntity> transferOpt = fileTransferRepo.findByTransferId(transferId);
            if (transferOpt.get() != null) {
                FileTransferEntity transfer = transferOpt.get();
                log.info("Transfer found in database");
                log.debug("  ID: {}, File: {}, Path: {}",
                        transfer.getTransferId(), transfer.getFileName(), transfer.getStoragePath());
                return transfer;
            } else {
                log.warn("Transfer not found in database - TransferId: {}", transferId);
                return null;
            }

        } catch (Exception e) {
            log.error("Error querying database for transferId: {}", transferId, e);
            return null;
        }
    }
    public FileTransferEntity getShareById(String ShareId) {
        log.info("Querying database for ShareId: {}", ShareId);
        FileShare fileShare = null;
        try {
                if (ShareId.length() < 6) {
                    fileShare = fileShareRepo.findByShareId(Long.parseLong(ShareId));
                } else {
                    fileShare = fileShareRepo.findByShareToken(ShareId);
                }

            Optional<FileTransferEntity> transferOpt = fileTransferRepo.findByShareToken(fileShare.getShareToken());

            if (transferOpt.isPresent()) {
                FileTransferEntity transfer = transferOpt.get();
                log.info("Transfer found in database");
                log.debug("  ID: {}, File: {}, Path: {}",
                        transfer.getTransferId(), transfer.getFileName(), transfer.getStoragePath());
                return transfer;
            } else {
                log.warn("Transfer not found in database - ShareId: {}", ShareId);
                return null;
            }

        } catch (Exception e) {
            log.error("Error querying database for ShareId: {}", ShareId, e);
            return null;
        }
    }


    private boolean isGzipCompressed(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] header = new byte[2];
            int bytesRead = fis.read(header);

            if (bytesRead < 2) {
                return false;
            }

            // Check GZIP magic number (0x1f8b)
            return (header[0] & 0xFF) == GZIP_MAGIC_BYTE_1 &&
                    (header[1] & 0xFF) == GZIP_MAGIC_BYTE_2;
        } catch (IOException e) {
            log.error("Error checking GZIP magic bytes", e);
            return false;
        }
    }

    private String getFileExtension(String fileName) {
        if (fileName == null || fileName.lastIndexOf(".") == -1) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
    }

    /**
     * Download file with intelligent chunking based on network conditions
     *
     * Features:
     * 1. Calculates optimal chunk size based on network speed & latency
     * 2. Decompresses GZIP files on-the-fly if needed
     * 3. Streams data efficiently to avoid memory issues
     * 4. Logs progress metrics
     */

    public ChunkedDownloadResource downloadFileWithAdaptiveChunking(
            String transferId,
            Double networkSpeedMbps,
            Integer latencyMs) {
        Users users  = retriveLoggedInUser();
        log.info("Starting adaptive chunked download for transferId: {}", transferId);

        try {
            Optional<FileTransferEntity> transferOpt = fileTransferRepo.findByTransferId(transferId);

            if(transferOpt.get().getDeleted()) {
                log.info("File deleted from Storage");
                throw new FileNotFoundException("File Expired");
            }

            if (transferOpt.isEmpty()) {
                log.error("Transfer not found: {}", transferId);
                return null;
            }
            log.error("File Transfer Id : {} ", transferId);

            FileTransferEntity transferEntity = transferOpt.get();
            FileDownload fileDownload = new FileDownload();
            fileDownload.setTransferId(transferId);
            fileDownload.setFileName(transferEntity.getFileName());
            fileDownload.setNetworkSpeedMbps(networkSpeedMbps);
            fileDownload.setLatencyMs(latencyMs);
            fileDownload.setFileSize(transferEntity.getFileSize());
            fileDownload.setFileType(transferEntity.getFileType());
            fileDownload.setChunkSize(transferOpt.get().getChunkSize());
            fileDownload.setTransferDurationSeconds(LocalDateTime.now());
            fileDownloadRepo.save(fileDownload);
            FileTransferEntity transfer = transferOpt.get();
            transfer.setDownloadCount(transfer.getDownloadCount() + 1);
            String storagePath = transfer.getStoragePath();

            File file = new File(storagePath);

            if (!file.exists() || !file.isFile()) {
                log.error("File not found or invalid path: {}", storagePath);
                return null;
            }

            log.info("File found on disk - Size: {} bytes", file.length());

            // Check if file is GZIP compressed
            boolean isCompressed = isGzipCompressed(file);
            log.info("File is {} compressed", isCompressed ? "GZIP" : "NOT");

            // Get file extension
            String extension = getFileExtension(transfer.getFileName());

            // Predict optimal parameters based on network conditions
            IntelligencePredictionService.OptimizationParams optimizationParams =
                    intelligencePredictionService.predictOptimalParameters(
                            transfer.getFileName(),
                            extension,
                            networkSpeedMbps,
                            latencyMs,
                            transfer.getFileSize()
                    );

            log.info("Optimization Parameters:");
            log.info("  Network Condition: {}", optimizationParams.getNetworkCondition());
            log.info("  Chunk Size: {} bytes", optimizationParams.getChunkSize());
            log.info("  Compression Level: {}", optimizationParams.getCompressionLevel());

            // Create chunked input stream
            InputStream baseInputStream = new FileInputStream(file);

            if (isCompressed) {
                baseInputStream = new GZIPInputStream(baseInputStream);
                log.info("GZIPInputStream created - file will be decompressed");
            }

            // Wrap in chunked resource with adaptive chunk size
            ChunkedInputStream chunkedInputStream = new ChunkedInputStream(
                    baseInputStream,
                    optimizationParams.getChunkSize(),
                    transfer.getFileName()
            );

            return ChunkedDownloadResource.builder()
                    .inputStream(chunkedInputStream)
                    .fileName(transfer.getFileName())
                    .originalSizeBytes(transfer.getFileSize())
                    .compressedSizeBytes(transfer.getBytesTransferred())
                    .chunkSize(optimizationParams.getChunkSize())
                    .networkCondition(optimizationParams.getNetworkCondition())
                    .isCompressed(isCompressed)
                    .build();

        } catch (Exception e) {
            log.error("Error in adaptive download for transferId: {}", transferId, e);
            return null;
        }
    }
    public ResponseEntity<?> getTransferInfoOfPublicFile(String shareId) {
        FileShare fileShare = null;
        try {
            if (shareId.length() < 6) {
                fileShare = fileShareRepo.findByShareId(Long.parseLong(shareId));
            } else {
                fileShare = fileShareRepo.findByShareToken(shareId);
            }

            if (fileShare == null) {
                return new ResponseEntity<>("File Not Found or Link Expired", HttpStatus.NOT_FOUND);
            }

            Optional<FileTransferEntity> transferOpt = fileTransferRepo.findByShareToken(fileShare.getShareToken());

            if (transferOpt.isEmpty()) {
                return new ResponseEntity<>("File Not Found", HttpStatus.NOT_FOUND);
            }

            FileTransferEntity transfer = transferOpt.get();

            double compressionRatio = 0.0;
            if (transfer.getFileSize() != null && transfer.getFileSize() > 0 &&
                    transfer.getBytesTransferred() != null && transfer.getBytesTransferred() > 0) {
                compressionRatio = (1.0 - (double) transfer.getBytesTransferred() / transfer.getFileSize()) * 100;
            }

            FileShareDownloadDTO dto = FileShareDownloadDTO.builder()
                    .success(transfer.getSuccess())
                    .shareToken(fileShare.getShareToken())
                    .fileName(transfer.getFileName())
                    .fileType(transfer.getFileType())
                    .originalSizeBytes(transfer.getFileSize())
                    .compressedSizeBytes(transfer.getBytesTransferred())
                    .compressionRatioPercent(String.format("%.2f%%", compressionRatio))
                    .uploadedAt(transfer.getCreatedAt())
                    .completedAt(transfer.getCompletedAt())
                    .build();

            return new ResponseEntity<>(dto, HttpStatus.OK);

        } catch (Exception e) {
            log.error("Error getting public file info", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error retrieving file information");
        }
    }



    public ChunkedDownloadResource downloadPublicFileWithAdaptiveChunking(
            String transferId,
            Double networkSpeedMbps,
            String shareId,
            Integer latencyMs) {

        log.info("Starting adaptive chunked download for transferId: {}", transferId);

        try {
            Optional<FileTransferEntity> transferOpt = fileTransferRepo.findByTransferId(transferId);
            FileShare fileShare = fileShareRepo.findByShareToken(shareId);
            if (transferOpt.get().getDeleted()){
                log.info("File deleted from Storage");
                throw new FileNotFoundException("File Expired");
            }
            if (transferOpt.isEmpty() || fileShare == null ) {
                log.error("Transfer not found: {}", transferId);
                return null;
            }

            if (transferOpt.get().getSuccess() && transferOpt.get().getMarkFileAs() == MarkFileAs.PRIVATE ) {
                log.error("Transfer not found: {}", transferId);
                return null;
            }
            FileTransferEntity transferEntity = transferOpt.get();
            FileDownload fileDownload = new FileDownload();
            fileDownload.setTransferId(shareId);
            fileDownload.setFileName(transferEntity.getFileName());
            fileDownload.setNetworkSpeedMbps(networkSpeedMbps);
            fileDownload.setLatencyMs(latencyMs);
            fileDownload.setFileSize(transferEntity.getFileSize());
            fileDownload.setFileType(transferEntity.getFileType());
            fileDownload.setChunkSize(transferOpt.get().getChunkSize());
            fileDownloadRepo.save(fileDownload);
            FileTransferEntity transfer = transferOpt.get();
            transfer.setDownloadCount(transfer.getDownloadCount() + 1);
            String storagePath = transfer.getStoragePath();
            fileTransferRepo.save(transfer);
            File file = new File(storagePath);

            if (!file.exists() || !file.isFile()) {
                log.error("File not found or invalid path: {}", storagePath);
                return null;
            }

            log.info("File found on disk - Size: {} bytes", file.length());

            // Check if file is GZIP compressed
            boolean isCompressed = isGzipCompressed(file);
            log.info("File is {} compressed", isCompressed ? "GZIP" : "NOT");

            // Get file extension
            String extension = getFileExtension(transfer.getFileName());

            // Predict optimal parameters based on network conditions
            IntelligencePredictionService.OptimizationParams optimizationParams =
                    intelligencePredictionService.predictOptimalParameters(
                            transfer.getFileName(),
                            extension,
                            networkSpeedMbps,
                            latencyMs,
                            transfer.getFileSize()
                    );

            log.info("Optimization Parameters:");
            log.info("  Network Condition: {}", optimizationParams.getNetworkCondition());
            log.info("  Chunk Size: {} bytes", optimizationParams.getChunkSize());
            log.info("  Compression Level: {}", optimizationParams.getCompressionLevel());

            // Create chunked input stream
            InputStream baseInputStream = new FileInputStream(file);

            if (isCompressed) {
                baseInputStream = new GZIPInputStream(baseInputStream);
                log.info("GZIPInputStream created - file will be decompressed");
            }

            // Wrap in chunked resource with adaptive chunk size
            ChunkedInputStream chunkedInputStream = new ChunkedInputStream(
                    baseInputStream,
                    optimizationParams.getChunkSize(),
                    transfer.getFileName()
            );

            return ChunkedDownloadResource.builder()
                    .inputStream(chunkedInputStream)
                    .fileName(transfer.getFileName())
                    .originalSizeBytes(transfer.getFileSize())
                    .compressedSizeBytes(transfer.getBytesTransferred())
                    .chunkSize(optimizationParams.getChunkSize())
                    .networkCondition(optimizationParams.getNetworkCondition())
                    .isCompressed(isCompressed)
                    .build();

        } catch (Exception e) {
            log.error("Error in adaptive download for transferId: {}", transferId, e);
            return null;
        }
    }


    private Users retriveLoggedInUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if(authentication == null || !authentication.isAuthenticated())
            throw new BadCredentialsException("Bad Credentials login ");
        String username = authentication.getName();
//        System.out.println(STR."In Logged In User \{username}");
        System.out.println("Logged In User "+username);
        Users user = userRepo.findByUsername(username);
        if(user == null){
            throw new UsernameNotFoundException("User Not Found");
        }
        return user;
    }

    @Slf4j
    public static class ChunkedInputStream extends InputStream {
        private final InputStream delegate;
        private final int chunkSize;
        private final String fileName;
        private final byte[] buffer;
        private long totalBytesRead = 0;
        private long startTime;
        private int chunkCount = 0;
        private long lastLogTime = System.currentTimeMillis();

        public ChunkedInputStream(InputStream delegate, int chunkSize, String fileName) {
            this.delegate = delegate;
            this.chunkSize = chunkSize;
            this.fileName = fileName;
            this.buffer = new byte[chunkSize];
            this.startTime = System.currentTimeMillis();
        }

        @Override
        public int read() throws IOException {
            int byte_ = delegate.read();
            if (byte_ != -1) {
                totalBytesRead++;
            }
            return byte_;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int bytesRead = delegate.read(b, off, Math.min(len, this.chunkSize));

            if (bytesRead > 0) {
                totalBytesRead += bytesRead;
                chunkCount++;

                long now = System.currentTimeMillis();

                // Log once every 1 second
                if (now - lastLogTime >= 1000) {
                    double speedMbps = (totalBytesRead * 8.0) / ((now - startTime) * 1000.0);
                    String speedFormatted = String.format("%.2f", speedMbps);

                    log.info("Downloading {} → {} MB transferred, Speed: {} Mbps",
                            fileName,
                            totalBytesRead / (1024 * 1024),
                            speedFormatted
                    );

                    lastLogTime = now;
                }
            }

            return bytesRead;
        }

        @Override
        public void close() throws IOException {
            long totalTime = System.currentTimeMillis() - startTime;
            double avgSpeedMbps = (totalBytesRead * 8.0) / (totalTime * 1000.0);

            String avgSpeedFormatted = String.format("%.2f", avgSpeedMbps);

            log.info("Download Complete → File: {}, Size: {} MB, Time: {} ms, Avg Speed: {} Mbps",
                    fileName,
                    totalBytesRead / (1024 * 1024),
                    totalTime,
                    avgSpeedFormatted
            );

            delegate.close();
        }


    }
}
