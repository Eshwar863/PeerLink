package peerlinkfilesharingsystem.Repo;
import org.springframework.data.jpa.repository.JpaRepository;
import peerlinkfilesharingsystem.Model.FileShareRequest;
import java.util.List;
import java.util.UUID;

public interface FileShareRequestRepo extends JpaRepository<FileShareRequest, Long> {
    
    // Find pending requests for a logged-in user
    List<FileShareRequest> findByReceiverIdAndStatus(UUID receiverId, String status);
    
    // Find pending requests for a guest device
    List<FileShareRequest> findByReceiverSessionTokenAndStatus(String sessionToken, String status);
    
    // Optional: Find all requests sent by a user
    List<FileShareRequest> findBySenderId(UUID senderId);
}