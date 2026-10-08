package pp.ua.avertooserver.common.exception;

/**
 * Thrown when the number of failed login attempts
 * exceeds the configured rate limit.
 */
public class LoginRateLimitExceededException
        extends RuntimeException {

    public LoginRateLimitExceededException() {
        super("Login rate limit exceeded");
    }
}