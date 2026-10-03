package pp.ua.avertooserver.privatechat.dto;

import java.util.UUID;

public record PrivateChatEventResponse(
        UUID chatId,
        PrivateChatEventType type
) {
}