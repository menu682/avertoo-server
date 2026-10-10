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

    /**
     * Native session attribute marking a background (silent)
     * connection. Mirrors the flag set by the auth interceptor.
     */
    private static final String SILENT_ATTRIBUTE =
            "silent";

    private final OpenedChatCoordinator
            openedChatCoordinator;

    private final PrivateChatService
            privateChatService;

    /**
     * Registers that the current user has opened a chat.
     *
     * <p>Foreground and silent sessions use two independent lanes.
     * A foreground open keeps the "one open chat at a time" rule;
     * a silent open is unlimited and never evicts it. The user who
     * opens the chat second becomes the WebRTC initiator.</p>
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

        boolean silent =
                isSilent(sessionAttributes);

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
         * Register this user's opened-chat state in the lane that
         * matches the connection.
         *
         * The returned value tells us whether the other user
         * was already present.
         *
         * true  -> current user opened second -> initiator
         * false -> current user opened first  -> receiver
         */
        boolean initiator = silent
                ? openedChatCoordinator.openSilent(
                        request.chatId(),
                        userId,
                        otherUserId,
                        sessionId
                )
                : openedChatCoordinator.open(
                        request.chatId(),
                        userId,
                        otherUserId,
                        sessionId
                );

        /*
         * Find the other participant's WebSocket session.
         *
         * Both lanes are considered: a background session may
         * rendezvous with a peer viewing the chat in the
         * foreground, and the other way around.
         */
        String otherOwner =
                openedChatCoordinator.getOwnerAnywhere(
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
         * Verify that our own opened-chat record exists in the
         * lane we just wrote to.
         */
        String currentOwner = silent
                ? openedChatCoordinator.getSilentOwner(
                        request.chatId(),
                        userId
                )
                : openedChatCoordinator.getOwner(
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

        /*
         * Tear down the lane this session used, so closing a
         * background chat never disturbs the foreground one.
         *
         * The session id is required so one silent connection
         * cannot wipe the record of a concurrent one.
         */
        if (isSilent(sessionAttributes)) {
            openedChatCoordinator.closeSilent(
                    request.chatId(),
                    userId,
                    headerAccessor.getSessionId()
            );
        } else {
            openedChatCoordinator.close(
                    request.chatId(),
                    userId
            );
        }
    }

    private boolean isSilent(
            Map<String, Object> sessionAttributes
    ) {
        return Boolean.TRUE.equals(
                sessionAttributes.get(
                        SILENT_ATTRIBUTE
                )
        );
    }
}