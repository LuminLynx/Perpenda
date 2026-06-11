"""FastAPI endpoint tests for the path-centric surface.

The 401/404 tests run without a database by stubbing repository functions
and overriding the auth dependency. The end-to-end happy-path test is
gated on TEST_DATABASE_URL via the `gated_db` fixture in conftest.
"""
from __future__ import annotations

from typing import Any

import pytest
from fastapi.testclient import TestClient

from app.auth import create_access_token
from app.main import app
from app.repositories import (
    completion_repository,
    path_repository,
    review_repository,
    unit_repository,
)

from .conftest import seed_path_with_units


@pytest.fixture
def client() -> TestClient:
    return TestClient(app)


@pytest.fixture
def auth_header() -> dict[str, str]:
    token = create_access_token("u-endpoint-test")
    return {"Authorization": f"Bearer {token}"}


# ----- 401 (auth required) -----


def test_units_endpoint_requires_auth(client: TestClient) -> None:
    response = client.get("/api/v1/units/any-id")
    assert response.status_code == 401
    assert response.json()["detail"]["code"] == "AUTH_REQUIRED"


def test_post_completion_endpoint_is_gone(client: TestClient, auth_header) -> None:
    # POST /completions recorded a completion with no grader involvement —
    # an authenticated curl could mark the whole path complete. Removed;
    # completions are recorded exclusively by the grade flow. This guards
    # against it quietly coming back. (405: GET still serves this path.)
    response = client.post(
        "/api/v1/completions", json={"unitId": "any-id"}, headers=auth_header
    )
    assert response.status_code == 405


def test_list_completions_endpoint_requires_auth(client: TestClient) -> None:
    response = client.get("/api/v1/completions")
    assert response.status_code == 401
    assert response.json()["detail"]["code"] == "AUTH_REQUIRED"


def test_list_completions_returns_user_completions(
    client: TestClient, monkeypatch: pytest.MonkeyPatch, auth_header: dict[str, str]
) -> None:
    captured: dict[str, Any] = {}

    def _list(user_id: str) -> list[dict[str, Any]]:
        captured["user_id"] = user_id
        return [
            {"id": 2, "userId": user_id, "pathId": "p1", "unitId": "u2", "completedAt": None},
            {"id": 1, "userId": user_id, "pathId": "p1", "unitId": "u1", "completedAt": None},
        ]

    monkeypatch.setattr(completion_repository, "list_completions", _list)

    response = client.get("/api/v1/completions", headers=auth_header)
    assert response.status_code == 200
    body = response.json()
    assert [row["unitId"] for row in body["data"]] == ["u2", "u1"]
    assert "user_id" in captured


# ----- 404 (resource missing) — repository monkeypatched, no DB needed -----


def test_get_path_returns_404_envelope(
    client: TestClient, monkeypatch: pytest.MonkeyPatch
) -> None:
    monkeypatch.setattr(path_repository, "get_path", lambda _id, **_kwargs: None)
    response = client.get("/api/v1/paths/missing")
    assert response.status_code == 404
    body = response.json()
    assert body["data"] is None
    assert body["error"]["code"] == "PATH_NOT_FOUND"
    assert "missing" in body["error"]["message"]


def test_get_unit_returns_404_envelope(
    client: TestClient, monkeypatch: pytest.MonkeyPatch, auth_header: dict[str, str]
) -> None:
    monkeypatch.setattr(unit_repository, "get_unit", lambda _id, **_kwargs: None)
    response = client.get("/api/v1/units/missing", headers=auth_header)
    assert response.status_code == 404
    body = response.json()
    assert body["data"] is None
    assert body["error"]["code"] == "UNIT_NOT_FOUND"


# ----- Happy paths via stubbed repositories (no DB) -----


def test_get_path_returns_data_envelope(
    client: TestClient, monkeypatch: pytest.MonkeyPatch
) -> None:
    fake_path = {
        "id": "p1",
        "slug": "s",
        "title": "T",
        "description": "",
        "createdAt": None,
        "updatedAt": None,
        "units": [
            {"id": "u1", "slug": "x", "title": "X", "position": 1, "status": "published"},
        ],
    }
    monkeypatch.setattr(path_repository, "get_path", lambda _id: fake_path)
    response = client.get("/api/v1/paths/p1")
    assert response.status_code == 200
    body = response.json()
    assert body["error"] is None
    assert body["data"]["units"][0]["id"] == "u1"


