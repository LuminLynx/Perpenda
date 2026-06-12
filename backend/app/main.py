import logging
from datetime import datetime
from typing import Any

from fastapi import BackgroundTasks, Depends, FastAPI, Query
from fastapi.encoders import jsonable_encoder
from fastapi.middleware.trustedhost import TrustedHostMiddleware
from fastapi.responses import JSONResponse
from psycopg.errors import UniqueViolation
from pydantic import BaseModel, Field

from .ai_service import AIServiceError, ai_service_metadata, grade_decision_answer
from .auth import (
    AuthError,
    create_access_token,
    hash_password,
    required_user_id,
    validate_display_name,
    validate_email,
    validate_password,
    verify_login,
)
from .config import ALLOWED_HOSTS, EMAIL_VERIFICATION_REQUIRED, validate_production_config
from .email_service import EmailSendError, send_password_reset_email, send_verification_email
from .migrations import run_migrations
from .repositories import (
    auth_rate_limit_repository,
    completion_repository,
    email_verification_repository,
    grade_repository,
    password_reset_repository,
    path_repository,
    rate_limit_repository,
    review_repository,
    unit_repository,
)
from .repositories.rate_limit_repository import RateLimitExceededError
from .repository import (
    create_user,
    delete_user,
    get_term_by_id,
    get_user_auth_by_email,
    get_user_by_email,
    get_user_by_id,
    list_categories,
    list_terms,
    list_terms_by_category,
    search_terms,
)

LOGGER = logging.getLogger(__name__)

app = FastAPI(title="AI-101 Backend", version="0.3.0")

# Reject spoofed Host headers in production (set ALLOWED_HOSTS to the real
# domain[s]); default "*" is a no-op so dev / CI / health checks are unaffected.
app.add_middleware(TrustedHostMiddleware, allowed_hosts=ALLOWED_HOSTS)


class GradeRequest(BaseModel):
    answer: str = Field(min_length=1, max_length=8000)


class SignupRequest(BaseModel):
    email: str
    password: str
    displayName: str


class LoginRequest(BaseModel):
    email: str
    password: str


class VerifyEmailRequest(BaseModel):
    email: str = Field(max_length=320)
    code: str = Field(min_length=1, max_length=16)


class ResendVerificationRequest(BaseModel):
    email: str = Field(max_length=320)


class RequestPasswordResetRequest(BaseModel):
    email: str = Field(max_length=320)


class ResetPasswordRequest(BaseModel):
    email: str = Field(max_length=320)
    code: str = Field(min_length=1, max_length=16)
    newPassword: str = Field(max_length=1024)


def _envelope_response(*, data, error=None, status_code: int = 200) -> JSONResponse:
    payload = {"data": data, "error": error}
    return JSONResponse(content=jsonable_encoder(payload), status_code=status_code)


def _rate_limited_response(exc: RateLimitExceededError, *, message: str) -> JSONResponse:
    response = _envelope_response(
        status_code=429,
        data=None,
        error={"code": "RATE_LIMITED", "message": message},
    )
    response.headers["Retry-After"] = str(exc.retry_after_seconds)
    return response


def _issue_and_send_verification(user_id: str, email: str) -> None:
    """Issue a verification code and email it. Runs as a background task so
    the originating request returns at constant time regardless of account
    state — closing the timing/side-effect oracle that would otherwise
    reveal which addresses have unverified accounts."""
    try:
        code = email_verification_repository.issue_code(user_id)
        send_verification_email(email, code)
    except EmailSendError as exc:
        LOGGER.warning("verification email failed for %s: %s", user_id, exc)
    except Exception:  # noqa: BLE001 — a background task must never crash the worker
        LOGGER.exception("verification email task failed for %s", user_id)


