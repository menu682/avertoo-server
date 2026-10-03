CREATE TABLE private_chats (
    id UUID PRIMARY KEY DEFAULT uuidv7(),

    user1_id UUID NOT NULL,
    user2_id UUID NOT NULL,

    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_private_chats_user1
       FOREIGN KEY (user1_id)
           REFERENCES users(id),

    CONSTRAINT fk_private_chats_user2
       FOREIGN KEY (user2_id)
           REFERENCES users(id),

    CONSTRAINT chk_private_chats_different_users
       CHECK (user1_id <> user2_id),

    CONSTRAINT chk_private_chats_user_order
       CHECK (user1_id < user2_id),

    CONSTRAINT chk_private_chats_status
       CHECK (status IN ('PENDING', 'ACTIVE')),

    CONSTRAINT uq_private_chats_users
       UNIQUE (user1_id, user2_id)
);