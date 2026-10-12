package pp.ua.avertooserver.signaling;

import java.util.UUID;

/**
 * WebRTC-сигнал, пересланий сервером адресату.
 *
 * chatId і negotiationId повертаються без змін, щоб клієнт
 * міг визначити, до якого чату та яких переговорів належить
 * отриманий сигнал.
 */
public record SignalingResponse(

        /**
         * Публічний числовий ID користувача, який надіслав сигнал.
         */
        Long senderUserId,

        /**
         * ID чату, у межах якого відбуваються переговори.
         */
        UUID chatId,

        /**
         * ID конкретної спроби WebRTC-переговорів.
         */
        UUID negotiationId,

        /**
         * Тип сигналу: OFFER, ANSWER, ICE тощо.
         */
        String type,

        /**
         * Початковий payload відправника без змін.
         */
        String payload

) {
}