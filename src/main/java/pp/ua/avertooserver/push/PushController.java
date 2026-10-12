package pp.ua.avertooserver.push;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST API для реєстрації push-токенів і пробудження пристроїв.
 *
 * Усі операції виконуються від імені автентифікованого користувача.
 */
@RestController
@RequestMapping("/api/push")
@RequiredArgsConstructor
public class PushController {

    private final PushService pushService;

    /**
     * Реєструє або оновлює push-токен пристрою.
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
     * Видаляє push-токен поточного користувача.
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
     * Просить сервер надіслати команду пробудження іншому учаснику чату.
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
     * Тіло POST /api/push/token.
     */
    public record RegisterTokenRequest(
            @NotBlank
            @Size(max = 255)
            String token,

            @NotBlank
            @Size(max = 20)
            String platform
    ) {
    }

    /**
     * Тіло DELETE /api/push/token.
     */
    public record TokenRequest(
            @NotBlank
            @Size(max = 255)
            String token
    ) {
    }

    /**
     * Тіло POST /api/push/wake.
     */
    public record WakeRequest(
            @NotNull
            Long targetUserId,

            @NotNull
            UUID chatId
    ) {
    }
}