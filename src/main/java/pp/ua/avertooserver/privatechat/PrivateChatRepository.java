package pp.ua.avertooserver.privatechat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PrivateChatRepository extends JpaRepository<PrivateChat, UUID> {

    @Query("""
            SELECT pc
            FROM PrivateChat pc
            WHERE (
            	pc.user1.userId = :userId1
            	AND pc.user2.userId = :userId2
            )
            OR (
            	pc.user1.userId = :userId2
            	AND pc.user2.userId = :userId1
            )
            """)
    Optional<PrivateChat> findByUserIds(
            @Param("userId1") Long userId1,
            @Param("userId2") Long userId2
    );

    @Query("""
            SELECT pc
            FROM PrivateChat pc
            WHERE pc.status = 'ACTIVE'
            AND (
            	pc.user1.userId = :userId
            	OR pc.user2.userId = :userId
            )
            """)
    List<PrivateChat> findActiveChatsByUserId(
            @Param("userId") Long userId
    );
}