package peerlinkfilesharingsystem.Service.FileStorageService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import peerlinkfilesharingsystem.Exception.UnauthorizedFileAccessException;
import peerlinkfilesharingsystem.Model.DeletedFiles;
import peerlinkfilesharingsystem.Model.FileTransferEntity;
import peerlinkfilesharingsystem.Repo.DeletedFilesRepo;
import peerlinkfilesharingsystem.Repo.FileTransferRepo;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class FilesStorageService {

    private final FileTransferRepo fileTransferRepo;
    private final DeletedFilesRepo deletedFilesRepo;
    @Value("${file.storage.path:./uploads}")
    private String baseUploadDirectory;

    public FilesStorageService(FileTransferRepo fileTransferRepo, DeletedFilesRepo deletedFilesRepo) {
        this.fileTransferRepo = fileTransferRepo;
        this.deletedFilesRepo = deletedFilesRepo;
    }


    public String createUserDirectory(String userId) {
        String userDirPath = baseUploadDirectory + "/user_" + userId;
        File userDir = new File(userDirPath);

        if (!userDir.exists()) {
            boolean created = userDir.mkdirs();
            if (created) {
                log.info("Created new user directory: {}", userDirPath);
            } else {
                log.error("Failed to create user directory: {}", userDirPath);
                throw new RuntimeException("Could not create user directory");
            }
        }

        return userDirPath;
    }

    public String getUserFilePath(String userId, String transferId) {
        String userDir = createUserDirectory(userId);
        return userDir + "/" + transferId + ".gz";
    }

    public boolean fileExists(String userId, String transferId) {
        String filePath = getUserFilePath(userId, transferId);
        return new File(filePath).exists();
    }


    public boolean  validateUserAccess(String requestingUserId, String filePath) {
        try {
            Path normalizedPath = Paths.get(filePath).normalize().toAbsolutePath();
            Path expectedUserDir = Paths.get(baseUploadDirectory, "user_" + requestingUserId)
                    .normalize().toAbsolutePath();

            if (!normalizedPath.startsWith(expectedUserDir)) {
                log.warn("SECURITY VIOLATION: User {} tried accessing {}", requestingUserId, filePath);
                throw new UnauthorizedFileAccessException("Access denied: You cannot access this file / Folder");
            }

            log.debug("Access granted: User {} accessing {}", requestingUserId, filePath);
        } catch (Exception e) {
            log.error("Access validation failed for user {}: {}", requestingUserId, e.getMessage());
            throw new UnauthorizedFileAccessException("Access validation failed: " + e.getMessage());
        }
        return true;
    }


    public void validateTransferOwnership(String userId, String transferId, String storedPath) {
        if (!storedPath.contains("user_" + userId)) {
            log.warn("OWNERSHIP VIOLATION: User {} tried accessing transfer {} owned by another user",
                    userId, transferId);
            throw new UnauthorizedFileAccessException("Access denied: This transfer does not belong to you");
        }
    }


    public String getUserDirectoryPath(String userId) {
        return baseUploadDirectory + "/user_" + userId;
    }

    public void deleteExpiredFiles() {

        List<FileTransferEntity> expiredFiles =
                fileTransferRepo.findExpiredFiles(LocalDateTime.now());

        if (expiredFiles.isEmpty()) {
            log.info("No expired files found.");
            System.out.println("No Expired files found.");
        }

        boolean allDeleted = true;

        for (FileTransferEntity file : expiredFiles) {
            if (!file.getDeleted()) {

                DeletedFiles deletedFiles = new DeletedFiles();
                deletedFiles.setFileName(file.getFileName());
                deletedFiles.setFileType(file.getFileType());
                deletedFiles.setFilePath(file.getStoragePath());
                deletedFiles.setDeletedAt(LocalDateTime.now());
                deletedFiles.setTransferId(file.getTransferId());
                deletedFiles.setUserId(file.getUserId().toString());
                deletedFilesRepo.save(deletedFiles);

                file.setDeleted(true);
                fileTransferRepo.save(file);
                String filePath = file.getStoragePath();
                File physicalFile = new File(filePath);

                boolean deleted = false;
                if (physicalFile.exists()) {
                    deleted = physicalFile.delete();
                    log.info("Deleted expired file: {} → {}", file.getFileName(), deleted);
                } else {
                    log.warn("File not found on disk: {}", filePath);
                }

                fileTransferRepo.delete(file);
                log.info("Deleted DB entry for file: {}", file.getTransferId());

                if (!deleted) {
                    allDeleted = false;
                }
            }
        }
    }

    public void deleteExpiredFilesinTransferEntity() {
        LocalDateTime now = LocalDateTime.now();

        List<FileTransferEntity> expiredFiles = fileTransferRepo
                .findByExpiresAtBeforeAndDeletedFalse(now);

        if (expiredFiles.isEmpty()) {
            log.info("No expired files found");
            return;
        }

        expiredFiles.forEach(file -> {
            file.setDeleted(true);
            file.setStatus("EXPIRED");
        });

        fileTransferRepo.saveAll(expiredFiles);

        log.info("Expired files deleted: {}", expiredFiles.size());
    }



    public void deleteUnsuccessfulFilesinTransferEntity() {

        List<FileTransferEntity> failedFiles = fileTransferRepo.findBySuccessFalseOrStatus("FAILED");

        log.info("Unsuccessful transfers found: {}", failedFiles.size());

        for (FileTransferEntity file : failedFiles) {

            try {
                if (file.getStoragePath() != null) {
                    File f = new File(file.getStoragePath());
                    if (f.exists()) {
                        boolean deleted = f.delete();
                        log.info("Deleted failed file from disk: {} -> {}",
                                file.getStoragePath(), deleted);
                    }
                }

                fileTransferRepo.delete(file);
                log.info("Deleted failed file metadata with transferId: {}", file.getTransferId());

            } catch (Exception e) {
                log.error("Error deleting failed file {} : {}", file.getTransferId(), e.getMessage());
            }
        }
    }

//    private void deleteFileFromDisk(String path) {
//        if (path == null) return;
//
//        File f = new File(path);
//        if (f.exists()) {
//            boolean result = f.delete();
//            log.info("Deleted file from disk {} -> {}", path, result);
//        }
//    }
}
