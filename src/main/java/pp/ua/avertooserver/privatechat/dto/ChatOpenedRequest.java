package pp.ua.avertooserver.privatechat.dto;

import java.util.UUID;

public record ChatOpenedRequest(
        UUID chatId
) {
}