CREATE TABLE device_push_tokens (
       user_id UUID NOT NULL,

       token VARCHAR(512) NOT NULL,

       platform VARCHAR(20),

       created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

       last_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

       CONSTRAINT pk_device_push_tokens
           PRIMARY KEY (user_id, token),

       CONSTRAINT fk_device_push_tokens_user
           FOREIGN KEY (user_id)
               REFERENCES users(id)
               ON DELETE CASCADE
);

CREATE INDEX idx_device_push_tokens_user
    ON device_push_tokens (user_id);
