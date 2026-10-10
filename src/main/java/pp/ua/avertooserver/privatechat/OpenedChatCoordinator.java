package pp.ua.avertooserver.privatechat;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;
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
     * Redis SET of chat ids currently opened by the SILENT
     * (background) sessions of a user.
     *
     * Silent sessions are independent from the single foreground
     * "opened chat" slot: a user may keep any number of silent chats
     * open at the same time without disturbing the foreground one.
     */
    private static final String SILENT_SET_KEY_PREFIX =
            "websocket:silent-chats:";

    /**
     * Redis key holding the WebSocket owner of a user within one
     * SILENT (background) opened chat.
     *
     * Kept apart from {@link #CHAT_KEY_PREFIX} so a silent session
     * never overwrites the foreground ownership record of the same
     * chat, and vice versa.
     */
    private static final String SILENT_CHAT_KEY_PREFIX =
            "websocket:silent-chat:";

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
         * opened this chat — in EITHER lane.
         *
         * A foreground open must rendezvous with a peer that
         * waits in a SILENT session (the normal "wake the
         * offline user" case) and vice versa, so the lookup
         * covers both lanes via getOwnerAnywhere.
         *
         * This check happens before registering the current
         * user, so "otherOwner != null" means that the other
         * user was already there.
         */
        String otherOwner =
                getOwnerAnywhere(
                        chatId,
                        otherUserId
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
     * Registers a chat as opened by a SILENT (background) session.
     *
     * <p>Silent sessions are unlimited and completely independent of
     * the single foreground "opened chat" slot: nothing is read from
     * or written to {@code websocket:opened-chat:{userId}}, so an
     * already-open foreground chat is never disturbed.</p>
     *
     * <p>The other participant is looked up across BOTH lanes, so a
     * silent session can rendezvous with a peer that has the chat
     * open in the foreground (the normal "wake the offline user"
     * case) and vice versa.</p>
     *
     * @return true if the other participant was already present,
     *         making the current session the WebRTC initiator.
     */
    public boolean openSilent(
            UUID chatId,
            Long userId,
            Long otherUserId,
            String sessionId
    ) {
        boolean otherPresent =
                getOwnerAnywhere(chatId, otherUserId) != null;

        String owner =
                serverId + ":" + sessionId;

        redisTemplate.opsForValue().set(
                buildSilentChatKey(chatId, userId),
                owner,
                CHAT_TTL
        );

        /*
         * The per-user set is what makes silent sessions unlimited:
         * one entry per chat instead of a single overwritten slot.
         */
        redisTemplate.opsForSet().add(
                buildSilentSetKey(userId),
                chatId.toString()
        );

        redisTemplate.expire(
                buildSilentSetKey(userId),
                CHAT_TTL
        );

        return otherPresent;
    }

    /**
     * Removes the silent opened-chat state of a user for one chat.
     *
     * <p>Leaves the foreground "opened chat" slot untouched.</p>
     *
     * <p>Only clears the record when this session still owns it,
     * so a concurrent silent session of the same user (e.g. a
     * second background wake for another message) survives the
     * teardown of the first one. Matches the ownership guard in
     * {@link #closeSilentForSession(Long, String)}.</p>
     */
    public void closeSilent(
            UUID chatId,
            Long userId,
            String sessionId
    ) {
        String expectedOwner =
                serverId + ":" + sessionId;

        String currentOwner =
                redisTemplate.opsForValue().get(
                        buildSilentChatKey(chatId, userId)
                );

        if (!expectedOwner.equals(currentOwner)) {
            return;
        }

        redisTemplate.delete(
                buildSilentChatKey(chatId, userId)
        );

        redisTemplate.opsForSet().remove(
                buildSilentSetKey(userId),
                chatId.toString()
        );
    }

    /**
     * Returns the WebSocket owner of a chat for a user, looking at
     * the foreground record first and the silent one second.
     *
     * <p>Used for peer-ready resolution: either lane is a valid
     * rendezvous target.</p>
     */
    public String getOwnerAnywhere(
            UUID chatId,
            Long userId
    ) {
        String owner =
                redisTemplate.opsForValue().get(
                        buildChatKey(chatId, userId)
                );

        if (owner != null) {
            return owner;
        }

        return redisTemplate.opsForValue().get(
                buildSilentChatKey(chatId, userId)
        );
    }

    /**
     * The silent owner of a chat for a user, if any.
     */
    public String getSilentOwner(
            UUID chatId,
            Long userId
    ) {
        return redisTemplate.opsForValue().get(
                buildSilentChatKey(chatId, userId)
        );
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

    /**
     * Removes every silent opened-chat record owned by one
     * WebSocket session.
     *
     * <p>Called when that session closes, so a silent connection
     * never leaks ownership records behind it. The foreground
     * "opened chat" slot is deliberately left alone.</p>
     */
    public void closeSilentForSession(
            Long userId,
            String sessionId
    ) {
        String silentSetKey =
                buildSilentSetKey(userId);

        Set<String> chatIds =
                redisTemplate.opsForSet()
                        .members(silentSetKey);

        if (chatIds == null) {
            return;
        }

        String expectedOwner =
                serverId + ":" + sessionId;

        for (String rawChatId : chatIds) {

            UUID chatId;

            try {
                chatId = UUID.fromString(rawChatId);
            } catch (IllegalArgumentException e) {
                /*
                 * Malformed stale entry — drop it from the set.
                 */
                redisTemplate.opsForSet()
                        .remove(silentSetKey, rawChatId);

                continue;
            }

            /*
             * Only clear records this session actually owns, so a
             * concurrent silent session of the same user survives.
             * The 3-arg closeSilent re-checks ownership atomically
             * enough for this cleanup path.
             */
            if (expectedOwner.equals(
                    redisTemplate.opsForValue().get(
                            buildSilentChatKey(chatId, userId)
                    )
            )) {
                closeSilent(chatId, userId, sessionId);
            }
        }
    }

    /**
     * Renews the TTL of the silent opened-chat records owned by a
     * session.
     *
     * <p>Silent connections send no {@code /app/heartbeat}, so this
     * is driven by the transport-level heartbeat monitor, which sees
     * every session.</p>
     */
    public boolean renewSilent(
            Long userId,
            String sessionId
    ) {
        String silentSetKey =
                buildSilentSetKey(userId);

        Set<String> chatIds =
                redisTemplate.opsForSet()
                        .members(silentSetKey);

        if (chatIds == null
                || chatIds.isEmpty()) {

            return false;
        }

        String expectedOwner =
                serverId + ":" + sessionId;

        boolean renewed = false;

        for (String rawChatId : chatIds) {

            UUID chatId;

            try {
                chatId = UUID.fromString(rawChatId);
            } catch (IllegalArgumentException e) {
                continue;
            }

            if (!expectedOwner.equals(
                    redisTemplate.opsForValue().get(
                            buildSilentChatKey(chatId, userId)
                    )
            )) {
                continue;
            }

            redisTemplate.expire(
                    buildSilentChatKey(chatId, userId),
                    CHAT_TTL
            );

            renewed = true;
        }

        if (renewed) {
            redisTemplate.expire(
                    silentSetKey,
                    CHAT_TTL
            );
        }

        return renewed;
    }

    private String buildSilentChatKey(
            UUID chatId,
            Long userId
    ) {
        return SILENT_CHAT_KEY_PREFIX
                + chatId
                + ":"
                + userId;
    }

    private String buildSilentSetKey(
            Long userId
    ) {
        return SILENT_SET_KEY_PREFIX
                + userId;
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