def _issue_and_send_password_reset(user_id: str, email: str) -> None:
    """Issue a password-reset code and email it, as a background task (see
    _issue_and_send_verification for the constant-time rationale)."""
    try:
        code = password_reset_repository.issue_code(user_id)
        send_password_reset_email(email, code)
    except EmailSendError as exc:
        LOGGER.warning("password reset email failed for %s: %s", user_id, exc)
    except Exception:  # noqa: BLE001
        LOGGER.exception("password reset email task failed for %s", user_id)


def _account_gone_response() -> JSONResponse:
    return _envelope_response(
        status_code=401,
        data=None,
        error={"code": "AUTH_REQUIRED", "message": "Account no longer exists."},
    )


@app.on_event("startup")
def on_startup() -> None:
    # Refuse to start in production with default secrets. No-op in dev /
    # test / ci. See backend/app/config.py for the gate.
    validate_production_config()
    # run_migrations() acquires a Postgres advisory lock, so it's
    # race-safe under horizontal scale, but the recommended deploy
    # pattern is still to run `python -m backend.scripts.migrate` as
    # a release/pre-deploy command and have this be a no-op verification.
    # See docs/guides/BACKEND_BEST_PRACTICES.md.
    run_migrations()


@app.get("/health")
def health() -> dict[str, Any]:
    return {
        "status": "ok",
        "ai": {
            "provider": ai_service_metadata()["provider"],
            "model": ai_service_metadata()["model"],
        },
    }


@app.get("/api/v1/terms")
def get_terms() -> JSONResponse:
    return _envelope_response(data=list_terms())


@app.get("/api/v1/terms/{term_id}")
def get_term_details(term_id: str) -> JSONResponse:
    term = get_term_by_id(term_id)
    if term is None:
        return _envelope_response(
            status_code=404,
            data=None,
            error={
                "code": "TERM_NOT_FOUND",
                "message": f"No term found for id '{term_id}'.",
            },
        )
    return _envelope_response(data=term)


@app.get("/api/v1/categories")
def get_categories() -> JSONResponse:
    return _envelope_response(data=list_categories())


@app.get("/api/v1/categories/{category_id}/terms")
def get_terms_for_category(category_id: str) -> JSONResponse:
    return _envelope_response(data=list_terms_by_category(category_id))


@app.get("/api/v1/search/terms")
def get_term_search_results(q: str = Query(default="", min_length=0)) -> JSONResponse:
    return _envelope_response(data=search_terms(q))


@app.post("/api/v1/auth/signup")
def post_signup(request: SignupRequest, background_tasks: BackgroundTasks) -> JSONResponse:
    try:
        email = validate_email(request.email)
        password = validate_password(request.password)
        display_name = validate_display_name(request.displayName)
    except AuthError as error:
        return _envelope_response(
            status_code=error.status_code,
            data=None,
            error={"code": error.code, "message": str(error)},
        )

    # Per-email throttle, before the expensive bcrypt hash, to bound
    # repeated probing of a single address. Atomic check+record.
    signup_key = f"signup:{email}"
    try:
        auth_rate_limit_repository.check_and_record_auth_attempt(signup_key)
    except RateLimitExceededError as exc:
        return _rate_limited_response(exc, message="Too many attempts. Try again later.")

    if get_user_by_email(email) is not None:
        return _envelope_response(
            status_code=409,
            data=None,
            error={"code": "EMAIL_TAKEN", "message": "An account with this email already exists."},
        )

    try:
        user = create_user(
            email=email,
            password_hash=hash_password(password),
            display_name=display_name,
            # When verification is required the account starts unverified
            # and login is gated until the emailed code is confirmed. With
            # the flag off, accounts are verified at creation so a later
            # flag flip can't strand them.
            email_verified=not EMAIL_VERIFICATION_REQUIRED,
        )
    except UniqueViolation:
        # Race: a concurrent signup for the same email won between the
        # lookup above and this INSERT; surface the same 409 the lookup
        # would have produced instead of a 500.
        return _envelope_response(
            status_code=409,
            data=None,
            error={"code": "EMAIL_TAKEN", "message": "An account with this email already exists."},
        )

    if EMAIL_VERIFICATION_REQUIRED:
        # No token until the email is confirmed. The code+email run in a
        # background task: a send failure can't orphan the account (the
        # client's "resend code" path recovers), and the request returns
        # without waiting on the email provider.
        background_tasks.add_task(
            _issue_and_send_verification, user["id"], user["email"]
        )
        return _envelope_response(
            status_code=201,
            data={"verificationRequired": True, "user": user},
        )

    token = create_access_token(user["id"])
    return _envelope_response(
        status_code=201,
        data={"token": token, "user": user},
    )


