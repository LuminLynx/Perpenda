-- Password reset codes (account recovery).
--
-- Mirrors email_verification_codes (migration 025): one active code per
-- user (PK user_id), SHA-256 hash only, per-code attempt counter, ON
-- DELETE CASCADE. Kept as a separate table rather than a purpose column
-- so an outstanding password-reset code can never be replayed as an
-- email-verification code or vice versa — the two prove different
-- intents even though both prove inbox ownership.

CREATE TABLE IF NOT EXISTS password_reset_codes (
    user_id TEXT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    code_hash TEXT NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
