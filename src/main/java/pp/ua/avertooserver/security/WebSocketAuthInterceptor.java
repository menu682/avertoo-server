package pp.ua.avertooserver.security;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;
import pp.ua.avertooserver.presence.PresenceService;
import pp.ua.avertooserver.presence.WebSocketSessionCoordinator;
import pp.ua.avertooserver.presence.WebSocketSessionRegistry;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor
        implements ChannelInterceptor {

    private static final String USER_ID_ATTRIBUTE =
            "userId";

    /**
     * Native STOMP header marking a connection as a background
     * (silent) wake-up session. Such a session establishes only
     * WebRTC signaling and must NOT turn the user "online".
     */
    private static final String SILENT_MODE_HEADER =
            "X-Avertoo-Mode";

    private static final String SILENT_MODE_VALUE =
            "background";

    private static final String SILENT_ATTRIBUTE =
            "silent";

    private final JwtService jwtService;

    private final WebSocketSessionRegistry
            sessionRegistry;

    private final PresenceService
            presenceService;

    private final WebSocketSessionCoordinator
            sessionCoordinator;

    @Override
    public Message<?> preSend(
            Message<?> message,
            MessageChannel channel
    ) {
        StompHeaderAccessor accessor =
                StompHeaderAccessor.wrap(message);

        StompCommand command =
                accessor.getCommand();

        if (StompCommand.CONNECT.equals(command)) {

            String authorization =
                    accessor.getFirstNativeHeader(
                            "Authorization"
                    );

            if (authorization == null
                    || !authorization.startsWith(
                    "Bearer "
            )) {

                throw new IllegalArgumentException(
                        "Missing Authorization header"
                );
            }

            String token =
                    authorization.substring(7);

            Long userId =
                    jwtService.extractUserId(
                            token
                    );

            String sessionId =
                    accessor.getSessionId();

            accessor.getSessionAttributes()
                    .put(
                            USER_ID_ATTRIBUTE,
                            userId
                    );

            /*
             * Bind the STOMP Principal to this exact WebSocket
             * session. All user-destination sends in this codebase
             * address a single session (convertAndSendToUser with
             * the sessionId + simpSessionId header), so the
             * Principal name must BE the sessionId — otherwise the
             * broker resolves a "user" named <sessionId> that does
             * not exist and the frame is silently dropped.
             *
             * Done for BOTH lanes (foreground and silent) before
             * the branch below, so signaling and peer-ready reach
             * every live session of the user.
             */
            accessor.setUser(
                    (java.security.Principal) () -> sessionId
            );

            String connectionMode =
                    accessor.getFirstNativeHeader(
                            SILENT_MODE_HEADER
                    );

            /*
             * Background/silent connections (used to receive
             * messages without showing the user as online) must
             * not refresh presence — neither on CONNECT nor on
             * any subsequent /app/heartbeat.
             *
             * They also bypass the single-session enforcement
             * entirely: a silent session is registered alongside
             * the foreground one (any number of them may coexist),
             * so establishing one never closes the chat the user
             * is currently viewing.
             *
             * Mark the session as silent so the heartbeat branch
             * below can skip presence renewal for it.
             */
            if (SILENT_MODE_VALUE.equals(connectionMode)) {

                sessionRegistry.registerSilent(
                        sessionId,
                        userId
                );

                accessor.getSessionAttributes()
                        .put(
                                SILENT_ATTRIBUTE,
                                Boolean.TRUE
                        );

                return message;
            }

            /*
             * Foreground sessions participate in single-session
             * enforcement: claim ownership so any duplicate session
             * of this user on another server is closed.
             */
            sessionRegistry.replace(
                    sessionId,
                    userId
            );

            sessionCoordinator.claim(
                    userId,
                    sessionId
            );

            presenceService.heartbeat(
                    userId
            );
        }

        if (StompCommand.SEND.equals(command)) {

            String destination =
                    accessor.getDestination();

            if ("/app/heartbeat".equals(
                    destination
            )) {

                String sessionId =
                        accessor.getSessionId();

                Long userId =
                        sessionRegistry.getUserId(
                                sessionId
                        );

                if (userId != null) {

                    /*
                     * Keep presence for foreground sessions only.
                     * A silent (background) session must not renew
                     * the online state.
                     */
                    Map<String, Object> attrs =
                            accessor.getSessionAttributes();

                    boolean silent =
                            attrs != null
                            && Boolean.TRUE.equals(
                                    attrs.get(
                                            SILENT_ATTRIBUTE
                                    )
                            );

                    /*
                     * A silent session connects quietly: it only keeps
                     * its WebSocket registry heartbeat so the connection
                     * stays open, but it neither renews single-session
                     * ownership (must not take over a foreground session
                     * nor be taken over) nor refreshes presence.
                     */
                    if (silent) {

                        sessionRegistry.heartbeat(
                                sessionId
                        );

                        return message;
                    }

                    boolean owner =
                            sessionCoordinator.renew(
                                    userId,
                                    sessionId
                            );

                    if (!owner) {

                        WebSocketSession session =
                                sessionRegistry.getSession(
                                        sessionId
                                );

                        if (session != null
                                && session.isOpen()) {

                            try {
                                session.close();
                            } catch (Exception ignored) {
                            }
                        }

                        return message;
                    }

                    sessionRegistry.heartbeat(
                            sessionId
                    );

                    presenceService.heartbeat(
                            userId
                    );
                }
            }
        }

//        if (StompCommand.DISCONNECT.equals(command)) {
//
//            String sessionId =
//                    accessor.getSessionId();
//
//            Long userId =
//                    sessionRegistry.getUserId(
//                            sessionId
//                    );
//
//            if (userId != null
//                    && sessionRegistry.isCurrentSession(
//                    userId,
//                    sessionId
//            )) {
//
//                boolean released =
//                        sessionCoordinator.release(
//                                userId,
//                                sessionId
//                        );
//
//                if (released) {
//                    presenceService.offline(
//                            userId
//                    );
//                }
//            }
//
//            sessionRegistry.unregister(
//                    sessionId
//            );
//        }

        return message;
    }
}