@app.post("/api/v1/auth/login")
def post_login(request: LoginRequest) -> JSONResponse:
    try:
        email = validate_email(request.email)
    except AuthError as error:
        return _envelope_response(
            status_code=error.status_code,
            data=None,
            error={"code": error.code, "message": str(error)},
        )

    # Per-account throttle: cap login attempts per email so a known account
    # can't be brute-forced. Atomic check+record before the (expensive)
    # password verify; a successful login clears the counter below.
    login_key = f"login:{email}"
    try:
        auth_rate_limit_repository.check_and_record_auth_attempt(login_key)
    except RateLimitExceededError as exc:
        return _rate_limited_response(exc, message="Too many attempts. Try again later.")

    # verify_login runs bcrypt even when the email is unknown, so the
    # response time doesn't reveal whether the account exists.
    row = get_user_auth_by_email(email)
    if not verify_login(request.password, row["password_hash"] if row else None):
        return _envelope_response(
            status_code=401,
            data=None,
            error={"code": "INVALID_CREDENTIALS", "message": "Invalid email or password."},
        )

    # Successful login clears the failure counter so honest users aren't
    # locked out by their own prior typos.
    auth_rate_limit_repository.clear_auth_attempts(login_key)

    # Verification gate AFTER the password check, so an unverified-account
    # response is only ever shown to someone holding the right password —
    # it can't be used to probe which emails have accounts.
    if EMAIL_VERIFICATION_REQUIRED and row["email_verified_at"] is None:
        return _envelope_response(
            status_code=403,
            data=None,
            error={
                "code": "EMAIL_NOT_VERIFIED",
                "message": "Confirm your email address to sign in. Check your inbox for the code.",
            },
        )

    user = {
        "id": row["id"],
        "email": row["email"],
        "displayName": row["display_name"],
        "createdAt": row["created_at"],
        "emailVerified": row["email_verified_at"] is not None,
    }
    token = create_access_token(user["id"])
    return _envelope_response(data={"token": token, "user": user})


@app.post("/api/v1/auth/verify-email")
def post_verify_email(request: VerifyEmailRequest) -> JSONResponse:
    """Confirm the emailed code; on success return a session token.

    INVALID_CODE deliberately covers unknown email, no pending code, wrong
    code, and attempt-cap reached — distinguishing them would leak which
    emails have (unverified) accounts. CODE_EXPIRED is surfaced separately
    because the user must know to request a resend.
    """
    verify_key = f"verify:{request.email.strip().lower()}"
    try:
        auth_rate_limit_repository.check_and_record_auth_attempt(verify_key)
    except RateLimitExceededError as exc:
        return _rate_limited_response(exc, message="Too many attempts. Try again later.")

    invalid = _envelope_response(
        status_code=400,
        data=None,
        error={"code": "INVALID_CODE", "message": "That code didn't work. Check it and try again."},
    )
    user = get_user_by_email(request.email.strip())
    if user is None:
        return invalid
    try:
        email_verification_repository.verify_code(user["id"], request.code.strip())
    except email_verification_repository.CodeExpiredError:
        return _envelope_response(
            status_code=400,
            data=None,
            error={"code": "CODE_EXPIRED", "message": "That code expired. Request a new one."},
        )
    except email_verification_repository.VerificationError:
        return invalid

    auth_rate_limit_repository.clear_auth_attempts(verify_key)
    verified_user = get_user_by_id(user["id"])
    token = create_access_token(user["id"])
    return _envelope_response(data={"token": token, "user": verified_user})


