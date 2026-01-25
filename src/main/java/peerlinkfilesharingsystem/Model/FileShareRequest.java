package peerlinkfilesharingsystem.Model;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Data
@Table(name = "file_share_requests")
public class FileShareRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private UUID senderId;
    @Column(nullable = false)
    private String senderUsername;
    // For logged-in user receivers
    private UUID receiverId;
    // For guest device receivers
    private String receiverSessionToken;
    @Column(nullable = false)
    private String transferId;
    @Column(nullable = false)
    private String status; // PENDING, ACCEPTED, REJECTED
    @Column(nullable = false)
    private LocalDateTime createdAt;
}