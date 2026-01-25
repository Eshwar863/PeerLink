package peerlinkfilesharingsystem.Controller;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import peerlinkfilesharingsystem.Model.FileShareRequest;
import peerlinkfilesharingsystem.Model.Users;
import peerlinkfilesharingsystem.Repo.FileShareRequestRepo;
import peerlinkfilesharingsystem.Repo.UserRepo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
@RestController
@RequestMapping("/api/requests")
@Slf4j
public class RequestController {
    private final FileShareRequestRepo requestRepo;
    private final UserRepo userRepo;
    public RequestController(FileShareRequestRepo requestRepo, UserRepo userRepo) {
        this.requestRepo = requestRepo;
        this.userRepo = userRepo;
    }
    /**
     * Send a file transfer request
     */
    @PostMapping("/send")
    public ResponseEntity<SendRequestResponse> sendRequest(@RequestBody SendRequestDto dto) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        
        Users sender = userRepo.findByUsername(username);
        if (sender == null) {
            throw new RuntimeException("User not found");
        }
        log.info("User {} sending request for transferId: {} to receiverId: {}, sessionToken: {}",
                username, dto.getTransferId(), dto.getReceiverId(), dto.getReceiverSessionToken());
        FileShareRequest request = new FileShareRequest();
        request.setSenderId(sender.getId());
        request.setSenderUsername(sender.getUsername());
        request.setReceiverId(dto.getReceiverId());
        request.setReceiverSessionToken(dto.getReceiverSessionToken());
        request.setTransferId(dto.getTransferId());
        request.setStatus("PENDING");
        request.setCreatedAt(LocalDateTime.now());
        requestRepo.save(request);
        return ResponseEntity.ok(new SendRequestResponse("Request sent successfully", request.getId()));
    }
    /**
     * Get pending requests for current user (logged-in)
     */
    @GetMapping("/pending")
    public ResponseEntity<List<RequestDto>> getPendingRequests() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();

        Users currentUser = userRepo.findByUsername(username);
        if (currentUser == null) {
            throw new RuntimeException("User not found");
        }
        List<FileShareRequest> requests = requestRepo.findByReceiverIdAndStatus(currentUser.getId(), "PENDING");
        List<RequestDto> dtos = requests.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }
    /**
     * PUBLIC ENDPOINT: Get pending requests for guest device
     */
    @PostMapping("/pending/guest")
    public ResponseEntity<List<RequestDto>> getPendingRequestsForGuest(@RequestBody GuestRequestDto dto) {
        if (dto.getSessionToken() == null || dto.getSessionToken().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        List<FileShareRequest> requests = requestRepo
                .findByReceiverSessionTokenAndStatus(dto.getSessionToken(), "PENDING");
        List<RequestDto> dtos = requests.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }
    /**
     * Accept a request
     */
    @PostMapping("/{requestId}/accept")
    public ResponseEntity<AcceptRequestResponse> acceptRequest(@PathVariable Long requestId) {
        FileShareRequest request = requestRepo.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Request not found"));
        request.setStatus("ACCEPTED");
        requestRepo.save(request);
        log.info("Request {} accepted. TransferId: {}", requestId, request.getTransferId());
        return ResponseEntity.ok(new AcceptRequestResponse(request.getTransferId()));
    }
    /**
     * Reject a request
     */
    @PostMapping("/{requestId}/reject")
    public ResponseEntity<String> rejectRequest(@PathVariable Long requestId) {
        FileShareRequest request = requestRepo.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Request not found"));
        request.setStatus("REJECTED");
        requestRepo.save(request);
        log.info("Request {} rejected", requestId);
        return ResponseEntity.ok("Request rejected");
    }
    private RequestDto toDto(FileShareRequest request) {
        return new RequestDto(
                request.getId(),
                request.getSenderUsername(),
                request.getTransferId(),
                request.getCreatedAt()
        );
    }
    // DTOs
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class SendRequestDto {
        private UUID receiverId;
        private String receiverSessionToken;
        private String transferId;
    }
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class SendRequestResponse {
        private String message;
        private Long requestId;
    }
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class RequestDto {
        private Long id;
        private String senderUsername;
        private String transferId;
        private LocalDateTime createdAt;
    }
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class GuestRequestDto {
        private String sessionToken;
    }
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class AcceptRequestResponse {
        private String transferId;
    }
}