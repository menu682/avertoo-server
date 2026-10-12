-- Зберігає квитанції Expo Push Service, які ще потрібно перевірити.
-- Це дозволяє продовжити перевірку після перезапуску сервера.

CREATE TABLE expo_push_receipts
(
    receipt_id    VARCHAR(64)  PRIMARY KEY,
    token         VARCHAR(255) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,
    next_check_at TIMESTAMPTZ  NOT NULL,
    attempts      INTEGER      NOT NULL DEFAULT 0,

    CONSTRAINT chk_expo_push_receipts_attempts
        CHECK (attempts >= 0)
);

-- Швидко знаходить квитанції, для яких настав час перевірки.
CREATE INDEX idx_expo_push_receipts_next_check_at
    ON expo_push_receipts (next_check_at);