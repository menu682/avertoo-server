package pp.ua.avertooserver.privatechat.dto;

import java.util.UUID;

public record PrivateChatCreateResponse(
        UUID chatId,
        PrivateChatCreateResult type
) {
}