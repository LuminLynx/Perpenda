"""Email verification codes — issue and verify (migration 025).

One active code per user: `issue_code` upserts, so a resend replaces the
outstanding code rather than leaving several valid at once. Only the
SHA-256 of the code is stored; a DB read can't mint a verification.

`verify_code` does its check-and-consume under `SELECT ... FOR UPDATE` in
one transaction, so concurrent guesses against the same code serialize:
they can't share an under-limit attempts count (same TOCTOU reasoning as
the rate limiters), and a success consumes the row before a second
success can be observed. Endpoint-level throttling (auth_attempts,
migration 024) bounds guessing across codes; the per-code `attempts` cap
bounds guessing within one code's lifetime (10^6 codes / 5 attempts).
"""
from __future__ import annotations

import hashlib
import secrets

from ..config import EMAIL_CODE_MAX_ATTEMPTS, EMAIL_CODE_TTL_MINUTES
from ..db import get_connection


class VerificationError(Exception):
    """Base for verification failures; carries the client-facing code."""

    code = "INVALID_CODE"


class CodeInvalidError(VerificationError):
    """No pending code, wrong code, or attempt cap exhausted."""

    code = "INVALID_CODE"


class CodeExpiredError(VerificationError):
    """The pending code's TTL has passed; ask for a resend."""

    code = "CODE_EXPIRED"


def _hash_code(code: str) -> str:
    return hashlib.sha256(code.encode("utf-8")).hexdigest()


def issue_code(user_id: str, *, ttl_minutes: int = EMAIL_CODE_TTL_MINUTES) -> str:
    """Create (or replace) the user's pending code; return the plaintext.

    The plaintext exists only in the email being sent — it is never stored
    or logged by this module.
    """
    # 6 digits, zero-padded, from the CSPRNG (not random.randint).
    code = f"{secrets.randbelow(1_000_000):06d}"
    with get_connection() as connection:
        connection.execute(
            """
            INSERT INTO email_verification_codes (user_id, code_hash, expires_at, attempts)
            VALUES (%s, %s, NOW() + make_interval(mins => %s), 0)
            ON CONFLICT (user_id) DO UPDATE
                SET code_hash = EXCLUDED.code_hash,
                    expires_at = EXCLUDED.expires_at,
                    attempts = 0,
                    created_at = NOW()
            """,
            (user_id, _hash_code(code), ttl_minutes),
        )
        connection.commit()
    return code


def verify_code(
    user_id: str,
    code: str,
    *,
    max_attempts: int = EMAIL_CODE_MAX_ATTEMPTS,
) -> None:
    """Consume the pending code and mark the user verified, atomically.

    Raises CodeExpiredError when the pending code is past its TTL, and
    CodeInvalidError for everything else (no pending code, wrong code,
    attempt cap reached) — callers surface both without revealing which
    wrong-code case occurred beyond expiry, which the user must know about
    to request a resend.
    """
    with get_connection() as connection:
        try:
            row = connection.execute(
                """
                SELECT code_hash, expires_at < NOW() AS expired, attempts
                FROM email_verification_codes
                WHERE user_id = %s
                FOR UPDATE
                """,
                (user_id,),
            ).fetchone()
            if row is None:
                raise CodeInvalidError("No pending verification code.")
            if row["expired"]:
                connection.execute(
                    "DELETE FROM email_verification_codes WHERE user_id = %s",
                    (user_id,),
                )
                connection.commit()
                raise CodeExpiredError("Verification code expired.")
            if row["attempts"] >= max_attempts:
                raise CodeInvalidError("Attempt cap reached for this code.")
            if not secrets.compare_digest(row["code_hash"], _hash_code(code)):
                connection.execute(
                    """
                    UPDATE email_verification_codes
                    SET attempts = attempts + 1
                    WHERE user_id = %s
                    """,
                    (user_id,),
                )
                connection.commit()
                raise CodeInvalidError("Wrong verification code.")

            connection.execute(
                "DELETE FROM email_verification_codes WHERE user_id = %s",
                (user_id,),
            )
            connection.execute(
                "UPDATE users SET email_verified_at = NOW() WHERE id = %s",
                (user_id,),
            )
            connection.commit()
        except VerificationError:
            raise
        except Exception:
            connection.rollback()
            raise
