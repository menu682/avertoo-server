package pp.ua.avertooserver.presence;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PresenceController {

    private final PresenceService presenceService;

    @GetMapping("/api/presence/{userId}")
    public boolean isOnline(
            @PathVariable Long userId
    ) {
        return presenceService.isOnline(userId);
    }
}