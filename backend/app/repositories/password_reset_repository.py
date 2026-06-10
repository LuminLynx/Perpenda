"""Password reset codes — issue and redeem (migration 026).

Same one-active-code-per-user discipline as email_verification_repository
(migration 025): upsert on issue, SHA-256 only at rest, check-and-consume
under SELECT ... FOR UPDATE so concurrent guesses serialize and a success
consumes the row before a second success can be observed.

`redeem_code_and_set_password` also marks the user's email verified:
redeeming a code that was emailed to the account address proves inbox
ownership, which is exactly what verification asserts — and it unsticks
an unverified user whose verification code lapsed.
"""
from __future__ import annotations

import hashlib
import secrets

from ..config import EMAIL_CODE_MAX_ATTEMPTS, EMAIL_CODE_TTL_MINUTES
from ..db import get_connection
from .email_verification_repository import CodeExpiredError, CodeInvalidError, VerificationError

__all__ = [
    "issue_code",
    "redeem_code_and_set_password",
    "CodeExpiredError",
    "CodeInvalidError",
    "VerificationError",
]


def _hash_code(code: str) -> str:
    return hashlib.sha256(code.encode("utf-8")).hexdigest()


def issue_code(user_id: str, *, ttl_minutes: int = EMAIL_CODE_TTL_MINUTES) -> str:
    """Create (or replace) the user's pending reset code; return plaintext.

    The plaintext exists only in the email being sent — never stored or
    logged by this module.
    """
    code = f"{secrets.randbelow(1_000_000):06d}"
    with get_connection() as connection:
        connection.execute(
            """
            INSERT INTO password_reset_codes (user_id, code_hash, expires_at, attempts)
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


def redeem_code_and_set_password(
    user_id: str,
    code: str,
    new_password_hash: str,
    *,
    max_attempts: int = EMAIL_CODE_MAX_ATTEMPTS,
) -> None:
    """Atomically consume the code, set the new password, verify the email.

    Raises CodeExpiredError past the TTL and CodeInvalidError for every
    other failure (no pending code, wrong code, attempt cap) — same
    error discipline as email verification.
    """
    with get_connection() as connection:
        try:
            row = connection.execute(
                """
                SELECT code_hash, expires_at < NOW() AS expired, attempts
                FROM password_reset_codes
                WHERE user_id = %s
                FOR UPDATE
                """,
                (user_id,),
            ).fetchone()
            if row is None:
                raise CodeInvalidError("No pending reset code.")
            if row["expired"]:
                connection.execute(
                    "DELETE FROM password_reset_codes WHERE user_id = %s",
                    (user_id,),
                )
                connection.commit()
                raise CodeExpiredError("Reset code expired.")
            if row["attempts"] >= max_attempts:
                raise CodeInvalidError("Attempt cap reached for this code.")
            if not secrets.compare_digest(row["code_hash"], _hash_code(code)):
                connection.execute(
                    """
                    UPDATE password_reset_codes
                    SET attempts = attempts + 1
                    WHERE user_id = %s
                    """,
                    (user_id,),
                )
                connection.commit()
                raise CodeInvalidError("Wrong reset code.")

            connection.execute(
                "DELETE FROM password_reset_codes WHERE user_id = %s",
                (user_id,),
            )
            connection.execute(
                """
                UPDATE users
                SET password_hash = %s,
                    email_verified_at = COALESCE(email_verified_at, NOW())
                WHERE id = %s
                """,
                (new_password_hash, user_id),
            )
            connection.commit()
        except VerificationError:
            raise
        except Exception:
            connection.rollback()
            raise
