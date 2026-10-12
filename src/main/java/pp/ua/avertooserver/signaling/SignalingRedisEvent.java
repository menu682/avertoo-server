package pp.ua.avertooserver.signaling;

/**
 * Подія маршрутизації WebRTC-сигналу через Redis Pub/Sub.
 *
 * targetUserId визначає одержувача, а response містить
 * сам сигнал та ідентифікатори чату й переговорів.
 *
 * Redis передає подію всім екземплярам бекенду.
 * Лише екземпляр із локальною WebSocket-сесією адресата
 * зможе доставити сигнал цьому адресату.
 *
 * Pub/Sub використовується для маршрутизації в реальному
 * часі, а не для зберігання сигналів або черги повідомлень.
 */
public record SignalingRedisEvent(
        Long targetUserId,
        SignalingResponse response
) {

    /**
     * Спільний канал для всіх екземплярів бекенду.
     */
    public static final String CHANNEL =
            "websocket:signaling-events";
}