@app.post("/api/v1/auth/resend-verification")
def post_resend_verification(
    request: ResendVerificationRequest, background_tasks: BackgroundTasks
) -> JSONResponse:
    """Issue a fresh code for an unverified account.

    Always responds 200 {"sent": true} whether or not the email has an
    account (anti-enumeration); the rate limit bounds the email volume an
    abuser can trigger toward one address. The code+email work runs in a
    background task so the response time is constant regardless of whether
    the address matched an unverified account — otherwise the latency of
    the inline issue+send would be a side-channel revealing exactly that.
    """
    resend_key = f"resend:{request.email.strip().lower()}"
    try:
        auth_rate_limit_repository.check_and_record_auth_attempt(resend_key)
    except RateLimitExceededError as exc:
        return _rate_limited_response(exc, message="Too many attempts. Try again later.")

    user = get_user_by_email(request.email.strip())
    if user is not None and not user["emailVerified"]:
        background_tasks.add_task(
            _issue_and_send_verification, user["id"], user["email"]
        )
    return _envelope_response(data={"sent": True})


@app.post("/api/v1/auth/request-password-reset")
def post_request_password_reset(
    request: RequestPasswordResetRequest, background_tasks: BackgroundTasks
) -> JSONResponse:
    """Email a reset code to an account's address.

    Always responds 200 {"sent": true} whether or not the email has an
    account (anti-enumeration), mirroring resend-verification; the rate
    limit bounds the email volume an abuser can trigger at one address.
    Unverified accounts may reset too — redeeming proves inbox ownership.
    The code+email run in a background task so the response time doesn't
    reveal whether the address matched an account.
    """
    reset_key = f"reset:{request.email.strip().lower()}"
    try:
        auth_rate_limit_repository.check_and_record_auth_attempt(reset_key)
    except RateLimitExceededError as exc:
        return _rate_limited_response(exc, message="Too many attempts. Try again later.")

    user = get_user_by_email(request.email.strip())
    if user is not None:
        background_tasks.add_task(
            _issue_and_send_password_reset, user["id"], user["email"]
        )
    return _envelope_response(data={"sent": True})


@app.post("/api/v1/auth/reset-password")
def post_reset_password(request: ResetPasswordRequest) -> JSONResponse:
    """Redeem a reset code and set the new password; returns a session.

    Same error discipline as verify-email: INVALID_CODE covers unknown
    email / no pending code / wrong code / attempt cap, CODE_EXPIRED is
    distinct so the client knows to offer a fresh code. The new password
    goes through the same validator as signup. Note: existing JWTs stay
    valid until expiry (stateless tokens, no revocation — the documented
    7-day bound applies).
    """
    try:
        new_password = validate_password(request.newPassword)
    except AuthError as error:
        return _envelope_response(
            status_code=error.status_code,
            data=None,
            error={"code": error.code, "message": str(error)},
        )

    confirm_key = f"reset-confirm:{request.email.strip().lower()}"
    try:
        auth_rate_limit_repository.check_and_record_auth_attempt(confirm_key)
    except RateLimitExceededError as exc:
        return _rate_limited_response(exc, message="Too many attempts. Try again later.")

    invalid = _envelope_response(
        status_code=400,
        data=None,
        error={"code": "INVALID_CODE", "message": "That code didn't work. Check it and try again."},
    )
    user = get_user_by_email(request.email.strip())
    if user is None:
        return invalid
    try:
        password_reset_repository.redeem_code_and_set_password(
            user["id"], request.code.strip(), hash_password(new_password)
        )
    except password_reset_repository.CodeExpiredError:
        return _envelope_response(
            status_code=400,
            data=None,
            error={"code": "CODE_EXPIRED", "message": "That code expired. Request a new one."},
        )
    except password_reset_repository.VerificationError:
        return invalid

    # Ownership + new password proven: clear the throttles the user may
    # have tripped while locked out, and sign them in directly.
    auth_rate_limit_repository.clear_auth_attempts(confirm_key)
    auth_rate_limit_repository.clear_auth_attempts(f"login:{request.email.strip().lower()}")
    refreshed = get_user_by_id(user["id"])
    token = create_access_token(user["id"])
    return _envelope_response(data={"token": token, "user": refreshed})


