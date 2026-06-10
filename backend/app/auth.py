from __future__ import annotations

import re
from datetime import datetime, timedelta, timezone
from typing import Any

import bcrypt
import jwt
from fastapi import Header, HTTPException

from .config import JWT_ALGORITHM, JWT_EXPIRATION_DAYS, JWT_SECRET

EMAIL_PATTERN = re.compile(r"^[^@\s]+@[^@\s]+\.[^@\s]+$")
MIN_PASSWORD_LENGTH = 8
MIN_DISPLAY_NAME_LENGTH = 2
MAX_DISPLAY_NAME_LENGTH = 50

# bcrypt only considers the first 72 bytes of the password and (4.x+) raises
# on longer input. Truncate to that to preserve the classic accept-any-length
# behavior — and the same truncation passlib applied, so hashes made by the
# old passlib+bcrypt stack still verify byte-for-byte.
_BCRYPT_MAX_BYTES = 72


class AuthError(Exception):
    def __init__(self, message: str, code: str, status_code: int = 401) -> None:
        super().__init__(message)
        self.code = code
        self.status_code = status_code


def _password_bytes(password: str) -> bytes:
    return password.encode("utf-8")[:_BCRYPT_MAX_BYTES]


def hash_password(password: str) -> str:
    return bcrypt.hashpw(_password_bytes(password), bcrypt.gensalt()).decode("utf-8")


def verify_password(password: str, password_hash: str) -> bool:
    try:
        return bcrypt.checkpw(_password_bytes(password), password_hash.encode("utf-8"))
    except (ValueError, TypeError):
        return False


# Precomputed hash used to equalize login timing when the email is unknown:
# bcrypt verify runs whether or not the account exists, so "no such user"
# and "wrong password" take the same time, closing an enumeration oracle.
_DUMMY_PASSWORD_HASH = hash_password("not-a-real-password-timing-equalizer")


def verify_login(password: str, password_hash: str | None) -> bool:
    """Verify a login password without leaking account existence via timing.

    When `password_hash` is None (no such user) we still run a bcrypt verify
    against a fixed dummy hash, so the response takes the same time as a
    wrong-password attempt on a real account. Always returns False for the
    unknown-user case.
    """
    if password_hash is None:
        verify_password(password, _DUMMY_PASSWORD_HASH)
        return False
    return verify_password(password, password_hash)


def normalize_email(email: str) -> str:
    return email.strip().lower()


def validate_email(email: str) -> str:
    normalized = normalize_email(email)
    if not EMAIL_PATTERN.match(normalized):
        raise AuthError("Invalid email format.", code="INVALID_EMAIL", status_code=400)
    return normalized


def validate_password(password: str) -> str:
    if not isinstance(password, str) or len(password) < MIN_PASSWORD_LENGTH:
        raise AuthError(
            f"Password must be at least {MIN_PASSWORD_LENGTH} characters.",
            code="WEAK_PASSWORD",
            status_code=400,
        )
    return password


def validate_display_name(display_name: str) -> str:
    cleaned = " ".join(display_name.split())
    if not (MIN_DISPLAY_NAME_LENGTH <= len(cleaned) <= MAX_DISPLAY_NAME_LENGTH):
        raise AuthError(
            f"Display name must be {MIN_DISPLAY_NAME_LENGTH}-{MAX_DISPLAY_NAME_LENGTH} characters.",
            code="INVALID_DISPLAY_NAME",
            status_code=400,
        )
    # First and last name required: at least two whitespace-separated
    # parts of 2+ characters each. Internal whitespace is collapsed above,
    # so "Ada   Lovelace" normalizes instead of failing.
    parts = cleaned.split(" ")
    if len(parts) < 2 or any(len(part) < 2 for part in parts):
        raise AuthError(
            "Enter your first and last name.",
            code="INVALID_DISPLAY_NAME",
            status_code=400,
        )
    return cleaned


def create_access_token(user_id: str) -> str:
    now = datetime.now(timezone.utc)
    payload = {
        "sub": user_id,
        "iat": int(now.timestamp()),
        "exp": int((now + timedelta(days=JWT_EXPIRATION_DAYS)).timestamp()),
    }
    return jwt.encode(payload, JWT_SECRET, algorithm=JWT_ALGORITHM)


def decode_access_token(token: str) -> dict[str, Any]:
    try:
        return jwt.decode(token, JWT_SECRET, algorithms=[JWT_ALGORITHM])
    except jwt.ExpiredSignatureError as error:
        raise AuthError("Token has expired.", code="TOKEN_EXPIRED") from error
    except jwt.InvalidTokenError as error:
        raise AuthError("Invalid token.", code="INVALID_TOKEN") from error


def _extract_token(authorization: str | None) -> str | None:
    if not authorization:
        return None
    parts = authorization.split(" ", 1)
    if len(parts) != 2 or parts[0].lower() != "bearer":
        return None
    token = parts[1].strip()
    return token or None


def optional_user_id(authorization: str | None = Header(default=None)) -> str | None:
    """FastAPI dependency: returns the authenticated user_id, or None if no/invalid token."""
    token = _extract_token(authorization)
    if token is None:
        return None
    try:
        payload = decode_access_token(token)
    except AuthError:
        return None
    sub = payload.get("sub")
    return sub if isinstance(sub, str) else None


def required_user_id(authorization: str | None = Header(default=None)) -> str:
    """FastAPI dependency: raises 401 if no valid token is present."""
    token = _extract_token(authorization)
    if token is None:
        raise HTTPException(
            status_code=401,
            detail={"code": "AUTH_REQUIRED", "message": "Authentication required."},
        )
    try:
        payload = decode_access_token(token)
    except AuthError as error:
        raise HTTPException(
            status_code=401,
            detail={"code": error.code, "message": str(error)},
        ) from error
    sub = payload.get("sub")
    if not isinstance(sub, str):
        raise HTTPException(
            status_code=401,
            detail={"code": "INVALID_TOKEN", "message": "Token payload missing subject."},
        )
    return sub
