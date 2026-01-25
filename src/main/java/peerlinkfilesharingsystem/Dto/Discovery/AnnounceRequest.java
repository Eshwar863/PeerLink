package peerlinkfilesharingsystem.Dto.Discovery;

@lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public class AnnounceRequest {
        private String deviceName;
        private String sessionToken; // For re-announcing
    }