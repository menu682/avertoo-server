package pp.ua.avertooserver.signaling;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import pp.ua.avertooserver.presence.WebSocketSessionRegistry;

import java.io.IOException;
import java.util.Set;

/**
 * Приймає WebRTC-сигнали з Redis і доставляє їх локальним
 * WebSocket-сесіям адресата.
 *
 * Кожен екземпляр бекенду має власний екземпляр цього класу
 * та власний WebSocketSessionRegistry.
 *
 * Тому всі сервери можуть отримати одну Redis-подію, але
 * кожен бачить тільки свої локальні сесії.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SignalingRedisEventListener implements MessageListener {

    private final ObjectMapper objectMapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final WebSocketSessionRegistry sessionRegistry;

    /**
     * Викликається Spring Data Redis після отримання повідомлення
     * з каналу SignalingRedisEvent.CHANNEL.
     */
    @Override
    public void onMessage(
            Message message,
            byte[] pattern
    ) {
        final SignalingRedisEvent event;

        /*
         * Перетворюємо JSON із Redis на типізовану подію.
         * Некоректну подію ігноруємо, не зупиняючи слухач.
         */
        try {
            event = objectMapper.readValue(
                    message.getBody(),
                    SignalingRedisEvent.class
            );
        } catch (IOException exception) {
            log.error(
                    "Failed to deserialize signaling event from Redis",
                    exception
            );
            return;
        }

        if (event == null
                || event.targetUserId() == null
                || event.response() == null) {

            log.warn("Ignored incomplete signaling event from Redis");
            return;
        }

        /*
         * Цей список формується локальним реєстром поточного
         * екземпляра сервера. Він включає основну та фонові
         * сесії адресата, зареєстровані саме тут.
         *
         * На інших серверах будуть інші локальні списки.
         */
        Set<String> targetSessionIds =
                sessionRegistry.getSessionIds(event.targetUserId());

        if (targetSessionIds.isEmpty()) {
            /*
             * Це нормально для сервера, на якому адресат
             * не має WebSocket-сесій. Інший екземпляр може
             * мати потрібну сесію та доставити сигнал.
             */
            return;
        }

        /*
         * Доставляємо сигнал кожній локальній сесії адресата.
         * Це важливо, оскільки основна й фонова сесії можуть
         * обслуговувати різні WebRTC-переговори.
         */
        for (String targetSessionId : targetSessionIds) {
            if (targetSessionId == null
                    || targetSessionId.isBlank()) {
                continue;
            }

            try {
                SimpMessageHeaderAccessor targetHeaders =
                        SimpMessageHeaderAccessor.create();

                /*
                 * Principal STOMP-сесії в цьому застосунку
                 * має ім'я, яке дорівнює sessionId.
                 * Це дозволяє адресувати конкретну сесію,
                 * а не розсилати сигнал усім сесіям користувача.
                 */
                targetHeaders.setSessionId(targetSessionId);
                targetHeaders.setLeaveMutable(true);

                messagingTemplate.convertAndSendToUser(
                        targetSessionId,
                        "/queue/signaling",
                        event.response(),
                        targetHeaders.getMessageHeaders()
                );
            } catch (RuntimeException exception) {
                /*
                 * Помилка однієї сесії не повинна заважати
                 * спробі доставити сигнал іншим сесіям.
                 */
                log.warn(
                        "Failed to deliver signaling to local session {}",
                        targetSessionId,
                        exception
                );
            }
        }
    }
}