# ----- DB-gated end-to-end -----


def test_end_to_end_flow_against_real_db(
    client: TestClient, gated_db, auth_header
) -> None:
    seed = seed_path_with_units(gated_db)
    # Auth identity must match the seeded user so the completion FK resolves.
    auth_header = {"Authorization": f"Bearer {create_access_token(seed['user_id'])}"}

    path_resp = client.get(f"/api/v1/paths/{seed['path_id']}")
    assert path_resp.status_code == 200
    assert [u["id"] for u in path_resp.json()["data"]["units"]] == ["unit-a", "unit-b"]

    unit_resp = client.get(f"/api/v1/units/{seed['unit_a_id']}", headers=auth_header)
    assert unit_resp.status_code == 200
    unit = unit_resp.json()["data"]
    assert unit["title"] == "Tokenization"
    assert len(unit["sources"]) == 2
    assert unit["rubric"]["version"] == 1

    # Completions are recorded by the grade flow; exercise the same
    # repository call it uses (the standalone POST endpoint was removed).
    first = completion_repository.record_completion(
        user_id=seed["user_id"], unit_id="unit-a"
    )
    assert first["alreadyCompleted"] is False
    assert first["completion"]["unitId"] == "unit-a"

    repeat = completion_repository.record_completion(
        user_id=seed["user_id"], unit_id="unit-a"
    )
    assert repeat["alreadyCompleted"] is True

    listed = client.get("/api/v1/completions", headers=auth_header)
    assert listed.status_code == 200
    assert [c["unitId"] for c in listed.json()["data"]] == ["unit-a"]


def test_end_to_end_review_lifecycle_against_real_db(
    client: TestClient, gated_db, auth_header
) -> None:
    """F5 step 4 — EXECUTION.md:167 backend half: a completion seeds
    a spaced review that surfaces on schedule, advances when done,
    and is gated against early ticks — exercised through the real
    HTTP stack + real DB in one flow.
    """
    seed = seed_path_with_units(gated_db)
    auth_header = {"Authorization": f"Bearer {create_access_token(seed['user_id'])}"}

    # 1. Completing a unit seeds its first review (D3). Recorded via the
    # repository — the same call the grade flow makes (the standalone
    # POST endpoint was removed).
    result = completion_repository.record_completion(
        user_id=seed["user_id"], unit_id="unit-a"
    )
    assert result["alreadyCompleted"] is False

    # 2. Nothing is due yet — first review is +1 day out (D3).
    due_resp = client.get("/api/v1/review-schedule", headers=auth_header)
    assert due_resp.status_code == 200
    assert due_resp.json()["data"] == []

    # 3. Make it due (backdate due_at), then it surfaces (D5).
    with gated_db() as conn:
        conn.execute(
            "UPDATE review_schedule SET due_at = NOW() - make_interval(secs => 1) "
            "WHERE user_id = %s AND unit_id = %s",
            (seed["user_id"], "unit-a"),
        )
        conn.commit()
    due_resp = client.get("/api/v1/review-schedule", headers=auth_header)
    body = due_resp.json()["data"]
    assert [r["unitId"] for r in body] == ["unit-a"]
    assert body[0]["slug"] == "tokenization"
    assert body[0]["title"] == "Tokenization"
    assert body[0]["intervalDays"] == 1

    # 4. Marking it reviewed advances the ladder 1 -> 3 (D6).
    reviewed = client.post(
        "/api/v1/review-schedule/unit-a/reviewed", headers=auth_header
    )
    assert reviewed.status_code == 200
    assert reviewed.json()["data"]["intervalDays"] == 3

    # 5. It drops off the due list — due_at advanced ~3 days out.
    assert client.get(
        "/api/v1/review-schedule", headers=auth_header
    ).json()["data"] == []

    # 6. Ticking again now is gated: not due -> 409 (D6 amendment).
    early = client.post(
        "/api/v1/review-schedule/unit-a/reviewed", headers=auth_header
    )
    assert early.status_code == 409
    assert early.json()["error"]["code"] == "REVIEW_NOT_DUE"

    # 7. A never-completed unit has no schedule -> 404.
    missing = client.post(
        "/api/v1/review-schedule/unit-b/reviewed", headers=auth_header
    )
    assert missing.status_code == 404
    assert missing.json()["error"]["code"] == "REVIEW_NOT_SCHEDULED"


# ----- F5 spaced review endpoints -----


