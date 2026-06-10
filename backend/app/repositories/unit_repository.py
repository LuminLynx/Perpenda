"""UnitRepository — reads the full 9-slot unit payload.

Slot mapping (per migrations 016–018, anchored in STRATEGY.md § Unit anatomy):
    1. Title             -> units.title
    2. Definition        -> units.definition
    3. Trade-off framing -> units.trade_off_framing
    4. 90-second bite    -> units.bite_md
    5. Depth             -> units.depth_md
    6. Calibration tags  -> calibration_tags rows
    7. Sources           -> unit_sources rows
    8. Decision prompt + rubric -> decision_prompts + rubrics + rubric_criteria
    9. Prereq pointers   -> units.prereq_unit_ids

The rubric returned is the latest version (highest `rubrics.version`) for
the unit's decision prompt; older versions remain in the table for audit
but are not exposed by this read.
"""
from __future__ import annotations

from typing import Any

from ..db import get_connection


def _map_source_row(row: Any) -> dict[str, Any]:
    return {
        "id": row["id"],
        "url": row["url"],
        "title": row["title"],
        "date": row["source_date"],
        "primarySource": row["primary_source"],
    }


def _map_calibration_tag_row(row: Any) -> dict[str, Any]:
    return {
        "id": row["id"],
        "claim": row["claim"],
        "tier": row["tier"],
    }


def _map_rubric_criterion_row(row: Any) -> dict[str, Any]:
    return {
        "id": row["id"],
        "position": row["position"],
        "text": row["criterion_text"],
    }


def _map_unit_manifest_row(row: Any) -> dict[str, Any]:
    return {
        "id": row["id"],
        "slug": row["slug"],
        "title": row["title"],
        "position": row["position"],
        "status": row["status"],
    }


def _map_unit_full(unit_row: Any) -> dict[str, Any]:
    return {
        "id": unit_row["id"],
        "pathId": unit_row["path_id"],
        "slug": unit_row["slug"],
        "position": unit_row["position"],
        "title": unit_row["title"],
        "definition": unit_row["definition"],
        "tradeOffFraming": unit_row["trade_off_framing"],
        "biteMd": unit_row["bite_md"],
        "depthMd": unit_row["depth_md"],
        "prereqUnitIds": list(unit_row["prereq_unit_ids"] or []),
        "status": unit_row["status"],
        "createdAt": unit_row["created_at"],
        "updatedAt": unit_row["updated_at"],
    }


def get_unit(unit_id: str, *, published_only: bool = False) -> dict[str, Any] | None:
    """Return the full 9-slot unit payload, or None if no such unit.

    `published_only=True` treats draft units as absent — the public API
    endpoints use it so drafts can't be fetched (or graded, burning paid
    model calls) by guessing ids. The regression-set runner and ingest
    tooling read with the default `False`, since the grader gate runs
    against drafts before they're published.
    """
    query = "SELECT * FROM units WHERE id = %s"
    if published_only:
        query += " AND status = 'published'"
    with get_connection() as connection:
        unit_row = connection.execute(
            query,
            (unit_id,),
        ).fetchone()
        if unit_row is None:
            return None

        source_rows = connection.execute(
            """
            SELECT id, url, title, source_date, primary_source
            FROM unit_sources
            WHERE unit_id = %s
            ORDER BY primary_source DESC, source_date DESC, id ASC
            """,
            (unit_id,),
        ).fetchall()

        tag_rows = connection.execute(
            """
            SELECT id, claim, tier
            FROM calibration_tags
            WHERE unit_id = %s
            ORDER BY id ASC
            """,
            (unit_id,),
        ).fetchall()

        prompt_row = connection.execute(
            """
            SELECT id, prompt_md
            FROM decision_prompts
            WHERE unit_id = %s
            """,
            (unit_id,),
        ).fetchone()

        rubric_payload: dict[str, Any] | None = None
        if prompt_row is not None:
            rubric_row = connection.execute(
                """
                SELECT id, version, rubric_json
                FROM rubrics
                WHERE decision_prompt_id = %s
                ORDER BY version DESC
                LIMIT 1
                """,
                (prompt_row["id"],),
            ).fetchone()
            if rubric_row is not None:
                criterion_rows = connection.execute(
                    """
                    SELECT id, position, criterion_text
                    FROM rubric_criteria
                    WHERE rubric_id = %s
                    ORDER BY position ASC
                    """,
                    (rubric_row["id"],),
                ).fetchall()
                rubric_payload = {
                    "id": rubric_row["id"],
                    "version": rubric_row["version"],
                    "rubricJson": rubric_row["rubric_json"],
                    "criteria": [_map_rubric_criterion_row(r) for r in criterion_rows],
                }

    payload = _map_unit_full(unit_row)
    payload["sources"] = [_map_source_row(r) for r in source_rows]
    payload["calibrationTags"] = [_map_calibration_tag_row(r) for r in tag_rows]
    payload["decisionPrompt"] = (
        {"id": prompt_row["id"], "promptMd": prompt_row["prompt_md"]}
        if prompt_row is not None
        else None
    )
    payload["rubric"] = rubric_payload
    return payload


def list_units_for_path(path_id: str) -> list[dict[str, Any]]:
    """Return the manifest (id/slug/title/position/status) for a path's units."""
    with get_connection() as connection:
        rows = connection.execute(
            """
            SELECT id, slug, title, position, status
            FROM units
            WHERE path_id = %s
            ORDER BY position ASC
            """,
            (path_id,),
        ).fetchall()
    return [_map_unit_manifest_row(r) for r in rows]
