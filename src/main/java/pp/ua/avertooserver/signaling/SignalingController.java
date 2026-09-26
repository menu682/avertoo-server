package pp.ua.avertooserver.signaling;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import pp.ua.avertooserver.presence.WebSocketSessionRegistry;

import java.util.Map;

@Controller
@RequiredArgsConstructor
public class SignalingController {

    private final SimpMessagingTemplate messagingTemplate;
    private final WebSocketSessionRegistry sessionRegistry;

    @MessageMapping("/signaling")
    public void signaling(
            SignalingMessage message,
            SimpMessageHeaderAccessor headerAccessor
    ) {

        Map<String, Object> sessionAttributes =
                headerAccessor.getSessionAttributes();

        Long senderUserId =
                (Long) sessionAttributes.get("userId");

        Long targetUserId =
                message.targetUserId();

        String targetSessionId =
                sessionRegistry.getSessionId(targetUserId);

        if (targetSessionId == null) {
            return;
        }

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