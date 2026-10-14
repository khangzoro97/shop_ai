-- One pending reset link per account; only the SHA-256 of the link token is stored.
CREATE TABLE password_reset (
    account_id bigint PRIMARY KEY REFERENCES account (id) ON DELETE CASCADE,
    token_hash varchar(64)              NOT NULL UNIQUE,
    expires_at timestamp(6) with time zone NOT NULL
);
