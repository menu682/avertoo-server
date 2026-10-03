package pp.ua.avertooserver.common.exception;

import java.util.UUID;

public class PrivateChatAccessDeniedException extends RuntimeException {

    public PrivateChatAccessDeniedException(UUID chatId) {
        super("User is not a participant of private chat: " + chatId);
    }
}