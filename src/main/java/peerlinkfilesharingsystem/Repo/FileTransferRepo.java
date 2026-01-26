package peerlinkfilesharingsystem.Repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import peerlinkfilesharingsystem.Enums.MarkFileAs;
import peerlinkfilesharingsystem.Model.FileTransferEntity;

import java.io.File;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FileTransferRepo extends JpaRepository<FileTransferEntity,UUID> {


    @Query(value = "SELECT * FROM file_transfer_entity " +
            "WHERE user_id = :userId " +
            "ORDER BY file_id DESC " +
            "LIMIT :limit",
            nativeQuery = true)
    List<FileTransferEntity> findLastUploads(@Param("userId") UUID userId,
                                             @Param("limit") int limit);


    Optional<FileTransferEntity> findByShareToken(String shareToken);

    @Query(value = "SELECT * FROM file_transfer_entity " +
            "WHERE expires_at <= :time " +
            "ORDER BY expires_at DESC",
            nativeQuery = true)
    List<FileTransferEntity> findExpiredFiles(@Param("time") LocalDateTime time);

    Optional<FileTransferEntity> findByTransferId(String transferId);

    @Query("SELECT CASE WHEN COUNT(f) > 0 THEN true ELSE false END FROM FileShare f WHERE f.ShareId = :shareId")
    boolean checkShareId(@Param("shareId") Long shareId);



    List<FileTransferEntity> findBySuccessFalseOrStatus(String failed);

    List<FileTransferEntity> findByExpiresAtBeforeAndDeletedFalse(LocalDateTime now);

//    List<FileTransferEntity> findByUserIdAndMarkFileAsWithLimit(UUID id, MarkFileAs markFileAs, Integer limit);
//
//    List<FileTransferEntity> findByUserIdAndMarkFileAs(UUID id, MarkFileAs markFileAs);
    @Query(value = "SELECT * FROM file_transfer_entity " +
            "WHERE user_id = :userId " +
            "AND mark_file_as = :status " +
            "AND deleted = false " +
            "ORDER BY created_at DESC " +
            "LIMIT :limit",
            nativeQuery = true)
    List<FileTransferEntity> findByUserIdAndMarkFileAsWithLimit(
            @Param("userId") UUID userId,
            @Param("status") String status,
            @Param("limit") int limit
    );
    @Query(value = "SELECT * FROM file_transfer_entity " +
            "WHERE user_id = :userId " +
            "AND mark_file_as = :status " +
            "AND deleted = false " +
            "ORDER BY created_at DESC",
            nativeQuery = true)
    List<FileTransferEntity> findByUserIdAndMarkFileAs(
            @Param("userId") UUID userId,
            @Param("status") String status
    );

}
