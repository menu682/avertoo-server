package pp.ua.avertooserver.privatechat;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import pp.ua.avertooserver.presence.WebSocketSessionRegistry;
import pp.ua.avertooserver.privatechat.dto.*;

import java.util.Map;

@Controller
@RequiredArgsConstructor
public class PrivateChatController {

    private final PrivateChatService privateChatService;
    private final SimpMessagingTemplate messagingTemplate;
    private final WebSocketSessionRegistry sessionRegistry;


    @MessageMapping("/private-chat/create")
    public void createChat(
            PrivateChatCreateRequest request,
            SimpMessageHeaderAccessor headerAccessor
    ) {
        Map<String, Object> sessionAttributes =
                headerAccessor.getSessionAttributes();

        assert sessionAttributes != null;
        Long userId =
                (Long) sessionAttributes.get("userId");

        PrivateChatCreateResponse response =
                privateChatService.createChat(
                        userId,
                        request.targetUserId()
                );

        sendToSession(
                headerAccessor.getSessionId(),
                response
        );

        if (response.type() ==
                PrivateChatCreateResult.CHAT_ACTIVATED) {

            PrivateChatEventResponse event =
                    new PrivateChatEventResponse(
                            response.chatId(),
                            PrivateChatEventType.CHAT_ACTIVATED
                    );

            sendToUser(
                    request.targetUserId(),
                    event
            );
        }
    }

    @MessageMapping("/private-chat/delete")
    public void deleteChat(
            PrivateChatDeleteRequest request,
            SimpMessageHeaderAccessor headerAccessor
    ) {
        Map<String, Object> sessionAttributes =
                headerAccessor.getSessionAttributes();

        assert sessionAttributes != null;
        Long userId =
                (Long) sessionAttributes.get("userId");

        privateChatService.deleteChat(
                request.chatId(),
                userId
        );
    }

    private void sendToUser(
            Long userId,
            PrivateChatEventResponse response
    ) {
        /*
         * Fan out to every live session of the user so a chat
         * activated while it is only connected in the background
         * is still observed.
         */
        for (String targetSessionId :
                sessionRegistry.getSessionIds(userId)) {

            sendToSession(
                    targetSessionId,
                    response
            );
        }
    }

    private void sendToSession(
            String sessionId,
            Object response
    ) {
        if (sessionId == null) {
            return;
        }

        SimpMessageHeaderAccessor targetHeaders =
                SimpMessageHeaderAccessor.create();

        targetHeaders.setSessionId(sessionId);
        targetHeaders.setLeaveMutable(true);

        messagingTemplate.convertAndSendToUser(
                sessionId,
                "/queue/private-chat",
                response,
                targetHeaders.getMessageHeaders()
        );
    }
}