CREATE SEQUENCE user_id_seq
    START WITH 1000000000
    INCREMENT BY 1;


CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    user_id BIGINT NOT NULL UNIQUE DEFAULT nextval('user_id_seq'),
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);