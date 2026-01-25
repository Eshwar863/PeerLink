package peerlinkfilesharingsystem.Repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import peerlinkfilesharingsystem.Model.Users;

import java.util.List;
import java.util.UUID;

@Repository
public interface UserRepo extends JpaRepository<Users, UUID> {
    Users findByEmail(String email);

    Users findByUsername(String username);
    Users findByEmailAndUsername(String email, String username);
    List<Users> findByLastIpAddressAndIdNot( String clientIp,UUID id);
}
