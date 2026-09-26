package pp.ua.avertooserver.presence;

import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;

public class WebSocketSessionHandlerDecorator
        extends WebSocketHandlerDecorator {

    private final WebSocketSessionRegistry sessionRegistry;
    private final PresenceService presenceService;

    public WebSocketSessionHandlerDecorator(
            WebSocketHandler delegate,
            WebSocketSessionRegistry sessionRegistry,
            PresenceService presenceService
    ) {
        super(delegate);
        this.sessionRegistry = sessionRegistry;
        this.presenceService = presenceService;
    }

    @Override
    public void afterConnectionEstablished(
            WebSocketSession session
    ) throws Exception {

        sessionRegistry.registerSession(session);

        super.afterConnectionEstablished(session);
    }

    @Override
    public void afterConnectionClosed(
            WebSocketSession session,
            CloseStatus closeStatus
    ) throws Exception {

        String sessionId = session.getId();

        Long userId =
                sessionRegistry.getUserId(sessionId);

        if (userId != null) {
            presenceService.offline(userId);
        }

        sessionRegistry.unregister(sessionId);

        super.afterConnectionClosed(
                session,
                closeStatus
        );
    }
}