package pp.ua.avertooserver.privatechat;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * Coordinates information about currently opened private chats.
 *
 * Redis stores:
 *
 * websocket:opened-chat:{userId}
 *      -> chatId
 *
 * websocket:chat:{chatId}:{userId}
 *      -> serverId:sessionId
 *
 * The records have a short TTL because an opened chat is a
 * temporary state of an active WebSocket session.
 *
 * WebRTC initiator is not stored in Redis.
 * When a user opens a chat, the coordinator checks whether
 * the other participant has already opened the same chat.
 *
 * If the other participant is already present, the current
 * user is the last one who opened the chat and therefore
 * becomes the WebRTC initiator.
 */
@Service
@RequiredArgsConstructor
public class OpenedChatCoordinator {

    /**
     * Redis Pub/Sub channel used to notify the server that
     * a peer is ready for WebRTC negotiation.
     */
    public static final String CHAT_CHANNEL =
            "websocket:chat-events";

    /**
     * Redis key containing the WebSocket owner of a user
     * within a particular opened chat.
     */
    private static final String CHAT_KEY_PREFIX =
            "websocket:chat:";

    /**
     * Redis key containing the chat currently opened by a user.
     *
     * A client can have only one opened chat at a time.
     */
    private static final String CURRENT_CHAT_KEY_PREFIX =
            "websocket:opened-chat:";

    /**
     * TTL of opened-chat information.
     *
     * The WebSocket heartbeat renews this TTL while the
     * session remains active.
     */
    private static final Duration CHAT_TTL =
            Duration.ofSeconds(60);

    private final RedisTemplate<String, String>
            redisTemplate;

    @Value("${websocket.server-id}")
    private String serverId;

    /**
     * Registers the chat as opened by the specified user.
     *
     * The method also checks whether the other participant
     * has already opened the same chat.
     *
     * @return true if the other participant was already present.
     *
     *         In that case the current user is the last one
     *         who opened the chat and must become the WebRTC
     *         initiator.
     */
    public boolean open(
            UUID chatId,
            Long userId,
            Long otherUserId,
            String sessionId
    ) {
        String currentChatKey =
                buildCurrentChatKey(userId);

        /*
         * A user may have only one chat open.
         *
         * If another chat was previously opened, remove its
         * per-chat ownership record before switching to the
         * new chat.
         */
        String oldChatId =
                redisTemplate.opsForValue()
                        .get(currentChatKey);

        if (oldChatId != null
                && !oldChatId.equals(chatId.toString())) {

            try {
                redisTemplate.delete(
                        buildChatKey(
                                UUID.fromString(oldChatId),
                                userId
                        )
                );
            } catch (IllegalArgumentException ignored) {
                /*
                 * Ignore malformed stale Redis data.
                 * The current chat can still be opened normally.
                 */
            }
        }

        /*
         * Check whether the other participant has already
         * opened this chat.
         *
         * This check happens before registering the current
         * user, so "otherOwner != null" means that the other
         * user was already there.
         */
        String otherOwner =
                redisTemplate.opsForValue()
                        .get(
                                buildChatKey(
                                        chatId,
                                        otherUserId
                                )
                        );

        String owner =
                serverId + ":" + sessionId;

        /*
         * Register the current chat for this user.
         */
        redisTemplate.opsForValue().set(
                currentChatKey,
                chatId.toString(),
                CHAT_TTL
        );

        /*
         * Register the WebSocket session that owns this
         * opened-chat state.
         */
        redisTemplate.opsForValue().set(
                buildChatKey(chatId, userId),
                owner,
                CHAT_TTL
        );

        /*
         * If the other participant was already present,
         * this user is the second/last one to open the chat.
         *
         * Therefore this user becomes the WebRTC initiator.
         */
        return otherOwner != null;
    }

    /**
     * Removes the opened-chat state for a user.
     */
    public void close(
            UUID chatId,
            Long userId
    ) {
        redisTemplate.delete(
                buildChatKey(chatId, userId)
        );

        String currentChatKey =
                buildCurrentChatKey(userId);

        String currentChatId =
                redisTemplate.opsForValue()
                        .get(currentChatKey);

        /*
         * Do not remove the current-chat marker if it already
         * points to another chat.
         */
        if (chatId.toString().equals(currentChatId)) {
            redisTemplate.delete(currentChatKey);
        }
    }

    /**
     * Removes all opened-chat information for a user.
     *
     * This is used when the user's current WebSocket session
     * is closed.
     */
    public void closeAll(Long userId) {
        String currentChatKey =
                buildCurrentChatKey(userId);

        String currentChatId =
                redisTemplate.opsForValue()
                        .get(currentChatKey);

        if (currentChatId != null) {
            try {
                redisTemplate.delete(
                        buildChatKey(
                                UUID.fromString(currentChatId),
                                userId
                        )
                );
            } catch (IllegalArgumentException ignored) {
                /*
                 * Ignore malformed stale Redis data.
                 */
            }
        }

        redisTemplate.delete(currentChatKey);
    }

    /**
     * Returns the server/session that currently owns
     * the opened chat for the specified user.
     */
    public String getOwner(
            UUID chatId,
            Long userId
    ) {
        return redisTemplate.opsForValue().get(
                buildChatKey(chatId, userId)
        );
    }

    /**
     * Renews the TTL of the user's currently opened chat.
     *
     * Called by the WebSocket heartbeat monitor while the
     * WebSocket session is alive.
     */
    public boolean renew(
            Long userId,
            String sessionId
    ) {
        String currentChatId =
                redisTemplate.opsForValue()
                        .get(
                                buildCurrentChatKey(userId)
                        );

        if (currentChatId == null) {
            return false;
        }

        UUID chatId;

        try {
            chatId =
                    UUID.fromString(currentChatId);
        } catch (IllegalArgumentException e) {
            return false;
        }

        return renew(
                chatId,
                userId,
                sessionId
        );
    }

    /**
     * Renews the TTL of a specific opened-chat ownership
     * record if this server/session is still its owner.
     */
    public boolean renew(
            UUID chatId,
            Long userId,
            String sessionId
    ) {
        String key =
                buildChatKey(chatId, userId);

        String expectedOwner =
                serverId + ":" + sessionId;

        String currentOwner =
                redisTemplate.opsForValue().get(key);

        /*
         * Do not renew a record that has already been taken
         * over by another WebSocket session/server.
         */
        if (!expectedOwner.equals(currentOwner)) {
            return false;
        }

        redisTemplate.expire(
                key,
                CHAT_TTL
        );

        redisTemplate.expire(
                buildCurrentChatKey(userId),
                CHAT_TTL
        );

        return true;
    }

    /**
     * Publishes a peer-ready event through Redis Pub/Sub.
     *
     * The event contains the target user and the WebRTC role.
     */
    public void peerReady(
            UUID chatId,
            Long userId,
            String targetOwner,
            boolean initiator
    ) {
        redisTemplate.convertAndSend(
                CHAT_CHANNEL,
                chatId
                        + "|"
                        + userId
                        + "|"
                        + targetOwner
                        + "|"
                        + initiator
        );
    }

    public String getServerId() {
        return serverId;
    }

    private String buildChatKey(
            UUID chatId,
            Long userId
    ) {
        return CHAT_KEY_PREFIX
                + chatId
                + ":"
                + userId;
    }

    private String buildCurrentChatKey(
            Long userId
    ) {
        return CURRENT_CHAT_KEY_PREFIX
                + userId;
    }
}