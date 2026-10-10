package pp.ua.avertooserver.push;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST endpoints for device push-token management and peer wake-up.
 *
 * <p>All endpoints require an authenticated user (the global
 * security configuration protects every non-permitted route).</p>
 */
@RestController
@RequestMapping("/api/push")
@RequiredArgsConstructor
public class PushController {

    private final PushService pushService;

    /**
     * Registers the caller's device push token.
     */
    @PostMapping("/token")
    public ResponseEntity<Void> registerToken(
            @RequestBody @Valid RegisterTokenRequest request,
            Authentication authentication
    ) {
        Long userId = Long.valueOf(authentication.getName());

        pushService.register(
                userId,
                request.token(),
                request.platform()
        );

        return ResponseEntity.ok().build();
    }

    /**
     * Removes the caller's device push token.
     */
    @DeleteMapping("/token")
    public ResponseEntity<Void> unregisterToken(
            @RequestBody @Valid TokenRequest request,
            Authentication authentication
    ) {
        Long userId = Long.valueOf(authentication.getName());

        pushService.unregister(userId, request.token());

        return ResponseEntity.noContent().build();
    }

    /**
     * Asks the backend to wake a peer's devices for a chat.
     */
    @PostMapping("/wake")
    public ResponseEntity<Void> wake(
            @RequestBody @Valid WakeRequest request,
            Authentication authentication
    ) {
        Long userId = Long.valueOf(authentication.getName());

        pushService.wakePeer(
                userId,
                request.targetUserId(),
                request.chatId()
        );

        return ResponseEntity.accepted().build();
    }

    /**
     * Body of {@code POST /api/push/token}.
     */
    public record RegisterTokenRequest(
            String token,
            String platform
    ) {
    }

    /**
     * Body of {@code DELETE /api/push/token}.
     */
    public record TokenRequest(String token) {
    }

    /**
     * Body of {@code POST /api/push/wake}.
     */
    public record WakeRequest(
            Long targetUserId,
            UUID chatId
    ) {
    }
}