def test_review_schedule_endpoint_requires_auth(client: TestClient) -> None:
    response = client.get("/api/v1/review-schedule")
    assert response.status_code == 401
    assert response.json()["detail"]["code"] == "AUTH_REQUIRED"


def test_post_reviewed_endpoint_requires_auth(client: TestClient) -> None:
    response = client.post("/api/v1/review-schedule/any-unit/reviewed")
    assert response.status_code == 401
    assert response.json()["detail"]["code"] == "AUTH_REQUIRED"


def test_review_schedule_returns_due_list(
    client: TestClient, monkeypatch: pytest.MonkeyPatch, auth_header: dict[str, str]
) -> None:
    captured: dict[str, Any] = {}

    def _list_due(user_id: str, due_before: Any = None) -> list[dict[str, Any]]:
        captured["user_id"] = user_id
        captured["due_before"] = due_before
        return [
            {
                "unitId": "unit-a",
                "slug": "tokenization",
                "title": "Tokenization",
                "dueAt": None,
                "intervalDays": 1,
                "lastReviewedAt": None,
            }
        ]

    monkeypatch.setattr(review_repository, "list_due", _list_due)

    response = client.get("/api/v1/review-schedule", headers=auth_header)
    assert response.status_code == 200
    body = response.json()
    assert [row["unitId"] for row in body["data"]] == ["unit-a"]
    assert captured["user_id"] == "u-endpoint-test"
    assert captured["due_before"] is None


def test_review_schedule_invalid_due_before_returns_400(
    client: TestClient, auth_header: dict[str, str]
) -> None:
    response = client.get(
        "/api/v1/review-schedule",
        params={"due_before": "not-a-timestamp"},
        headers=auth_header,
    )
    assert response.status_code == 400
    assert response.json()["error"]["code"] == "INVALID_DUE_BEFORE"


def test_post_reviewed_returns_404_when_not_scheduled(
    client: TestClient, monkeypatch: pytest.MonkeyPatch, auth_header: dict[str, str]
) -> None:
    def _raise(user_id: str, unit_id: str) -> dict[str, Any]:
        raise review_repository.ReviewNotScheduledError(f"{user_id}/{unit_id}")

    monkeypatch.setattr(review_repository, "mark_reviewed", _raise)

    response = client.post(
        "/api/v1/review-schedule/unit-x/reviewed", headers=auth_header
    )
    assert response.status_code == 404
    assert response.json()["error"]["code"] == "REVIEW_NOT_SCHEDULED"


def test_post_reviewed_returns_advanced_row(
    client: TestClient, monkeypatch: pytest.MonkeyPatch, auth_header: dict[str, str]
) -> None:
    captured: dict[str, Any] = {}

    def _mark(user_id: str, unit_id: str) -> dict[str, Any]:
        captured["user_id"] = user_id
        captured["unit_id"] = unit_id
        return {
            "id": 7,
            "userId": user_id,
            "unitId": unit_id,
            "dueAt": None,
            "intervalDays": 3,
            "lastReviewedAt": None,
        }

    monkeypatch.setattr(review_repository, "mark_reviewed", _mark)

    response = client.post(
        "/api/v1/review-schedule/unit-a/reviewed", headers=auth_header
    )
    assert response.status_code == 200
    assert response.json()["data"]["intervalDays"] == 3
    assert captured == {"user_id": "u-endpoint-test", "unit_id": "unit-a"}


def test_post_reviewed_returns_409_when_not_due(
    client: TestClient, monkeypatch: pytest.MonkeyPatch, auth_header: dict[str, str]
) -> None:
    def _raise(user_id: str, unit_id: str) -> dict[str, Any]:
        raise review_repository.ReviewNotDueError(f"{user_id}/{unit_id}")

    monkeypatch.setattr(review_repository, "mark_reviewed", _raise)

    response = client.post(
        "/api/v1/review-schedule/unit-a/reviewed", headers=auth_header
    )
    assert response.status_code == 409
    assert response.json()["error"]["code"] == "REVIEW_NOT_DUE"


def test_review_schedule_naive_due_before_returns_400(
    client: TestClient, auth_header: dict[str, str]
) -> None:
    # Parses fine but has no UTC offset -> rejected (D5/P2).
    response = client.get(
        "/api/v1/review-schedule",
        params={"due_before": "2026-05-18T10:00:00"},
        headers=auth_header,
    )
    assert response.status_code == 400
    assert response.json()["error"]["code"] == "INVALID_DUE_BEFORE"
