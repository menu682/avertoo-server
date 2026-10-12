package pp.ua.avertooserver.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import pp.ua.avertooserver.presence.WebSocketSessionCoordinator;
import pp.ua.avertooserver.presence.WebSocketSessionEventListener;
import pp.ua.avertooserver.privatechat.OpenedChatCoordinator;
import pp.ua.avertooserver.privatechat.OpenedChatEventListener;
import pp.ua.avertooserver.signaling.SignalingRedisEvent;
import pp.ua.avertooserver.signaling.SignalingRedisEventListener;

/**
 * Налаштовує підписки бекенду на Redis Pub/Sub.
 *
 * Усі екземпляри Avertoo підписуються на однакові канали,
 * щоб координувати WebSocket-сесії, відкриті чати та
 * пересилання WebRTC-сигналів між серверами.
 */
@Configuration
@RequiredArgsConstructor
public class RedisListenerConfig {

    private final WebSocketSessionEventListener
            webSocketSessionEventListener;

    private final OpenedChatEventListener
            openedChatEventListener;

    private final SignalingRedisEventListener
            signalingRedisEventListener;

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory
    ) {
        RedisMessageListenerContainer container =
                new RedisMessageListenerContainer();

        container.setConnectionFactory(connectionFactory);

        /*
         * Події зміни власника основної WebSocket-сесії.
         */
        container.addMessageListener(
                webSocketSessionEventListener,
                new ChannelTopic(
                        WebSocketSessionCoordinator.SESSION_CHANNEL
                )
        );

        /*
         * Події про готовність учасників до WebRTC
         * та координацію відкритих чатів.
         */
        container.addMessageListener(
                openedChatEventListener,
                new ChannelTopic(
                        OpenedChatCoordinator.CHAT_CHANNEL
                )
        );

        /*
         * WebRTC-сигнали між різними екземплярами бекенду.
         * Кожен екземпляр отримує подію та перевіряє
         * власний локальний WebSocketSessionRegistry.
         */
        container.addMessageListener(
                signalingRedisEventListener,
                new ChannelTopic(
                        SignalingRedisEvent.CHANNEL
                )
        );

        return container;
    }
}
