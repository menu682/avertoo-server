package pp.ua.avertooserver.privatechat.dto;

import pp.ua.avertooserver.privatechat.PrivateChatStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Single chat returned to a client during chat list
 * synchronization.
 *
 * <p>The client compares this server-side list with its
 * local chat storage. Chats that exist locally but are
 * missing from the server response have been deleted
 * and must be marked as such.</p>
 */
public record PrivateChatListItemResponse(

        UUID chatId,

        Long otherUserId,

        PrivateChatStatus status,

        OffsetDateTime createdAt
) {
}