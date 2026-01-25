package peerlinkfilesharingsystem.Model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
@Entity
@Data
@Table(name = "active_devices")
public class ActiveDevice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String ipAddress;
    private String deviceName;
    @Column(nullable = false, unique = true)
    private String sessionToken;
    @Column(nullable = false)
    private LocalDateTime lastSeen;
}
