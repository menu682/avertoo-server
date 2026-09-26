package pp.ua.avertooserver.presence;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.time.Duration;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class WebSocketHeartbeatMonitor {

    private final WebSocketSessionRegistry sessionRegistry;

    @Value("${websocket.heartbeat-timeout-ms}")
    private long heartbeatTimeoutMs;

    @Scheduled(
            fixedRateString =
                    "${websocket.heartbeat-check-interval-ms}"
    )
    public void checkHeartbeats() {

        Instant now = Instant.now();

        for (String sessionId :
                sessionRegistry.getSessionIds()) {

            Instant lastHeartbeat =
                    sessionRegistry.getLastHeartbeat(
                            sessionId
                    );

            if (lastHeartbeat == null) {
                continue;
            }

            long elapsed =
                    Duration.between(
                            lastHeartbeat,
                            now
                    ).toMillis();

            if (elapsed <= heartbeatTimeoutMs) {
                continue;
            }

            WebSocketSession session =
                    sessionRegistry.getSession(
                            sessionId
                    );

            if (session == null) {
                continue;
            }

            try {
                if (session.isOpen()) {
                    session.close(
                            CloseStatus.SESSION_NOT_RELIABLE
                    );
                }
            } catch (Exception ignored) {
            }
        }
    }
}