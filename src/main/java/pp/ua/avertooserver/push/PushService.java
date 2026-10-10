package pp.ua.avertooserver.push;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import pp.ua.avertooserver.common.exception.PrivateChatAccessDeniedException;
import pp.ua.avertooserver.common.exception.UserNotFoundException;
import pp.ua.avertooserver.privatechat.PrivateChatService;
import pp.ua.avertooserver.user.User;
import pp.ua.avertooserver.user.UserRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Relays background wake-up commands to user devices through the
 * Expo Push Service.
 *
 * <p>Messages are delivered peer-to-peer and end-to-end encrypted,
 * so the server never sees message content. When a peer cannot be
 * reached over WebRTC (offline), the sender asks this service to
 * wake the recipient. Only a data payload {@code {type, chatId}}
 * is pushed — never message content.</p>
 */
@Service
@RequiredArgsConstructor
public class PushService {

    /**
     * Public Expo Push Service endpoint. It requires no secret
     * credentials, so the backend can relay pushes without holding
     * a Firebase service account.
     */
    private static final String EXPO_PUSH_URL =
            "https://exp.host/--/api/v2/push/send";

    private final PushTokenRepository pushTokenRepository;

    private final UserRepository userRepository;

    private final PrivateChatService privateChatService;

    private final RestClient restClient;

    /**
     * Registers (or refreshes) a device push token for the
     * authenticated user.
     */
    @Transactional
    public void register(
            Long userId,
            String token,
            String platform
    ) {
        UUID internalId = requireInternalId(userId);

        pushTokenRepository.upsert(internalId, token, platform);
    }

    /**
     * Removes a device push token of the authenticated user.
     */
    @Transactional
    public void unregister(Long userId, String token) {
        UUID internalId = requireInternalId(userId);

        pushTokenRepository.deleteByUserIdAndToken(internalId, token);
    }

    /**
     * Wakes the target user's devices for the given chat.
     *
     * <p>Validates that the requesting user actually shares the
     * chat with the target before pushing anything.</p>
     */
    public void wakePeer(
            Long fromUserId,
            Long targetUserId,
            UUID chatId
    ) {
        // Throws PrivateChatAccessDeniedException when the caller
        // is not a participant of the chat.
        Long other =
                privateChatService.getOtherUserId(chatId, fromUserId);

        if (!other.equals(targetUserId)) {
            throw new PrivateChatAccessDeniedException(chatId);
        }

        UUID targetInternalId = requireInternalId(targetUserId);

        List<PushToken> tokens =
                pushTokenRepository.findByUserId(targetInternalId);

        for (PushToken pushToken : tokens) {
            sendWake(pushToken.getToken(), chatId);
        }
    }

    /**
     * Sends a single data-only wake push to one Expo token.
     *
     * <p>Failures are swallowed: waking is best-effort and must
     * never break the sender's flow.</p>
     */
    private void sendWake(String token, UUID chatId) {
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("to", token);
            message.put("priority", "high");
            message.put("_contentAvailable", true);

            Map<String, Object> data = new HashMap<>();
            data.put("type", "wake");
            data.put("chatId", chatId.toString());
            message.put("data", data);

            restClient.post()
                    .uri(EXPO_PUSH_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(message)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception error) {
            // Best-effort delivery — ignore transient push errors.
        }
    }

    private UUID requireInternalId(Long userId) {
        return userRepository.findByUserId(userId)
                .map(User::getId)
                .orElseThrow(() ->
                        new UserNotFoundException(userId));
    }
}
