package pp.ua.avertooserver.signaling;

public record SignalingMessage(
        Long targetUserId,
        String type,
        String payload
) {
}