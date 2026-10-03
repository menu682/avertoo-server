package pp.ua.avertooserver.privatechat.dto;

import java.util.UUID;

public record PrivateChatDeleteRequest(
        UUID chatId
) {
}