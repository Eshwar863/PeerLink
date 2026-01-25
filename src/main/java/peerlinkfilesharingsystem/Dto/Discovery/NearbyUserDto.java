package peerlinkfilesharingsystem.Dto.Discovery;


import java.util.UUID;

    @lombok.Data
    @lombok.AllArgsConstructor
    public  class NearbyUserDto {
        private UUID id;
        private String username;
        private String type;
    }