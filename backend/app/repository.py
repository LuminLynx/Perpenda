from __future__ import annotations

import re
import secrets
from typing import Any

from .db import get_connection

TERM_SELECT_BASE = """
    SELECT
        t.id,
        t.slug,
        t.term,
        t.definition,
        t.explanation,
        t.humor,
        t.controversy_level,
        t.short_definition,
        t.full_explanation,
        t.category_id,
        t.tags,
        t.related_terms,
        t.example_usage,
        t.source,
        t.created_at,
        t.updated_at,
        COALESCE(
            ARRAY_AGG(related.slug ORDER BY related.term)
            FILTER (WHERE related.slug IS NOT NULL),
            '{}'::TEXT[]
        ) AS see_also,
        COALESCE(
            ARRAY_AGG(related.id ORDER BY related.term)
            FILTER (WHERE related.id IS NOT NULL),
            '{}'::TEXT[]
        ) AS related_term_ids
    FROM terms t
    LEFT JOIN term_relations relation ON relation.term_id = t.id
    LEFT JOIN terms related ON related.id = relation.related_term_id
"""

TERM_GROUP_BY = """
    GROUP BY
        t.id,
        t.slug,
        t.term,
        t.definition,
        t.explanation,
        t.humor,
        t.controversy_level,
        t.short_definition,
        t.full_explanation,
        t.category_id,
        t.tags,
        t.related_terms,
        t.example_usage,
        t.source,
        t.created_at,
        t.updated_at
"""


def _parse_csv(value: str | None) -> list[str]:
    if not value:
        return []
    return [item.strip() for item in value.split(",") if item.strip()]


def _to_list(value: Any) -> list[str]:
    if value is None:
        return []
    if isinstance(value, list):
        return [str(item).strip() for item in value if str(item).strip()]
    return _parse_csv(str(value))


def _normalize_spaces(value: str) -> str:
    return re.sub(r"\s+", " ", value.strip())


def normalize_query(value: str) -> str:
    return _normalize_spaces(value).lower()


def slugify(value: str) -> str:
    slug = re.sub(r"[^a-z0-9]+", "-", value.lower()).strip("-")
    if not slug:
        raise ValueError("slug must contain at least one alphanumeric character")
    return slug


def list_terms() -> list[dict[str, Any]]:
    query = f"""
        {TERM_SELECT_BASE}
        {TERM_GROUP_BY}
        ORDER BY LOWER(t.term) ASC
    """
    with get_connection() as connection:
        rows = connection.execute(query).fetchall()
    return [_map_term_row(row) for row in rows]


def get_term_by_id(term_id: str) -> dict[str, Any] | None:
    query = f"""
        {TERM_SELECT_BASE}
        WHERE t.id = %s
        {TERM_GROUP_BY}
    """
    with get_connection() as connection:
        row = connection.execute(query, (term_id,)).fetchone()
    return _map_term_row(row) if row else None


def list_categories() -> list[dict[str, Any]]:
    query = """
        SELECT id, name, description
        FROM categories
        ORDER BY LOWER(name) ASC
    """
    with get_connection() as connection:
        rows = connection.execute(query).fetchall()
    return [dict(row) for row in rows]


def list_terms_by_category(category_id: str) -> list[dict[str, Any]]:
    query = f"""
        {TERM_SELECT_BASE}
        WHERE t.category_id = %s
        {TERM_GROUP_BY}
        ORDER BY LOWER(t.term) ASC
    """
    with get_connection() as connection:
        rows = connection.execute(query, (category_id,)).fetchall()
    return [_map_term_row(row) for row in rows]


def search_terms(raw_query: str) -> list[dict[str, Any]]:
    query = raw_query.strip()
    search_value = f"%{query}%"
    sql = f"""
        {TERM_SELECT_BASE}
        WHERE t.term ILIKE %s
           OR t.definition ILIKE %s
           OR t.explanation ILIKE %s
           OR t.tags ILIKE %s
           OR t.slug ILIKE %s
        {TERM_GROUP_BY}
        ORDER BY LOWER(t.term) ASC
    """
    params = (search_value, search_value, search_value, search_value, search_value)

    with get_connection() as connection:
        rows = connection.execute(sql, params).fetchall()

    return [_map_term_row(row) for row in rows]


def _get_term_title_by_id_or_slug(reference: str) -> str | None:
    if not reference:
        return None

    query = """
        SELECT term
        FROM terms
        WHERE id = %s OR slug = %s
        LIMIT 1
    """
    with get_connection() as connection:
        row = connection.execute(query, (reference, reference)).fetchone()
        return row["term"] if row else None


def _resolve_see_also_display_names(references: list[str]) -> list[str]:
    resolved: list[str] = []
    for reference in references:
        title = _get_term_title_by_id_or_slug(reference)
        resolved.append(title if title else reference)
    return resolved


