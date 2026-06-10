"""Password reset — endpoint behavior and code lifecycle.

Mirrors test_email_verification.py: endpoint tests monkeypatch
`app.main.send_password_reset_email` so no real mail is attempted; the
lifecycle tests are Postgres-gated.
"""
from __future__ import annotations

import os

import pytest

os.environ.setdefault("JWT_SECRET", "test-secret-key-for-password-reset")

from fastapi.testclient import TestClient  # noqa: E402

from app.main import app  # noqa: E402
from app.repositories import auth_rate_limit_repository  # noqa: E402
from app.repositories import password_reset_repository  # noqa: E402

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
        "app.main.send_password_reset_email",
        lambda to, code: captured.append({"to": to, "code": code}),
    )
    return captured


# ----- endpoint behavior (no DB) -----


def test_request_reset_unknown_address_still_says_sent(
    monkeypatch: pytest.MonkeyPatch, no_rate_limit, sent_emails
) -> None:
    monkeypatch.setattr("app.main.get_user_by_email", lambda _email: None)

    client = TestClient(app)
    response = client.post(
        "/api/v1/auth/request-password-reset",
        json={"email": "nobody@example.com"},
    )

    assert response.status_code == 200
    assert response.json()["data"] == {"sent": True}
    assert sent_emails == []


def test_reset_unknown_address_returns_generic_invalid(
    monkeypatch: pytest.MonkeyPatch, no_rate_limit
) -> None:
    monkeypatch.setattr("app.main.get_user_by_email", lambda _email: None)

    client = TestClient(app)
    response = client.post(
        "/api/v1/auth/reset-password",
        json={"email": "nobody@example.com", "code": "123456", "newPassword": "newpassword1"},
    )

    assert response.status_code == 400
    assert response.json()["error"]["code"] == "INVALID_CODE"


def test_reset_rejects_weak_password_before_touching_the_code(
    monkeypatch: pytest.MonkeyPatch, no_rate_limit
) -> None:
    def _must_not_be_called(*_args, **_kwargs):
        raise AssertionError("code must not be redeemed for a weak password")

    monkeypatch.setattr(
        password_reset_repository, "redeem_code_and_set_password", _must_not_be_called
    )

    client = TestClient(app)
    response = client.post(
        "/api/v1/auth/reset-password",
        json={"email": "pm@example.com", "code": "123456", "newPassword": "short"},
    )

    assert response.status_code == 400
    assert response.json()["error"]["code"] == "WEAK_PASSWORD"


# ----- code lifecycle (Postgres-gated) -----


def test_full_forgot_password_round_trip(gated_db, monkeypatch, sent_emails) -> None:
    monkeypatch.setattr("app.main.EMAIL_VERIFICATION_REQUIRED", False)
    client = TestClient(app)

    signup = client.post(
        "/api/v1/auth/signup",
        json={"email": "ada@example.com", "password": "oldpassword1", "displayName": "Ada Lovelace"},
    )
    assert signup.status_code == 201

    requested = client.post(
        "/api/v1/auth/request-password-reset", json={"email": "ada@example.com"}
    )
    assert requested.status_code == 200
    assert len(sent_emails) == 1
    code = sent_emails[0]["code"]

    wrong = client.post(
        "/api/v1/auth/reset-password",
        json={
            "email": "ada@example.com",
            "code": "000000" if code != "000000" else "111111",
            "newPassword": "newpassword1",
        },
    )
    assert wrong.status_code == 400
    assert wrong.json()["error"]["code"] == "INVALID_CODE"

    reset = client.post(
        "/api/v1/auth/reset-password",
        json={"email": "ada@example.com", "code": code, "newPassword": "newpassword1"},
    )
    assert reset.status_code == 200
    assert reset.json()["data"]["token"]

    # The code is single-use: replaying it fails.
    replay = client.post(
        "/api/v1/auth/reset-password",
        json={"email": "ada@example.com", "code": code, "newPassword": "newpassword2"},
    )
    assert replay.status_code == 400

    old_login = client.post(
        "/api/v1/auth/login",
        json={"email": "ada@example.com", "password": "oldpassword1"},
    )
    assert old_login.status_code == 401

    new_login = client.post(
        "/api/v1/auth/login",
        json={"email": "ada@example.com", "password": "newpassword1"},
    )
    assert new_login.status_code == 200


def test_reset_also_verifies_an_unverified_account(
    gated_db, monkeypatch: pytest.MonkeyPatch, sent_emails
) -> None:
    # An unverified account whose verification code lapsed can recover
    # entirely through password reset: redeeming proves inbox ownership.
    monkeypatch.setattr("app.main.EMAIL_VERIFICATION_REQUIRED", True)
    monkeypatch.setattr(
        "app.main.send_verification_email", lambda _to, _code: None
    )
    client = TestClient(app)
    client.post(
        "/api/v1/auth/signup",
        json={"email": "ada@example.com", "password": "oldpassword1", "displayName": "Ada Lovelace"},
    )

    client.post("/api/v1/auth/request-password-reset", json={"email": "ada@example.com"})
    code = sent_emails[0]["code"]
    reset = client.post(
        "/api/v1/auth/reset-password",
        json={"email": "ada@example.com", "code": code, "newPassword": "newpassword1"},
    )
    assert reset.status_code == 200
    assert reset.json()["data"]["user"]["emailVerified"] is True

    # And login now passes the verification gate.
    login = client.post(
        "/api/v1/auth/login",
        json={"email": "ada@example.com", "password": "newpassword1"},
    )
    assert login.status_code == 200


def test_reset_code_attempt_cap(gated_db) -> None:
    seed = seed_path_with_units(gated_db)
    user_id = seed["user_id"]

    code = password_reset_repository.issue_code(user_id)
    for _ in range(2):
        with pytest.raises(password_reset_repository.CodeInvalidError):
            password_reset_repository.redeem_code_and_set_password(
                user_id,
                "999999" if code != "999999" else "888888",
                "irrelevant-hash",
                max_attempts=2,
            )
    with pytest.raises(password_reset_repository.CodeInvalidError):
        password_reset_repository.redeem_code_and_set_password(
            user_id, code, "irrelevant-hash", max_attempts=2
        )
