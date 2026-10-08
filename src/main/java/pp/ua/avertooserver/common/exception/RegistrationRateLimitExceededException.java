package pp.ua.avertooserver.common.exception;

/**
 * Thrown when the number of registration attempts exceeds
 * the configured rate limit.
 */
public class RegistrationRateLimitExceededException
        extends RuntimeException {

    public RegistrationRateLimitExceededException() {
        super("Registration rate limit exceeded");
    }
}