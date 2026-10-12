package pp.ua.avertooserver.signaling;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;
import pp.ua.avertooserver.privatechat.PrivateChatRepository;

import java.util.Map;
import java.util.UUID;

/**
 * Приймає WebRTC-сигнали від клієнтів і публікує їх у Redis.
 *
 * Контролер більше не намагається самостійно знайти всі
 * WebSocket-сесії адресата. Він публікує сигнал у спільний
 * Redis-канал, який слухають усі екземпляри бекенду.
 *
 * Екземпляр, на якому підключений адресат, доставить сигнал
 * його локальним WebSocket-сесіям.
 *
 * Через цей канал передається тільки WebRTC-сигналізація,
 * а не текстові повідомлення чату.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class SignalingController {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final PrivateChatRepository privateChatRepository;

    /**
     * Приймає сигнал за STOMP-призначенням /app/signaling.
     */
    @MessageMapping("/signaling")
    public void signaling(
            SignalingMessage message,
            SimpMessageHeaderAccessor headerAccessor
    ) {
        Map<String, Object> sessionAttributes =
                headerAccessor.getSessionAttributes();

        if (sessionAttributes == null || message == null) {
            return;
        }

        /*
         * userId береться лише з автентифікованої WebSocket-сесії.
         * Ідентифікатор відправника не приймається від клієнта.
         */
        Long senderUserId =
                (Long) sessionAttributes.get("userId");

        Long targetUserId = message.targetUserId();
        UUID chatId = message.chatId();
        UUID negotiationId = message.negotiationId();

        /*
         * Відхиляємо неповні або некоректні сигнали.
         */
        if (senderUserId == null
                || targetUserId == null
                || senderUserId.equals(targetUserId)
                || chatId == null
                || negotiationId == null
                || message.type() == null
                || message.type().isBlank()
                || message.payload() == null) {

            return;
        }

        /*
         * Перевіряємо, що відправник та адресат мають саме
         * цей приватний чат. Одного факту існування чату
         * між користувачами недостатньо — перевіряємо chatId.
         *
         * Якщо чат фізично видалений із БД, перевірка не пройде.
         */
        boolean authorizedChat =
                privateChatRepository
                        .findByUserIds(senderUserId, targetUserId)
                        .filter(chat -> chatId.equals(chat.getId()))
                        .isPresent();

        if (!authorizedChat) {
            log.debug(
                    "Rejected signaling: sender={}, target={}, chat={}",
                    senderUserId,
                    targetUserId,
                    chatId
            );
            return;
        }

        /*
         * Формуємо відповідь для адресата.
         * chatId та negotiationId потрібні клієнту, щоб
         * відрізняти сигнали різних чатів і спроб з'єднання.
         */
        SignalingResponse response =
                new SignalingResponse(
                        senderUserId,
                        chatId,
                        negotiationId,
                        message.type(),
                        message.payload()
                );

        /*
         * Redis-подія містить адресата та сам сигнал.
         * Усі сервери отримають її, але кожен перевірятиме
         * тільки власні локальні WebSocket-сесії.
         */
        SignalingRedisEvent event =
                new SignalingRedisEvent(
                        targetUserId,
                        response
                );

        try {
            String json = objectMapper.writeValueAsString(event);

            Long subscriberCount =
                    redisTemplate.convertAndSend(
                            SignalingRedisEvent.CHANNEL,
                            json
                    );

            /*
             * Нуль підписників означає, що на момент публікації
             * жоден слухач Redis не отримав подію.
             * Pub/Sub не зберігає її для майбутньої доставки.
             */
            if (subscriberCount == null || subscriberCount == 0) {
                log.warn(
                        "No Redis subscribers for signaling: chat={}, target={}",
                        chatId,
                        targetUserId
                );
            }
        } catch (JsonProcessingException exception) {
            log.error(
                    "Failed to serialize signaling event: chat={}, target={}",
                    chatId,
                    targetUserId,
                    exception
            );
        } catch (RuntimeException exception) {
            log.error(
                    "Failed to publish signaling event to Redis: chat={}, target={}",
                    chatId,
                    targetUserId,
                    exception
            );
        }
    }
}
