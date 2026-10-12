package pp.ua.avertooserver.push;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

public interface PushTokenRepository
        extends JpaRepository<PushToken, PushToken.PushTokenId> {

    /**
     * Додає токен або оновлює платформу й час останньої реєстрації.
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
     * Видаляє один токен конкретного користувача.
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
     * Видаляє токен з усіх облікових записів, де він зареєстрований.
     *
     * Використовується лише після підтвердженої помилки
     * DeviceNotRegistered від Expo.
     */
    @Transactional
    @Modifying
    @Query(value = """
            DELETE FROM device_push_tokens
            WHERE token = :token
            """, nativeQuery = true)
    int deleteAllByToken(@Param("token") String token);

    /**
     * Повертає всі токени користувача.
     */
    List<PushToken> findByUserId(UUID userId);
}