@app.get("/api/v1/auth/me")
def get_me(current_user_id: str = Depends(required_user_id)) -> JSONResponse:
    user = get_user_by_id(current_user_id)
    if user is None:
        return _envelope_response(
            status_code=404,
            data=None,
            error={"code": "USER_NOT_FOUND", "message": "Authenticated user no longer exists."},
        )
    return _envelope_response(data=user)


@app.delete("/api/v1/auth/me")
def delete_me(current_user_id: str = Depends(required_user_id)) -> JSONResponse:
    """Delete the authenticated user's account and all their data.

    Satisfies the app-store account-deletion requirement. Cascades remove
    completions, grades, review schedule, and grade attempts; the user's
    auth-attempt rows are cleared too (see repository.delete_user).
    """
    if not delete_user(current_user_id):
        return _envelope_response(
            status_code=404,
            data=None,
            error={"code": "USER_NOT_FOUND", "message": "Authenticated user no longer exists."},
        )
    return _envelope_response(data={"deleted": True})


@app.get("/api/v1/paths/{path_id}")
def get_path(path_id: str) -> JSONResponse:
    path = path_repository.get_path(path_id)
    if path is None:
        return _envelope_response(
            status_code=404,
            data=None,
            error={"code": "PATH_NOT_FOUND", "message": f"No path found for id '{path_id}'."},
        )
    return _envelope_response(data=path)


@app.get("/api/v1/units/{unit_id}")
def get_unit(
    unit_id: str,
    current_user_id: str = Depends(required_user_id),  # noqa: ARG001 — auth gate
) -> JSONResponse:
    unit = unit_repository.get_unit(unit_id, published_only=True)
    if unit is None:
        return _envelope_response(
            status_code=404,
            data=None,
            error={"code": "UNIT_NOT_FOUND", "message": f"No unit found for id '{unit_id}'."},
        )
    return _envelope_response(data=unit)


# NOTE: POST /api/v1/completions was removed deliberately. It recorded a
# completion with no grader involvement, letting any authenticated client
# mark the whole path complete via curl — bypassing the product's core
# loop. Completions are now recorded exclusively by the grade flow
# (POST /units/{id}/grade). GET /api/v1/completions (the read used for
# cross-device sync) remains below.


@app.get("/api/v1/completions")
def list_completions(
    current_user_id: str = Depends(required_user_id),
) -> JSONResponse:
    """Return every completion for the authenticated user, newest first.

    Lets clients seed their local completion cache after sign-in or when
    moving to a new device, so per-user completion state survives across
    installs.
    """
    completions = completion_repository.list_completions(user_id=current_user_id)
    return _envelope_response(data=completions)


