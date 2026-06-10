import os
from pathlib import Path
from urllib.parse import urlsplit, urlunsplit

BASE_DIR = Path(__file__).resolve().parent.parent
REPO_ROOT = BASE_DIR.parent
SEED_PATH = BASE_DIR / "db" / "seed.sql"

# Load a local .env before reading any environment variables, so local
# development doesn't depend on hand-exporting secrets per shell. Real
# platform env vars (Railway, CI) take precedence — override=False — so
# production behaviour is unchanged and a missing .env is a no-op.
try:
    from dotenv import load_dotenv

    for _env_file in (REPO_ROOT / ".env", BASE_DIR / ".env"):
        if _env_file.is_file():
            load_dotenv(_env_file, override=False)
except ModuleNotFoundError:
    pass

# `production` triggers the strict-secrets / strict-config gate below.
# Set via Railway / your hosting platform's env vars; defaults to
# `development` for local work and CI.
APP_ENV = os.getenv("APP_ENV", "development")

POSTGRES_HOST = os.getenv("POSTGRES_HOST", "localhost")
POSTGRES_PORT = int(os.getenv("POSTGRES_PORT", "5432"))
POSTGRES_DB = os.getenv("POSTGRES_DB", "ai101")
POSTGRES_USER = os.getenv("POSTGRES_USER", "postgres")
POSTGRES_PASSWORD = os.getenv("POSTGRES_PASSWORD", "postgres")

DATABASE_URL = os.getenv("DATABASE_URL")
if DATABASE_URL:
    RESOLVED_DATABASE_URL = DATABASE_URL
else:
    RESOLVED_DATABASE_URL = (
        f"postgresql://{POSTGRES_USER}:{POSTGRES_PASSWORD}@"
        f"{POSTGRES_HOST}:{POSTGRES_PORT}/{POSTGRES_DB}"
    )

APP_HOST = os.getenv("APP_HOST", "0.0.0.0")
APP_PORT = int(os.getenv("APP_PORT", "8000"))

AI_PROVIDER = os.getenv("AI_PROVIDER", "anthropic")
AI_PROVIDER_BASE_URL = os.getenv("AI_PROVIDER_BASE_URL", "https://api.anthropic.com/v1")
AI_PROVIDER_API_KEY = os.getenv("AI_PROVIDER_API_KEY", "")
AI_MODEL = os.getenv("AI_MODEL", "claude-sonnet-4-6")

# Email verification gate. Default OFF so the backend can deploy ahead of
# the Android build that understands the verification flow — the v1.0
# binary expects a token straight from signup. Flip to true (Railway env)
# once the updated app ships. Accounts created while the flag is off are
# marked verified at creation, so enabling it later strands nobody.
def _parse_bool(raw: str) -> bool:
    return raw.strip().lower() in ("1", "true", "yes", "on")


EMAIL_VERIFICATION_REQUIRED = _parse_bool(os.getenv("EMAIL_VERIFICATION_REQUIRED", "false"))

# Resend (https://resend.com) HTTP API — chosen over SMTP because Railway
# has a history of blocking outbound SMTP ports. Empty key = the logging
# sender (dev/CI); production requires a real key when verification is on.
RESEND_API_KEY = os.getenv("RESEND_API_KEY", "")
EMAIL_FROM = os.getenv("EMAIL_FROM", "Perpenda <no-reply@perpenda.com>")

# Verification-code policy: short TTL + per-code attempt cap bound
# guessing within one code; the auth_attempts limiter bounds it across
# codes. 10^6 codes / 5 attempts / 15 minutes.
EMAIL_CODE_TTL_MINUTES = int(os.getenv("EMAIL_CODE_TTL_MINUTES", "15"))
EMAIL_CODE_MAX_ATTEMPTS = int(os.getenv("EMAIL_CODE_MAX_ATTEMPTS", "5"))

# Default kept for local dev; production is required to override (see
# validate_production_config).
JWT_SECRET = os.getenv("JWT_SECRET", "change-me-in-production")
JWT_ALGORITHM = os.getenv("JWT_ALGORITHM", "HS256")
# Tokens are stateless with no server-side revocation, so the TTL is the only
# bound on a lost/stolen token's lifetime. Keep it short (7 days) to limit that
# window; env-overridable. (Instant revocation / logout-all is deferred — see
# the P2 backlog — until there's a refresh flow or higher-sensitivity data.)
JWT_EXPIRATION_DAYS = int(os.getenv("JWT_EXPIRATION_DAYS", "7"))

# Per-user grade rate limit (OWASP LLM10 — unbounded consumption). Each
# grade call is a paid model call; cap attempts per user over a sliding
# window. Defaults are generous for genuine practice (a learner won't
# legitimately grade 30 answers an hour) but bound scripted cost abuse.
GRADE_RATE_LIMIT_MAX = int(os.getenv("GRADE_RATE_LIMIT_MAX", "30"))
GRADE_RATE_LIMIT_WINDOW_SECONDS = int(
    os.getenv("GRADE_RATE_LIMIT_WINDOW_SECONDS", "3600")
)

# Per-account rate limit for auth endpoints (login/signup), keyed by email
# rather than client IP (which is the proxy's behind Railway). Caps targeted
# brute-force of one account; generous enough for honest retries.
AUTH_RATE_LIMIT_MAX = int(os.getenv("AUTH_RATE_LIMIT_MAX", "10"))
AUTH_RATE_LIMIT_WINDOW_SECONDS = int(
    os.getenv("AUTH_RATE_LIMIT_WINDOW_SECONDS", "900")
)