def _map_term_row(row: Any) -> dict[str, Any]:
    normalized_tags = _parse_csv(row["tags"])
    normalized_see_also_refs = _to_list(row["see_also"])
    normalized_legacy_related_refs = _parse_csv(row["related_terms"])
    normalized_related_ids = _to_list(row["related_term_ids"])

    # Prefer relation-table slugs; if absent, fallback to legacy related_terms references.
    see_also_refs = normalized_see_also_refs if normalized_see_also_refs else normalized_legacy_related_refs
    resolved_see_also = _resolve_see_also_display_names(see_also_refs)

    return {
        "id": row["id"],
        "slug": row["slug"],
        "term": row["term"],
        "definition": row["definition"],
        "explanation": row["explanation"],
        "humor": row["humor"],
        "seeAlso": resolved_see_also,
        "tags": normalized_tags,
        "controversyLevel": row["controversy_level"],
        # Backward-compatible aliases for existing Android consumers.
        "shortDefinition": row["definition"],
        "fullExplanation": row["explanation"],
        "relatedTerms": normalized_related_ids,
        "categoryId": row["category_id"],
        "exampleUsage": row["example_usage"],
        "source": row["source"],
        "createdAt": row["created_at"],
        "updatedAt": row["updated_at"],
    }


# ----- Users / Auth -----


def _user_id() -> str:
    return f"u-{secrets.token_urlsafe(12)}"


def _map_user_row(row: Any) -> dict[str, Any]:
    return {
        "id": row["id"],
        "email": row["email"],
        "displayName": row["display_name"],
        "createdAt": row["created_at"],
        "emailVerified": row["email_verified_at"] is not None,
    }


def create_user(
    *,
    email: str,
    password_hash: str,
    display_name: str,
    email_verified: bool = True,
) -> dict[str, Any]:
    """Insert a user. Pass `email_verified=False` only when the signup flow
    follows up with a verification code (EMAIL_VERIFICATION_REQUIRED) —
    accounts created without that flow must stay usable if the flag is
    enabled later, so the default is verified.
    """
    user_id = _user_id()
    with get_connection() as connection:
        row = connection.execute(
            """
            INSERT INTO users (id, email, password_hash, display_name, email_verified_at)
            VALUES (%s, %s, %s, %s, CASE WHEN %s THEN NOW() ELSE NULL END)
            RETURNING *
            """,
            (user_id, email, password_hash, display_name, email_verified),
        ).fetchone()
        connection.commit()
    return _map_user_row(row)


def get_user_by_email(email: str) -> dict[str, Any] | None:
    """Public-safe user lookup — never includes password_hash.

    Use for existence checks and any path whose result may be serialized.
    Login, which needs the hash to verify a password, must use
    `get_user_auth_by_email` instead.
    """
    with get_connection() as connection:
        row = connection.execute(
            "SELECT * FROM users WHERE LOWER(email) = LOWER(%s)",
            (email,),
        ).fetchone()
    return None if row is None else _map_user_row(row)


def get_user_auth_by_email(email: str) -> dict[str, Any] | None:
    """Auth-only lookup that DOES include password_hash, for login.

    Kept separate from `get_user_by_email` so the hash can never leak via
    a handler that serializes a user dict — only the auth path reaches it.
    """
    with get_connection() as connection:
        row = connection.execute(
            "SELECT * FROM users WHERE LOWER(email) = LOWER(%s)",
            (email,),
        ).fetchone()
    return None if row is None else dict(row)


def get_user_by_id(user_id: str) -> dict[str, Any] | None:
    with get_connection() as connection:
        row = connection.execute(
            "SELECT * FROM users WHERE id = %s",
            (user_id,),
        ).fetchone()
    if row is None:
        return None
    return _map_user_row(row)


def delete_user(user_id: str) -> bool:
    """Delete a user and all their data. Returns True if a user was removed.

    completions, grades (via completion), review_schedule, and grade_attempts
    cascade through their `ON DELETE CASCADE` foreign keys to users(id).
    auth_attempts is keyed by email (not user_id), so its rows for this
    account are cleared explicitly — together this removes the account's
    personal data, satisfying the data-deletion requirement.
    """
    with get_connection() as connection:
        row = connection.execute(
            "SELECT email FROM users WHERE id = %s", (user_id,)
        ).fetchone()
        if row is None:
            connection.commit()
            return False
        email = row["email"]
        connection.execute("DELETE FROM users WHERE id = %s", (user_id,))
        connection.execute(
            "DELETE FROM auth_attempts WHERE attempt_key IN (%s, %s)",
            (f"login:{email}", f"signup:{email}"),
        )
        connection.commit()
    return True


# Path-centric Completions live in backend/app/repositories/completion_repository.py.
# The legacy term-centric `learning_completions` table is dropped in migration 022.
