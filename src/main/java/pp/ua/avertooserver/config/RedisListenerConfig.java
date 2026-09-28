package pp.ua.avertooserver.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import pp.ua.avertooserver.presence.WebSocketSessionCoordinator;
import pp.ua.avertooserver.presence.WebSocketSessionEventListener;

@Configuration
@RequiredArgsConstructor
public class RedisListenerConfig {

    private final WebSocketSessionEventListener
            webSocketSessionEventListener;

    @Bean
    public RedisMessageListenerContainer
    redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory
    ) {
        RedisMessageListenerContainer container =
                new RedisMessageListenerContainer();

        container.setConnectionFactory(
                connectionFactory
        );

        container.addMessageListener(
                webSocketSessionEventListener,
                new ChannelTopic(
                        WebSocketSessionCoordinator.SESSION_CHANNEL
                )
        );

        return container;
    }
}