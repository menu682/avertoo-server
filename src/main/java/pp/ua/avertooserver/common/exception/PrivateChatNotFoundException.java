package pp.ua.avertooserver.common.exception;

import java.util.UUID;

public class PrivateChatNotFoundException extends RuntimeException {

    public PrivateChatNotFoundException(UUID chatId) {
        super("Private chat not found: " + chatId);
    }
}