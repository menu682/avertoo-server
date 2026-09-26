package pp.ua.avertooserver.presence;

import org.springframework.web.socket.WebSocketSession;
import org.springframework.stereotype.Component;

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

    public void register(
            String sessionId,
            Long userId
    ) {
        sessionUsers.put(
                sessionId,
                userId
        );

        userSessions.put(
                userId,
                sessionId
        );

        lastHeartbeats.put(
                sessionId,
                Instant.now()
        );
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

    public Iterable<String> getSessionIds() {
        return sessions.keySet();
    }

}