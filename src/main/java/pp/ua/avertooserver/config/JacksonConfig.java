package pp.ua.avertooserver.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Спільна конфігурація Jackson.
 *
 * ObjectMapper використовується для перетворення об'єктів
 * сигналізації WebRTC у JSON та назад під час передачі через Redis.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