@app.get("/api/v1/review-schedule")
def get_review_schedule(
    due_before: str | None = Query(default=None),
    current_user_id: str = Depends(required_user_id),
) -> JSONResponse:
    """F5 / D5 — spaced reviews due for the authenticated user.

    `due_before` is an optional ISO-8601 timestamp that MUST carry
    a UTC offset; omitted means "due right now" (server NOW()).
    Ordered by due_at then unit position. Malformed or
    timezone-naive `due_before` is a 400 — a request-boundary
    input, validated here, not deeper. Offset-less timestamps are
    rejected (not silently assumed UTC) because Postgres would
    interpret them in the session timezone, making the same string
    filter different rows across environments.
    """
    parsed_due_before: datetime | None = None
    if due_before is not None:
        try:
            parsed_due_before = datetime.fromisoformat(due_before)
        except ValueError:
            return _envelope_response(
                status_code=400,
                data=None,
                error={
                    "code": "INVALID_DUE_BEFORE",
                    "message": (
                        f"due_before '{due_before}' is not a valid ISO-8601 "
                        "timestamp."
                    ),
                },
            )
        if parsed_due_before.tzinfo is None:
            return _envelope_response(
                status_code=400,
                data=None,
                error={
                    "code": "INVALID_DUE_BEFORE",
                    "message": (
                        f"due_before '{due_before}' must include a UTC "
                        "offset (e.g. '2026-05-18T10:00:00+00:00'); "
                        "offset-less timestamps are ambiguous."
                    ),
                },
            )
    due = review_repository.list_due(
        user_id=current_user_id,
        due_before=parsed_due_before,
    )
    return _envelope_response(data=due)


@app.post("/api/v1/review-schedule/{unit_id}/reviewed")
def post_review_reviewed(
    unit_id: str,
    current_user_id: str = Depends(required_user_id),
) -> JSONResponse:
    """F5 / D6 — mark a due review done; advance it one ladder step.

    404 REVIEW_NOT_SCHEDULED if the (user, unit) pair was never
    completed (so never seeded) — nothing to advance. 409
    REVIEW_NOT_DUE if it is scheduled but not yet due (D6
    amendment) — something to advance, just not yet.
    """
    try:
        result = review_repository.mark_reviewed(
            user_id=current_user_id,
            unit_id=unit_id,
        )
    except review_repository.ReviewNotScheduledError:
        return _envelope_response(
            status_code=404,
            data=None,
            error={
                "code": "REVIEW_NOT_SCHEDULED",
                "message": (
                    f"No review scheduled for unit '{unit_id}' — it was "
                    "never completed."
                ),
            },
        )
    except review_repository.ReviewNotDueError:
        return _envelope_response(
            status_code=409,
            data=None,
            error={
                "code": "REVIEW_NOT_DUE",
                "message": (
                    f"Review for unit '{unit_id}' is not due yet; ticking "
                    "early would bypass the spaced-review cadence."
                ),
            },
        )
    return _envelope_response(data=result)


