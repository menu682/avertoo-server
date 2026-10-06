package pp.ua.avertooserver.privatechat;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;
import pp.ua.avertooserver.privatechat.dto.ChatClosedRequest;
import pp.ua.avertooserver.privatechat.dto.ChatOpenedRequest;

import java.util.Map;

/**
 * Handles opening and closing of a private chat on a client.
 *
 * Opening a chat is a temporary presence state.
 * It means that the client is currently viewing this chat
 * and is ready to establish a direct WebRTC connection.
 */
@Controller
@RequiredArgsConstructor
public class ChatPresenceController {

    private final OpenedChatCoordinator
            openedChatCoordinator;

    private final PrivateChatService
            privateChatService;

    /**
     * Registers that the current user has opened a chat.
     *
     * The user who opens the chat second becomes the
     * WebRTC initiator.
     */
    @MessageMapping("/private-chat/open")
    public void openChat(
            ChatOpenedRequest request,
            SimpMessageHeaderAccessor headerAccessor
    ) {
        Map<String, Object> sessionAttributes =
                headerAccessor.getSessionAttributes();

        if (sessionAttributes == null) {
            return;
        }

        Long userId =
                (Long) sessionAttributes.get("userId");

        String sessionId =
                headerAccessor.getSessionId();

        if (userId == null || sessionId == null) {
            return;
        }

        /*
         * Validate that the current user actually belongs
         * to this private chat and obtain the counterpart.
         */
        Long otherUserId =
                privateChatService.getOtherUserId(
                        request.chatId(),
                        userId
                );

        /*
         * Register this user's opened-chat state.
         *
         * The returned value tells us whether the other user
         * was already present.
         *
         * true  -> current user opened second -> initiator
         * false -> current user opened first  -> receiver
         */
        boolean initiator =
                openedChatCoordinator.open(
                        request.chatId(),
                        userId,
                        otherUserId,
                        sessionId
                );

        /*
         * Find the other participant's WebSocket session.
         */
        String otherOwner =
                openedChatCoordinator.getOwner(
                        request.chatId(),
                        otherUserId
                );

        /*
         * The other participant has not opened the chat yet.
         * Nothing more needs to happen.
         */
        if (otherOwner == null) {
            return;
        }

        /*
         * Verify that our own opened-chat record exists.
         */
        String currentOwner =
                openedChatCoordinator.getOwner(
                        request.chatId(),
                        userId
                );

        if (currentOwner == null) {
            return;
        }

        /*
         * Both participants are now present.
         *
         * Notify the current user with its calculated WebRTC
         * role.
         */
        openedChatCoordinator.peerReady(
                request.chatId(),
                userId,
                currentOwner,
                initiator
        );

        /*
         * Notify the other participant.
         *
         * The other participant is always the receiver here,
         * because the current user is the one who opened last.
         */
        openedChatCoordinator.peerReady(
                request.chatId(),
                otherUserId,
                otherOwner,
                false
        );
    }

    /**
     * Removes the current user's opened-chat state.
     *
     * Closing the chat also causes the WebRTC connection
     * on the client side to be closed.
     */
    @MessageMapping("/private-chat/close")
    public void closeChat(
            ChatClosedRequest request,
            SimpMessageHeaderAccessor headerAccessor
    ) {
        Map<String, Object> sessionAttributes =
                headerAccessor.getSessionAttributes();

        if (sessionAttributes == null) {
            return;
        }

        Long userId =
                (Long) sessionAttributes.get("userId");

        if (userId == null) {
            return;
        }

        /*
         * Validate access to the private chat before modifying
         * its opened-chat state.
         */
        privateChatService.getOtherUserId(
                request.chatId(),
                userId
        );

        openedChatCoordinator.close(
                request.chatId(),
                userId
        );
    }
}