package pp.ua.avertooserver.presence;

import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;

public class WebSocketSessionHandlerDecorator
        extends WebSocketHandlerDecorator {

    private final WebSocketSessionRegistry sessionRegistry;

    private final PresenceService presenceService;

    private final WebSocketSessionCoordinator
            sessionCoordinator;

    public WebSocketSessionHandlerDecorator(
            WebSocketHandler delegate,
            WebSocketSessionRegistry sessionRegistry,
            PresenceService presenceService,
            WebSocketSessionCoordinator sessionCoordinator
    ) {
        super(delegate);

        this.sessionRegistry =
                sessionRegistry;

        this.presenceService =
                presenceService;

        this.sessionCoordinator =
                sessionCoordinator;
    }

    @Override
    public void afterConnectionEstablished(
            WebSocketSession session
    ) throws Exception {

        sessionRegistry.registerSession(
                session
        );

        super.afterConnectionEstablished(
                session
        );
    }

    @Override
    public void afterConnectionClosed(
            WebSocketSession session,
            CloseStatus closeStatus
    ) throws Exception {

        String sessionId =
                session.getId();

        Long userId =
                sessionRegistry.getUserId(
                        sessionId
                );

        if (userId != null
                && sessionRegistry.isCurrentSession(
                userId,
                sessionId
        )) {

            boolean released =
                    sessionCoordinator.release(
                            userId,
                            sessionId
                    );

            if (released) {
                presenceService.offline(
                        userId
                );
            }
        }

        sessionRegistry.unregister(
                sessionId
        );

        super.afterConnectionClosed(
                session,
                closeStatus
        );
    }
}