@app.post("/api/v1/units/{unit_id}/grade")
def post_grade(
    unit_id: str,
    request: GradeRequest,
    current_user_id: str = Depends(required_user_id),
) -> JSONResponse:
    """F4 — grade the user's open-ended decision-prompt answer.

    Per docs/strategy/STRATEGY.md § Loop step 4 + T2: per-criterion Met/Not Met
    with confidence + rationale + answer-quote. The unit's rubric and
    decision prompt must already be authored (chunk 6 ingest).
    """
    # Reject a token whose account was deleted before it does any DB write
    # (the grade rate-limit row and the completion both FK-reference the user).
    if get_user_by_id(current_user_id) is None:
        return _account_gone_response()

    unit = unit_repository.get_unit(unit_id, published_only=True)
    if unit is None:
        return _envelope_response(
            status_code=404,
            data=None,
            error={"code": "UNIT_NOT_FOUND", "message": f"No unit found for id '{unit_id}'."},
        )
    rubric = unit.get("rubric") or {}
    if not (rubric.get("criteria") or []):
        return _envelope_response(
            status_code=409,
            data=None,
            error={
                "code": "UNIT_NOT_GRADABLE",
                "message": f"Unit '{unit_id}' has no rubric criteria; nothing to grade.",
            },
        )

    # Strict-order guarantee: a unit can't be completed until its
    # prerequisite chain is. The app gates this in the UI, but ordered
    # progression is a product guarantee (STRATEGY core loop), so it's
    # enforced server-side too — a direct API client can't complete units
    # out of order. Checked before the paid model call so an out-of-order
    # attempt costs nothing.
    prereq_ids = unit.get("prereqUnitIds") or []
    if prereq_ids:
        completed_ids = {
            c["unitId"] for c in completion_repository.list_completions(current_user_id)
        }
        missing = [p for p in prereq_ids if p not in completed_ids]
        if missing:
            return _envelope_response(
                status_code=409,
                data=None,
                error={
                    "code": "PREREQ_NOT_MET",
                    "message": "Complete the prerequisite unit(s) before this one.",
                    "missingPrereqUnitIds": missing,
                },
            )

    # Cost guard (OWASP LLM10): cap paid grade calls per user. Recorded
    # before the model call so abusive attempts count even if grading
    # then fails.
    try:
        rate_limit_repository.check_and_record_grade_attempt(current_user_id)
    except RateLimitExceededError as exc:
        return _rate_limited_response(
            exc,
            message=(
                f"Grade rate limit reached ({exc.limit} per "
                f"{exc.window_seconds}s). Try again later."
            ),
        )

    try:
        grader_output = grade_decision_answer(unit, request.answer)
    except AIServiceError as exc:
        # Log the detail (which may include raw provider error text) server-side
        # only; return a generic message so internals aren't leaked to clients.
        LOGGER.warning("grade failed for unit %s: %s", unit_id, exc)
        return _envelope_response(
            status_code=502,
            data=None,
            error={
                "code": exc.code,
                "message": "Grading is temporarily unavailable. Please try again.",
            },
        )

    # T2 amendment (2026-06): completion requires the answer to hold up —
    # AT MOST ONE criterion Not met, and the answer not flagged. Below
    # that bar nothing is persisted: the grades return inline as
    # calibration, the unit stays incomplete, and the learner revises and
    # resubmits. (Grades are keyed to completions, so a non-completing
    # attempt has nowhere to persist by design.)
    missed = sum(1 for g in grader_output.grades if not g["met"])
    completed = (not grader_output.flagged) and missed <= 1
    if not completed:
        return _envelope_response(
            data={
                "completed": False,
                "completion": None,
                "grades": [
                    {
                        "id": 0,
                        "completionId": None,
                        "criterionId": g["criterion_id"],
                        "met": g["met"],
                        "confidence": g["confidence"],
                        "rationale": g["rationale"],
                        "flagged": grader_output.flagged,
                        "createdAt": None,
                    }
                    for g in grader_output.grades
                ],
                "flagged": grader_output.flagged,
                "answerQuotes": [
                    {"criterionId": g["criterion_id"], "quote": g["answer_quote"]}
                    for g in grader_output.grades
                ],
            }
        )

    # Only commit a completion + grades if the grader call succeeded.
    try:
        completion_result = completion_repository.record_completion(
            user_id=current_user_id,
            unit_id=unit_id,
        )
    except completion_repository.UnitNotFoundError:
        # Race: unit deleted between the lookup above and now.
        return _envelope_response(
            status_code=404,
            data=None,
            error={"code": "UNIT_NOT_FOUND", "message": f"No unit found for id '{unit_id}'."},
        )

    completion = completion_result["completion"]
    grades = grade_repository.upsert_grades(
        completion_id=completion["id"],
        grades=grader_output.grades,
        flagged=grader_output.flagged,
        user_id=current_user_id,
    )

    return _envelope_response(
        data={
            "completed": True,
            "completion": completion,
            "grades": grades,
            "flagged": grader_output.flagged,
            # answer_quote isn't persisted (no column in migration 019);
            # return it inline so the UI can surface it without a schema
            # change. Tracked as a follow-up.
            "answerQuotes": [
                {"criterionId": g["criterion_id"], "quote": g["answer_quote"]}
                for g in grader_output.grades
            ],
        }
    )
