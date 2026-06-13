"""Tests for backend/app/config.py:validate_production_config.

Pure-Python: no DB, no SDKs. Covers the fail-fast gate that refuses to
boot under APP_ENV=production with default secrets in place.
"""
from __future__ import annotations

import pytest

from app import config
from app.config import (
    ProductionConfigError,
    _parse_allowed_hosts,
    is_production,
    validate_production_config,
)


@pytest.fixture(autouse=True)
def _restore_env(monkeypatch: pytest.MonkeyPatch) -> None:
    """Each test starts from a clean slate of the module-level config
    constants. We monkeypatch the module attributes directly because
    `validate_production_config` reads them via lambdas that close over
    the module namespace.
    """
    # No setup-time mutation; tests set what they need.
    yield


def test_noop_when_app_env_is_development(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(config, "APP_ENV", "development")
    monkeypatch.setattr(config, "JWT_SECRET", "change-me-in-production")
    monkeypatch.setattr(config, "DATABASE_URL", None)
    monkeypatch.setattr(config, "POSTGRES_PASSWORD", "postgres")
    monkeypatch.setattr(config, "AI_PROVIDER_API_KEY", "")
    # Should not raise — the gate explicitly tolerates default values
    # outside production so local dev and CI runs aren't blocked.
    validate_production_config()


def test_passes_in_production_with_real_values(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(config, "APP_ENV", "production")
    monkeypatch.setattr(config, "DATABASE_URL", None)
    monkeypatch.setattr(config, "JWT_SECRET", "an-actual-strong-secret-from-deploy-env")
    monkeypatch.setattr(config, "POSTGRES_PASSWORD", "real-prod-password")
    monkeypatch.setattr(config, "AI_PROVIDER_API_KEY", "sk-ant-real-key")
    monkeypatch.setattr(config, "RESEND_API_KEY", "re_real-key")
    validate_production_config()


def test_passes_in_production_when_database_url_set_even_if_postgres_password_default(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """Railway-style deploy pattern: operator sets DATABASE_URL with
    strong creds inside; POSTGRES_PASSWORD is dead-code fallback. The
    gate must not refuse this perfectly valid configuration.
    """
    monkeypatch.setattr(config, "APP_ENV", "production")
    monkeypatch.setattr(
        config,
        "DATABASE_URL",
        "postgresql://user:strongpassword@db.host:5432/dbname",
    )
    monkeypatch.setattr(config, "JWT_SECRET", "real")
    monkeypatch.setattr(config, "POSTGRES_PASSWORD", "postgres")  # default
    monkeypatch.setattr(config, "AI_PROVIDER_API_KEY", "sk-ant-real")
    monkeypatch.setattr(config, "RESEND_API_KEY", "re_real")
    validate_production_config()


def test_fails_in_production_when_jwt_secret_is_default(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(config, "APP_ENV", "production")
    monkeypatch.setattr(config, "DATABASE_URL", "postgresql://u:p@h:5432/d")
    monkeypatch.setattr(config, "JWT_SECRET", "change-me-in-production")
    monkeypatch.setattr(config, "POSTGRES_PASSWORD", "real")
    monkeypatch.setattr(config, "AI_PROVIDER_API_KEY", "sk-ant-real")

    with pytest.raises(ProductionConfigError, match="JWT_SECRET"):
        validate_production_config()


def test_fails_in_production_when_postgres_password_default_and_no_database_url(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """POSTGRES_PASSWORD only matters when DATABASE_URL is unset (the
    fallback path that builds the connection string from POSTGRES_*).
    """
    monkeypatch.setattr(config, "APP_ENV", "production")
    monkeypatch.setattr(config, "DATABASE_URL", None)
    monkeypatch.setattr(config, "JWT_SECRET", "real")
    monkeypatch.setattr(config, "POSTGRES_PASSWORD", "postgres")
    monkeypatch.setattr(config, "AI_PROVIDER_API_KEY", "sk-ant-real")

    with pytest.raises(ProductionConfigError, match="POSTGRES_PASSWORD"):
        validate_production_config()


def test_fails_in_production_when_ai_provider_api_key_is_empty(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(config, "APP_ENV", "production")
    monkeypatch.setattr(config, "DATABASE_URL", "postgresql://u:p@h:5432/d")
    monkeypatch.setattr(config, "JWT_SECRET", "real")
    monkeypatch.setattr(config, "POSTGRES_PASSWORD", "real")
    monkeypatch.setattr(config, "AI_PROVIDER_API_KEY", "")

    with pytest.raises(ProductionConfigError, match="AI_PROVIDER_API_KEY"):
        validate_production_config()


def test_fails_in_production_when_resend_key_missing(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """RESEND_API_KEY is required in production unconditionally: password
    reset is always live, so a missing key would route reset codes to the
    logging sender and strand locked-out users."""
    monkeypatch.setattr(config, "APP_ENV", "production")
    monkeypatch.setattr(config, "DATABASE_URL", "postgresql://u:p@h:5432/d")
    monkeypatch.setattr(config, "JWT_SECRET", "real")
    monkeypatch.setattr(config, "POSTGRES_PASSWORD", "real")
    monkeypatch.setattr(config, "AI_PROVIDER_API_KEY", "sk-ant-real")
    monkeypatch.setattr(config, "RESEND_API_KEY", "")

    with pytest.raises(ProductionConfigError, match="RESEND_API_KEY"):
        validate_production_config()

    # And a key satisfies the gate.
    monkeypatch.setattr(config, "RESEND_API_KEY", "re_real_key")
    validate_production_config()


def test_resend_key_required_even_when_verification_off(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    # Password reset is reachable with verification off, so the key is
    # still mandatory — the gate must not be conditioned on the flag.
    monkeypatch.setattr(config, "APP_ENV", "production")
    monkeypatch.setattr(config, "DATABASE_URL", "postgresql://u:p@h:5432/d")
    monkeypatch.setattr(config, "JWT_SECRET", "real")
    monkeypatch.setattr(config, "POSTGRES_PASSWORD", "real")
    monkeypatch.setattr(config, "AI_PROVIDER_API_KEY", "sk-ant-real")
    monkeypatch.setattr(config, "EMAIL_VERIFICATION_REQUIRED", False)
    monkeypatch.setattr(config, "RESEND_API_KEY", "")
    with pytest.raises(ProductionConfigError, match="RESEND_API_KEY"):
        validate_production_config()


def test_reports_all_problems_at_once(monkeypatch: pytest.MonkeyPatch) -> None:
    """The gate should surface every misconfiguration in one message,
    not bisect — operators shouldn't have to fix-redeploy three times.
    """
    monkeypatch.setattr(config, "APP_ENV", "production")
    monkeypatch.setattr(config, "DATABASE_URL", None)
    monkeypatch.setattr(config, "JWT_SECRET", "change-me-in-production")
    monkeypatch.setattr(config, "POSTGRES_PASSWORD", "postgres")
    monkeypatch.setattr(config, "AI_PROVIDER_API_KEY", "")

    with pytest.raises(ProductionConfigError) as exc_info:
        validate_production_config()

    message = str(exc_info.value)
    assert "JWT_SECRET" in message
    assert "POSTGRES_PASSWORD" in message
    assert "AI_PROVIDER_API_KEY" in message


def test_staging_env_is_treated_as_non_production(monkeypatch: pytest.MonkeyPatch) -> None:
    """A 'staging' env can run with whatever its operator chose — the
    intent is to protect the launch surface, not force every
    environment through prod-grade secret hygiene.
    """
    monkeypatch.setattr(config, "APP_ENV", "staging")
    monkeypatch.setattr(config, "DATABASE_URL", None)
    monkeypatch.setattr(config, "JWT_SECRET", "change-me-in-production")
    monkeypatch.setattr(config, "POSTGRES_PASSWORD", "postgres")
    monkeypatch.setattr(config, "AI_PROVIDER_API_KEY", "")
    validate_production_config()  # should not raise


def test_is_production_matches_case_and_whitespace_insensitively(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    for value in ("production", "Production", "PRODUCTION", "  production  "):
        monkeypatch.setattr(config, "APP_ENV", value)
        assert is_production() is True, value
    for value in ("development", "staging", "ci", "prod", ""):
        monkeypatch.setattr(config, "APP_ENV", value)
        assert is_production() is False, value


def test_gate_fires_for_capitalized_production_typo(monkeypatch: pytest.MonkeyPatch) -> None:
    # The old exact "== production" check failed open on a capitalized typo;
    # the gate must now catch it and refuse the default secret.
    monkeypatch.setattr(config, "APP_ENV", "Production")
    monkeypatch.setattr(config, "DATABASE_URL", "postgresql://u:p@h:5432/d")
    monkeypatch.setattr(config, "JWT_SECRET", "change-me-in-production")
    monkeypatch.setattr(config, "POSTGRES_PASSWORD", "real")
    monkeypatch.setattr(config, "AI_PROVIDER_API_KEY", "sk-ant-real")
    with pytest.raises(ProductionConfigError, match="JWT_SECRET"):
        validate_production_config()


def test_parse_allowed_hosts_falls_back_to_wildcard_when_blank() -> None:
    # A present-but-empty env var must not yield an empty list (which would
    # make TrustedHostMiddleware reject every request).
    assert _parse_allowed_hosts("") == ["*"]
    assert _parse_allowed_hosts(",") == ["*"]
    assert _parse_allowed_hosts("   ") == ["*"]


def test_parse_allowed_hosts_parses_real_hosts() -> None:
    assert _parse_allowed_hosts("*") == ["*"]
    assert _parse_allowed_hosts("a.com, b.com") == ["a.com", "b.com"]
