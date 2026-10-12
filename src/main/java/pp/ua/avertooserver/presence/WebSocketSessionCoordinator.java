package pp.ua.avertooserver.presence;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;

@Service
@RequiredArgsConstructor
public class WebSocketSessionCoordinator {

    public static final String SESSION_CHANNEL =
            "websocket:session-events";

    private static final String OWNER_KEY_PREFIX =
            "websocket:owner:";

    private static final Duration OWNER_TTL =
            Duration.ofSeconds(60);

    /**
     * Lua-скрипт атомарно перевіряє власника ключа
     * та продовжує TTL тільки для очікуваної сесії.
     *
     * Перевірка GET і операція EXPIRE виконуються Redis
     * як одна неподільна операція.
     *
     * Повертає:
     * 1 — TTL успішно продовжено;
     * 0 — ключ відсутній або власник уже змінився.
     */
    private static final DefaultRedisScript<Long> RENEW_SCRIPT =
            createScript("""
                    if redis.call('GET', KEYS[1]) == ARGV[1] then
                        return redis.call('EXPIRE', KEYS[1], ARGV[2])
                    end
                    return 0
                    """);

    /**
     * Lua-скрипт атомарно перевіряє власника ключа
     * та видаляє ключ тільки для очікуваної сесії.
     *
     * Стара сесія не зможе видалити призначення,
     * яке вже належить новій сесії.
     *
     * Повертає:
     * 1 — ключ успішно видалено;
     * 0 — ключ відсутній або власник змінився.
     */
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT =
            createScript("""
                    if redis.call('GET', KEYS[1]) == ARGV[1] then
                        return redis.call('DEL', KEYS[1])
                    end
                    return 0
                    """);

    private final RedisTemplate<String, String> redisTemplate;

    @Value("${websocket.server-id}")
    private String serverId;

    /**
     * Призначає користувачу поточну WebSocket-сесію.
     *
     * Запис власника разом із TTL виконується однією
     * атомарною операцією Redis.
     * Після запису повідомляємо інші екземпляри сервера.
     */
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

    /**
     * Продовжує TTL, тільки якщо ця сесія
     * все ще є власником ключа.
     *
     * Перевірка власника і продовження TTL атомарні.
     */
    public boolean renew(
            Long userId,
            String sessionId
    ) {
        String key =
                OWNER_KEY_PREFIX + userId;

        String expectedOwner =
                serverId + ":" + sessionId;

        Long result = redisTemplate.execute(
                RENEW_SCRIPT,
                Collections.singletonList(key),
                expectedOwner,
                String.valueOf(OWNER_TTL.toSeconds())
        );

        return Long.valueOf(1L).equals(result);
    }

    /**
     * Видаляє призначення власника, тільки якщо ключ
     * усе ще належить цій сесії.
     *
     * Перевірка власника і видалення атомарні.
     * Якщо нова сесія вже виконала claim(), цей виклик
     * не видалить її призначення.
     */
    public boolean release(
            Long userId,
            String sessionId
    ) {
        String key =
                OWNER_KEY_PREFIX + userId;

        String expectedOwner =
                serverId + ":" + sessionId;

        Long result = redisTemplate.execute(
                RELEASE_SCRIPT,
                Collections.singletonList(key),
                expectedOwner
        );

        return Long.valueOf(1L).equals(result);
    }

    /**
     * Повертає ідентифікатор цього екземпляра сервера.
     */
    public String getServerId() {
        return serverId;
    }

    /**
     * Створює Lua-скрипт, який повертає числовий результат.
     */
    private static DefaultRedisScript<Long> createScript(
            String scriptText
    ) {
        DefaultRedisScript<Long> script =
                new DefaultRedisScript<>();

        script.setScriptText(scriptText);
        script.setResultType(Long.class);

        return script;
    }
}
