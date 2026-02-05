package peerlinkfilesharingsystem.Service.FileShareService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import peerlinkfilesharingsystem.Config.SecurityConfig;
import peerlinkfilesharingsystem.Dto.EmailFileRequest;
import peerlinkfilesharingsystem.Dto.ShareFileResponse;
import peerlinkfilesharingsystem.Enums.MarkFileAs;
import peerlinkfilesharingsystem.Exception.UnauthorizedFileAccessException;
import peerlinkfilesharingsystem.Model.FileShare;
import peerlinkfilesharingsystem.Model.FileTransferEntity;
import peerlinkfilesharingsystem.Model.Users;
import peerlinkfilesharingsystem.Repo.FileShareRepo;
import peerlinkfilesharingsystem.Repo.FileTransferRepo;
import peerlinkfilesharingsystem.Repo.UserRepo;
import peerlinkfilesharingsystem.Service.FileStorageService.FilesStorageService;
import peerlinkfilesharingsystem.Service.MailService.MailService;

import java.io.FileNotFoundException;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
public class FileShareService {

    private final FileTransferRepo fileTransferRepo;
    private final FileShareRepo fileShareRepo;
    private final SecurityConfig config;
    private final FilesStorageService filesStorageService;
    private final MailService mailService;
    private final UserRepo userRepo;

    public FileShareService(FileTransferRepo fileTransferRepo, FileShareRepo fileShareRepo, SecurityConfig config, FilesStorageService filesStorageService, FilesStorageService filesStorageService1, MailService mailService1, UserRepo userRepo) {
        this.fileTransferRepo = fileTransferRepo;
        this.fileShareRepo = fileShareRepo;
        this.config = config;
        this.filesStorageService = filesStorageService1;
        this.mailService = mailService1;
        this.userRepo = userRepo;
    }

    public ResponseEntity<?> markFileAspublic(String transferId) {

//        Optional<FileTransferEntity> fileTransferEntity = fileTransferRepo.findByTransferId(transferId);
        FileTransferEntity fileTransferEntity = fileTransferRepo.findByTransferId(transferId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "File transfer not found with ID: " + transferId)
                );
        log.info("fileTransfer Entity: {}", fileTransferEntity);
        Users users = retriveLoggedInUser();

        try {
            filesStorageService.validateUserAccess(users.getId().toString(), fileTransferEntity.getStoragePath());
        } catch (UnauthorizedFileAccessException ex) {
            return new ResponseEntity<>("Invalid Access", HttpStatus.UNAUTHORIZED);
        }
//
//
//        System.out.println(fileTransferEntity.getStoragePath());
//        if (fileStorageService.validateUserAccess("13131",fileTransferEntity.getStoragePath())){
//            return new ResponseEntity<>("Invalid Access ", HttpStatus.UNAUTHORIZED);
//        }
        FileShare  fileShare1 = fileShareRepo.findByShareToken(fileTransferEntity.getShareToken());
        log.info("fileTransfer Entity: {}", fileShare1);
        if (fileShare1!=null && fileTransferEntity.getMarkFileAs() == MarkFileAs.PUBLIC) {
            return new ResponseEntity<>("Already Marked as Public" ,HttpStatus.OK);
        }
        FileTransferEntity file = fileTransferEntity;
        file.setMarkFileAs(MarkFileAs.PUBLIC);
        file.setShareToken(UUID.randomUUID().toString());
        fileTransferRepo.save(file);

