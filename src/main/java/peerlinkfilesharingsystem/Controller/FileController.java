package peerlinkfilesharingsystem.Controller;


import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import peerlinkfilesharingsystem.Dto.ShareFileResponse;
import peerlinkfilesharingsystem.Service.FileShareService.FileShareService;

import java.util.List;

@RestController("files")
@Slf4j
public class FileController {

    private final FileShareService fileShareService;


    public FileController(FileShareService fileShareService) {
        this.fileShareService = fileShareService;
    }



    @GetMapping("/my-shares")
    public ResponseEntity<List<ShareFileResponse>> getMySharedFiles(
            @RequestParam(defaultValue = "10") Integer limit) {

        log.info("GET /fileshare/my-shares - Limit: {}", limit);

        try {
            List<ShareFileResponse> sharedFiles = fileShareService.getUserSharedFiles(limit);

            log.info("Returning {} shared files", sharedFiles.size());

            return ResponseEntity.ok(sharedFiles);

        } catch (Exception e) {
            log.error("Error fetching shared files", e);
            return ResponseEntity.internalServerError().build();
        }
    } @GetMapping("/my-shares/all")
    public ResponseEntity<List<ShareFileResponse>> getAllSharedFiles() {
        log.info("GET /fileshare/my-shares/all");

        try {
            List<ShareFileResponse> sharedFiles = fileShareService.getUserSharedFiles(null);
            return ResponseEntity.ok(sharedFiles);

        } catch (Exception e) {
            log.error("Error fetching all shared files", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}