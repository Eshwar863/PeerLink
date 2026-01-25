package peerlinkfilesharingsystem.Dto.Discovery;

@lombok.Data
    @lombok.AllArgsConstructor
    public  class NearbyDeviceDto {
        private String sessionToken;
        private String deviceName;
        private String type;
    }