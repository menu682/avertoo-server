package pp.ua.avertooserver.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * HTTP client configuration used by the application
 * for communication with external services.
 */
@Configuration
public class RestClientConfig {

    /**
     * Creates the application's HTTP client.
     *
     * <p>The same client can be reused by services that need
     * to communicate with external APIs, such as Cloudflare
     * Turnstile.</p>
     *
     * @return configured RestClient instance
     */
    @Bean
    public RestClient restClient() {
        return RestClient.builder().build();
    }
}