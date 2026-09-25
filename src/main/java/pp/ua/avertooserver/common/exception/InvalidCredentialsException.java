package pp.ua.avertooserver.common.exception;

public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid user ID or password");
    }
}