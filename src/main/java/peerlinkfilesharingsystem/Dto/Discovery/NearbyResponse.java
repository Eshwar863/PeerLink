package peerlinkfilesharingsystem.Dto.Discovery;

import java.util.List;

@lombok.Data
    @lombok.AllArgsConstructor
    public  class NearbyResponse {
        private List<NearbyUserDto> users;
        private List<NearbyDeviceDto> devices;
    }