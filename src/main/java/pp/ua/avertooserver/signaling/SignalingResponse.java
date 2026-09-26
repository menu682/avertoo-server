package pp.ua.avertooserver.signaling;

public record SignalingResponse(
        Long senderUserId,
        String type,
        String payload
) {
}