package pp.ua.avertooserver.presence;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of the live WebSocket sessions of authenticated users.
 *
 * <p>A user owns two independent kinds of sessions:</p>
 *
 * <ul>
 *   <li><b>Foreground</b> — at most ONE per user. A new foreground
 *       session replaces (and closes) the previous one. This is the
 *       single-session enforcement used for presence.</li>
 *   <li><b>Silent</b> (background wake-up) — ANY NUMBER per user.
 *       Silent sessions never take over, and are never taken over
 *       by, a foreground session. They exist only to negotiate one
 *       additional WebRTC connection and are torn down by the client
 *       as soon as the background messages have been exchanged.</li>
 * </ul>
 *
 * <p>Keeping the two kinds in separate maps is what allows a silent
 * session to be established while a foreground chat is already open,
 * without disturbing it.</p>
 */
@Component
public class WebSocketSessionRegistry {

    private final Map<String, WebSocketSession> sessions =
            new ConcurrentHashMap<>();

    private final Map<String, Long> sessionUsers =
            new ConcurrentHashMap<>();

    /** userId -> its single foreground sessionId. */
    private final Map<Long, String> userSessions =
            new ConcurrentHashMap<>();

    /** userId -> every silent (background) sessionId of that user. */
    private final Map<Long, Set<String>> userSilentSessions =
            new ConcurrentHashMap<>();

    private final Map<String, Instant> lastHeartbeats =
            new ConcurrentHashMap<>();

    public void registerSession(
            WebSocketSession session
    ) {
        sessions.put(
                session.getId(),
                session
        );

        lastHeartbeats.put(
                session.getId(),
                Instant.now()
        );
    }

    /**
     * Registers a FOREGROUND session.
     *
     * <p>Closes the user's previous foreground session, if any.
     * Silent sessions are deliberately left untouched — they are
     * independent of the foreground one.</p>
     */
    public synchronized void replace(
            String sessionId,
            Long userId
    ) {
        sessionUsers.put(
                sessionId,
                userId
        );

        lastHeartbeats.put(
                sessionId,
                Instant.now()
        );

        String oldSessionId =
                userSessions.put(
                        userId,
                        sessionId
                );

        if (oldSessionId == null
                || oldSessionId.equals(sessionId)) {

            return;
        }

        closeQuietly(
                sessions.get(oldSessionId)
        );
    }

    /**
     * Registers a SILENT (background) session.
     *
     * <p>Unlimited per user and completely independent from the
     * foreground session: nothing is closed and no foreground
     * mapping is modified.</p>
     */
    public synchronized void registerSilent(
            String sessionId,
            Long userId
    ) {
        sessionUsers.put(
                sessionId,
                userId
        );

        lastHeartbeats.put(
                sessionId,
                Instant.now()
        );

        userSilentSessions
                .computeIfAbsent(
                        userId,
                        key -> ConcurrentHashMap.newKeySet()
                )
                .add(sessionId);
    }

    public Long getUserId(
            String sessionId
    ) {
        return sessionUsers.get(sessionId);
    }

    /**
     * The user's single foreground session, if any.
     */
    public String getSessionId(
            Long userId
    ) {
        return userSessions.get(userId);
    }

    /**
     * The raw WebSocket session, when it is still registered.
     */
    public WebSocketSession getSession(
            String sessionId
    ) {
        return sessions.get(sessionId);
    }

    /**
     * Every live session of the user — the foreground one plus all
     * of its silent ones.
     *
     * <p>Signaling and chat events must reach all of them, because
     * a silent session owns its own WebRTC negotiation.</p>
     */
    public Set<String> getSessionIds(
            Long userId
    ) {
        Set<String> ids = new HashSet<>();

        String foreground =
                userSessions.get(userId);

        if (foreground != null) {
            ids.add(foreground);
        }

        Set<String> silent =
                userSilentSessions.get(userId);

        if (silent != null) {
            ids.addAll(silent);
        }

        return ids;
    }

    /**
     * Whether the session was established as a background (silent)
     * wake-up session.
     */
    public boolean isSilent(
            String sessionId
    ) {
        Long userId =
                sessionUsers.get(sessionId);

        if (userId == null) {
            return false;
        }

        Set<String> silent =
                userSilentSessions.get(userId);

        return silent != null
                && silent.contains(sessionId);
    }

    /**
     * Whether the session is one of the user's live sessions.
     *
     * <p>True for the foreground session and for any silent session,
     * so a peer-ready event may legitimately target a silent one.</p>
     */
    public boolean isCurrentSession(
            Long userId,
            String sessionId
    ) {
        if (sessionId == null) {
            return false;
        }

        if (sessionId.equals(
                userSessions.get(userId)
        )) {
            return true;
        }

        Set<String> silent =
                userSilentSessions.get(userId);

        return silent != null
                && silent.contains(sessionId);
    }

    public void heartbeat(
            String sessionId
    ) {
        lastHeartbeats.put(
                sessionId,
                Instant.now()
        );
    }

    public Instant getLastHeartbeat(
            String sessionId
    ) {
        return lastHeartbeats.get(sessionId);
    }

    public void unregister(
            String sessionId
    ) {
        sessions.remove(sessionId);

        lastHeartbeats.remove(sessionId);

        Long userId =
                sessionUsers.remove(sessionId);

        if (userId != null) {
            userSessions.remove(
                    userId,
                    sessionId
            );

            Set<String> silent =
                    userSilentSessions.get(userId);

            if (silent != null) {
                silent.remove(sessionId);

                if (silent.isEmpty()) {
                    userSilentSessions.remove(userId);
                }
            }
        }
    }

    /**
     * Closes the user's FOREGROUND session on this server.
     *
     * <p>Invoked when another server claims the user through a new
     * foreground connection. Silent sessions survive on purpose —
     * they are short-lived and self-terminating.</p>
     */
    public void closeUserSession(
            Long userId
    ) {
        String sessionId =
                userSessions.get(userId);

        if (sessionId == null) {
            return;
        }

        closeQuietly(
                sessions.get(sessionId)
        );
    }

    public Iterable<String> getSessionIds() {
        return sessions.keySet();
    }

    private void closeQuietly(
            WebSocketSession session
    ) {
        if (session == null
                || !session.isOpen()) {

            return;
        }

        try {
            session.close();
        } catch (Exception ignored) {
            /* The socket is going away anyway. */
        }
    }
}