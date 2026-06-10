"""Shared pytest fixtures for backend tests.

The path-centric repository tests are integration tests against a real
Postgres database. They are gated on the `TEST_DATABASE_URL` environment
variable: when it's unset (the default in most local runs and the
no-DB CI matrix), the gated tests skip cleanly.

The fixture monkeypatches `app.config.RESOLVED_DATABASE_URL` and
`app.db.RESOLVED_DATABASE_URL` to the test DB, runs the migration set,
and truncates the path-centric tables before each test for isolation.
"""
from __future__ import annotations

import os
from typing import Any, Callable

import pytest

TEST_DATABASE_URL = os.environ.get("TEST_DATABASE_URL")

# Tables truncated between gated tests. CASCADE handles the FK chain
# (grades → completions, rubric_criteria → rubrics → decision_prompts, etc.).
_TRUNCATE_TABLES = (
    "auth_attempts",
    "email_verification_codes",
    "password_reset_codes",
    "review_schedule",
    "completions",
    "rubric_criteria",
    "rubrics",
    "decision_prompts",
    "calibration_tags",
    "unit_sources",
    "units",
    "paths",
    "users",
)


@pytest.fixture
def gated_db(monkeypatch: pytest.MonkeyPatch) -> Callable[[], Any]:
    """Yield a `get_connection` callable bound to the test database.

    Skips the test when TEST_DATABASE_URL is unset.
    """
    if not TEST_DATABASE_URL:
        pytest.skip("TEST_DATABASE_URL not set; skipping Postgres-gated test")

    from app import config, db, migrations

    monkeypatch.setattr(config, "RESOLVED_DATABASE_URL", TEST_DATABASE_URL)
    monkeypatch.setattr(db, "RESOLVED_DATABASE_URL", TEST_DATABASE_URL)

    migrations.run_migrations()

    with db.get_connection() as conn:
        conn.execute(
            "TRUNCATE TABLE " + ", ".join(_TRUNCATE_TABLES) + " RESTART IDENTITY CASCADE"
        )
        conn.commit()

    return db.get_connection


def seed_path_with_units(get_connection: Callable[[], Any]) -> dict[str, Any]:
    """Insert one path + two units + sources/tags/decision-prompt/rubric.

    Returns the ids the test will assert against:
        {
            "path_id": ..., "unit_a_id": ..., "unit_b_id": ...,
            "rubric_id": ..., "criterion_ids": [..., ...],
            "user_id": ...,
        }
    """
    with get_connection() as conn:
        conn.execute(
            """
            INSERT INTO paths (id, slug, title, description)
            VALUES ('path-llm-pms', 'llm-systems-for-pms', 'LLM Systems for PMs', 'desc')
            """,
        )
        conn.execute(
            """
            INSERT INTO units (
                id, path_id, slug, position, title, definition,
                trade_off_framing, bite_md, depth_md, prereq_unit_ids, status
            ) VALUES
                ('unit-a', 'path-llm-pms', 'tokenization', 1,
                 'Tokenization', 'Tokens are how models read and bill.',
                 'when this matters / when this breaks / what it costs',
                 'bite text A', 'depth text A', '{}', 'published'),
                ('unit-b', 'path-llm-pms', 'context-windows', 2,
                 'Context Windows', 'Context windows bound what fits.',
                 'when this matters / when this breaks / what it costs',
                 'bite text B', 'depth text B', '{unit-a}', 'published')
            """,
        )
        conn.execute(
            """
            INSERT INTO unit_sources (unit_id, url, title, source_date, primary_source)
            VALUES
                ('unit-a', 'https://example.com/bpe', 'BPE Paper', '2015-08-31', TRUE),
                ('unit-a', 'https://example.com/tiktoken', 'tiktoken', '2024-11-12', FALSE)
            """,
        )
        conn.execute(
            """
            INSERT INTO calibration_tags (unit_id, claim, tier)
            VALUES
                ('unit-a', 'Models bill in tokens.', 'settled'),
                ('unit-a', 'Token-pricing is the right unit long-term.', 'contested')
            """,
        )
        prompt_row = conn.execute(
            """
            INSERT INTO decision_prompts (unit_id, prompt_md)
            VALUES ('unit-a', 'How would you estimate cost?')
            RETURNING id
            """,
        ).fetchone()
        prompt_id = prompt_row["id"]

        rubric_row = conn.execute(
            """
            INSERT INTO rubrics (decision_prompt_id, version, rubric_json)
            VALUES (%s, 1, '{"slots": []}'::jsonb)
            RETURNING id
            """,
            (prompt_id,),
        ).fetchone()
        rubric_id = rubric_row["id"]

        c1 = conn.execute(
            """
            INSERT INTO rubric_criteria (rubric_id, position, criterion_text)
            VALUES (%s, 1, 'Names the trade-off.')
            RETURNING id
            """,
            (rubric_id,),
        ).fetchone()
        c2 = conn.execute(
            """
            INSERT INTO rubric_criteria (rubric_id, position, criterion_text)
            VALUES (%s, 2, 'Names a concrete scenario.')
            RETURNING id
            """,
            (rubric_id,),
        ).fetchone()

        user_row = conn.execute(
            """
            INSERT INTO users (id, email, password_hash, display_name)
            VALUES ('u-test', 'pm@example.com', 'x', 'PM')
            RETURNING id
            """,
        ).fetchone()
        conn.commit()

    return {
        "path_id": "path-llm-pms",
        "unit_a_id": "unit-a",
        "unit_b_id": "unit-b",
        "rubric_id": rubric_id,
        "criterion_ids": [c1["id"], c2["id"]],
        "user_id": user_row["id"],
    }
