package pp.ua.avertooserver.push;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

/**
 * Доступ до квитанцій Expo, очікуваних на перевірку.
 */
public interface PushReceiptRepository
        extends JpaRepository<PushReceipt, String> {

    /**
     * Повертає не більше 1000 квитанцій, час перевірки яких настав.
     * Ліміт відповідає максимальному розміру запиту Expo receipts.
     */
    List<PushReceipt> findTop1000ByNextCheckAtBeforeOrderByNextCheckAtAsc(
            Instant now
    );
}