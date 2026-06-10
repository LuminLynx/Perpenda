from __future__ import annotations

import time

import pytest

from app.auth import (
    AuthError,
    create_access_token,
    decode_access_token,
    hash_password,
    normalize_email,
    validate_display_name,
    validate_email,
    validate_password,
    verify_login,
    verify_password,
)


def test_password_hash_round_trip() -> None:
    password = "Sup3rSecret!"
    hashed = hash_password(password)
    assert hashed != password
    assert verify_password(password, hashed)
    assert not verify_password("wrong-password", hashed)


def test_verify_password_accepts_preexisting_bcrypt_hash() -> None:
    # Backward compatibility: a $2b$ hash stored by the old passlib+bcrypt
    # stack must still verify after dropping passlib for direct bcrypt.
    existing = "$2b$12$G9ieOf4j4ziNLbNipYzPhOtiIk3QQsEd/ebI9PrIlsi3ha2HDVXES"
    assert verify_password("correct-horse-battery", existing)
    assert not verify_password("wrong", existing)


def test_hash_password_handles_overlong_password() -> None:
    # bcrypt only uses the first 72 bytes; an overlong password must hash and
    # verify (no ValueError), matching the classic truncation behavior.
    long_password = "x" * 200
    hashed = hash_password(long_password)
    assert verify_password(long_password, hashed)


def test_verify_password_returns_false_on_garbage_hash() -> None:
    assert verify_password("anything", "not-a-bcrypt-hash") is False


def test_verify_login_handles_unknown_user() -> None:
    # Unknown user (None hash) still returns False, having run bcrypt against
    # the dummy hash so timing doesn't reveal the account doesn't exist.
    assert verify_login("anything", None) is False


def test_verify_login_checks_real_hash() -> None:
    hashed = hash_password("correct-horse-battery")
    assert verify_login("correct-horse-battery", hashed) is True
    assert verify_login("wrong-password", hashed) is False


def test_jwt_round_trip() -> None:
    token = create_access_token("u-abc123")
    payload = decode_access_token(token)
    assert payload["sub"] == "u-abc123"
    assert "iat" in payload and "exp" in payload
    assert payload["exp"] > payload["iat"]


def test_jwt_invalid_token_raises() -> None:
    with pytest.raises(AuthError):
        decode_access_token("not-a-jwt-token")


def test_validate_email_normalizes_and_rejects_invalid() -> None:
    assert validate_email("  USER@Example.COM ") == "user@example.com"
    assert normalize_email("Foo@Bar.io") == "foo@bar.io"
    with pytest.raises(AuthError):
        validate_email("not-an-email")
    with pytest.raises(AuthError):
        validate_email("missing@dot")


def test_validate_password_min_length() -> None:
    validate_password("longenough")
    with pytest.raises(AuthError):
        validate_password("short")


def test_validate_display_name_requires_first_and_last() -> None:
    # Whitespace is normalized, including runs between names.
    assert validate_display_name("  Ada   Lovelace  ") == "Ada Lovelace"
    # Single word — even a long one — is rejected: first AND last required.
    with pytest.raises(AuthError):
        validate_display_name("Ada")
    # Each part needs 2+ characters ("J Doe" is an initial, not a name).
    with pytest.raises(AuthError):
        validate_display_name("J Doe")
    # Length bounds still apply.
    with pytest.raises(AuthError):
        validate_display_name("A")
    with pytest.raises(AuthError):
        validate_display_name("X" * 100)


# ---------------------------------------------------------------------------
# Per-account rate limiting on the auth endpoints (H2)
# ---------------------------------------------------------------------------

from fastapi.testclient import TestClient  # noqa: E402

from app.main import app  # noqa: E402
from app.repositories import auth_rate_limit_repository  # noqa: E402
from app.repositories.rate_limit_repository import RateLimitExceededError  # noqa: E402


def _block(*_args, **_kwargs):
    raise RateLimitExceededError(retry_after_seconds=30, limit=10, window_seconds=900)


def test_login_returns_429_when_rate_limited(monkeypatch) -> None:
    monkeypatch.setattr(
        auth_rate_limit_repository, "check_and_record_auth_attempt", _block
    )
    client = TestClient(app)
    response = client.post(
        "/api/v1/auth/login", json={"email": "a@b.com", "password": "whatever123"}
    )
    assert response.status_code == 429
    assert response.json()["error"]["code"] == "RATE_LIMITED"
    assert response.headers["Retry-After"] == "30"


def test_signup_returns_429_when_rate_limited(monkeypatch) -> None:
    monkeypatch.setattr(
        auth_rate_limit_repository, "check_and_record_auth_attempt", _block
    )
    client = TestClient(app)
    response = client.post(
        "/api/v1/auth/signup",
        json={"email": "a@b.com", "password": "whatever123", "displayName": "Ada Lovelace"},
    )
    assert response.status_code == 429
    assert response.json()["error"]["code"] == "RATE_LIMITED"