# Anthropic client resilience. max_retries lets the SDK ride through
# transient 429s (it honors the provider's Retry-After), so a rate-limited
# grader call self-paces and completes instead of erroring — raise it via
# env when running a regression sweep on a tight tier. timeout bounds a
# single hung request (per attempt).
AI_MAX_RETRIES = int(os.getenv("AI_MAX_RETRIES", "5"))
AI_REQUEST_TIMEOUT_SECONDS = float(os.getenv("AI_REQUEST_TIMEOUT_SECONDS", "60"))

# Minimum fraction of an answer_quote's words that must appear in the
# submitted answer for the quote to count as "grounded". Below this, the
# grader's cited evidence looks fabricated and the answer is flagged for
# review. Token-overlap (not exact substring) tolerates how the model
# really quotes — paraphrase, reorder, truncation — while still catching a
# quote that shares little with the answer. Lenient by design: a false flag
# costs a needless human review, a missed one only loses a backstop.
AI_QUOTE_MIN_OVERLAP = float(os.getenv("AI_QUOTE_MIN_OVERLAP", "0.5"))

# Host allow-list for TrustedHostMiddleware. Comma-separated; default "*"
# (allow any Host) so dev / CI / health checks aren't blocked. Production
# should set it to its real domain(s) to reject Host-header spoofing.
def _parse_allowed_hosts(raw: str) -> list[str]:
    # Fall back to allow-all when the value is blank/empty ("" or ","), so a
    # present-but-empty env var can't hand TrustedHostMiddleware an empty list
    # and reject every request (a self-inflicted outage).
    hosts = [h.strip() for h in raw.split(",") if h.strip()]
    return hosts or ["*"]


ALLOWED_HOSTS = _parse_allowed_hosts(os.getenv("ALLOWED_HOSTS", "*"))


# Default values that must NOT appear in a production deployment.
# POSTGRES_PASSWORD is intentionally absent from this tuple — it's
# only consulted when DATABASE_URL is unset (see the conditional in
# validate_production_config below). The Railway-style deploy pattern
# is to set DATABASE_URL directly with strong credentials inside, in
# which case the POSTGRES_* fallbacks are dead code; gating on them
# unconditionally would refuse perfectly valid deploys.
_PRODUCTION_FORBIDDEN_DEFAULTS = (
    ("JWT_SECRET", lambda: JWT_SECRET, "change-me-in-production"),
)


class ProductionConfigError(RuntimeError):
    """Raised when the process is configured as APP_ENV=production but
    has insecure defaults still in place. Caught at app startup so a
    misconfigured deploy fails fast and visibly instead of silently
    running with weak credentials.
    """


def is_production() -> bool:
    """True when APP_ENV names the production environment.

    Matched case- and whitespace-insensitively so a typo like
    "Production" or " production " can't silently slip past the secrets
    gate (the previous exact `== "production"` check failed open on any
    near-miss). Other names (development, test, ci, staging, …) remain
    non-production by design — the gate protects the launch surface, not
    every environment.
    """
    return APP_ENV.strip().lower() == "production"


def validate_production_config() -> None:
    """Refuse to start in production with default secrets.

    Called from `main.py`'s startup hook. In any non-production
    environment (development, test, ci, staging — anything that isn't the
    production env per `is_production`) this is a no-op so local work and
    test runs aren't blocked.
    """
    if not is_production():
        return

    problems: list[str] = []
    for name, current, forbidden_default in _PRODUCTION_FORBIDDEN_DEFAULTS:
        if current() == forbidden_default:
            problems.append(
                f"{name} is still the development default ({forbidden_default!r}); "
                f"set it via the deploy environment."
            )

    # POSTGRES_PASSWORD is only consulted when DATABASE_URL is unset
    # (see RESOLVED_DATABASE_URL above). Skip the gate when the
    # operator has provided a full DATABASE_URL — its credentials are
    # the operator's explicit choice and the fallback never runs.
    if not DATABASE_URL and POSTGRES_PASSWORD == "postgres":
        problems.append(
            "POSTGRES_PASSWORD is still the development default ('postgres') "
            "and DATABASE_URL is unset; set DATABASE_URL to a complete "
            "connection string (recommended) or set POSTGRES_PASSWORD."
        )

    # AI_PROVIDER_API_KEY's default is empty; in production we require
    # any non-empty value. Per-provider validity is checked at first
    # call (the grader returns 502 AI_UNAVAILABLE if the key is bad).
    if not AI_PROVIDER_API_KEY:
        problems.append(
            "AI_PROVIDER_API_KEY is empty; set it via the deploy environment."
        )

    # When verification is required, a missing email-provider key would
    # silently route codes to the logging sender — users could never
    # verify. Fail the deploy instead.
    if EMAIL_VERIFICATION_REQUIRED and not RESEND_API_KEY:
        problems.append(
            "EMAIL_VERIFICATION_REQUIRED is on but RESEND_API_KEY is empty; "
            "set it via the deploy environment."
        )

    if problems:
        joined = "\n  - ".join(problems)
        raise ProductionConfigError(
            f"Refusing to start: APP_ENV=production but config has weak defaults:\n  - {joined}"
        )


def masked_database_url() -> str:
    split = urlsplit(RESOLVED_DATABASE_URL)
    if split.password is None:
        return RESOLVED_DATABASE_URL

    username = split.username or ""
    host = split.hostname or ""
    port = f":{split.port}" if split.port else ""
    safe_netloc = f"{username}:***@{host}{port}"
    return urlunsplit((split.scheme, safe_netloc, split.path, split.query, split.fragment))
