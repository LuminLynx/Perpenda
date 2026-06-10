"""Email verification — endpoint behavior and code lifecycle.

The endpoint tests monkeypatch `app.main.EMAIL_VERIFICATION_REQUIRED`
(module attribute, read at call time) and `app.main.send_verification_email`
so no real mail is ever attempted. The lifecycle tests are Postgres-gated.
"""
from __future__ import annotations

import os

import pytest

os.environ.setdefault("JWT_SECRET", "test-secret-key-for-email-verification")

from fastapi.testclient import TestClient  # noqa: E402

from app.main import app  # noqa: E402
from app.repositories import auth_rate_limit_repository  # noqa: E402
from app.repositories import email_verification_repository  # noqa: E402

from .conftest import seed_path_with_units  # noqa: E402


@pytest.fixture
def no_rate_limit(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(
        auth_rate_limit_repository,
        "check_and_record_auth_attempt",
        lambda *_args, **_kwargs: None,
    )
    monkeypatch.setattr(
        auth_rate_limit_repository,
        "clear_auth_attempts",
        lambda *_args, **_kwargs: None,
    )


@pytest.fixture
def sent_emails(monkeypatch: pytest.MonkeyPatch) -> list[dict[str, str]]:
    captured: list[dict[str, str]] = []
    monkeypatch.setattr(
        "app.main.send_verification_email",
        lambda to, code: captured.append({"to": to, "code": code}),
    )
    return captured


# ----- endpoint behavior (no DB) -----


def test_signup_with_verification_required_returns_no_token(
    monkeypatch: pytest.MonkeyPatch, no_rate_limit, sent_emails
) -> None:
    monkeypatch.setattr("app.main.EMAIL_VERIFICATION_REQUIRED", True)
    monkeypatch.setattr("app.main.get_user_by_email", lambda _email: None)

    created: dict = {}

    def _create_user(**kwargs):
        created.update(kwargs)
        return {
            "id": "u-new",
            "email": kwargs["email"],
            "displayName": kwargs["display_name"],
            "createdAt": "2026-06-10T00:00:00+00:00",
            "emailVerified": kwargs["email_verified"],
        }

    monkeypatch.setattr("app.main.create_user", _create_user)
    monkeypatch.setattr(
        email_verification_repository, "issue_code", lambda _uid: "123456"
    )

    client = TestClient(app)
    response = client.post(
        "/api/v1/auth/signup",
        json={"email": "new@example.com", "password": "whatever123", "displayName": "Ada"},
    )

    assert response.status_code == 201
    data = response.json()["data"]
    assert data["verificationRequired"] is True
    assert "token" not in data
    assert created["email_verified"] is False
    assert sent_emails == [{"to": "new@example.com", "code": "123456"}]


def test_signup_with_flag_off_keeps_current_behavior(
    monkeypatch: pytest.MonkeyPatch, no_rate_limit, sent_emails
) -> None:
    monkeypatch.setattr("app.main.EMAIL_VERIFICATION_REQUIRED", False)
    monkeypatch.setattr("app.main.get_user_by_email", lambda _email: None)
    monkeypatch.setattr(
        "app.main.create_user",
        lambda **kwargs: {
            "id": "u-new",
            "email": kwargs["email"],
            "displayName": kwargs["display_name"],
            "createdAt": "2026-06-10T00:00:00+00:00",
            "emailVerified": kwargs["email_verified"],
        },
    )

    client = TestClient(app)
    response = client.post(
        "/api/v1/auth/signup",
        json={"email": "new@example.com", "password": "whatever123", "displayName": "Ada"},
    )

    assert response.status_code == 201
    data = response.json()["data"]
    assert data["token"]
    assert data["user"]["emailVerified"] is True
    assert sent_emails == []


def test_login_unverified_returns_403_after_password_check(
    monkeypatch: pytest.MonkeyPatch, no_rate_limit
) -> None:
    monkeypatch.setattr("app.main.EMAIL_VERIFICATION_REQUIRED", True)
    monkeypatch.setattr(
        "app.main.get_user_auth_by_email",
        lambda _email: {
            "id": "u-1",
            "email": "pm@example.com",
            "display_name": "PM",
            "created_at": "2026-06-10T00:00:00+00:00",
            "password_hash": "hash",
            "email_verified_at": None,
        },
    )
    monkeypatch.setattr("app.main.verify_login", lambda _pw, _hash: True)

    client = TestClient(app)
    response = client.post(
        "/api/v1/auth/login",
        json={"email": "pm@example.com", "password": "whatever123"},
    )

    assert response.status_code == 403
    assert response.json()["error"]["code"] == "EMAIL_NOT_VERIFIED"


def test_login_wrong_password_beats_verification_gate(
    monkeypatch: pytest.MonkeyPatch, no_rate_limit
) -> None:
    # The 403 must never appear without the right password — otherwise it
    # would confirm which emails have accounts.
    monkeypatch.setattr("app.main.EMAIL_VERIFICATION_REQUIRED", True)
    monkeypatch.setattr(
        "app.main.get_user_auth_by_email",
        lambda _email: {
            "id": "u-1",
            "email": "pm@example.com",
            "display_name": "PM",
            "created_at": "2026-06-10T00:00:00+00:00",
            "password_hash": "hash",
            "email_verified_at": None,
        },
    )
    monkeypatch.setattr("app.main.verify_login", lambda _pw, _hash: False)

    client = TestClient(app)
    response = client.post(
        "/api/v1/auth/login",
        json={"email": "pm@example.com", "password": "wrong"},
    )

    assert response.status_code == 401
    assert response.json()["error"]["code"] == "INVALID_CREDENTIALS"


def test_verify_email_unknown_address_returns_generic_invalid(
    monkeypatch: pytest.MonkeyPatch, no_rate_limit
) -> None:
    monkeypatch.setattr("app.main.get_user_by_email", lambda _email: None)

    client = TestClient(app)
    response = client.post(
        "/api/v1/auth/verify-email",
        json={"email": "nobody@example.com", "code": "123456"},
    )

    assert response.status_code == 400
    assert response.json()["error"]["code"] == "INVALID_CODE"


def test_resend_unknown_address_still_says_sent(
    monkeypatch: pytest.MonkeyPatch, no_rate_limit, sent_emails
) -> None:
    monkeypatch.setattr("app.main.get_user_by_email", lambda _email: None)

    client = TestClient(app)
    response = client.post(
        "/api/v1/auth/resend-verification",
        json={"email": "nobody@example.com"},
    )

    assert response.status_code == 200
    assert response.json()["data"] == {"sent": True}
    assert sent_emails == []


# ----- code lifecycle (Postgres-gated) -----


def test_full_signup_verify_login_round_trip(
    gated_db, monkeypatch: pytest.MonkeyPatch, sent_emails
) -> None:
    monkeypatch.setattr("app.main.EMAIL_VERIFICATION_REQUIRED", True)
    client = TestClient(app)

    signup = client.post(
        "/api/v1/auth/signup",
        json={"email": "ada@example.com", "password": "whatever123", "displayName": "Ada"},
    )
    assert signup.status_code == 201
    assert signup.json()["data"]["verificationRequired"] is True
    assert len(sent_emails) == 1
    code = sent_emails[0]["code"]

    login_before = client.post(
        "/api/v1/auth/login",
        json={"email": "ada@example.com", "password": "whatever123"},
    )
    assert login_before.status_code == 403
    assert login_before.json()["error"]["code"] == "EMAIL_NOT_VERIFIED"

    wrong = client.post(
        "/api/v1/auth/verify-email",
        json={"email": "ada@example.com", "code": "000000" if code != "000000" else "111111"},
    )
    assert wrong.status_code == 400
    assert wrong.json()["error"]["code"] == "INVALID_CODE"

    verify = client.post(
        "/api/v1/auth/verify-email",
        json={"email": "ada@example.com", "code": code},
    )
    assert verify.status_code == 200
    verified = verify.json()["data"]
    assert verified["token"]
    assert verified["user"]["emailVerified"] is True

    # The code is single-use: replaying it fails.
    replay = client.post(
        "/api/v1/auth/verify-email",
        json={"email": "ada@example.com", "code": code},
    )
    assert replay.status_code == 400

    login_after = client.post(
        "/api/v1/auth/login",
        json={"email": "ada@example.com", "password": "whatever123"},
    )
    assert login_after.status_code == 200
    assert login_after.json()["data"]["user"]["emailVerified"] is True


def test_resend_replaces_the_outstanding_code(gated_db, monkeypatch, sent_emails) -> None:
    monkeypatch.setattr("app.main.EMAIL_VERIFICATION_REQUIRED", True)
    client = TestClient(app)
    client.post(
        "/api/v1/auth/signup",
        json={"email": "ada@example.com", "password": "whatever123", "displayName": "Ada"},
    )
    first_code = sent_emails[0]["code"]

    client.post("/api/v1/auth/resend-verification", json={"email": "ada@example.com"})
    assert len(sent_emails) == 2
    second_code = sent_emails[1]["code"]

    if first_code != second_code:
        stale = client.post(
            "/api/v1/auth/verify-email",
            json={"email": "ada@example.com", "code": first_code},
        )
        assert stale.status_code == 400

    fresh = client.post(
        "/api/v1/auth/verify-email",
        json={"email": "ada@example.com", "code": second_code},
    )
    assert fresh.status_code == 200


def test_code_expiry_and_attempt_cap(gated_db) -> None:
    seed = seed_path_with_units(gated_db)
    user_id = seed["user_id"]

    # Expired code: TTL of 0 minutes is immediately stale.
    email_verification_repository.issue_code(user_id, ttl_minutes=0)
    with pytest.raises(email_verification_repository.CodeExpiredError):
        email_verification_repository.verify_code(user_id, "000000")
    # The expired row was consumed; the next failure is generic.
    with pytest.raises(email_verification_repository.CodeInvalidError):
        email_verification_repository.verify_code(user_id, "000000")

    # Attempt cap: after max_attempts wrong guesses, even the right code
    # is rejected — a fresh code must be issued.
    code = email_verification_repository.issue_code(user_id)
    for _ in range(2):
        with pytest.raises(email_verification_repository.CodeInvalidError):
            email_verification_repository.verify_code(
                user_id, "999999" if code != "999999" else "888888", max_attempts=2
            )
    with pytest.raises(email_verification_repository.CodeInvalidError):
        email_verification_repository.verify_code(user_id, code, max_attempts=2)


def test_grandfathered_user_logs_in_with_verification_on(
    gated_db, monkeypatch: pytest.MonkeyPatch, sent_emails
) -> None:
    # An account created while the flag was OFF (e.g. every v1.0 user, via
    # the migration backfill or the verified-at-creation default) must not
    # be locked out when the flag turns on.
    monkeypatch.setattr("app.main.EMAIL_VERIFICATION_REQUIRED", False)
    client = TestClient(app)
    client.post(
        "/api/v1/auth/signup",
        json={"email": "old@example.com", "password": "whatever123", "displayName": "Old"},
    )

    monkeypatch.setattr("app.main.EMAIL_VERIFICATION_REQUIRED", True)
    login = client.post(
        "/api/v1/auth/login",
        json={"email": "old@example.com", "password": "whatever123"},
    )
    assert login.status_code == 200
    assert login.json()["data"]["token"]
