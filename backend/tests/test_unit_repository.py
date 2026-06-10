"""Integration tests for UnitRepository (Postgres-gated on TEST_DATABASE_URL)."""
from __future__ import annotations

from .conftest import seed_path_with_units


def test_get_unit_returns_full_9_slot_payload(gated_db) -> None:
    seed = seed_path_with_units(gated_db)

    from app.repositories import unit_repository

    unit = unit_repository.get_unit(seed["unit_a_id"])

    assert unit is not None
    # Slots 1, 2, 3, 4, 5
    assert unit["title"] == "Tokenization"
    assert unit["definition"].startswith("Tokens are how")
    assert unit["tradeOffFraming"].startswith("when this matters")
    assert unit["biteMd"] == "bite text A"
    assert unit["depthMd"] == "depth text A"
    # Slot 9
    assert unit["prereqUnitIds"] == []
    # Slot 7 — sources, primary first
    assert len(unit["sources"]) == 2
    assert unit["sources"][0]["primarySource"] is True
    assert unit["sources"][0]["url"] == "https://example.com/bpe"
    # Slot 6 — calibration tags
    assert len(unit["calibrationTags"]) == 2
    assert {t["tier"] for t in unit["calibrationTags"]} == {"settled", "contested"}
    # Slot 8 — decision prompt + rubric (latest version) + criteria
    assert unit["decisionPrompt"] is not None
    assert unit["decisionPrompt"]["promptMd"].startswith("How would you")
    assert unit["rubric"] is not None
    assert unit["rubric"]["version"] == 1
    assert [c["text"] for c in unit["rubric"]["criteria"]] == [
        "Names the trade-off.",
        "Names a concrete scenario.",
    ]


def test_get_unit_returns_latest_rubric_version(gated_db) -> None:
    seed = seed_path_with_units(gated_db)

    # Add a v2 rubric with one different criterion.
    with gated_db() as conn:
        prompt_row = conn.execute(
            "SELECT id FROM decision_prompts WHERE unit_id = 'unit-a'"
        ).fetchone()
        v2 = conn.execute(
            """
            INSERT INTO rubrics (decision_prompt_id, version, rubric_json)
            VALUES (%s, 2, '{"v": 2}'::jsonb)
            RETURNING id
            """,
            (prompt_row["id"],),
        ).fetchone()
        conn.execute(
            """
            INSERT INTO rubric_criteria (rubric_id, position, criterion_text)
            VALUES (%s, 1, 'V2 criterion only.')
            """,
            (v2["id"],),
        )
        conn.commit()

    from app.repositories import unit_repository

    unit = unit_repository.get_unit(seed["unit_a_id"])

    assert unit["rubric"]["version"] == 2
    assert [c["text"] for c in unit["rubric"]["criteria"]] == ["V2 criterion only."]


def test_get_unit_returns_none_for_unknown_id(gated_db) -> None:
    from app.repositories import unit_repository

    assert unit_repository.get_unit("does-not-exist") is None


def test_get_unit_handles_missing_decision_prompt(gated_db) -> None:
    seed = seed_path_with_units(gated_db)

    from app.repositories import unit_repository

    # unit-b in the seed has no decision prompt or sources.
    unit = unit_repository.get_unit(seed["unit_b_id"])

    assert unit is not None
    assert unit["decisionPrompt"] is None
    assert unit["rubric"] is None
    assert unit["sources"] == []
    assert unit["calibrationTags"] == []
    assert unit["prereqUnitIds"] == ["unit-a"]


def test_get_unit_published_only_hides_drafts(gated_db) -> None:
    seed = seed_path_with_units(gated_db)

    with gated_db() as conn:
        conn.execute(
            """
            INSERT INTO units (
                id, path_id, slug, position, title, definition,
                trade_off_framing, bite_md, depth_md, prereq_unit_ids, status
            ) VALUES
                ('unit-draft', 'path-llm-pms', 'monitoring', 3,
                 'Monitoring', 'def', 'framing', 'bite', 'depth', '{}', 'draft')
            """,
        )
        conn.commit()

    from app.repositories import unit_repository

    # The public-endpoint read treats the draft as absent...
    assert unit_repository.get_unit("unit-draft", published_only=True) is None
    # ...while the default read (regression runner, ingest tooling) sees it,
    # and published units pass either way.
    assert unit_repository.get_unit("unit-draft") is not None
    assert unit_repository.get_unit(seed["unit_a_id"], published_only=True) is not None


def test_list_units_for_path_returns_manifest_only(gated_db) -> None:
    seed = seed_path_with_units(gated_db)

    from app.repositories import unit_repository

    units = unit_repository.list_units_for_path(seed["path_id"])

    assert [u["id"] for u in units] == ["unit-a", "unit-b"]
    assert set(units[0].keys()) == {"id", "slug", "title", "position", "status"}
