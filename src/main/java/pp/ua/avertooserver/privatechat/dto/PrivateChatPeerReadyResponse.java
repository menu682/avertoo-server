package pp.ua.avertooserver.privatechat.dto;

import java.util.UUID;

/**
 * Sent to a client when both participants have opened
 * the same private chat and a WebRTC connection can be
 * established.
 *
 * initiator=true means this client must create the
 * WebRTC OFFER.
 */
public record PrivateChatPeerReadyResponse(
        UUID chatId,
        boolean initiator
) {
}