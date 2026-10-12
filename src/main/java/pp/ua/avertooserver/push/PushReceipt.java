package pp.ua.avertooserver.push;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Квитанція Expo Push Service, результат якої ще потрібно
 * перевірити або обробити.
 *
 * Зберігаємо токен, до якого належить квитанція, щоб при
 * помилці DeviceNotRegistered видалити недійсний токен.
 */
@Entity
@Table(name = "expo_push_receipts")
@Getter
@NoArgsConstructor
public class PushReceipt {

    @Id
    @Column(name = "receipt_id", nullable = false, length = 64)
    private String receiptId;

    @Column(name = "token", nullable = false, length = 255)
    private String token;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "next_check_at", nullable = false)
    private Instant nextCheckAt;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    public PushReceipt(
            String receiptId,
            String token,
            Instant createdAt,
            Instant nextCheckAt,
            int attempts
    ) {
        this.receiptId = receiptId;
        this.token = token;
        this.createdAt = createdAt;
        this.nextCheckAt = nextCheckAt;
        this.attempts = attempts;
    }

    /**
     * Створює новий запис для квитанції, яку потрібно перевірити.
     */
    public static PushReceipt pending(
            String receiptId,
            String token,
            Instant createdAt,
            Instant nextCheckAt
    ) {
        return new PushReceipt(
                receiptId,
                token,
                createdAt,
                nextCheckAt,
                0
        );
    }

    /**
     * Планує наступну перевірку та збільшує лічильник спроб.
     */
    public void reschedule(Instant nextCheckAt) {
        this.nextCheckAt = nextCheckAt;
        this.attempts++;
    }
}