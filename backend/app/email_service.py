"""Outbound transactional email — verification codes.

Provider-agnostic by design: `EmailSender` is the seam, `get_email_sender`
picks the implementation from config. Today that's Resend's HTTP API
(chosen over SMTP because Railway has a history of blocking outbound SMTP
ports) with a logging fallback for local dev and CI, where no real mail
must ever be sent. Swapping providers is one new sender class + env vars.
"""
from __future__ import annotations

import logging
from typing import Protocol

import httpx

from .config import EMAIL_FROM, RESEND_API_KEY

LOGGER = logging.getLogger(__name__)

RESEND_API_URL = "https://api.resend.com/emails"
SEND_TIMEOUT_SECONDS = 10.0


class EmailSendError(RuntimeError):
    """Raised when an email could not be handed to the provider."""


class EmailSender(Protocol):
    def send(self, *, to: str, subject: str, text: str) -> None: ...


class LoggingEmailSender:
    """Dev/CI sender: logs instead of sending.

    The body (which contains the verification code) is logged on purpose —
    this sender only runs when RESEND_API_KEY is unset, which the
    production config gate forbids when verification is required.
    """

    def send(self, *, to: str, subject: str, text: str) -> None:
        LOGGER.info("email (not sent, no provider configured) to=%s subject=%r body=%r", to, subject, text)


class ResendEmailSender:
    def __init__(self, api_key: str, from_address: str) -> None:
        self._api_key = api_key
        self._from = from_address

    def send(self, *, to: str, subject: str, text: str) -> None:
        try:
            response = httpx.post(
                RESEND_API_URL,
                headers={"Authorization": f"Bearer {self._api_key}"},
                json={"from": self._from, "to": [to], "subject": subject, "text": text},
                timeout=SEND_TIMEOUT_SECONDS,
            )
        except httpx.HTTPError as exc:
            raise EmailSendError(f"Resend request failed: {exc}") from exc
        if response.status_code >= 400:
            # Body may contain provider detail; keep it server-side only.
            raise EmailSendError(
                f"Resend returned {response.status_code}: {response.text[:500]}"
            )


def get_email_sender() -> EmailSender:
    if RESEND_API_KEY:
        return ResendEmailSender(RESEND_API_KEY, EMAIL_FROM)
    return LoggingEmailSender()


def send_verification_email(to: str, code: str) -> None:
    """Send the 6-digit verification code. Raises EmailSendError on failure."""
    get_email_sender().send(
        to=to,
        subject=f"{code} is your Perpenda verification code",
        text=(
            f"Your Perpenda verification code is: {code}\n\n"
            "Enter it in the app to confirm your email address. "
            "The code expires in 15 minutes.\n\n"
            "If you didn't create a Perpenda account, you can ignore this email."
        ),
    )


def send_password_reset_email(to: str, code: str) -> None:
    """Send the 6-digit password-reset code. Raises EmailSendError on failure."""
    get_email_sender().send(
        to=to,
        subject=f"{code} is your Perpenda password reset code",
        text=(
            f"Your Perpenda password reset code is: {code}\n\n"
            "Enter it in the app along with your new password. "
            "The code expires in 15 minutes.\n\n"
            "If you didn't request a password reset, you can ignore this "
            "email — your password is unchanged."
        ),
    )
