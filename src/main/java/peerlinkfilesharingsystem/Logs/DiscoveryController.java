package peerlinkfilesharingsystem.Logs;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import peerlinkfilesharingsystem.Dto.Discovery.*;
import peerlinkfilesharingsystem.Model.*;
import peerlinkfilesharingsystem.Model.Users;
import peerlinkfilesharingsystem.Repo.UserRepo;
import peerlinkfilesharingsystem.Repo.ActiveDeviceRepo;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/discovery")
@Slf4j
public class DiscoveryController {

    private final UserRepo userRepo;
    private final ActiveDeviceRepo activeDeviceRepo;
    private static final int SESSION_TIMEOUT_MINUTES = 5;

    public DiscoveryController(UserRepo userRepo, ActiveDeviceRepo activeDeviceRepo) {
        this.userRepo = userRepo;
        this.activeDeviceRepo = activeDeviceRepo;
    }

    /**
     * PUBLIC ENDPOINT: Announce a guest device's presence
     */
    @PostMapping("/announce")
    public ResponseEntity<AnnounceResponse> announceDevice(
            @RequestBody(required = false) AnnounceRequest request,
            HttpServletRequest httpRequest) {

        String clientIp = getClientIp(httpRequest);
        String deviceName = (request != null && request.getDeviceName() != null)
                ? request.getDeviceName()
                : "Guest Device";
        String sessionToken = request != null && request.getSessionToken() != null
                ? request.getSessionToken()
                : UUID.randomUUID().toString();

        log.info("Device announcement from IP: {} with session: {}", clientIp, sessionToken);

        // Find or create device record
        ActiveDevice device = activeDeviceRepo.findBySessionToken(sessionToken)
                .orElse(new ActiveDevice());

        device.setIpAddress(clientIp);
        device.setDeviceName(deviceName);
        device.setSessionToken(sessionToken);
        device.setLastSeen(LocalDateTime.now());

        activeDeviceRepo.save(device);

        // Cleanup old sessions
        cleanupStaleDevices();

        return ResponseEntity.ok(new AnnounceResponse(sessionToken));
    }

    /**
     * Get nearby users and devices (requires login)
     */
    @GetMapping("/nearby")
    public ResponseEntity<NearbyResponse> getNearbyDevices(HttpServletRequest httpRequest) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();

        // Get current user (Handle Optional if your repo returns Optional)
        Users currentUser = userRepo.findByUsername(username);
        if (currentUser == null) {
            throw new RuntimeException("User not found");
        }

        String clientIp = getClientIp(httpRequest);
        log.info("User {} searching for nearby devices at IP: {}", username, clientIp);

        // Update user's IP
        currentUser.setLastIpAddress(clientIp);
        userRepo.save(currentUser);

        // Find nearby logged-in users (excluding self)
        List<Users> nearbyUsers = userRepo.findByLastIpAddressAndIdNot(clientIp, currentUser.getId());

        // Find nearby guest devices
        cleanupStaleDevices();

        // FIX: Use findByIpAddress and filter in memory (more reliable)
        // Make sure your ActiveDeviceRepo has: List<ActiveDevice> findByIpAddress(String ipAddress);
        List<ActiveDevice> allDevices = activeDeviceRepo.findByIpAddress(clientIp);

        // Filter out devices that shouldn't be there (if any logic needed)
        List<ActiveDevice> nearbyDevices = allDevices;

        log.info("Found {} users and {} guest devices on IP {}", nearbyUsers.size(), nearbyDevices.size(), clientIp);

        // Map to DTOs
        List<NearbyUserDto> users = nearbyUsers.stream()
                .map(u -> new NearbyUserDto(u.getId(), u.getUsername(), "USER"))
                .collect(Collectors.toList());

        List<NearbyDeviceDto> devices = nearbyDevices.stream()
                .map(d -> new NearbyDeviceDto(d.getSessionToken(), d.getDeviceName(), "GUEST"))
                .collect(Collectors.toList());

        return ResponseEntity.ok(new NearbyResponse(users, devices));
    }

    /**
     * Clear user's IP address on logout (called by frontend)
     */
    @PostMapping("/clear")
    public ResponseEntity<String> clearUserDiscovery() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();

        Users currentUser = userRepo.findByUsername(username);
        if (currentUser == null) {
            throw new RuntimeException("User not found");
        }
        // Clear the IP so they don't appear in nearby devices
        currentUser.setLastIpAddress(null);
        userRepo.save(currentUser);

        log.info("Cleared discovery for user: {}", username);

        return ResponseEntity.ok("Discovery cleared");
    }

    @Transactional
    protected void cleanupStaleDevices() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(SESSION_TIMEOUT_MINUTES);
        activeDeviceRepo.deleteByLastSeenBefore(cutoff);
    }

    private String getClientIp(HttpServletRequest request) {
        String remoteAddr = "";
        if (request.getHeader("X-FORWARDED-FOR") != null) {
            remoteAddr = request.getHeader("X-FORWARDED-FOR");
        } else {
            remoteAddr = request.getRemoteAddr();
        }
        if ("0:0:0:0:0:0:0:1".equals(remoteAddr)) {
            return "127.0.0.1";
        }
        return remoteAddr;
    }
}