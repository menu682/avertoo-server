package pp.ua.avertooserver.privatechat.dto;

import java.util.UUID;

public record PrivateChatResponse(
        UUID chatId,
        Long user1UserId,
        Long user2UserId
) {
}