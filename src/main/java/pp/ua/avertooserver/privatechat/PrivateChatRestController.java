package pp.ua.avertooserver.privatechat;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import pp.ua.avertooserver.privatechat.dto.PrivateChatListItemResponse;

import java.util.List;

/**
 * REST controller responsible for chat list synchronization.
 *
 * <p>The client calls this endpoint every time the chat list
 * screen is opened. The response is compared with the local
 * chat storage: chats missing from the server response were
 * deleted by the other participant and must be marked as
 * deleted locally.</p>
 */
@RestController
@RequiredArgsConstructor
public class PrivateChatRestController {

    private final PrivateChatService privateChatService;

    /**
     * Returns all chats of the currently authenticated user
     * regardless of their status (PENDING and ACTIVE).
     *
     * @param authentication authenticated user's security context
     * @return user's chats ordered by creation time (newest first)
     */
    @GetMapping("/api/private-chats")
    public ResponseEntity<List<PrivateChatListItemResponse>> getChats(
            Authentication authentication
    ) {

        Long userId = Long.valueOf(authentication.getName());

        List<PrivateChatListItemResponse> chats =
                privateChatService.getChatsByUserId(userId);

        return ResponseEntity.ok(chats);
    }
}