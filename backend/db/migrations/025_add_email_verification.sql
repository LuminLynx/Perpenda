-- Email verification (required-signup gate).
--
-- users.email_verified_at: NULL = unverified. Existing accounts are
-- grandfathered as verified — they signed up before verification existed
-- and locking them out retroactively would strand real v1.0 users. New
-- accounts are created verified or unverified by the application
-- depending on EMAIL_VERIFICATION_REQUIRED (see config.py), so accounts
-- created while the flag is off are never stranded if it's enabled later.
--
-- email_verification_codes: one active code per user (PK user_id), so a
-- resend replaces the outstanding code instead of accumulating rows. The
-- code itself is never stored — only its SHA-256 — so a DB read can't
-- mint a verification. attempts counts failed guesses against THIS code;
-- the per-email auth_attempts limiter (migration 024) throttles the
-- endpoint itself. ON DELETE CASCADE: account deletion takes the pending
-- code with it.

ALTER TABLE users ADD COLUMN IF NOT EXISTS email_verified_at TIMESTAMPTZ;

UPDATE users SET email_verified_at = NOW() WHERE email_verified_at IS NULL;

CREATE TABLE IF NOT EXISTS email_verification_codes (
    user_id TEXT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    code_hash TEXT NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
