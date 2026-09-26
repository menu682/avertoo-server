package pp.ua.avertooserver.presence;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PresenceController {

    private final PresenceService presenceService;

    @PostMapping("/api/presence/heartbeat")
    public void heartbeat(Authentication authentication) {

        Long userId = Long.valueOf(authentication.getName());

        presenceService.heartbeat(userId);
    }


    @GetMapping("/api/presence/{userId}")
    public boolean isOnline(@PathVariable Long userId) {

        return presenceService.isOnline(userId);
    }


}