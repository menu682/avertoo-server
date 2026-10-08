package pp.ua.avertooserver.security.captcha;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Cloudflare Turnstile implementation of {@link CaptchaService}.
 *
 * <p>The service sends the CAPTCHA token received from the client
 * to Cloudflare and accepts the registration request only when
 * Cloudflare confirms that the token is valid.</p>
 */
@Service
@RequiredArgsConstructor
public class TurnstileCaptchaService implements CaptchaService {

    private static final String VERIFY_URL =
            "https://challenges.cloudflare.com/turnstile/v0/siteverify";

    private final RestClient restClient;

    /**
     * Secret key used to authenticate our application
     * when communicating with Cloudflare Turnstile.
     */
    @Value("${avertoo.security.turnstile.secret-key}")
    private String secretKey;

    /**
     * Verifies a CAPTCHA token using Cloudflare Turnstile.
     *
     * @param token CAPTCHA token received from the client
     * @return {@code true} when Cloudflare confirms the token
     */
    @Override
    public boolean verify(String token) {

        if (token == null || token.isBlank()) {
            return false;
        }

        return restClient.post()
                .uri(VERIFY_URL)
                .body(new TurnstileVerifyRequest(secretKey, token))
                .retrieve()
                .body(TurnstileVerifyResponse.class)
                .success();
    }

    /**
     * Request body expected by Cloudflare Turnstile verification API.
     */
    private record TurnstileVerifyRequest(
            String secret,
            String response
    ) {
    }

    /**
     * Response returned by Cloudflare after token verification.
     */
    private record TurnstileVerifyResponse(
            boolean success
    ) {
    }
}