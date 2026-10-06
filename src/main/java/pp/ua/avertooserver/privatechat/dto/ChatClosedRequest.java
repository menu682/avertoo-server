package pp.ua.avertooserver.privatechat.dto;

import java.util.UUID;

public record ChatClosedRequest(
        UUID chatId
) {
}