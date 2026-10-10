package pp.ua.avertooserver.push;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PushTokenRepository
        extends JpaRepository<PushToken, PushToken.PushTokenId> {

    /**
     * Inserts a device token or refreshes it when it already
     * exists for the user (upsert).
     */
    @Modifying
    @Query(value = """
            INSERT INTO device_push_tokens
                (user_id, token, platform, last_seen_at)
            VALUES (:userId, :token, :platform, NOW())
            ON CONFLICT (user_id, token)
            DO UPDATE SET
                platform = EXCLUDED.platform,
                last_seen_at = NOW()
            """, nativeQuery = true)
    void upsert(
            @Param("userId") UUID userId,
            @Param("token") String token,
            @Param("platform") String platform
    );

    /**
     * Removes a single device token of a user.
     */
    @Modifying
    @Query(value = """
            DELETE FROM device_push_tokens
            WHERE user_id = :userId AND token = :token
            """, nativeQuery = true)
    int deleteByUserIdAndToken(
            @Param("userId") UUID userId,
            @Param("token") String token
    );

    /**
     * Returns every device token registered by the user.
     */
    List<PushToken> findByUserId(UUID userId);
}