        FileShare fileShare = new FileShare();
        fileShare.setFileName(file.getFileName());
        fileShare.setFileSize(file.getFileSize());
        fileShare.setFileType(file.getFileType());
        fileShare.setShareToken(file.getShareToken());
        fileShare.setShareId(generateUniqueShareId());
//        fileShare.setShareExpiresAt(LocalDateTime.now().plusSeconds(15));
        fileShare.setShareExpiresAt(LocalDateTime.now().plusDays(1));
        fileShareRepo.save(fileShare);
        return new ResponseEntity<>("File marked as PUBLIC: " + transferId, HttpStatus.OK);
    }


    public ResponseEntity<?> getShareUrl(String transferId) {
        Users users = retriveLoggedInUser();
        Optional<FileTransferEntity> fileOpt = fileTransferRepo.findByTransferId(transferId);
        System.out.println(fileOpt.get());
        if (fileOpt.isEmpty()) {
            return ResponseEntity.status(404).body("File not found");
        }
        try {
            filesStorageService.validateUserAccess(users.getId().toString(), fileOpt.get().getStoragePath());
        } catch (UnauthorizedFileAccessException ex) {
            return new ResponseEntity<>("Invalid Access", HttpStatus.UNAUTHORIZED);
        }


        FileTransferEntity file = fileOpt.get();

        if (file.getMarkFileAs() != MarkFileAs.PUBLIC) {
            return ResponseEntity.status(403).body("File is not public");
        }

        FileShare fileShare = fileShareRepo.findByShareToken(file.getShareToken());
        if (fileShare == null) {
            return ResponseEntity.status(404).body("Share record not found");
        }

        if (fileShare.getShareExpiresAt().isBefore(LocalDateTime.now())) {
            return ResponseEntity.status(410).body("Share link has expired");
        }

        String shareUrl = ServletUriComponentsBuilder
                .fromCurrentContextPath()
                .path("/files/info/public/")
                .path(fileShare.getShareToken())
                .toUriString();


        ShareFileResponse response = ShareFileResponse.builder()
                .fileName(fileShare.getFileName())
                .fileDownloadUri(shareUrl)
                .shareport(fileShare.getShareId())
                .fileSize(fileShare.getFileSize())
                .fileType(fileShare.getFileType())
                .expiresAt(fileShare.getShareExpiresAt())
                .build();

        return ResponseEntity.ok(response);
    }
    public ResponseEntity<?> markFileAsPrivate(String transferId) throws FileNotFoundException {
        Users users = retriveLoggedInUser();
//        Optional<FileTransferEntity> fileTransferEntity = Optional.ofNullable(fileTransferRepo.findByTransferId(transferId).orElseThrow(() ->
        //new FileNotFoundException("File Not Found")));
        FileTransferEntity fileTransferEntity = fileTransferRepo.findByTransferId(transferId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "File transfer not found with ID: " + transferId)
                );
        System.out.println(fileTransferEntity.getStoragePath());
        try {
            filesStorageService.validateUserAccess(users.getId().toString(), fileTransferEntity.getStoragePath());
        } catch (UnauthorizedFileAccessException ex) {
            return new ResponseEntity<>("Invalid Access", HttpStatus.UNAUTHORIZED);
        }
        FileShare  fileShare1 = fileShareRepo.findByShareToken(fileTransferEntity.getShareToken());
        if (fileTransferEntity.getMarkFileAs() == MarkFileAs.PRIVATE ) {
            return new ResponseEntity<>("Already Marked as Private" ,HttpStatus.OK);
        }

        FileTransferEntity file = fileTransferEntity;
        file.setMarkFileAs(MarkFileAs.PRIVATE);
        file.setShareToken(null);
        fileTransferRepo.save(file);
        fileShareRepo.delete(fileShare1);
        return new ResponseEntity<>("File marked as PRIVATE : " + transferId, HttpStatus.OK);
    }

    public Long generateRandomId() {
        return 10000 + (long)(Math.random() * 90000);
    }

    public Long generateUniqueShareId() {
        Long randomId;

        do {
            randomId = generateRandomId();
        } while (fileShareRepo.checkShareId(randomId));

        return randomId;    }



    public ResponseEntity<?> mailShareUrl(String transferId) {
        Users users = retriveLoggedInUser();
        Optional<FileTransferEntity> fileOpt = fileTransferRepo.findByShareToken(transferId);
        System.out.println(fileOpt.get());
        if (fileOpt.isEmpty()) {
            return ResponseEntity.status(404).body("File not found");
        }
        try {
            filesStorageService.validateUserAccess(users.getId().toString(), fileOpt.get().getStoragePath());
        } catch (UnauthorizedFileAccessException ex) {
            return new ResponseEntity<>("Invalid Access", HttpStatus.UNAUTHORIZED);
        }


        FileTransferEntity file = fileOpt.get();

        if (file.getMarkFileAs() != MarkFileAs.PUBLIC) {
            return ResponseEntity.status(403).body("File is not public");
        }

        FileShare fileShare = fileShareRepo.findByShareToken(file.getShareToken());
        if (fileShare == null) {
            return ResponseEntity.status(404).body("Share record not found");
        }

        if (fileShare.getShareExpiresAt().isBefore(LocalDateTime.now())) {
            return ResponseEntity.status(410).body("Share link has expired");
        }

        String shareUrl = ServletUriComponentsBuilder
                .fromCurrentContextPath()
                .path("/files/info/public/")
                .path(fileShare.getShareToken())
                .toUriString();


        ShareFileResponse response = ShareFileResponse.builder()
                .fileName(fileShare.getFileName())
                .fileDownloadUri(shareUrl)
                .shareport(fileShare.getShareId())
                .fileSize(fileShare.getFileSize())
                .fileType(fileShare.getFileType())
                .expiresAt(fileShare.getShareExpiresAt())
                .build();

        return ResponseEntity.ok(response);

    }

    public ResponseEntity<?> sendLinkToEmail(EmailFileRequest emailFileRequest ) {
        retriveLoggedInUser();
        ResponseEntity<?> shareFileResponse = mailShareUrl(emailFileRequest.getShareToken());

        if (!shareFileResponse.getStatusCode().is2xxSuccessful()) {
            return shareFileResponse; // return the error (404/401/403/410 → same)
        }

        Object body = shareFileResponse.getBody();

        if (!(body instanceof ShareFileResponse)) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Invalid response format");
        }

        ShareFileResponse response = (ShareFileResponse) body;

        try {
            if (mailService.sendLinkToMail(response, emailFileRequest.getEmail())) {
                return ResponseEntity.ok("Link has been sent");
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error sending link to email");
        }

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error sending link to email");
    }

    public ResponseEntity<?> markedStatus(String transferId) {
        Users users = retriveLoggedInUser();
        FileTransferEntity fileTransferEntity = fileTransferRepo.findByTransferId(transferId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "File transfer not found with ID: " + transferId)
                );
        try {
            filesStorageService.validateUserAccess(users.getId().toString(), fileTransferEntity.getStoragePath());
        } catch (UnauthorizedFileAccessException ex) {
            return new ResponseEntity<>("Invalid Access", HttpStatus.UNAUTHORIZED);
        }
        if(fileTransferEntity.getMarkFileAs() == MarkFileAs.PRIVATE) {
            return new ResponseEntity<>(MarkFileAs.PRIVATE,HttpStatus.OK);
        }
        return new ResponseEntity<>(MarkFileAs.PUBLIC,HttpStatus.OK);
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

    /**
     * Get all shared files for the logged-in user
     * @param limit - Maximum number of files to return (null = all)
     * @return List of shared file information
     */
    public List<ShareFileResponse> getUserSharedFiles(Integer limit) {
        // Get logged-in user
        Users user = retriveLoggedInUser();

        log.info("Fetching shared files for user: {}", user.getUsername());

        // Find all PUBLIC files for this user
        List<FileTransferEntity> userFiles;

        if (limit == null) {
            // Get all public files for user
            userFiles = fileTransferRepo.findByUserIdAndMarkFileAs(
                    user.getId(),
                    String.valueOf(MarkFileAs.PUBLIC)
            );
        } else {
            // Get limited number of public files
            userFiles = fileTransferRepo.findByUserIdAndMarkFileAsWithLimit(
                    user.getId(),
                    String.valueOf(MarkFileAs.PUBLIC),
                    limit
            );
        }

        // Convert to response DTOs
        List<ShareFileResponse> responses = new ArrayList<>();

        for (FileTransferEntity file : userFiles) {
            // Get share info from FileShare table
            FileShare fileShare = fileShareRepo.findByShareToken(file.getShareToken());

            if (fileShare != null && fileShare.getShareExpiresAt().isAfter(LocalDateTime.now())) {
                // Build share URL
                String shareUrl = ServletUriComponentsBuilder
                        .fromCurrentContextPath()
                        .path("/files/info/public/")
                        .path(fileShare.getShareToken())
                        .toUriString();

                // Create response object
                ShareFileResponse response = ShareFileResponse.builder()
                        .fileName(fileShare.getFileName())
                        .fileDownloadUri(shareUrl)
                        .shareport(fileShare.getShareId())
                        .fileSize(fileShare.getFileSize())
                        .fileType(fileShare.getFileType())
                        .expiresAt(fileShare.getShareExpiresAt())
                        .build();

                responses.add(response);
            }
        }

        log.info("Found {} shared files for user {}", responses.size(), user.getUsername());

        return responses;
    }

}