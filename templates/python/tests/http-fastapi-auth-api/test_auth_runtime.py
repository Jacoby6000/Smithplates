from __future__ import annotations

import sys
from pathlib import Path
from typing import cast

from fastapi import FastAPI
from fastapi.testclient import TestClient

CASE_DIR = Path(__file__).parent
sys.path.insert(0, str(CASE_DIR / "expected" / "src"))

from generated.example.auth_api.api_exception_handler import DefaultFallbackApiExceptionHandler  # noqa: E402
from generated.example.auth_api.app_factory import create_app  # noqa: E402
from generated.example.auth_api.app_services import ApiServices, AuthContext, AuthCredential  # noqa: E402
from generated.example.auth_api.operation_bindings import (  # noqa: E402
    OPERATION_AUTH_BINDINGS,
    OperationAuthBinding,
)
from generated.example.auth_output import AuthOutput  # noqa: E402


class Services:
    def __init__(self) -> None:
        self.calls: list[tuple[str, AuthContext | None]] = []

    async def required(self, auth: AuthContext) -> AuthOutput:
        self.calls.append(("required", auth))
        return AuthOutput(authenticated=True)

    async def optional(self, auth: AuthContext | None) -> AuthOutput:
        self.calls.append(("optional", auth))
        return AuthOutput(authenticated=auth is not None)

    async def public(self) -> AuthOutput:
        self.calls.append(("public", None))
        return AuthOutput(authenticated=False)


class Verifier:
    def __init__(self) -> None:
        self.credentials: list[AuthCredential] = []

    async def verify(self, credential: AuthCredential) -> object | None:
        self.credentials.append(credential)
        if credential.value.startswith("good-"):
            return {"credential": credential.value}
        return None


def make_client() -> tuple[TestClient, Services, Verifier]:
    services = Services()
    verifier = Verifier()
    app = create_app(
        ApiServices(auth_api=services),
        DefaultFallbackApiExceptionHandler(),
        verifier,
    )
    return TestClient(app), services, verifier


def test_required_auth_uses_modeled_priority_and_dispatches_context() -> None:
    client, services, verifier = make_client()

    assert client.get("/required").status_code == 401
    response = client.get(
        "/required",
        headers={
            "Authorization": "Bearer bad-token",
            "X-API-Key": "ApiKey good-api-key",
        },
    )

    assert response.status_code == 200
    assert [credential.scheme_id for credential in verifier.credentials] == [
        "smithy.api#httpBearerAuth",
        "smithy.api#httpApiKeyAuth",
    ]
    operation, auth = services.calls[-1]
    assert operation == "required"
    assert auth is not None
    assert auth.scheme_id == "smithy.api#httpApiKeyAuth"
    assert auth.identity == {"credential": "good-api-key"}


def test_optional_auth_only_allows_anonymous_when_no_credential_was_supplied() -> None:
    client, services, _ = make_client()

    assert client.get("/optional").status_code == 200
    assert services.calls == [("optional", None)]
    assert client.get("/optional", headers={"X-API-Key": "malformed"}).status_code == 401
    assert services.calls == [("optional", None)]


def test_cookie_auth_and_public_operation() -> None:
    client, services, _ = make_client()

    client.cookies.set("session", "good-cookie")
    assert client.get("/required").status_code == 200
    assert services.calls[-1][1] == AuthContext(
        scheme_id="smithplates.codegen.http#httpCookieAuth",
        identity={"credential": "good-cookie"},
    )
    assert client.get("/public", headers={"Authorization": "not bearer"}).status_code == 200
    assert services.calls[-1] == ("public", None)


def test_missing_verifier_and_unknown_scheme_fail_closed() -> None:
    client, services, _ = make_client()
    del cast(FastAPI, client.app).state.auth_verifier

    assert client.get("/optional").status_code == 200
    client.cookies.set("session", "good-cookie")
    assert client.get("/optional").status_code == 500
    client.cookies.clear()
    assert client.get("/required").status_code == 500

    original = OPERATION_AUTH_BINDINGS["optional"]
    OPERATION_AUTH_BINDINGS["optional"] = OperationAuthBinding(
        scheme_ids=("example#unknown",),
        allows_anonymous=True,
    )
    try:
        assert client.get("/optional").status_code == 500
    finally:
        OPERATION_AUTH_BINDINGS["optional"] = original

    assert [name for name, _ in services.calls] == ["optional"]


def test_duplicate_authentication_credentials_are_rejected() -> None:
    client, services, _ = make_client()

    assert (
        client.get(
            "/required",
            headers=[
                ("Authorization", "Bearer good-first"),
                ("Authorization", "Bearer good-second"),
            ],
        ).status_code
        == 401
    )
    assert client.get("/required", headers={"Cookie": "session=good-first; session=good-second"}).status_code == 401
    assert services.calls == []
