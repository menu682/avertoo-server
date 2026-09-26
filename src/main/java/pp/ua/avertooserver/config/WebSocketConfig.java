package pp.ua.avertooserver.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.handler.WebSocketHandlerDecoratorFactory;
import pp.ua.avertooserver.presence.PresenceService;
import pp.ua.avertooserver.presence.WebSocketSessionHandlerDecorator;
import pp.ua.avertooserver.presence.WebSocketSessionRegistry;
import pp.ua.avertooserver.security.WebSocketAuthInterceptor;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig
        implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthInterceptor webSocketAuthInterceptor;
    private final WebSocketSessionRegistry sessionRegistry;
    private final PresenceService presenceService;

    @Value("${websocket.allowed-origin}")
    private String allowedOrigin;

    @Override
    public void configureMessageBroker(
            MessageBrokerRegistry registry
    ) {
        registry.enableSimpleBroker("/queue");

        registry.setApplicationDestinationPrefixes(
                "/app"
        );

        registry.setUserDestinationPrefix(
                "/user"
        );
    }

    @Override
    public void registerStompEndpoints(
            StompEndpointRegistry registry
    ) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(
                        allowedOrigin
                );
    }

    @Override
    public void configureClientInboundChannel(
            ChannelRegistration registration
    ) {
        registration.interceptors(
                webSocketAuthInterceptor
        );
    }

    @Override
    public void configureWebSocketTransport(
            WebSocketTransportRegistration registration
    ) {
        registration.addDecoratorFactory(
                new WebSocketHandlerDecoratorFactory() {

                    @Override
                    public WebSocketHandler decorate(
                            WebSocketHandler handler
                    ) {
                        return new WebSocketSessionHandlerDecorator(
                                handler,
                                sessionRegistry,
                                presenceService
                        );
                    }
                }
        );
    }
}