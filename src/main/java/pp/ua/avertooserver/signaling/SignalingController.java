package pp.ua.avertooserver.signaling;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import pp.ua.avertooserver.presence.WebSocketSessionRegistry;
import pp.ua.avertooserver.privatechat.PrivateChatRepository;

import java.util.Map;

@Controller
@RequiredArgsConstructor
public class SignalingController {

    private final SimpMessagingTemplate messagingTemplate;
    private final WebSocketSessionRegistry sessionRegistry;
    private final PrivateChatRepository privateChatRepository;

    @MessageMapping("/signaling")
    public void signaling(
            SignalingMessage message,
            SimpMessageHeaderAccessor headerAccessor
    ) {

        Map<String, Object> sessionAttributes =
                headerAccessor.getSessionAttributes();

        if (sessionAttributes == null) {
            return;
        }

        Long senderUserId =
                (Long) sessionAttributes.get("userId");

        Long targetUserId =
                message.targetUserId();

        if (senderUserId == null
                || targetUserId == null
                || senderUserId.equals(targetUserId)) {
            return;
        }

        /*
         * Access check: signaling is allowed only between users
         * that share a private chat. Without it any authenticated
         * user could spam OFFER/ANSWER/ICE frames to any userId
         * and disturb its WebRTC negotiations.
         *
         * Any status (PENDING or ACTIVE) counts — the key/answer
         * exchange must already work while the chat request is
         * still pending.
         */
        if (privateChatRepository
                .findByUserIds(senderUserId, targetUserId)
                .isEmpty()) {
            return;
        }

        /*
         * Deliver to every live session of the target — its
         * foreground session and all of its background (silent)
         * ones. Each of them owns an independent WebRTC
         * negotiation, and only the one holding the matching chat
         * will act on the frame; the others ignore it.
         */
        for (String targetSessionId :
                sessionRegistry.getSessionIds(targetUserId)) {

            SignalingResponse response =
                    new SignalingResponse(
                            senderUserId,
                            message.type(),
                            message.payload()
                    );

            SimpMessageHeaderAccessor targetHeaders =
                    SimpMessageHeaderAccessor.create();

            targetHeaders.setSessionId(targetSessionId);
            targetHeaders.setLeaveMutable(true);

            messagingTemplate.convertAndSendToUser(
                    targetSessionId,
                    "/queue/signaling",
                    response,
                    targetHeaders.getMessageHeaders()
            );
        }
    }
}