package pp.ua.avertooserver.common.exception.dto;

public record ErrorResponse(
        int status,
        String message
) {
}
