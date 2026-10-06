package pp.ua.avertooserver.privatechat;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import pp.ua.avertooserver.presence.WebSocketSessionRegistry;
import pp.ua.avertooserver.privatechat.dto.PrivateChatPeerReadyResponse;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Receives opened-chat events from Redis Pub/Sub.
 *
 * This listener is required because the two WebSocket sessions
 * may be connected to different application servers.
 *
 * Redis Pub/Sub delivers the event to the server that owns
 * the target WebSocket session, after which the event is sent
 * to that client through STOMP.
 */
@Component
@RequiredArgsConstructor
public class OpenedChatEventListener
        implements MessageListener {

    private final WebSocketSessionRegistry
            sessionRegistry;

    private final OpenedChatCoordinator
            openedChatCoordinator;

    private final SimpMessagingTemplate
            messagingTemplate;

    /**
     * Processes a peer-ready Redis event.
     *
     * Event format:
     *
     * chatId|targetUserId|serverId:sessionId|initiator
     */
    @Override
    public void onMessage(
            Message message,
            byte[] pattern
    ) {
        String event =
                new String(
                        message.getBody(),
                        StandardCharsets.UTF_8
                );

        String[] parts =
                event.split("\\|", 4);

        if (parts.length != 4) {
            return;
        }

        UUID chatId;
        Long userId;
        boolean initiator;

        try {
            chatId =
                    UUID.fromString(parts[0]);

            userId =
                    Long.valueOf(parts[1]);

            initiator =
                    Boolean.parseBoolean(parts[3]);

        } catch (IllegalArgumentException e) {
            return;
        }

        String otherOwner =
                parts[2];

        String[] ownerParts =
                otherOwner.split(":", 2);

        if (ownerParts.length != 2) {
            return;
        }

        String targetServerId =
                ownerParts[0];

        String targetSessionId =
                ownerParts[1];

        /*
         * Only the server that owns the target WebSocket
         * session should deliver this event.
         */
        if (!openedChatCoordinator
                .getServerId()
                .equals(targetServerId)) {

            return;
        }

        /*
         * The target session may already have been replaced
         * by another WebSocket connection.
         *
         * Never send an event to an old session.
         */
        if (!sessionRegistry.isCurrentSession(
                userId,
                targetSessionId
        )) {
            return;
        }

        PrivateChatPeerReadyResponse response =
                new PrivateChatPeerReadyResponse(
                        chatId,
                        initiator
                );

        SimpMessageHeaderAccessor headers =
                SimpMessageHeaderAccessor.create();

        headers.setSessionId(
                targetSessionId
        );

        headers.setLeaveMutable(true);

        messagingTemplate.convertAndSendToUser(
                targetSessionId,
                "/queue/private-chat",
                response,
                headers.getMessageHeaders()
        );
    }
}