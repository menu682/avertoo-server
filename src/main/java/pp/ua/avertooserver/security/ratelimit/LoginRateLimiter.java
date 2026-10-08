package pp.ua.avertooserver.security.ratelimit;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Rate limiter for user login attempts.
 *
 * <p>The limiter tracks failed login attempts for each user
 * in Redis. Successful authentication attempts do not increase
 * the counter.</p>
 *
 * <p>The counter automatically expires after the configured
 * time window.</p>
 */
@Component
@RequiredArgsConstructor
public class LoginRateLimiter {

    private static final String KEY_PREFIX =
            "rate-limit:login:";

    private static final String INCREMENT_SCRIPT = """
            local current = redis.call('INCR', KEYS[1])

            if current == 1 then
                redis.call('EXPIRE', KEYS[1], ARGV[1])
            end

            return current
            """;

    private static final String GET_COUNT_SCRIPT = """
            local current = redis.call('GET', KEYS[1])

            if current == false then
                return 0
            end

            return tonumber(current)
            """;

    private final RedisTemplate<String, String> redisTemplate;

    @Value("${avertoo.security.rate-limit.login.max-attempts}")
    private int maxAttempts;

    @Value("${avertoo.security.rate-limit.login.window-seconds}")
    private long windowSeconds;

    private final DefaultRedisScript<Long> incrementScript =
            new DefaultRedisScript<>(
                    INCREMENT_SCRIPT,
                    Long.class
            );

    private final DefaultRedisScript<Long> getCountScript =
            new DefaultRedisScript<>(
                    GET_COUNT_SCRIPT,
                    Long.class
            );

    /**
     * Checks whether another login attempt for the given user
     * is currently allowed.
     *
     * <p>This method does not modify the current attempt counter.</p>
     *
     * @param userId public user identifier
     * @return {@code true} when another attempt is allowed
     */
    public boolean isAllowed(Long userId) {

        String key = buildKey(userId);

        Long currentAttempts = redisTemplate.execute(
                getCountScript,
                List.of(key)
        );

        return currentAttempts == null
                || currentAttempts < maxAttempts;
    }

    /**
     * Records a failed login attempt for the given user.
     *
     * <p>The expiration timer starts with the first failed attempt.
     * Subsequent failed attempts within the same window do not
     * reset the timer.</p>
     *
     * @param userId public user identifier
     */
    public void recordFailedAttempt(Long userId) {

        String key = buildKey(userId);

        redisTemplate.execute(
                incrementScript,
                List.of(key),
                String.valueOf(windowSeconds)
        );
    }

    /**
     * Builds the Redis key used for the user's login rate limit.
     *
     * @param userId public user identifier
     * @return Redis key
     */
    private String buildKey(Long userId) {

        return KEY_PREFIX + userId;
    }
}