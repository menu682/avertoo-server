package pp.ua.avertooserver.presence;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WebSocketSessionEventListener
        implements MessageListener {

    private final WebSocketSessionRegistry sessionRegistry;

    private final WebSocketSessionCoordinator
            sessionCoordinator;

    @Override
    public void onMessage(
            Message message,
            byte[] pattern
    ) {
        String event =
                new String(message.getBody());

        String[] parts =
                event.split("\\|", 2);

        if (parts.length != 2) {
            return;
        }

        Long userId;

        try {
            userId = Long.valueOf(parts[0]);
        } catch (NumberFormatException e) {
            return;
        }

        String newServerId =
                parts[1];

        if (sessionCoordinator
                .getServerId()
                .equals(newServerId)) {

            return;
        }

        sessionRegistry.closeUserSession(
                userId
        );
    }
}