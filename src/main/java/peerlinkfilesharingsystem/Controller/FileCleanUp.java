package peerlinkfilesharingsystem.Controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.RestController;
import peerlinkfilesharingsystem.Service.FileStorageService.FilesStorageService;

@RestController
@Slf4j
public class FileCleanUp {

    private final FilesStorageService fileDownloadService;

    public FileCleanUp(FilesStorageService filesStorageService) {
        this.fileDownloadService = filesStorageService;
    }

//        @Scheduled(cron = "0 * * * * *")
@Scheduled(cron = "0 1 * * * *")
//    @Scheduled(cron = "0 0 * * * *")
    public void deleteExpiredFile(){
        log.debug("Deleting expired files");
        fileDownloadService.deleteExpiredFiles();
    }
    @Scheduled(cron = "0 5 * * * *")
    public void deleteExpiredFilesinTransferEntity(){
        log.debug("Deleting expired files");
        fileDownloadService.deleteExpiredFilesinTransferEntity();
    }
    @Scheduled(cron = "0 8 * * * *")
    public void deleteUnsuccessFulFilesinTransferEntity(){
        log.debug("Deleting expired files");
        fileDownloadService.deleteUnsuccessfulFilesinTransferEntity();
    }
}
