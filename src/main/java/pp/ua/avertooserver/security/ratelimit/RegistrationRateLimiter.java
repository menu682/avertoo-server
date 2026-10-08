package pp.ua.avertooserver.security.ratelimit;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Rate limiter for user registration requests.
 *
 * <p>The limiter uses the client's IP address as the rate-limit key.
 * This provides an additional protection layer against automated
 * registration attempts. CAPTCHA verification remains a separate
 * protection mechanism.</p>
 *
 * <p>The counter is stored in Redis and automatically expires after
 * the configured time window.</p>
 */
@Component
@RequiredArgsConstructor
public class RegistrationRateLimiter {

    private static final String KEY_PREFIX =
            "rate-limit:registration:";

    private static final String RATE_LIMIT_SCRIPT = """
            local current = redis.call('INCR', KEYS[1])

            if current == 1 then
                redis.call('EXPIRE', KEYS[1], ARGV[1])
            end

            if current <= tonumber(ARGV[2]) then
                return 1
            end

            return 0
            """;

    private final RedisTemplate<String, String> redisTemplate;

    @Value("${avertoo.security.rate-limit.registration.max-attempts}")
    private int maxAttempts;

    @Value("${avertoo.security.rate-limit.registration.window-seconds}")
    private long windowSeconds;

    private final DefaultRedisScript<Long> rateLimitScript =
            new DefaultRedisScript<>(
                    RATE_LIMIT_SCRIPT,
                    Long.class
            );

    /**
     * Checks whether another registration attempt from the given
     * IP address is currently allowed.
     *
     * @param clientIp client IP address
     * @return {@code true} when the attempt is allowed
     */
    public boolean isAllowed(String clientIp) {

        String key = KEY_PREFIX + clientIp;

        Long result = redisTemplate.execute(
                rateLimitScript,
                List.of(key),
                String.valueOf(windowSeconds),
                String.valueOf(maxAttempts)
        );

        return Long.valueOf(1L).equals(result);
    }
}