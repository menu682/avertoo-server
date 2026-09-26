package pp.ua.avertooserver.presence;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class PresenceService {

    private static final Duration PRESENCE_TTL =
            Duration.ofSeconds(60);

    private static final String KEY_PREFIX =
            "presence:user:";

    private final RedisTemplate<String, String> redisTemplate;

    public void heartbeat(Long userId) {

        String key =
                KEY_PREFIX + userId;

        redisTemplate.opsForValue().set(
                key,
                "ONLINE",
                PRESENCE_TTL
        );
    }

    public boolean isOnline(Long userId) {

        String key =
                KEY_PREFIX + userId;

        return Boolean.TRUE.equals(
                redisTemplate.hasKey(key)
        );
    }

    public void offline(Long userId) {

        String key =
                KEY_PREFIX + userId;

        redisTemplate.delete(key);
    }
}