def test_signup_race_returns_409_not_500(monkeypatch) -> None:
    # Two concurrent signups for the same email can both pass the
    # get_user_by_email check; the loser's INSERT hits the users.email
    # UNIQUE constraint and must surface as the same 409 EMAIL_TAKEN,
    # not an unhandled 500.
    from psycopg.errors import UniqueViolation

    monkeypatch.setattr(
        auth_rate_limit_repository,
        "check_and_record_auth_attempt",
        lambda *_args, **_kwargs: None,
    )
    monkeypatch.setattr("app.main.get_user_by_email", lambda _email: None)

    def _lose_insert_race(**_kwargs):
        raise UniqueViolation("duplicate key value violates unique constraint")

    monkeypatch.setattr("app.main.create_user", _lose_insert_race)

    client = TestClient(app)
    response = client.post(
        "/api/v1/auth/signup",
        json={"email": "raced@example.com", "password": "whatever123", "displayName": "Ada Lovelace"},
    )
    assert response.status_code == 409
    assert response.json()["error"]["code"] == "EMAIL_TAKEN"


def test_auth_rate_limit_blocks_after_cap_then_clears(gated_db) -> None:
    key = "login:victim@example.com"
    # check+record is atomic: each call records one attempt; the cap+1-th
    # call is blocked without recording.
    for _ in range(3):
        auth_rate_limit_repository.check_and_record_auth_attempt(
            key, max_attempts=3, window_seconds=900
        )
    with pytest.raises(RateLimitExceededError):
        auth_rate_limit_repository.check_and_record_auth_attempt(
            key, max_attempts=3, window_seconds=900
        )
    # A successful login clears the counter.
    auth_rate_limit_repository.clear_auth_attempts(key)
    auth_rate_limit_repository.check_and_record_auth_attempt(
        key, max_attempts=3, window_seconds=900
    )

    # Per-account isolation: a different email is unaffected.
    other = "login:someone-else@example.com"
    auth_rate_limit_repository.check_and_record_auth_attempt(
        other, max_attempts=3, window_seconds=900
    )


def test_get_user_by_email_omits_password_hash(gated_db) -> None:
    # L2: the public lookup must never expose the hash; the auth-only
    # lookup may, since login needs it to verify a password.
    from app.repository import (
        create_user,
        get_user_auth_by_email,
        get_user_by_email,
    )

    create_user(
        email="leak@example.com",
        password_hash="hash-must-not-leak",
        display_name="Leaky",
    )

    safe = get_user_by_email("leak@example.com")
    assert safe is not None
    assert "password_hash" not in safe

    auth = get_user_auth_by_email("leak@example.com")
    assert auth is not None
    assert auth["password_hash"] == "hash-must-not-leak"


def test_delete_me_requires_auth() -> None:
    client = TestClient(app)
    assert client.delete("/api/v1/auth/me").status_code == 401


def test_delete_me_deletes_account(monkeypatch) -> None:
    captured: dict = {}

    def _delete(user_id: str) -> bool:
        captured["uid"] = user_id
        return True

    monkeypatch.setattr("app.main.delete_user", _delete)
    client = TestClient(app)
    token = create_access_token("u-del-test")
    resp = client.delete("/api/v1/auth/me", headers={"Authorization": f"Bearer {token}"})
    assert resp.status_code == 200
    assert resp.json()["data"]["deleted"] is True
    assert captured["uid"] == "u-del-test"


def test_delete_user_removes_account_and_cascades(gated_db) -> None:
    from app.repositories import completion_repository, grade_repository
    from app.repository import delete_user, get_user_by_id
    from .conftest import seed_path_with_units

    seed = seed_path_with_units(gated_db)
    user_id, unit_id, crit = seed["user_id"], seed["unit_a_id"], seed["criterion_ids"][0]
    completion = completion_repository.record_completion(user_id=user_id, unit_id=unit_id)["completion"]
    grade_repository.upsert_grades(
        completion_id=completion["id"],
        grades=[{"criterion_id": crit, "met": True, "confidence": 0.9, "rationale": "ok", "answer_quote": "x"}],
        flagged=False,
        user_id=user_id,
    )
    assert get_user_by_id(user_id) is not None

    assert delete_user(user_id) is True
    assert get_user_by_id(user_id) is None
    # completion (and its grades) cascade-deleted with the user
    assert grade_repository.list_grades_for_completion(completion["id"], user_id) == []
    # deleting an already-gone user is a no-op, returns False
    assert delete_user(user_id) is False
