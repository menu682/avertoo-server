package pp.ua.avertooserver.common.exception;

/**
 * Thrown when CAPTCHA verification fails during a protected operation.
 */
public class CaptchaVerificationException extends RuntimeException {

    public CaptchaVerificationException() {
        super("CAPTCHA verification failed");
    }
}