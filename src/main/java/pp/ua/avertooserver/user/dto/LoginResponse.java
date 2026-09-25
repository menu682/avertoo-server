package pp.ua.avertooserver.user.dto;

public record LoginResponse(
        String accessToken,
        String refreshToken
) {
}
