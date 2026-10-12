package pp.ua.avertooserver.push;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import pp.ua.avertooserver.common.exception.PrivateChatAccessDeniedException;
import pp.ua.avertooserver.common.exception.UserNotFoundException;
import pp.ua.avertooserver.privatechat.PrivateChatService;
import pp.ua.avertooserver.user.User;
import pp.ua.avertooserver.user.UserRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PushService {

    private static final String EXPO_PUSH_URL =
            "https://exp.host/--/api/v2/push/send";

    private static final String EXPO_RECEIPTS_URL =
            "https://exp.host/--/api/v2/push/getReceipts";

    private static final int MAX_SEND_ATTEMPTS = 3;
    private static final int RECEIPT_BATCH_SIZE = 1000;

    private static final Duration FIRST_RECEIPT_CHECK =
            Duration.ofMinutes(15);

    private static final Duration RECEIPT_RETRY_DELAY =
            Duration.ofMinutes(5);

    private static final Duration MAX_RECEIPT_AGE =
            Duration.ofHours(24);

    private final PushTokenRepository pushTokenRepository;
    private final PushReceiptRepository pushReceiptRepository;
    private final UserRepository userRepository;
    private final PrivateChatService privateChatService;
    private final RestClient restClient;

    /**
     * Реєструє або оновлює push-токен пристрою.
     */
    @Transactional
    public void register(
            Long userId,
            String token,
            String platform
    ) {
        UUID internalId = requireInternalId(userId);

        pushTokenRepository.upsert(
                internalId,
                token,
                platform
        );
    }

    /**
     * Відв'язує push-токен від облікового запису користувача.
     */
    @Transactional
    public void unregister(Long userId, String token) {
        UUID internalId = requireInternalId(userId);

        pushTokenRepository.deleteByUserIdAndToken(
                internalId,
                token
        );
    }

    /**
     * Перевіряє право відправника на чат і надсилає
     * команду пробудження на зареєстровані пристрої адресата.
     *
     * Вмісту повідомлень чату в push немає.
     */
    public void wakePeer(
            Long fromUserId,
            Long targetUserId,
            UUID chatId
    ) {
        Long other = privateChatService.getOtherUserId(
                chatId,
                fromUserId
        );

        if (!other.equals(targetUserId)) {
            throw new PrivateChatAccessDeniedException(chatId);
        }

        UUID targetInternalId = requireInternalId(targetUserId);

        List<PushToken> tokens =
                pushTokenRepository.findByUserId(targetInternalId);

        for (PushToken pushToken : tokens) {
            sendWake(pushToken.getToken(), chatId);
        }
    }

    /**
     * Надсилає одну data-only команду пробудження.
     *
     * Тимчасові HTTP-помилки повторюються обмежену кількість
     * разів. Успішний ticket зберігається для подальшої
     * перевірки receipt.
     */
    private void sendWake(String token, UUID chatId) {
        Map<String, Object> message = new HashMap<>();
        message.put("to", token);
        message.put("priority", "high");
        message.put("_contentAvailable", true);

        Map<String, Object> data = new HashMap<>();
        data.put("type", "wake");
        data.put("chatId", chatId.toString());
        message.put("data", data);

        // Expo очікує список push-повідомлень у тілі запиту.
        List<Map<String, Object>> messages = List.of(message);

        for (int attempt = 1; attempt <= MAX_SEND_ATTEMPTS; attempt++) {
            try {
                JsonNode response = restClient.post()
                        .uri(EXPO_PUSH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .body(messages)
                        .retrieve()
                        .body(JsonNode.class);

                handleTicketResponse(response, token);
                return;

            } catch (RestClientException error) {
                boolean retry = isRetryable(error);

                log.warn(
                        "Expo push request failed; attempt {}/{}; retryable={}; reason={}",
                        attempt,
                        MAX_SEND_ATTEMPTS,
                        retry,
                        error.getMessage()
                );

                if (!retry || attempt == MAX_SEND_ATTEMPTS) {
                    return;
                }

                if (!waitBeforeRetry(attempt)) {
                    return;
                }
            }
        }
    }

    /**
     * Обробляє відповідь Expo Push Service.
     *
     * Прийняття push сервісом ще не означає, що пристрій
     * отримав його. Для цього результату потрібен receipt.
     */
    private void handleTicketResponse(
            JsonNode response,
            String token
    ) {
        JsonNode ticket = extractTicket(response);

        if (ticket == null) {
            log.warn("Expo returned an empty or unexpected push ticket");
            return;
        }

        String status = ticket.path("status").asText("");

        if ("ok".equals(status)) {
            String receiptId = ticket.path("id").asText("");

            if (receiptId.isBlank()) {
                log.warn("Expo accepted push but returned no receipt ID");
                return;
            }

            Instant now = Instant.now();

            pushReceiptRepository.save(
                    PushReceipt.pending(
                            receiptId,
                            token,
                            now,
                            now.plus(FIRST_RECEIPT_CHECK)
                    )
            );

            return;
        }

        String errorCode =
                ticket.path("details").path("error").asText("");

        if ("DeviceNotRegistered".equals(errorCode)) {
            // Expo підтвердив, що токен більше не працює.
            pushTokenRepository.deleteAllByToken(token);

            log.info("Removed invalid Expo push token");
            return;
        }

        log.warn(
                "Expo rejected push; status={}, message={}",
                status,
                ticket.path("message").asText("No error message")
        );
    }

    /**
     * Періодично перевіряє квитанції, які Expo вже мав час
     * сформувати. Список ідентифікаторів обмежений лімітом API.
     *
     * Потрібно, щоб у застосунку була ввімкнена підтримка
     * @Scheduled через @EnableScheduling.
     */
    @Scheduled(
            fixedDelayString = "${push.receipts.poll-interval-ms:300000}"
    )
    public void processPendingReceipts() {
        Instant now = Instant.now();

        List<PushReceipt> receipts =
                pushReceiptRepository
                        .findTop1000ByNextCheckAtBeforeOrderByNextCheckAtAsc(now);

        if (receipts.isEmpty()) {
            return;
        }

        // Старі квитанції не можна перевіряти нескінченно.
        List<PushReceipt> activeReceipts = new ArrayList<>();

        for (PushReceipt receipt : receipts) {
            if (receipt.getCreatedAt().isBefore(
                    now.minus(MAX_RECEIPT_AGE)
            )) {
                pushReceiptRepository.delete(receipt);
            } else {
                activeReceipts.add(receipt);
            }
        }

        if (activeReceipts.isEmpty()) {
            return;
        }

        List<String> receiptIds = activeReceipts.stream()
                .map(PushReceipt::getReceiptId)
                .toList();

        try {
            JsonNode response = restClient.post()
                    .uri(EXPO_RECEIPTS_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(Map.of("ids", receiptIds))
                    .retrieve()
                    .body(JsonNode.class);

            JsonNode data = response == null
                    ? null
                    : response.path("data");

            if (data == null || !data.isObject()) {
                rescheduleReceipts(activeReceipts);
                return;
            }

            for (PushReceipt receipt : activeReceipts) {
                JsonNode result = data.path(receipt.getReceiptId());

                // Відсутній receipt означає, що він ще не готовий
                // або Expo не повернув його в цій відповіді.
                if (result.isMissingNode() || result.isNull()) {
                    rescheduleReceipt(receipt);
                    continue;
                }

                handleReceipt(receipt, result);
            }

        } catch (RestClientException error) {
            log.warn(
                    "Could not retrieve Expo push receipts: {}",
                    error.getMessage()
            );

            rescheduleReceipts(activeReceipts);
        }
    }

    /**
     * Обробляє окрему квитанцію.
     */
    private void handleReceipt(
            PushReceipt receipt,
            JsonNode result
    ) {
        String status = result.path("status").asText("");

        if ("ok".equals(status)) {
            // Expo обробив push без помилки.
            pushReceiptRepository.delete(receipt);
            return;
        }

        if ("error".equals(status)) {
            String errorCode =
                    result.path("details").path("error").asText("");

            if ("DeviceNotRegistered".equals(errorCode)) {
                pushTokenRepository.deleteAllByToken(
                        receipt.getToken()
                );
            }

            log.warn(
                    "Expo receipt reports an error; code={}, message={}",
                    errorCode,
                    result.path("message").asText("No error message")
            );

            // Receipt уже має остаточний результат.
            // Повторно перевіряти його немає сенсу.
            pushReceiptRepository.delete(receipt);
            return;
        }

        // Невідомий статус не трактуємо як успіх.
        rescheduleReceipt(receipt);
    }

    /**
     * Переносить перевірку квитанції на пізніший час.
     */
    private void rescheduleReceipt(PushReceipt receipt) {
        Instant now = Instant.now();

        if (receipt.getCreatedAt().isBefore(
                now.minus(MAX_RECEIPT_AGE)
        )) {
            pushReceiptRepository.delete(receipt);
            return;
        }

        receipt.reschedule(now.plus(RECEIPT_RETRY_DELAY));
        pushReceiptRepository.save(receipt);
    }

    private void rescheduleReceipts(List<PushReceipt> receipts) {
        for (PushReceipt receipt : receipts) {
            rescheduleReceipt(receipt);
        }
    }

    /**
     * Повторюємо тільки тимчасові HTTP-помилки:
     * мережеві збої, HTTP 429 та відповіді сервера 5xx.
     */
    private boolean isRetryable(RestClientException error) {
        if (!(error instanceof RestClientResponseException responseError)) {
            return true;
        }

        int status = responseError.getStatusCode().value();

        return status == 429
                || responseError.getStatusCode().is5xxServerError();
    }

    /**
     * Коротка затримка між повторними HTTP-запитами.
     */
    private boolean waitBeforeRetry(int attempt) {
        long delayMillis = 250L * (1L << (attempt - 1));

        try {
            Thread.sleep(delayMillis);
            return true;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Expo повертає tickets у полі data.
     * Підтримуємо масив та одиночний об'єкт у відповіді.
     */
    private JsonNode extractTicket(JsonNode response) {
        if (response == null) {
            return null;
        }

        JsonNode data = response.path("data");

        if (data.isArray()) {
            return data.isEmpty() ? null : data.get(0);
        }

        if (data.isObject()) {
            return data;
        }

        return null;
    }

    private UUID requireInternalId(Long userId) {
        return userRepository.findByUserId(userId)
                .map(User::getId)
                .orElseThrow(() ->
                        new UserNotFoundException(userId));
    }
}