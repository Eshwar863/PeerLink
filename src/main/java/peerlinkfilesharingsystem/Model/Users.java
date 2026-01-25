package peerlinkfilesharingsystem.Model;

import jakarta.persistence.*;
import lombok.Data;
import peerlinkfilesharingsystem.Enums.UserRole;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Data
public class Users {
    @Id
    @GeneratedValue
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;
    @Column(unique = true, nullable = false)
    private String username;
    @Column(nullable = false)
    private String password;
    @Column(unique = true, nullable = false)
    private String email;
    private String LastIpAddress;
    private UserRole role;
//    @OneToOne(mappedBy = "users")
//    private Otp otp;
}
