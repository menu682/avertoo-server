package pp.ua.avertooserver.presence;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WebSocketSessionRegistry {

    private final Map<String, WebSocketSession> sessions =
            new ConcurrentHashMap<>();

    private final Map<String, Long> sessionUsers =
            new ConcurrentHashMap<>();

    private final Map<Long, String> userSessions =
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

    public synchronized void replace(
            String sessionId,
            Long userId
    ) {
        String oldSessionId =
                userSessions.put(
                        userId,
                        sessionId
                );

        sessionUsers.put(
                sessionId,
                userId
        );

        lastHeartbeats.put(
                sessionId,
                Instant.now()
        );

        if (oldSessionId == null
                || oldSessionId.equals(sessionId)) {

            return;
        }

        WebSocketSession oldSession =
                sessions.get(oldSessionId);

        if (oldSession != null
                && oldSession.isOpen()) {

            try {
                oldSession.close();
            } catch (Exception ignored) {
            }
        }
    }

    public Long getUserId(
            String sessionId
    ) {
        return sessionUsers.get(sessionId);
    }

    public String getSessionId(
            Long userId
    ) {
        return userSessions.get(userId);
    }

    public WebSocketSession getSession(
            String sessionId
    ) {
        return sessions.get(sessionId);
    }

    public boolean isCurrentSession(
            Long userId,
            String sessionId
    ) {
        return sessionId.equals(
                userSessions.get(userId)
        );
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
        }
    }

    public void closeUserSession(
            Long userId
    ) {
        String sessionId =
                userSessions.get(userId);

        if (sessionId == null) {
            return;
        }

        WebSocketSession session =
                sessions.get(sessionId);

        if (session == null) {
            return;
        }

        try {
            if (session.isOpen()) {
                session.close();
            }
        } catch (Exception ignored) {
        }
    }

    public Iterable<String> getSessionIds() {
        return sessions.keySet();
    }
}