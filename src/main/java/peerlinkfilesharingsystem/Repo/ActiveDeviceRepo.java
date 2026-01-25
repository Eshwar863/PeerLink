package peerlinkfilesharingsystem.Repo;

import org.springframework.data.jpa.repository.JpaRepository;
import peerlinkfilesharingsystem.Model.ActiveDevice;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
public interface ActiveDeviceRepo extends JpaRepository<ActiveDevice, Long> {
    
    // Find other devices on same IP, excluding this session
    List<ActiveDevice> findByIpAddressAndSessionTokenNot(String ipAddress, String sessionToken);
    
    // Find device by session token
    Optional<ActiveDevice> findBySessionToken(String sessionToken);
    
    // Cleanup stale sessions (older than X minutes)
    void deleteByLastSeenBefore(LocalDateTime cutoff);

    List<ActiveDevice> findByIpAddress(String clientIp);
}