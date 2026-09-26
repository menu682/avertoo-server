package pp.ua.avertooserver.security;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;
import pp.ua.avertooserver.presence.PresenceService;
import pp.ua.avertooserver.presence.WebSocketSessionRegistry;

@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor
        implements ChannelInterceptor {

    private static final String USER_ID_ATTRIBUTE =
            "userId";

    private final JwtService jwtService;
    private final WebSocketSessionRegistry sessionRegistry;
    private final PresenceService presenceService;

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
                    || !authorization.startsWith("Bearer ")) {

                throw new IllegalArgumentException(
                        "Missing Authorization header"
                );
            }

            String token =
                    authorization.substring(7);

            Long userId =
                    jwtService.extractUserId(token);

            accessor.getSessionAttributes()
                    .put(
                            USER_ID_ATTRIBUTE,
                            userId
                    );

            sessionRegistry.register(
                    accessor.getSessionId(),
                    userId
            );

            presenceService.heartbeat(userId);
        }

        if (StompCommand.SEND.equals(command)) {

            String destination =
                    accessor.getDestination();

            if ("/app/heartbeat".equals(destination)) {

                String sessionId =
                        accessor.getSessionId();

                Long userId =
                        sessionRegistry.getUserId(
                                sessionId
                        );

                if (userId != null) {

                    sessionRegistry.heartbeat(
                            sessionId
                    );

                    presenceService.heartbeat(
                            userId
                    );
                }
            }
        }

        if (StompCommand.DISCONNECT.equals(command)) {

            String sessionId =
                    accessor.getSessionId();

            Long userId =
                    sessionRegistry.getUserId(
                            sessionId
                    );

            if (userId != null) {
                presenceService.offline(userId);
            }

            sessionRegistry.unregister(
                    sessionId
            );
        }

        return message;
    }
}