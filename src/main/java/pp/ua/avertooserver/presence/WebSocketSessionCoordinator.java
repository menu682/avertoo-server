package pp.ua.avertooserver.presence;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class WebSocketSessionCoordinator {

    public static final String SESSION_CHANNEL =
            "websocket:session-events";

    private static final String OWNER_KEY_PREFIX =
            "websocket:owner:";

    private static final Duration OWNER_TTL =
            Duration.ofSeconds(60);

    private final RedisTemplate<String, String>
            redisTemplate;

    @Value("${websocket.server-id}")
    private String serverId;

    public void claim(
            Long userId,
            String sessionId
    ) {
        String key =
                OWNER_KEY_PREFIX + userId;

        String owner =
                serverId + ":" + sessionId;

        redisTemplate.opsForValue().set(
                key,
                owner,
                OWNER_TTL
        );

        redisTemplate.convertAndSend(
                SESSION_CHANNEL,
                userId + "|" + serverId
        );
    }

    public boolean renew(
            Long userId,
            String sessionId
    ) {
        String key =
                OWNER_KEY_PREFIX + userId;

        String expectedOwner =
                serverId + ":" + sessionId;

        String currentOwner =
                redisTemplate.opsForValue().get(key);

        if (!expectedOwner.equals(currentOwner)) {
            return false;
        }

        redisTemplate.expire(
                key,
                OWNER_TTL
        );

        return true;
    }

    public boolean release(
            Long userId,
            String sessionId
    ) {
        String key =
                OWNER_KEY_PREFIX + userId;

        String expectedOwner =
                serverId + ":" + sessionId;

        String currentOwner =
                redisTemplate.opsForValue().get(key);

        if (!expectedOwner.equals(currentOwner)) {
            return false;
        }

        redisTemplate.delete(key);

        return true;
    }

    public String getServerId() {
        return serverId;
    }
}