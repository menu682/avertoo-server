package pp.ua.avertooserver.presence;

import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import pp.ua.avertooserver.privatechat.OpenedChatCoordinator;

public class WebSocketSessionHandlerDecorator
        extends WebSocketHandlerDecorator {

    private final WebSocketSessionRegistry sessionRegistry;

    private final PresenceService presenceService;

    private final WebSocketSessionCoordinator
            sessionCoordinator;

    private final OpenedChatCoordinator
            openedChatCoordinator;

    public WebSocketSessionHandlerDecorator(
            WebSocketHandler delegate,
            WebSocketSessionRegistry sessionRegistry,
            PresenceService presenceService,
            WebSocketSessionCoordinator sessionCoordinator,
            OpenedChatCoordinator openedChatCoordinator
    ) {
        super(delegate);

        this.sessionRegistry =
                sessionRegistry;

        this.presenceService =
                presenceService;

        this.sessionCoordinator =
                sessionCoordinator;

        this.openedChatCoordinator =
                openedChatCoordinator;
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
    public void handleMessage(
            WebSocketSession session,
            WebSocketMessage<?> message
    ) throws Exception {

        System.out.println(
                "[WS_RAW] SESSION="
                        + session.getId()
                        + "; TYPE="
                        + message.getClass().getSimpleName()
                        + "; PAYLOAD="
                        + message.getPayload()
        );

        sessionRegistry.heartbeat(
                session.getId()
        );

        super.handleMessage(
                session,
                message
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

        if (userId != null) {

            /*
             * A background (silent) session owns only its own
             * silent chats. It must never clear the foreground
             * "opened chat" slot, nor release presence, nor take
             * the user offline — it was never the owner of any of
             * those.
             */
            if (sessionRegistry.isSilent(sessionId)) {

                openedChatCoordinator.closeSilentForSession(
                        userId,
                        sessionId
                );
            } else if (sessionRegistry.isCurrentSession(
                    userId,
                    sessionId
            )) {

                openedChatCoordinator.closeAll(
                        userId
                );

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